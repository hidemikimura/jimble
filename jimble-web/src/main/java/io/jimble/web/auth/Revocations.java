package io.jimble.web.auth;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FrameworkTables;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.internal.Docs;
import io.jimble.util.log.Log;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 利用者ごとの失効の世代（要件 F-W-33 / D-199）
 *
 * <p>
 * <b>{@link Auth} だけが使う。</b>公開 API は {@link Auth#revoke} / {@link Auth#revokeOthers} で、
 * ここは表の読み書きと控えだけを持つ。
 * </p>
 *
 * <h2>作り</h2>
 * <p>
 * 表 {@code auth_revocation} に <b>(種別, 利用者 ID) → 世代</b> を持つ。
 * {@link Auth#login} がその時点の世代をセッションに入れ、{@link Auth#guard} が比べる。
 * <b>セッションの世代 &lt; 表の世代</b>なら、そのログインは締め出されている。
 * </p>
 *
 * <p>
 * <b>行が無い = 世代 0。</b>上げる前のセッション（世代の鍵が無い）も 0 なので、
 * 版を上げた日に誰もログアウトされない。
 * </p>
 *
 * <h2>時刻ではなく番号にしている理由</h2>
 * <p>
 * 「この時刻より前のログインは無効」だと、<b>台ごとの時計のずれで、入ったばかりの人が弾かれる</b>。
 * 番号なら DB の1行を足すだけで、比べるのも {@code <} だけである。
 * </p>
 *
 * <h2>控え（D-274）</h2>
 * <p>
 * 締め出すたびに、利用者の行と一緒に<b>全体の世代</b>（種別 {@value #GLOBAL_REALM}・ID 0 の行）も上げる。
 * 各台は {@code auth.revocation.cache_ttl}（既定 5 秒）ごとに<b>全体の世代の1行だけ</b>を引き、
 * 変わっていなければ利用者ごとの控えを使い続ける（最長 {@link #MAX_AGE}）。変わっていたら控えを全部捨てる。
 * </p>
 *
 * <p>
 * 2.5.1 までは利用者ごとの控えが 5 秒で切れていたので、10〜60 秒おきに操作する人が多いサイトでは
 * <b>ログイン中のほぼすべてのリクエストが DB を引いていた</b>。控えが 1 万件で丸ごと消えるのも、混んだサイトで重なった。
 * </p>
 */
final class Revocations {

	/** 控えの上限。溢れたら全部捨てる（引き直すだけなので、正しさは変わらない） */
	static final int CACHE_LIMIT = 100_000;

	/**
	 * 利用者ごとの控えを使う最長の時間
	 *
	 * <p>
	 * 全体の世代が変わらなくても、これを過ぎたら引き直す。<b>版を上げている途中の保険</b>である
	 * （2.5.1 以前の台は全体の世代を上げないので、そちらで締め出した人に気づくのは、これが過ぎたとき）。
	 * </p>
	 */
	static final java.time.Duration MAX_AGE = java.time.Duration.ofMinutes(1);

	/** 全体の世代を持つ行の種別（{@link Auth} の種別には使えない文字） */
	static final String GLOBAL_REALM = "*";

	/* 控え：(種別, ID) → 世代と、引いたときの代と時刻 */
	private static final ConcurrentHashMap<String, Cached> CACHE = new ConcurrentHashMap<>();

	/* 控えの代。全体の世代が変わったら進める（それより前の控えは使わない） */
	private static volatile long epoch = 0;

	/* 最後に見た全体の世代 */
	private static volatile long watermark = Long.MIN_VALUE;

	/* 最後に全体の世代を見た時刻（System.nanoTime） */
	private static volatile long checkedAt = 0;

	/* 全体の世代を見にいくのは1人だけ */
	private static final java.util.concurrent.locks.ReentrantLock WATERMARK_LOCK = new java.util.concurrent.locks.ReentrantLock();

	/* まだ全体の世代を見ていない */
	private static volatile boolean watermarkChecked = false;

	/* 表を作ったか */
	private static volatile boolean initialized = false;

	/* DB が無いことを言ったか */
	private static volatile boolean warnedNoDb = false;

	/**
	 * 控え
	 *
	 * @param generation	世代
	 * @param epoch			引いたときの控えの代
	 * @param loadedAt		引いた時刻（{@link System#nanoTime()}）
	 */
	private record Cached (long generation, long epoch, long loadedAt) {}

	private Revocations () {
	}

	// region Auth から

	/**
	 * ログインのときに入れる世代
	 *
	 * <p>
	 * <b>控えではなく DB から引く</b>（そして控えを書き換える）。
	 * 控えから入れると、ほかの台で締め出した直後にこの台でログインしたとき<b>古い世代が入り</b>、
	 * 控えが切れた瞬間に<b>いまログインした人が弾かれる</b>。
	 * </p>
	 *
	 * @param realm		種別
	 * @param userId	利用者 ID
	 * @return	世代。使っていなければ 0
	 * @throws IllegalStateException	引けなかった場合（ログインさせない）
	 */
	static long forLogin (String realm, long userId) {

		if (!inUse()) {
			return 0;
		}

		return load(realm, userId);

	}

	/**
	 * このログインは締め出されているか
	 *
	 * @param realm				種別
	 * @param userId			利用者 ID
	 * @param sessionGeneration	セッションに入っている世代（無ければ 0）
	 * @return	締め出されている場合 = true
	 * @throws IllegalStateException	引けなかった場合（通さない）
	 */
	static boolean isRevoked (String realm, long userId, long sessionGeneration) {

		if (!inUse()) {
			return false;
		}

		return sessionGeneration < cached(realm, userId);

	}

	/**
	 * 世代を1つ上げる
	 *
	 * @param realm		種別
	 * @param userId	利用者 ID
	 * @return	上げたあとの世代
	 * @throws IllegalStateException	使っていない・書けなかった場合
	 */
	static long bump (String realm, long userId) {

		if (!RevocationConf.enabled()) {
			throw new IllegalStateException(RevocationConf.KEY_ENABLED + " = false なので、締め出せません。"
				+ "締め出すなら true にしてください" + Docs.see("auth"));
		}

		if (!DBUtil.isUseDB()) {
			throw new IllegalStateException("締め出すには DB が要ります（世代を auth_revocation に持ちます）" + Docs.see("auth"));
		}

		install();

		long now = System.currentTimeMillis();

		try (DB db = DBUtil.getMainDB()) {

			String table = table(db);

			StringBuilder sql = new StringBuilder("INSERT INTO %s (realm, user_id, generation, revoked_at) VALUES (?, ?, 1, ?)"
				.formatted(table));
			sql.append(db.dialect().onDuplicateKeyUpdate(List.of("realm", "user_id")));
			sql.append("generation = %s.generation + 1, revoked_at = ".formatted(table));
			db.dialect().insertedValue(sql, null, "revoked_at");

			long at = epoch;

			/*
			 * 利用者の行と全体の世代を、<b>一緒に</b>上げる（D-274）。
			 * 全体の世代だけ上がらないと、ほかの台は控えを最長 MAX_AGE まで使い続ける
			 */
			long generation = db.transactionResult(tx -> {
				db.execute(sql.toString(), realm, userId, now);
				db.execute(sql.toString(), GLOBAL_REALM, 0L, now);
				return select(db, realm, userId);
			});

			put(realm, userId, generation, at);

			return generation;

		} catch (IllegalStateException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			throw new IllegalStateException("締め出せませんでした（世代を書けませんでした）: realm=%s user_id=%d"
				.formatted(realm, userId), ex);
		}

	}

	/**
	 * 控えを捨てる（テストから）
	 */
	static void clearCache () {

		CACHE.clear();
		epoch++;
		watermark = Long.MIN_VALUE;
		watermarkChecked = false;

	}

	// endregion

	// region 中身

	/**
	 * 比べるか
	 *
	 * <p>
	 * DB が無ければ比べない。{@link #bump} が必ず例外になるので、<b>締め出された人はそもそもいない</b>。
	 * </p>
	 */
	private static boolean inUse () {

		if (!RevocationConf.enabled()) {
			return false;
		}

		if (!DBUtil.isUseDB()) {

			if (!warnedNoDb) {
				warnedNoDb = true;
				Log.warn("auth.revocation は DB が要ります。DB が無いので、締め出しを見ません（Auth.revoke は例外になります）");
			}

			return false;

		}

		install();

		return true;

	}

	/**
	 * 控えから引く（切れていれば DB から）
	 */
	private static long cached (String realm, long userId) {

		long ttl = RevocationConf.cacheTtl().toNanos();

		if (ttl <= 0) {
			return load(realm, userId);
		}

		refreshWatermarkIfDue(ttl);

		Cached hit = CACHE.get(key(realm, userId));

		if (hit != null && hit.epoch() == epoch && System.nanoTime() - hit.loadedAt() < MAX_AGE.toNanos()) {
			return hit.generation();
		}

		return load(realm, userId);

	}

	/**
	 * 全体の世代を見る（cache_ttl ごとに1回。変わっていたら控えの代を進める）
	 *
	 * <p>
	 * 見ている人がいれば待たない（いまの控えを使う）。<b>まだ1度も見ていなければ、見終わるまで待つ</b>。
	 * </p>
	 *
	 * @param ttl	見る間隔（ナノ秒）
	 */
	private static void refreshWatermarkIfDue (long ttl) {

		if (watermarkChecked && System.nanoTime() - checkedAt < ttl) {
			return;
		}

		if (watermarkChecked) {
			if (!WATERMARK_LOCK.tryLock()) {
				return;
			}
		} else {
			WATERMARK_LOCK.lock();
		}

		try {

			if (watermarkChecked && System.nanoTime() - checkedAt < ttl) {
				return;
			}

			long global;

			try (DB db = DBUtil.getMainDB()) {
				global = select(db, GLOBAL_REALM, 0L);
			} catch (RuntimeException ex) {
				// 引けないなら閉じる（load と同じ）
				throw new IllegalStateException("締め出しの世代（全体）を引けませんでした" + Docs.see("auth"), ex);
			}

			if (global != watermark) {
				/*
				 * <b>先に代を進めてから、控えを捨てる。</b>
				 * 引いている最中の人は、進める前の代で控えるので、捨てたあとに入っても使われない
				 */
				epoch++;
				CACHE.clear();
				watermark = global;
			}

			checkedAt = System.nanoTime();
			watermarkChecked = true;

		} finally {
			WATERMARK_LOCK.unlock();
		}

	}

	/**
	 * DB から引いて控える
	 */
	private static long load (String realm, long userId) {

		// 引く前の代で控える（引いている最中に全体の世代が変わったら、この控えは使われない）
		long at = epoch;

		try (DB db = DBUtil.getMainDB()) {

			long generation = select(db, realm, userId);

			put(realm, userId, generation, at);

			return generation;

		} catch (RuntimeException ex) {
			/*
			 * <b>通さない。</b>引けないのに通すと、締め出した人が入る。
			 * ログインの判定が引けないなら閉じる（F-W-28 の「既定は要ログイン」と同じ向き）。
			 */
			throw new IllegalStateException("締め出しの世代を引けませんでした: realm=%s user_id=%d"
				.formatted(realm, userId) + Docs.see("auth"), ex);
		}

	}

	/**
	 * 世代を読む（行が無ければ 0）
	 */
	private static long select (DB db, String realm, long userId) {

		return db.select("SELECT generation FROM %s WHERE realm = ? AND user_id = ?".formatted(table(db))
				, realm, userId)
			.map(row -> row.getLong("generation"))
			.orElse(0L);

	}

	/**
	 * 控える
	 */
	private static void put (String realm, long userId, long generation, long at) {

		if (CACHE.size() >= CACHE_LIMIT) {
			CACHE.clear();
		}

		CACHE.put(key(realm, userId), new Cached(generation, at, System.nanoTime()));

	}

	/**
	 * 控えの鍵
	 */
	private static String key (String realm, long userId) {

		return realm + '\u0000' + userId;

	}

	/**
	 * テーブルを作る
	 *
	 * <p>
	 * <b>作れなければ投げる。</b>remember-me は「無くてもログインは通す」ので作れなければ覚えないで進むが、
	 * 失効は逆で、<b>無いことにすると守りが消える</b>。
	 * </p>
	 */
	private static void install () {

		if (initialized) {
			return;
		}

		synchronized (Revocations.class) {

			if (initialized) {
				return;
			}

			String name = FrameworkTables.AUTH_REVOCATION;

			DBVersion dbVersion = new DBVersion(name, "ログインの失効の世代");

			dbVersion.add(1)
				.mysql("""
					create table `%s`
					(
						realm        varchar(64)  not null default ''
						, user_id      bigint       not null
						, generation   bigint       not null
						, revoked_at   bigint       not null
						, primary key (realm, user_id)
					) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
					""".formatted(name, dbVersion.placeholder()))
				.postgresql("""
					create table "%s"
					(
						realm        varchar(64)  not null default ''
						, user_id      bigint       not null
						, generation   bigint       not null
						, revoked_at   bigint       not null
						, primary key (realm, user_id)
					)
					""".formatted(name));

			if (!dbVersion.apply(DBUtil.getMainDB())) {
				throw new IllegalStateException("ログインの失効の表（" + name + "）を作れませんでした（直前のエラーログを見てください）"
					+ Docs.see("auth"));
			}

			initialized = true;

		}

	}

	/**
	 * テーブル名（製品ごとの囲みを付ける）
	 */
	private static String table (DB db) {

		return db.dialect().identifier(FrameworkTables.AUTH_REVOCATION);

	}

	// endregion

}
