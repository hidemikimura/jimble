package io.jimble.web.auth;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FrameworkTables;
import io.jimble.db.version.DBVersion;
import io.jimble.util.data.Data;
import io.jimble.util.hash.Hash;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.Handler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongFunction;

/**
 * ログインしたままにする（remember-me。要件 F-W-30）
 *
 * <h2>使い方</h2>
 * <pre>
 * public class App extends JimbleApp {
 *
 *     {
 *         before(Remember.restore(App::findPrincipal));   // 先に「思い出す」
 *         before(Auth::guard);                            // そのあと見張る
 *
 *         get("/password", Password::show).attribute(Auth.FULL_AUTH, true);
 *     }
 *
 *     // id から誰かを引き直す。<b>役割はここで引くので、剥奪がすぐ効く</b>
 *     private static Principal findPrincipal (long id) { ... }
 *
 * }
 * </pre>
 *
 * <p>
 * ログインのときに {@link #issue} を呼ぶと Cookie を出す
 * （「ログインしたままにする」に印が付いていたときだけ呼ぶ）。
 * </p>
 *
 * <h2>作り</h2>
 * <p>
 * Cookie には <b>{@code selector:validator}</b> の2つを入れる。
 * </p>
 * <table>
 *   <caption>2つに分けている理由</caption>
 *   <tr><th></th><th>Cookie</th><th>DB</th><th>役目</th></tr>
 *   <tr><td>selector</td><td>そのまま</td><td>そのまま</td><td>行を<b>引く</b>ための鍵</td></tr>
 *   <tr><td>validator</td><td>そのまま</td><td><b>ハッシュ</b></td><td>本人かを<b>照合する</b></td></tr>
 * </table>
 *
 * <p>
 * <b>1本にすると、どちらかを諦めることになる。</b>
 * ハッシュだけ保存すると<b>引けない</b>（全行を舐めて照合することになる）。
 * そのまま保存すると<b>DB を読まれた人が全員になりすませる</b>。
 * 引く鍵と照合する値を分ければ、どちらも立つ。
 * </p>
 *
 * <h2>使うたびに回す</h2>
 * <p>
 * 照合が通ったら、<b>validator を作り直して Cookie を出し直す</b>。
 * こうすると、<b>盗まれた Cookie と本物の Cookie は同時に生きられない</b>——
 * 先に使ったほうが回してしまうので、あとから来たほうは<b>回転前の古い値</b>を持っている。
 * それが<b>盗まれた合図</b>になる（{@link RememberConf#grace 猶予}の外なら）。
 * </p>
 *
 * <p>
 * 合図を見つけたら<b>その利用者の記憶を全部消す</b>。
 * 盗んだ側と本人のどちらが先に使ったかは<b>区別できない</b>ので、
 * 片方だけ消すと<b>本人だけが締め出されて盗んだ側が生き残る</b>ことがある。
 * </p>
 *
 * <h2>やらないこと</h2>
 * <p>
 * <b>これで戻ってきた人は「full auth」ではない。</b>
 * パスワード変更や退会のような操作は {@code attribute(Auth.FULL_AUTH, true)} で閉じること
 * （{@link Auth#FULL_AUTH}）。Cookie を盗まれたときの被害は<b>そこで止まる</b>。
 * </p>
 *
 * <p>
 * <b>パスワードを変えたら {@link #forgetAll} を呼ぶこと。</b>
 * これを忘れると、<b>パスワードを変えた意味が無い</b>——
 * 盗まれた Cookie はそのまま使える。
 * </p>
 */
public final class Remember {

	private Remember () {
	}

	/* テーブルを作ったか */
	private static volatile boolean initialized = false;

	/* DB が無いことを言ったか（毎回は言わない） */
	private static volatile boolean warnedNoDb = false;

	/** 掃除を試みる間隔 */
	private static final Duration CLEANUP_INTERVAL = Duration.ofHours(1);

	/* 最後に掃除した時刻 */
	private static final AtomicReference<Instant> lastCleanup = new AtomicReference<>(Instant.now());

	/* 乱数 */
	private static final SecureRandom RANDOM = new SecureRandom();

	/** Cookie の中の区切り */
	private static final String SEPARATOR = ":";

	// region 使う

