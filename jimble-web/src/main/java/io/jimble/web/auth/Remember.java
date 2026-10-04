package io.jimble.web.auth;

import io.jimble.util.internal.Docs;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FrameworkTables;
import io.jimble.db.internal.version.DBVersion;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongFunction;
import java.util.regex.Pattern;

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
 * <h2>ログインの種別が複数あるとき（D-183）</h2>
 * <p>
 * 運用者の画面と利用者の管理画面のように<b>別々の表から引く ID</b> があると、
 * 記憶は<b>利用者 ID だけ</b>で持っていて Cookie の名前も1つなので、
 * <b>運用者として覚えた Cookie が、利用者の画面で「同じ ID の利用者」として思い出される</b>。
 * {@link #forgetAll} も、同じ ID の別の種別の人の記憶まで消す。
 * </p>
 *
 * <p>
 * <b>種別（realm）を渡すと、ID の数字が同じでも別の人として扱う。</b>
 * Cookie の名前も種別ごとに分かれる（{@code remember_operator} のように、設定の名前 + {@code _} + 種別）ので、
 * 同じホストで両方にログインしていても互いの Cookie を上書きしない。
 * 思い出すときは<b>記憶の行の種別も見る</b>——Cookie の名前を取り違えても、別の種別の人としては入れない。
 * </p>
 *
 * <pre>
 * path("/ops", () -&gt; {
 *     before(Remember.restore("operator", Ops::findStaff));
 *     before(Auth::guard);
 * });
 *
 * Remember.issue(context, principal, "operator");   // ログインのとき
 * Remember.forgetAll("operator", staffId);          // パスワードを変えたとき
 * </pre>
 *
 * <p>
 * 渡さなければ、これまでどおりの1つの種別として動く（Cookie の名前も既存の記憶もそのまま）。
 * {@link Auth#logout} は、種別なしと、このアプリが使っている種別の Cookie をすべて消す
 * （セッションごと捨てるので、どの種別のログインも終わる——記憶だけ残すと次のリクエストでまた入る）。
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

	/** 種別なし（D-183） */
	private static final String NO_REALM = "";

	/** 種別に使える形（二要素認証の種別と同じ） */
	private static final Pattern REALM_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	/*
	 * このアプリが使っている種別（D-183）。
	 *
	 * <b>ログアウトで消す Cookie を知るため</b>に、restore / issue で渡された種別を覚えておく。
	 * 知らない名前の Cookie を「remember_ で始まるから」と消しに行くと、
	 * アプリが自分で置いた同じ頭の Cookie まで消してしまう。
	 */
	private static final Set<String> REALMS = ConcurrentHashMap.newKeySet();

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

		return restore(NO_REALM, lookup);

	}

	/**
	 * 思い出す（種別つき。{@code before} に置く。D-183）
	 *
	 * <p>
	 * <b>この種別の Cookie だけを見て、この種別の記憶だけを受け付ける。</b>
	 * {@code lookup} は、この種別の表から引くものを渡す。
	 * </p>
	 *
	 * <p>
	 * <b>ルートにログインの種別（{@link Auth#REALM}）が付いていて、それがこの種別と違えば、何もしない</b>（D-185）。
	 * アプリ全体に置いた種別なしの {@code restore} は、種別を付けたブロックでは効かない。
	 * そのブロックには、同じ名前の種別で {@code restore} を置く。
	 * </p>
	 *
	 * @param realm		種別（{@code "operator"} など）。空文字なら種別なし
	 * @param lookup	id から利用者を引き直す。見つからなければ null を返すこと
	 * @return	{@code before} に渡すもの
	 */
	public static Handler restore (String realm, LongFunction<Principal> lookup) {

		checkRealm(realm);

		if (lookup == null) {
			throw new IllegalArgumentException("id から利用者を引く方法がありません");
		}

		register(realm);
		RESTORED_REALMS.add(realm);

		return context -> restore(context, realm, lookup);

	}

	/**
	 * 思い出す
	 *
	 * @param context	コンテキスト
	 * @param lookup	id から利用者を引き直す
	 */
	static void restore (WebContext context, LongFunction<Principal> lookup) {

		restore(context, NO_REALM, lookup);

	}

	/**
	 * 思い出す（種別つき）
	 *
	 * @param context	コンテキスト
	 * @param realm		種別
	 * @param lookup	id から利用者を引き直す
	 */
	static void restore (WebContext context, String realm, LongFunction<Principal> lookup) {

		if (context.route() == null || !context.route().matched()) {
			return;
		}

		if (context.route().route().attribute(Auth.NO_SESSION)) {
			return;
		}

		/*
		 * <b>ルートの種別（Auth.REALM）と違う記憶では、思い出さない（何もしない）。</b>
		 * アプリ全体に置いた before(Remember.restore(...)) は、種別を付けたブロックにも掛かる。
		 * ここで投げると、<b>Cookie が無くてもそのブロックの全リクエストが 500</b> になる。
		 * 思い出さなければログインもしないので、ログアウトで記憶が消えずに残る穴も開かない。
		 * 取り違えは issue のほうで止める（ログインのときに気づく）
		 */
		if (!sameRealm(context, realm)) {
			return;
		}

		// 思い出そうとした印（guard が「restore より先に走った」を見分けるため。D-189）
		context.attribute(triedKey(realm), Boolean.TRUE);

		String cookie = context.cookies().get(cookieName(realm));

		if (cookie == null || cookie.isEmpty()) {
			// 覚えていない。<b>ここで DB は触らない</b>（ほとんどのリクエストはここで返る）
			return;
		}

		if (Auth.principal(context, realm).isAuthenticated()) {
			// その種別でもうログインしている。Cookie は次に切れるまでそのまま（D-271。ルートの種別で見ると、毎回思い出し直していた）
			return;
		}

		if (!isUsable()) {
			return;
		}

		try {
			restoreFromCookie(context, realm, cookie, lookup);
		} catch (Exception ex) {
			// <b>思い出せなくてもリクエストは通す</b>（ログインしていない扱いになるだけ）
			Log.error(ex, "ログインを思い出せませんでした");
		}

		cleanupIfDue();

	}

	/**
	 * 記憶の種別が、ルートのログインの種別（{@link Auth#REALM}）に合っているか（D-185）
	 *
	 * <p>ルートに種別が無ければ、どの記憶でも合っている（これまでどおり）。</p>
	 *
	 * @param context	コンテキスト
	 * @param realm		記憶の種別
	 * @return	合っている場合 = true
	 */
	private static boolean sameRealm (WebContext context, String realm) {

		String sessionRealm = Auth.realmOf(context);

		return sessionRealm.isEmpty() || sessionRealm.equals(realm);

	}

	/**
	 * ルートにログインの種別（{@link Auth#REALM}）が付いていれば、覚える記憶の種別も同じであること（D-185）
	 *
	 * <p>
	 * 種別を付けたルートのログアウト（{@link Auth#logout}）は、<b>同じ名前の種別の記憶だけ</b>を消す。
	 * 名前が違うと記憶が消えずに残り、<b>ログアウトしても記憶が生き残る</b>。
	 * 黙ってそうなるくらいなら、ここで止める。
	 * </p>
	 */
	private static void requireSameRealm (WebContext context, String realm) {

		String sessionRealm = Auth.realmOf(context);

		if (!sameRealm(context, realm)) {
			throw new IllegalStateException(("remember-me の種別（%s）が、ルートのログインの種別 Auth.REALM（%s）と違います。"
				+ "ログアウトで記憶を消せなくなるので、同じ名前にしてください").formatted(realmLabel(realm), sessionRealm) + Docs.see("auth"));
		}

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

		issue(context, principal, NO_REALM);

	}

	/**
	 * 覚える（種別つき。ログインしたときに呼ぶ。D-183）
	 *
	 * @param context	コンテキスト
	 * @param principal	覚える相手
	 * @param realm		種別。空文字なら種別なし
	 */
	public static void issue (WebContext context, Principal principal, String realm) {

		checkRealm(realm);

		requireSameRealm(context, realm);

		register(realm);

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
				INSERT INTO %s (selector, realm, validator, previous_validator, rotated_at
					, user_id, created_at, last_used_at)
				VALUES (?, ?, ?, '', ?, ?, ?, ?)
				""".formatted(table(db))
				, selector, realm, hash(validator), now, principal.id(), now, now);

		} catch (Exception ex) {
			Log.error(ex, "ログインを覚えられませんでした");
			return;
		}

		writeCookie(context, realm, selector, validator);

	}

	/**
	 * この端末のぶんだけ忘れる（ログアウト）
	 *
	 * <p>
	 * <b>{@link Auth#logout} が呼ぶので、直に呼ばなくてよい。</b>
	 * ログアウトで消し忘れると、<b>次のリクエストでまた入ってしまう</b>。
	 * </p>
	 *
	 * <p>
	 * <b>種別なしと、このアプリが使っている種別（{@link #restore(String, LongFunction)} /
	 * {@link #issue(WebContext, Principal, String)} に渡した種別）の Cookie をすべて消す</b>（D-183）。
	 * ログアウトはセッションごと捨てるので、どの種別のログインも終わる——
	 * 記憶だけ残すと、<b>その種別の画面を開いた次のリクエストでまた入る</b>。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	public static void forget (WebContext context) {

		forget(context, NO_REALM);

		for (String realm : REALMS) {
			forget(context, realm);
		}

	}

	/**
	 * この端末のこの種別のぶんだけ忘れる（D-183）
	 *
	 * @param context	コンテキスト
	 * @param realm		種別。空文字なら種別なし
	 */
	public static void forget (WebContext context, String realm) {

		checkRealm(realm);

		String cookie = context.cookies().get(cookieName(realm));

		if (cookie == null || cookie.isEmpty()) {
			return;
		}

		context.cookies().remove(cookieName(realm));

		if (!isUsable()) {
			return;
		}

		String selector = selectorOf(cookie);

		if (selector == null) {
			return;
		}

		try (DB db = DBUtil.getMainDB()) {
			db.delete("DELETE FROM %s WHERE selector = ? AND realm = ?".formatted(table(db)), selector, realm);
		} catch (Exception ex) {
			Log.error(ex, "ログインの記憶を消せませんでした");
		}

	}

	/**
	 * その人のぶんを全部忘れる
	 *
	 * <p>
	 * <b>パスワードを変えたら、これではなく {@link Auth#revokeOthers} を呼ぶこと</b>（中でこれも呼ぶ）。
	 * 呼ばないと、<b>パスワードを変えても盗まれた Cookie とセッションはそのまま使える</b>——
	 * 変えた意味が無い。
	 * </p>
	 *
	 * <p>
	 * <b>生きているセッションは切らない</b>——消えるのは remember-me の記憶だけで、
	 * いまログインしている端末はそのまま入れる。<b>「全端末からログアウト」は {@link Auth#revoke}</b>
	 * （パスワードを変えたあとなら {@link Auth#revokeOthers}）で、どちらも中でこれを呼ぶ（F-W-33）。
	 * </p>
	 *
	 * <p>
	 * <b>消せなかったら投げる（D-173）。</b>かつては DB が落ちていても {@code 0} を返していた——
	 * <b>「1つも無かった」と見分けが付かない</b>ので、呼んだ側は消えたつもりで先へ進む。
	 * ここで消え損なうと、<b>盗まれた Cookie がそのまま生き残る</b>。
	 * </p>
	 *
	 * <p>
	 * <b>消えるのは種別なしの記憶だけ</b>（D-183）。種別を渡して覚えたものは
	 * {@link #forgetAll(String, long)} で消す——同じ ID の別の種別の人は、別の人である。
	 * </p>
	 *
	 * @param userId	利用者 ID
	 * @return	消した件数
	 * @throws IllegalStateException	消せなかった場合
	 */
	public static int forgetAll (long userId) {

		return forgetAll(NO_REALM, userId);

	}

	/**
	 * その人のぶんを全部忘れる（種別つき。D-183）
	 *
	 * <p><b>パスワードを変えたら、これではなく {@link Auth#revokeOthers} を呼ぶこと。</b>消えるのはこの種別のこの人の記憶だけで、セッションは切らない。</p>
	 *
	 * @param realm		種別。空文字なら種別なし
	 * @param userId	利用者 ID
	 * @return	消した件数
	 * @throws IllegalStateException	消せなかった場合
	 */
	public static int forgetAll (String realm, long userId) {

		checkRealm(realm);

		String who = who(realm, userId);

		/*
		 * <b>「使っていない」と「使えない」を分ける。</b>
		 * 切っている・DB が無いなら消すものは無いので 0。表を作れなかったなら<b>投げる</b>——
		 * 0 を返すと、パスワードを変えた側は消えたつもりで先へ進み、盗まれた Cookie が生き残る（D-173）。
		 */
		if (!RememberConf.enabled() || !DBUtil.isUseDB()) {
			return 0;
		}

		if (!install()) {
			throw new IllegalStateException("ログインの記憶を消せませんでした（表を作れません）: " + who);
		}

		try (DB db = DBUtil.getMainDB()) {

			// 消せなければ例外（下で包み直す）
			return db.delete("DELETE FROM %s WHERE realm = ? AND user_id = ?".formatted(table(db)), realm, userId);

		} catch (IllegalStateException ex) {

			Log.error(ex, "ログインの記憶を消せませんでした: " + who);
			throw ex;

		} catch (Exception ex) {

			Log.error(ex, "ログインの記憶を消せませんでした: " + who);
			throw new IllegalStateException("ログインの記憶を消せませんでした: " + who, ex);

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
	 * @param realm		種別
	 * @param cookie	Cookie の値
	 * @param lookup	id から利用者を引き直す
	 */
	private static void restoreFromCookie (WebContext context, String realm, String cookie
		, LongFunction<Principal> lookup) {

		String selector = selectorOf(cookie);
		String validator = validatorOf(cookie);

		if (selector == null || validator == null) {
			context.cookies().remove(cookieName(realm));
			return;
		}

		Data row = row(selector);

		if (row == null) {
			/*
			 * <b>これは盗用ではない。</b>掃除で消えたあとや、
			 * 別の端末で「全部忘れる」をしたあとに来ると、ふつうにここへ来る。
			 */
			context.cookies().remove(cookieName(realm));
			return;
		}

		/*
		 * <b>別の種別の記憶では入れない</b>（D-183）。
		 * Cookie の名前は種別ごとに分けてあるのでふつうはここへ来ない——来たのは、
		 * 名前を取り違えたか、別の種別の Cookie をこの名前で送ってきたときである。
		 * 相手の種別の記憶は<b>消さない</b>（持ち主の Cookie かもしれない）。この Cookie だけ捨てる。
		 */
		if (!realm.equals(row.getStringOptional("realm"))) {
			Log.warn("別の種別のログインの記憶が送られてきました。受け付けません: この入口=%s 記憶=%s"
				.formatted(realmLabel(realm), realmLabel(row.getStringOptional("realm"))));
			context.cookies().remove(cookieName(realm));
			return;
		}

		long userId = row.getLong("user_id");

		if (isExpired(row)) {
			delete(selector);
			context.cookies().remove(cookieName(realm));
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
			stolen(context, realm, userId, "照合できない validator");
			return;
		}

		if (matchedPrevious && !inGrace(row)) {
			/*
			 * <b>回したあとの古い validator が、猶予を過ぎて使われた。</b>
			 * 本物はもう新しいほうを持っているので、これは<b>盗まれたほう</b>である
			 * （どちらが盗んだ側かは分からないので、両方消す）。
			 */
			stolen(context, realm, userId, "回転前の validator");
			return;
		}

		Principal principal = lookup.apply(userId);

		if (principal == null || !principal.isAuthenticated()) {
			/*
			 * 利用者が消えている（退会など）。<b>記憶も消す</b>——
			 * 残すと、id を作り直したときに<b>別人が入る</b>。
			 */
			forgetAll(realm, userId);
			context.cookies().remove(cookieName(realm));
			return;
		}

		/*
		 * <b>世代を引いてから、記憶がまだあるかをもう一度見る</b>（F-W-33 / D-199）。
		 * Auth.revoke は「記憶を消す → 世代を上げる」の順なので、
		 * 世代を引いたあとで記憶が残っていれば、その世代は上がる前のもの——入れても次の guard で弾かれる。
		 * 見直さないと、確かめた直後に締め出されたとき、<b>上がったあとの世代をもらって生き残る</b>。
		 */
		long generation = Auth.generationForLogin(realm, userId);

		if (row(selector) == null) {
			context.cookies().remove(cookieName(realm));
			return;
		}

		if (matched) {
			rotate(context, realm, selector, row);
		} else {
			/*
			 * 猶予の中。<b>回さない</b>（回すと、遅れて届いた通信のぶんだけ回り続ける）。
			 * いま有効な validator は既に相手へ渡してあるので、<b>ここでは Cookie も出し直さない</b>。
			 */
			touch(selector);
		}

		Auth.loginWithoutPassword(context, realm, principal, generation);

	}

	/**
	 * validator を回して、Cookie を出し直す
	 *
	 * @param context	コンテキスト
	 * @param realm		種別
	 * @param selector	selector
	 * @param row		いまの行
	 */
	private static void rotate (WebContext context, String realm, String selector, Data row) {

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

		writeCookie(context, realm, selector, next);

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
	 * @param realm		種別
	 * @param userId	利用者 ID
	 * @param reason	理由（ログ用）
	 */
	private static void stolen (WebContext context, String realm, long userId, String reason) {

		/*
		 * <b>黙って消さない。</b>「たまにログアウトする」と言われたときに、
		 * これが盗用なのか作りの問題なのかを<b>ログでしか区別できない</b>。
		 */
		Log.warn("ログインの記憶が盗まれた可能性があります。全部消します: %s（%s）"
			.formatted(who(realm, userId), reason));

		// <b>消すのはこの種別のこの人だけ</b>（同じ ID の別の種別の人は、盗まれていない）
		forgetAll(realm, userId);

		context.cookies().remove(cookieName(realm));

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
	 * @param realm		種別
	 * @param selector	selector
	 * @param validator	validator
	 */
	private static void writeCookie (WebContext context, String realm, String selector, String validator) {

		/*
		 * 有効秒数は<b>滑るほうの期限に合わせる</b>。
		 * 絶対の上限に合わせると、<b>DB ではもう切れている Cookie が</b>
		 * ブラウザに残り続ける（毎回1往復むだになる）。
		 */
		context.cookies().put(cookieName(realm), selector + SEPARATOR + validator
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
				SELECT realm, validator, previous_validator, rotated_at, user_id, created_at, last_used_at
				FROM %s WHERE selector = ?
				""".formatted(table(db)), selector).orElse(null);
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
	 * <p>
	 * <b>種別なしは、これまでと同じ名前</b>（{@code auth.remember.cookie_name}。既定 {@code remember}）。
	 * 変えると、上げた瞬間に全員の記憶が効かなくなる。種別つきは {@code <名前>_<種別>}。
	 * </p>
	 *
	 * @param realm	種別
	 * @return	名前
	 */
	/* restore を置いた種別（種別なしは空文字）。置いていない種別では、guard の順番を言わない */
	private static final Set<String> RESTORED_REALMS = ConcurrentHashMap.newKeySet();

	/* restore より先に guard が走ったことを、1度だけ言うための印 */
	private static final java.util.concurrent.atomic.AtomicBoolean GUARD_FIRST_WARNED = new java.util.concurrent.atomic.AtomicBoolean();

	/**
	 * 「guard が先に走った」をもう一度言えるようにする（テスト用）
	 */
	static void resetGuardWarning () {

		GUARD_FIRST_WARNED.set(false);

	}

	/**
	 * 思い出そうとした印のキー
	 *
	 * @param realm	種別
	 * @return	キー
	 */
	private static String triedKey (String realm) {

		return "jimble.remember.tried:" + realm;

	}

	/**
	 * guard が 401 を返す前に呼ぶ（要件 D-189）
	 *
	 * <p>
	 * <b>覚えている Cookie が来ているのに、その種別の restore がまだ走っていない</b>なら、
	 * {@code before} の順番が逆である（アプリ全体の guard が、種別のブロックの restore より先に走る）。
	 * 覚えていても毎回ログインを求めることになるのに、何も言われない。1度だけ言う。
	 * 401 を返すことは変えない。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param realm		ルートのログインの種別
	 */
	static void warnIfGuardRanFirst (WebContext context, String realm) {

		if (!RememberConf.enabled() || !RESTORED_REALMS.contains(realm)) {
			return;
		}

		if (Boolean.TRUE.equals(context.attribute(triedKey(realm)))) {
			return;
		}

		String cookie = context.cookies().get(cookieName(realm));

		if (cookie == null || cookie.isEmpty()) {
			return;
		}

		if (GUARD_FIRST_WARNED.compareAndSet(false, true)) {
			Log.warn("""
				remember-me の Cookie（%s）が来ているのに、Remember.restore より先に Auth.guard が走って 401 になりました（%s %s）。
				  before(Remember.restore(...)) は before(Auth::guard) より先に置いてください。
				  ログインの種別（Auth.REALM）を付けたブロックでは、restore と guard の両方をブロックの中に置きます
				  （アプリ全体の before は、ブロックの before より先に走ります）。
				  詳しく: %s""".formatted(cookieName(realm), context.request().method(), context.request().path(), Docs.url("auth")));
		}

	}

	static String cookieName (String realm) {

		String base = RememberConf.cookieName();

		return realm.isEmpty() ? base : base + "_" + realm;

	}

	/**
	 * 使っている種別として覚える
	 *
	 * @param realm	種別
	 */
	private static void register (String realm) {

		if (!realm.isEmpty()) {
			REALMS.add(realm);
		}

	}

	/**
	 * ログに出す相手
	 *
	 * @param realm		種別
	 * @param userId	利用者 ID
	 * @return	{@code user_id=1} か {@code realm=operator user_id=1}
	 */
	private static String who (String realm, long userId) {

		return realm.isEmpty() ? "user_id=" + userId : "realm=" + realm + " user_id=" + userId;

	}

	/**
	 * ログに出す種別
	 *
	 * @param realm	種別
	 * @return	種別。空なら「（種別なし）」
	 */
	private static String realmLabel (String realm) {

		return realm == null || realm.isEmpty() ? "（種別なし）" : realm;

	}

	/**
	 * 種別の形を確かめる（二要素認証の種別と同じ形。D-183）
	 *
	 * @param realm	種別
	 */
	private static void checkRealm (String realm) {

		if (realm == null) {
			throw new IllegalArgumentException("ログインの記憶の種別が null です（種別なしなら、種別を渡さない形を使ってください）");
		}

		if (!realm.isEmpty() && !REALM_PATTERN.matcher(realm).matches()) {
			throw new IllegalArgumentException(
				"ログインの記憶の種別に使えない文字があります（英数字・_・- の 64 文字まで）: " + realm);
		}

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

		return install();

	}

	/**
	 * テーブルを作る
	 *
	 * @return	使える状態なら true
	 */
	private static boolean install () {

		if (initialized) {
			return true;
		}

		synchronized (Remember.class) {

			if (initialized) {
				return true;
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

			/*
			 * 版2：種別（D-183）。<b>既存の行は空文字＝種別なしになる</b>ので、
			 * 種別を渡さないアプリはこれまでどおり思い出せる。
			 */
			dbVersion.add(2)
				.mysql("alter table `%s` add column realm varchar(64) not null default '' after selector".formatted(name))
				.postgresql("alter table \"%s\" add column realm varchar(64) not null default ''".formatted(name));

			/*
			 * <b>作れなかったら、このリクエストでは使わない</b>（次のリクエストでもう一度試す）。
			 * apply は失敗しても false を返すだけなので、見ずに進むと
			 * 種別の列が無い表に {@code realm = ?} を投げ、「思い出せませんでした」としか出ない。
			 * 落とさないのは、<b>ログインしたままにする</b>が無くてもログインそのものは通すため（ここの作りの方針）。
			 */
			if (!dbVersion.apply(DBUtil.getMainDB())) {
				Log.error("ログインの記憶の表を作れませんでした。ログインを覚えません（直前のエラーログを見てください）");
				return false;
			}

			initialized = true;

			return true;

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
