package io.jimble.web.auth;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FrameworkTables;
import io.jimble.db.dialect.Sqls;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.data.Data;
import io.jimble.util.hash.Hash;
import io.jimble.util.log.Log;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ログインの失敗を数えて、次の試行を待たせる（要件 F-W-29）
 *
 * <h2>流量制限（{@code RateLimit}）とどう違うか</h2>
 * <table>
 *   <caption>数える単位</caption>
 *   <tr><th></th><th>単位</th><th>破られ方</th></tr>
 *   <tr><td>流量制限</td><td><b>IP ごと</b></td>
 *       <td>IP を替えられる。<b>1つのアカウントに 1000 個の IP から1回ずつ</b>来ると発火しない</td></tr>
 *   <tr><td>これ</td><td><b>アカウントごと</b></td>
 *       <td>誰から来ても数える。代わりに<b>嫌がらせでロックされうる</b>（下記）</td></tr>
 * </table>
 *
 * <p><b>どちらか片方では足りない。</b>両方掛けること。</p>
 *
 * <h2>止めずに、遅くする</h2>
 * <p>
 * <b>「N 回で M 分ロック」にしていない。</b>アカウント単位で止める仕組みは
 * <b>そのまま嫌がらせの道具になる</b>——わざと間違えるだけで締め出せる。
 * </p>
 *
 * <p>
 * 代わりに<b>待ち時間を倍にしていく</b>。攻撃者から見た試行速度は実質ゼロになり、
 * <b>正規の利用者は数秒待つだけ</b>で済む。
 * </p>
 *
 * <pre>
 * 失敗 1〜3 回目 … 待たない（打ち間違い）
 * 4 回目 … 1 秒
 * 5 回目 … 2 秒
 * 6 回目 … 4 秒     …… 上限（既定 300 秒）まで
 * </pre>
 *
 * <h2>使い方</h2>
 * <p>
 * <b>直に呼ばなくてよい。</b>{@link Auth#attemptLogin} が
 * 「待たせる → 照合する → 成功なら消す」までやる。
 * <b>成功したときに消し忘れる</b>と、正しく入れた人が翌日待たされるので、
 * 忘れようのない形にしてある。
 * </p>
 *
 * <p>
 * ここを直に使うのは、<b>管理画面から手で解除する</b>ときくらいである（{@link #clear}）。
 * </p>
 */
public final class Lockout {

	private Lockout () {
	}

	/* テーブルを作ったか */
	private static volatile boolean initialized = false;

	/* DB が無いことを言ったか（毎回は言わない） */
	private static volatile boolean warnedNoDb = false;

	/** 掃除を試みる間隔 */
	private static final Duration CLEANUP_INTERVAL = Duration.ofHours(1);

	/* 最後に掃除した時刻 */
	private static final AtomicReference<Instant> lastCleanup = new AtomicReference<>(Instant.now());

	// region 数える

	/**
	 * あと何秒待たせるか
	 *
	 * <p>0 なら待たせない。</p>
	 *
	 * @param key	数える単位（ログイン ID など）
	 * @return	秒
	 */
	public static long waitSeconds (String key) {

		if (!isUsable()) {
			return 0;
		}

		Data row = row(key);

		if (row == null) {
			return 0;
		}

		long failed = row.getLong("failed_count");
		long lastFailedAt = row.getLong("last_failed_at");

		if (isForgotten(lastFailedAt)) {
			return 0;
		}

		long required = requiredSeconds(failed);

		if (required <= 0) {
			return 0;
		}

		long elapsed = (nowMillis() - lastFailedAt) / 1000;

		return Math.max(0, required - elapsed);

	}

	/**
	 * 失敗を1つ数える
	 *
	 * @param key	数える単位
	 */
	public static void fail (String key) {

		if (!isUsable()) {
			return;
		}

		String hashed = hash(key);
		long now = nowMillis();
		long forgetBefore = now - LockoutConf.forget().toMillis();

		try (DB db = DBUtil.getMainDB()) {

			/*
			 * <b>先に「無ければ作る」。</b>
			 * 「UPDATE して 0 件なら INSERT」にすると、<b>初回に同時に来た2本が
			 * どちらも 0 件を見て、どちらも INSERT し、片方が一意キーで落ちる</b>——
			 * 落ちたほうは<b>1回ぶん数えられない</b>（実際に、並列のテストで落ちた）。
			 *
			 * 作るときの {@code last_failed_at} は 0 にしてある。
			 * <b>すぐ下の UPDATE に「間が空いていたら 1 に戻す」を任せる</b>ためで、
			 * こうすると作った直後でも、既にあった行でも、同じ1文で足せる。
			 */
			db.execute(Sqls.insertIgnoreInto(db.dialect(), FrameworkTables.AUTH_ATTEMPT)
				+ " (attempt_key, failed_count, last_failed_at) VALUES (?, 0, 0)"
				+ Sqls.insertIgnoreTail(db.dialect())
				, hashed);

			/*
			 * <b>読んでから足さない。</b>同じ人に2本同時に来ると、
			 * どちらも「3」を読んで「4」を書き、<b>1回ぶん数えそこねる</b>——
			 * 総当たりは並列で来るので、これは効く。SQL の中で足す。
			 *
			 * <b>間が空いていれば数え直す</b>のも同じ1文でやる。
			 * これが無いと、<b>半年前に3回間違えた人が今日いきなり待たされる</b>。
			 */
			db.update("""
				UPDATE %s
				SET failed_count = CASE WHEN last_failed_at < ? THEN 1 ELSE failed_count + 1 END
					, last_failed_at = ?
				WHERE attempt_key = ?
				""".formatted(table(db)), forgetBefore, now, hashed);

		} catch (Exception ex) {
			Log.error(ex, "ログイン失敗の記録に失敗しました");
		}

		cleanupIfDue();

	}

	/**
	 * 数えたものを消す（成功したとき、または手で解除するとき）
	 *
	 * @param key	数える単位
	 */
	public static void clear (String key) {

		if (!isUsable()) {
			return;
		}

		try (DB db = DBUtil.getMainDB()) {
			db.delete("DELETE FROM %s WHERE attempt_key = ?".formatted(table(db)), hash(key));
		} catch (Exception ex) {
			Log.error(ex, "ログイン失敗の記録を消せませんでした");
		}

	}

	/**
	 * 古い記録を消す
	 *
	 * <p>
	 * <b>ふつうは呼ばなくてよい。</b>{@link #fail} が1時間に1度これを呼ぶ
	 * （DB セッションと同じ遅延削除。要件 F-S-09）。
	 * </p>
	 *
	 * <p>
	 * <b>バッチの常駐スレッドにしていない。</b>記録が増えるのは失敗したときだけなので、
	 * <b>失敗したついでに掃除すれば、増えたぶんだけ確実に減る</b>——
	 * バッチを動かしていない構成で永遠に溜まる、ということが起きない。
	 * </p>
	 *
	 * @return	消した件数
	 */
	public static int cleanup () {

		if (!isUsable()) {
			return 0;
		}

		long limit = nowMillis() - LockoutConf.forget().toMillis();

		try (DB db = DBUtil.getMainDB()) {
			return db.delete("DELETE FROM %s WHERE last_failed_at < ?".formatted(table(db)), limit);
		} catch (Exception ex) {
			Log.error(ex, "ログイン失敗の記録を掃除できませんでした");
			return 0;
		}

	}

	/**
	 * 間隔を見て掃除する
	 */
	private static void cleanupIfDue () {

		Instant last = lastCleanup.get();

		if (Instant.now().isBefore(last.plus(CLEANUP_INTERVAL))) {
			return;
		}

		if (!lastCleanup.compareAndSet(last, Instant.now())) {
			// 誰かが先に始めた
			return;
		}

		cleanup();

	}

	// endregion

	// region 中身

	/**
	 * 失敗回数から、要る待ち時間
	 *
	 * @param failedCount	失敗回数
	 * @return	秒
	 */
	static long requiredSeconds (long failedCount) {

		long over = failedCount - LockoutConf.freeAttempts();

		if (over <= 0) {
			return 0;
		}

		long max = LockoutConf.max().toSeconds();

		/*
		 * <b>先に上限で打ち切る。</b>
		 * 63 回失敗すると 2^62 秒になり、<b>桁が溢れて負になる</b>
		 * （負の待ち時間は「待たなくてよい」なので、<b>数えるほど甘くなる</b>）。
		 */
		if (over > 40) {
			return max;
		}

		long seconds = LockoutConf.base().toSeconds() * (1L << (over - 1));

		return Math.min(seconds, max);

	}

	/**
	 * 間が空いて数え直すか
	 *
	 * @param lastFailedAt	最後に失敗した時刻
	 * @return	数え直す場合 = true
	 */
	private static boolean isForgotten (long lastFailedAt) {

		return nowMillis() - lastFailedAt > LockoutConf.forget().toMillis();

	}

	/**
	 * 1件読む
	 *
	 * @param key	数える単位
	 * @return	行。無ければ null
	 */
	private static Data row (String key) {

		try (DB db = DBUtil.getMainDB()) {
			return db.select("SELECT failed_count, last_failed_at FROM %s WHERE attempt_key = ?"
				.formatted(table(db)), hash(key));
		} catch (Exception ex) {
			Log.error(ex, "ログイン失敗の記録を読めませんでした");
			return null;
		}

	}

	/**
	 * 数える単位をハッシュにする
	 *
	 * <p>
	 * <b>入力されたログイン ID をそのまま行にしない。</b>
	 * そうすると<b>攻撃者が好きな文字列で行を作れる</b>ので、
	 * 存在しない ID でテーブルを膨らませられる（そして<b>ログイン ID が平文で溜まる</b>）。
	 * </p>
	 *
	 * <p>
	 * <b>大文字小文字と前後の空白は無視する。</b>そろえないと、
	 * ログイン ID を照合するときに大小を区別しないアプリでは
	 * <b>{@code alice} / {@code Alice} / {@code ALICE} …… と綴りを変えるだけで
	 * 何回でも試せる</b>（数える単位が別々になるため）。
	 * </p>
	 *
	 * @param key	数える単位
	 * @return	ハッシュ
	 */
	private static String hash (String key) {

		return Hash.sha256(key == null ? "" : key.strip().toLowerCase(Locale.ROOT));

	}

	/**
	 * いま（ミリ秒）
	 *
	 * @return	ミリ秒
	 */
	private static long nowMillis () {

		return Instant.now().toEpochMilli();

	}

	/**
	 * 使えるか（DB があって、有効になっているか）
	 *
	 * @return	使える場合 = true
	 */
	private static boolean isUsable () {

		if (!LockoutConf.enabled()) {
			return false;
		}

		if (!DBUtil.isUseDB()) {

			/*
			 * <b>DB が無ければ何もしない。</b>ここで例外にすると、
			 * DB を使わないアプリがログインを組めなくなる。
			 * ただし<b>黙らない</b>——「効いているつもりで効いていない」がいちばん質が悪い。
			 */
			if (!warnedNoDb) {
				warnedNoDb = true;
				Log.warn("auth.lockout は DB が要ります。DB が無いので、ログインの失敗を数えません");
			}

			return false;

		}

		install();

		return true;

	}

	/**
	 * テーブルを作る
	 */
	private static void install () {

		if (initialized) {
			return;
		}

		synchronized (Lockout.class) {

			if (initialized) {
				return;
			}

			DBVersion dbVersion = new DBVersion(FrameworkTables.AUTH_ATTEMPT, "ログイン失敗回数");

			dbVersion.add(1)
				.mysql("""
					create table auth_attempt
					(
						attempt_key    varchar(64) not null primary key,
						failed_count   bigint      not null,
						last_failed_at bigint      not null
					) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
				""".formatted(dbVersion.placeholder()))
				.postgresql("""
					create table auth_attempt
					(
						attempt_key    varchar(64) not null primary key,
						failed_count   bigint      not null,
						last_failed_at bigint      not null
					)
				""");

			dbVersion.apply(DBUtil.getMainDB());

			initialized = true;

		}

	}

	/**
	 * テーブル名（製品ごとの囲みを付ける）
	 *
	 * @param db	DB
	 * @return	テーブル名
	 */
	private static String table (DB db) {

		return db.dialect().identifier(FrameworkTables.AUTH_ATTEMPT);

	}

	// endregion

}