	/**
	 * 思い出す（{@code before} に置く）
	 *
	 * <p>
	 * <b>{@code before(Auth::guard)} より先に登録すること。</b>
	 * あとに置くと、<b>guard が「ログインしていない」と判断したあとで思い出す</b>ことになり、
	 * 401 のあとにログイン済みになる、という噛み合わない状態になる。
	 * </p>
	 *
	 * <p>
	 * <b>セッションを使わないルート（{@link Auth#NO_SESSION}）では何もしない。</b>
	 * 思い出す先が無いうえ、ここでセッションに触ると
	 * <b>guard が保存先を {@code none} に差し替える前に決まってしまう</b>。
	 * </p>
	 *
	 * @param lookup	id から利用者を引き直す。見つからなければ null を返すこと
	 * @return	{@code before} に渡すもの
	 */
	public static Handler restore (LongFunction<Principal> lookup) {

		if (lookup == null) {
			throw new IllegalArgumentException("id から利用者を引く方法がありません");
		}

		return context -> restore(context, lookup);

	}

	/**
	 * 思い出す
	 *
	 * @param context	コンテキスト
	 * @param lookup	id から利用者を引き直す
	 */
	static void restore (WebContext context, LongFunction<Principal> lookup) {

		if (context.route() == null || !context.route().matched()) {
			return;
		}

		if (context.route().route().attribute(Auth.NO_SESSION)) {
			return;
		}

		String cookie = context.cookies().get(cookieName());

		if (cookie == null || cookie.isEmpty()) {
			// 覚えていない。<b>ここで DB は触らない</b>（ほとんどのリクエストはここで返る）
			return;
		}

		if (Auth.principal(context).isAuthenticated()) {
			// もうログインしている。Cookie は次に切れるまでそのまま
			return;
		}

		if (!isUsable()) {
			return;
		}

		try {
			restoreFromCookie(context, cookie, lookup);
		} catch (Exception ex) {
			// <b>思い出せなくてもリクエストは通す</b>（ログインしていない扱いになるだけ）
			Log.error(ex, "ログインを思い出せませんでした");
		}

		cleanupIfDue();

	}

	/**
	 * 覚える（ログインしたときに呼ぶ）
	 *
	 * <p>
	 * <b>「ログインしたままにする」に印が付いていたときだけ呼ぶこと。</b>
	 * いつも呼ぶと、<b>共用の端末で次の人が入れてしまう</b>。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param principal	覚える相手
	 */
	public static void issue (WebContext context, Principal principal) {

		if (principal == null || !principal.isAuthenticated()) {
			throw new IllegalArgumentException("覚える相手がいません（id が 0 です）");
		}

		if (!isUsable()) {
			return;
		}

		String selector = token();
		String validator = token();
		long now = nowMillis();

		try (DB db = DBUtil.getMainDB()) {

			db.insert("""
				INSERT INTO %s (selector, validator, previous_validator, rotated_at
					, user_id, created_at, last_used_at)
				VALUES (?, ?, '', ?, ?, ?, ?)
				""".formatted(table(db))
				, selector, hash(validator), now, principal.id(), now, now);

		} catch (Exception ex) {
			Log.error(ex, "ログインを覚えられませんでした");
			return;
		}

		writeCookie(context, selector, validator);

	}

	/**
	 * この端末のぶんだけ忘れる（ログアウト）
	 *
	 * <p>
	 * <b>{@link Auth#logout} が呼ぶので、直に呼ばなくてよい。</b>
	 * ログアウトで消し忘れると、<b>次のリクエストでまた入ってしまう</b>。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	public static void forget (WebContext context) {

		String cookie = context.cookies().get(cookieName());

		if (cookie == null || cookie.isEmpty()) {
			return;
		}

		context.cookies().remove(cookieName());

		if (!isUsable()) {
			return;
		}

		String selector = selectorOf(cookie);

		if (selector == null) {
			return;
		}

		try (DB db = DBUtil.getMainDB()) {
			db.delete("DELETE FROM %s WHERE selector = ?".formatted(table(db)), selector);
		} catch (Exception ex) {
			Log.error(ex, "ログインの記憶を消せませんでした");
		}

	}

	/**
	 * その人のぶんを全部忘れる
	 *
	 * <p>
	 * <b>パスワードを変えたら必ず呼ぶこと。</b>
	 * 呼ばないと、<b>パスワードを変えても盗まれた Cookie はそのまま使える</b>——
	 * 変えた意味が無い。
	 * </p>
	 *
	 * <p>「全端末からログアウト」もこれである。</p>
	 *
	 * @param userId	利用者 ID
	 * @return	消した件数
	 */
	public static int forgetAll (long userId) {

		if (!isUsable()) {
			return 0;
		}

		try (DB db = DBUtil.getMainDB()) {
			return db.delete("DELETE FROM %s WHERE user_id = ?".formatted(table(db)), userId);
		} catch (Exception ex) {
			Log.error(ex, "ログインの記憶を消せませんでした: %d".formatted(userId));
			return 0;
		}

	}

	/**
	 * 切れたものを消す
	 *
	 * <p>
	 * <b>ふつうは呼ばなくてよい。</b>思い出すとき・覚えるときに1時間に1度呼んでいる。
	 * </p>
	 *
	 * @return	消した件数
	 */
	public static int cleanup () {

		if (!isUsable()) {
			return 0;
		}

		long now = nowMillis();
		long slidingLimit = now - RememberConf.sliding().toMillis();
		long absoluteLimit = now - RememberConf.absolute().toMillis();

		try (DB db = DBUtil.getMainDB()) {
			return db.delete("DELETE FROM %s WHERE last_used_at < ? OR created_at < ?"
				.formatted(table(db)), slidingLimit, absoluteLimit);
		} catch (Exception ex) {
			Log.error(ex, "ログインの記憶を掃除できませんでした");
			return 0;
		}

	}

	// endregion

	// region 中身

	/**
	 * Cookie から思い出す
	 *
	 * @param context	コンテキスト
	 * @param cookie	Cookie の値
	 * @param lookup	id から利用者を引き直す
	 */
	private static void restoreFromCookie (WebContext context, String cookie, LongFunction<Principal> lookup) {

		String selector = selectorOf(cookie);
		String validator = validatorOf(cookie);

		if (selector == null || validator == null) {
			context.cookies().remove(cookieName());
			return;
		}

		Data row = row(selector);

		if (row == null) {
			/*
			 * <b>これは盗用ではない。</b>掃除で消えたあとや、
			 * 別の端末で「全部忘れる」をしたあとに来ると、ふつうにここへ来る。
			 */
			context.cookies().remove(cookieName());
			return;
		}

		long userId = row.getLong("user_id");

		if (isExpired(row)) {
			delete(selector);
			context.cookies().remove(cookieName());
			return;
		}

		String hashed = hash(validator);
		boolean matched = equalsConstantTime(hashed, row.getString("validator"));
		boolean matchedPrevious = !matched
			&& equalsConstantTime(hashed, row.getString("previous_validator"));

		if (!matched && !matchedPrevious) {
			/*
			 * <b>selector は当たっているのに validator が合わない。</b>
			 * 引くための鍵を持っているということは、<b>Cookie が漏れている</b>。
			 */
			stolen(context, userId, "照合できない validator");
			return;
		}

		if (matchedPrevious && !inGrace(row)) {
			/*
			 * <b>回したあとの古い validator が、猶予を過ぎて使われた。</b>
			 * 本物はもう新しいほうを持っているので、これは<b>盗まれたほう</b>である
			 * （どちらが盗んだ側かは分からないので、両方消す）。
			 */
			stolen(context, userId, "回転前の validator");
			return;
		}

		Principal principal = lookup.apply(userId);

		if (principal == null || !principal.isAuthenticated()) {
			/*
			 * 利用者が消えている（退会など）。<b>記憶も消す</b>——
			 * 残すと、id を作り直したときに<b>別人が入る</b>。
			 */
			forgetAll(userId);
			context.cookies().remove(cookieName());
			return;
		}

		if (matched) {
			rotate(context, selector, row);
		} else {
			/*
			 * 猶予の中。<b>回さない</b>（回すと、遅れて届いた通信のぶんだけ回り続ける）。
			 * いま有効な validator は既に相手へ渡してあるので、<b>ここでは Cookie も出し直さない</b>。
			 */
			touch(selector);
		}

		Auth.loginWithoutPassword(context, principal);

	}

	/**
	 * validator を回して、Cookie を出し直す
	 *
	 * @param context	コンテキスト
	 * @param selector	selector
	 * @param row		いまの行
	 */
	private static void rotate (WebContext context, String selector, Data row) {

		String next = token();
		long now = nowMillis();

		try (DB db = DBUtil.getMainDB()) {

			int updated = db.update("""
				UPDATE %s
				SET validator = ?, previous_validator = ?, rotated_at = ?, last_used_at = ?
				WHERE selector = ? AND validator = ?
				""".formatted(table(db))
				, hash(next), row.getString("validator"), now, now
				, selector, row.getString("validator"));

			if (updated == 0) {
				/*
				 * <b>同時に来たもう1本が先に回した。</b>
				 * ここで書くと<b>相手が渡した validator を上書きしてしまう</b>ので、何もしない
				 * （こちらの Cookie は古いままだが、猶予の中なので次も通る）。
				 */
				return;
			}

		} catch (Exception ex) {
			Log.error(ex, "ログインの記憶を回せませんでした");
			return;
		}

		writeCookie(context, selector, next);

	}

	/**
	 * 使ったことにする（猶予の中で通したとき）
	 *
	 * @param selector	selector
	 */
	private static void touch (String selector) {

		try (DB db = DBUtil.getMainDB()) {
			db.update("UPDATE %s SET last_used_at = ? WHERE selector = ?".formatted(table(db))
				, nowMillis(), selector);
		} catch (Exception ex) {
			Log.error(ex, "ログインの記憶を更新できませんでした");
		}

	}

	/**
	 * 盗まれた合図。その人のぶんを全部消す
	 *
	 * @param context	コンテキスト
	 * @param userId	利用者 ID
	 * @param reason	理由（ログ用）
	 */
	private static void stolen (WebContext context, long userId, String reason) {

		/*
		 * <b>黙って消さない。</b>「たまにログアウトする」と言われたときに、
		 * これが盗用なのか作りの問題なのかを<b>ログでしか区別できない</b>。
		 */
		Log.warn("ログインの記憶が盗まれた可能性があります。全部消します: user_id=%d（%s）"
			.formatted(userId, reason));

		forgetAll(userId);

		context.cookies().remove(cookieName());

	}

	/**
	 * 切れているか
	 *
	 * @param row	行
	 * @return	切れている場合 = true
	 */
	private static boolean isExpired (Data row) {

		long now = nowMillis();

		if (now - row.getLong("last_used_at") > RememberConf.sliding().toMillis()) {
			return true;
		}

		/*
		 * <b>使っていても、いつかは切れる。</b>
		 * 滑る期限だけだと、毎日来る人の Cookie は永遠に有効なままになる。
		 */
		return now - row.getLong("created_at") > RememberConf.absolute().toMillis();

	}

	/**
	 * 回した直後の猶予の中か
	 *
	 * @param row	行
	 * @return	猶予の中の場合 = true
	 */
	private static boolean inGrace (Data row) {

		return nowMillis() - row.getLong("rotated_at")
			<= RememberConf.grace().toMillis();

	}

	/**
	 * Cookie を書く
	 *
	 * @param context	コンテキスト
	 * @param selector	selector
	 * @param validator	validator
	 */
	private static void writeCookie (WebContext context, String selector, String validator) {

		/*
		 * 有効秒数は<b>滑るほうの期限に合わせる</b>。
		 * 絶対の上限に合わせると、<b>DB ではもう切れている Cookie が</b>
		 * ブラウザに残り続ける（毎回1往復むだになる）。
		 */
		context.cookies().put(cookieName(), selector + SEPARATOR + validator
			, RememberConf.sliding().toSeconds());

	}

	/**
	 * 1件読む
	 *
	 * @param selector	selector
	 * @return	行。無ければ null
	 */
	private static Data row (String selector) {

		try (DB db = DBUtil.getMainDB()) {
			return db.select("""
				SELECT validator, previous_validator, rotated_at, user_id, created_at, last_used_at
				FROM %s WHERE selector = ?
				""".formatted(table(db)), selector);
		} catch (Exception ex) {
			Log.error(ex, "ログインの記憶を読めませんでした");
			return null;
		}

	}

	/**
	 * 1件消す
	 *
	 * @param selector	selector
	 */
	private static void delete (String selector) {

		try (DB db = DBUtil.getMainDB()) {
			db.delete("DELETE FROM %s WHERE selector = ?".formatted(table(db)), selector);
		} catch (Exception ex) {
			Log.error(ex, "ログインの記憶を消せませんでした");
		}

	}

	/**
	 * Cookie の selector
	 *
	 * @param cookie	Cookie の値
	 * @return	selector。形が違えば null
	 */
	private static String selectorOf (String cookie) {

		int index = cookie.indexOf(SEPARATOR);

		return index <= 0 ? null : cookie.substring(0, index);

	}

	/**
	 * Cookie の validator
	 *
	 * @param cookie	Cookie の値
	 * @return	validator。形が違えば null
	 */
	private static String validatorOf (String cookie) {

		int index = cookie.indexOf(SEPARATOR);

		return index <= 0 || index == cookie.length() - 1 ? null : cookie.substring(index + 1);

	}

	/**
	 * 乱数の文字列
	 *
	 * @return	文字列
	 */
	private static String token () {

		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);

		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

	}

	/**
	 * ハッシュにする
	 *
	 * <p>
	 * <b>validator をそのまま保存しない。</b>
	 * 保存すると、<b>DB を読めた人が全員になりすませる</b>——
	 * パスワードを平文で持つのと同じことになる。
	 * </p>
	 *
	 * <p>
	 * <b>BCrypt ではなく SHA-256 でよい。</b>
	 * validator は<b>こちらが作った 128 ビットの乱数</b>なので、
	 * 総当たりも辞書も効かない（遅いハッシュは、人間が決めた短い文字列を守るためのものである）。
	 * リクエストのたびに BCrypt を回すと、<b>そちらのほうが問題になる</b>。
	 * </p>
	 *
	 * @param value	値
	 * @return	ハッシュ
	 */
	private static String hash (String value) {

		return Hash.sha256(value);

	}

	/**
	 * 時間を測られない比較
	 *
	 * @param left	左
	 * @param right	右
	 * @return	同じ場合 = true
	 */
	private static boolean equalsConstantTime (String left, String right) {

		if (left == null || right == null || right.isEmpty()) {
			return false;
		}

		return MessageDigest.isEqual(
			left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));

	}

	/**
	 * Cookie の名前
	 *
	 * @return	名前
	 */
	private static String cookieName () {

		return RememberConf.cookieName();

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
	 * 間隔を見て掃除する
	 */
	private static void cleanupIfDue () {

		Instant last = lastCleanup.get();

		if (Instant.now().isBefore(last.plus(CLEANUP_INTERVAL))) {
			return;
		}

		if (!lastCleanup.compareAndSet(last, Instant.now())) {
			return;
		}

		cleanup();

	}

	/**
	 * 使えるか（DB があって、有効になっているか）
	 *
	 * @return	使える場合 = true
	 */
	private static boolean isUsable () {

		if (!RememberConf.enabled()) {
			return false;
		}

		if (!DBUtil.isUseDB()) {

			if (!warnedNoDb) {
				warnedNoDb = true;
				Log.warn("auth.remember は DB が要ります。DB が無いので、ログインを覚えません");
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

		synchronized (Remember.class) {

			if (initialized) {
				return;
			}

			String name = FrameworkTables.AUTH_REMEMBER;

			DBVersion dbVersion = new DBVersion(name, "ログインの記憶");

			dbVersion.add(1)
				.mysql("""
					create table `%s`
					(
						selector           varchar(64)  not null primary key
						, validator          varchar(64)  not null
						, previous_validator varchar(64)  not null
						, rotated_at         bigint       not null
						, user_id            bigint       not null
						, created_at         bigint       not null
						, last_used_at       bigint       not null
					) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
					""".formatted(name, dbVersion.placeholder())
					, "create index %s__index_1 on `%s` (user_id)".formatted(name, name))
				.postgresql("""
					create table "%s"
					(
						selector           varchar(64)  not null primary key
						, validator          varchar(64)  not null
						, previous_validator varchar(64)  not null
						, rotated_at         bigint       not null
						, user_id            bigint       not null
						, created_at         bigint       not null
						, last_used_at       bigint       not null
					)
					""".formatted(name)
					, "create index %s__index_1 on \"%s\" (user_id)".formatted(name, name));

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

		return db.dialect().identifier(FrameworkTables.AUTH_REMEMBER);

	}

	// endregion

}
