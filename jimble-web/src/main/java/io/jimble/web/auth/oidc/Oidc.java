package io.jimble.web.auth.oidc;

import io.jimble.util.data.Data;
import io.jimble.util.http.httpclient.method.HttpPostExecutor;
import io.jimble.util.log.Log;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Principal;
import io.jimble.web.auth.mfa.Mfa;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.router.Handler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Function;

/**
 * 「Google でログイン」（OpenID Connect。要件 F-W-31）
 *
 * <h2>使い方</h2>
 * <pre>
 * public class App extends JimbleApp {
 *
 *     {
 *         before(Auth::guard);
 *
 *         // <b>どちらも「ログインは要らないが、セッションは要る」</b>
 *         get("/auth/google", Oidc.start("google"))
 *             .attribute(Auth.PUBLIC, true);
 *
 *         get("/auth/google/callback", Oidc.callback("google", App::findOrCreate))
 *             .attribute(Auth.PUBLIC, true);
 *     }
 *
 *     // 名乗ってきた相手を、アプリの利用者に結び付ける。<b>入れたくなければ null</b>
 *     private static Principal findOrCreate (OidcUser user) { ... }
 *
 * }
 * </pre>
 *
 * <h2>やっていること</h2>
 * <p>
 * <b>認可コード + PKCE</b>（RFC 7636）。暗黙フローは持たない。
 * </p>
 * <ol>
 *   <li>{@code start} が <b>state / nonce / PKCE の検証子</b>を作ってセッションに入れ、
 *       プロバイダの認可の入口へ飛ばす</li>
 *   <li>{@code callback} が <b>state を突き合わせ</b>、コードをトークンに換え、
 *       <b>ID トークンを検証</b>して（{@link IdToken}）、
 *       アプリの関数に渡し、返ってきた {@link Principal} で {@link Auth#login} する</li>
 * </ol>
 *
 * <h2>やらないこと</h2>
 * <p>
 * <b>アクセストークンを保管しない。</b>ここは「誰がログインしたか」までで、
 * 外部 API を叩くための入れ物は持たない（保管・暗号化・失効・スコープの追加同意まで
 * 面倒を見ることになるため）。要るなら、返ってきたトークンをアプリが自分で持つ。
 * </p>
 *
 * <h2>二要素認証</h2>
 * <p>
 * <b>コードを入れる画面のパスを渡すこと</b>（{@link #callback(String, Function, String)}）。
 * 渡しておくと、二要素認証を有効にしている人が来たときに
 * {@link Mfa#pending} に倒してそこへ飛ばす——パスワードで入るときと同じ形である。
 * </p>
 *
 * <p>
 * <b>2引数の {@link #callback(String, Function)} は、そういう人が来たら断る。</b>
 * 以前はここで黙って {@link Auth#login} まで進んでいたので、
 * <b>「Google でログイン」を選ぶだけで二要素が飛んでいた</b>（D-155）。
 * <b>素通りさせるくらいなら入れないほうがよい</b>ので、いまは 401 にしている。
 * </p>
 *
 * <p>
 * <b>メールが同じでも既存の利用者に自動で結び付けない。</b>
 * {@code provider + sub} でしか引かないのが既定である（{@link OidcUser#key}）。
 * 自動で結び付けると、<b>メールを検証していないプロバイダが1つ混ざるだけで
 * アカウント乗っ取りになる</b>。既存のアカウントに紐付けたいなら、
 * <b>ログイン済みの状態で明示的に「連携する」</b>操作をさせること。
 * </p>
 */
public final class Oidc {

	/** セッションに入れる鍵：state */
	private static final String KEY_STATE = "__oidc_state";

	/** セッションに入れる鍵：nonce */
	private static final String KEY_NONCE = "__oidc_nonce";

	/** セッションに入れる鍵：PKCE の検証子 */
	private static final String KEY_VERIFIER = "__oidc_verifier";

	/** セッションに入れる鍵：どのプロバイダで始めたか */
	private static final String KEY_PROVIDER = "__oidc_provider";

	/** 乱数 */
	private static final SecureRandom RANDOM = new SecureRandom();

	/** base64url */
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private Oidc () {
	}

	// region 入口

	/**
	 * ログインを始める
	 *
	 * <p>
	 * <b>{@code attribute(Auth.PUBLIC, true)} を付けること。</b>
	 * ただし <b>{@code Auth.NO_SESSION} は付けない</b>——
	 * state も nonce もセッションに置くので、
	 * 付けると<b>戻ってきたときに何も残っていない</b>。
	 * </p>
	 *
	 * @param provider	設定に書いた名前
	 * @return	{@code get(...)} に渡すもの
	 */
	public static Handler start (String provider) {

		return context -> {

			OidcProvider target = OidcProvider.of(provider);

			String state = random();
			String nonce = random();
			String verifier = random() + random();

			context.session().put(KEY_STATE, state);
			context.session().put(KEY_NONCE, nonce);
			context.session().put(KEY_VERIFIER, verifier);
			context.session().put(KEY_PROVIDER, provider);

			/*
			 * <b>保存を忘れると、戻ってきたときに state が無い。</b>
			 * jimble は自動保存しない（要件 F-S-02）。
			 */
			context.session().save();

			context.response().redirect(authorizationUrl(target, state, nonce, verifier));

		};

	}

	/**
	 * 戻ってきたところ
	 *
	 * @param provider	設定に書いた名前
	 * @param lookup	名乗ってきた相手を、アプリの利用者に結び付ける。<b>入れないなら null を返す</b>
	 * @return	{@code get(...)} に渡すもの
	 */
	public static Handler callback (String provider, Function<OidcUser, Principal> lookup) {

		return callback(provider, lookup, null);

	}

	/**
	 * 戻ってきたところ（二要素認証つき）
	 *
	 * <p>
	 * 二要素認証を有効にしている人が来たら、{@link Mfa#pending} に倒して
	 * {@code mfaPath} へ飛ばす。<b>パスワードで入るときとまったく同じ形</b>である。
	 * </p>
	 *
	 * <pre>
	 * get("/auth/google/callback", Oidc.callback("google", App::findOrCreate, "/login/code"))
	 *     .attribute(Auth.PUBLIC, true);
	 * </pre>
	 *
	 * @param provider	設定に書いた名前
	 * @param lookup	名乗ってきた相手を、アプリの利用者に結び付ける。<b>入れないなら null を返す</b>
	 * @param mfaPath	コードを入れる画面のパス。{@code null} なら二要素の人を断る
	 * @return	{@code get(...)} に渡すもの
	 */
	public static Handler callback (String provider, Function<OidcUser, Principal> lookup, String mfaPath) {

		if (lookup == null) {
			throw new IllegalArgumentException("利用者に結び付ける方法がありません");
		}

		return context -> {

			OidcProvider target = OidcProvider.of(provider);

			/*
			 * <b>途中の値は、何をするより先に捨てる。</b>
			 * 残すと<b>同じ state をもう一度使える</b>（1回だけにするのが state の役目）。
			 *
			 * <b>ここでは保存しない。</b>保存は1リクエストに1回しか効かないので、
			 * ここで save すると<b>そのあとの Auth.login の保存が黙って捨てられる</b>——
			 * 「ログインできたのに次のリクエストで 401」になる（要件 F-S-02）。
			 * 成功したときは Auth.login が、失敗したときは下の catch が保存する。
			 */
			Flow flow = take(context);

			try {

				OidcUser user = receive(context, target, flow);

				Principal principal = lookup.apply(user);

				if (principal == null || !principal.isAuthenticated()) {
					/*
					 * <b>「知らない人だった」も 401 にする。</b>
					 * 「登録されていません」と返すと、
					 * <b>どのメールが登録済みかを外から数えられる</b>。
					 */
					Log.warn("OIDC で入れませんでした（アプリが断りました）: %s".formatted(user.key()));
					throw new HttpException(401, "ログインできませんでした");
				}

				finishLogin(context, principal, user.key(), mfaPath);

			} catch (OidcException cause) {

				/*
				 * <b>理由は返さない。</b>「nonce が合いません」「aud が違います」と返すと、
				 * <b>どこまで通ったかを外から測れる</b>。ログにだけ残す。
				 */
				Log.warn("OIDC の検証に落ちました（%s）: %s".formatted(provider, cause.getMessage()));

				context.session().save();

				throw new HttpException(401, "ログインできませんでした");

			} catch (HttpException cause) {

				context.session().save();

				throw cause;

			}

		};

	}

	// endregion

	// region 中身

	/**
	 * ログインまで進める（二要素認証を有効にしている人は、コードを待たせる）
	 *
	 * <p>
	 * <b>ここを素通りさせると、「Google でログイン」を選ぶだけで二要素が飛ぶ</b>（D-155）。
	 * パスワードで入る道には {@code Mfa.pending} があるのに、こちらだけ無い、という形になっていた。
	 * </p>
	 *
	 * <p>
	 * <b>行き先を渡していなければ断る。</b>黙って入れるより、入れないほうがよい。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param principal	アプリが結び付けた利用者
	 * @param userKey	ログに出す相手（{@code provider:sub}）
	 * @param mfaPath	コードを入れる画面のパス。{@code null} なら断る
	 */
	static void finishLogin (WebContext context, Principal principal, String userKey, String mfaPath) {

		if (Mfa.isActive(principal.id())) {

			if (mfaPath == null || mfaPath.isEmpty()) {

				Log.warn("""
					二要素認証を有効にしている人が OIDC で来ましたが、コードを入れる画面がありません: %s
					  Oidc.callback(プロバイダ, 関数, "/login/code") の形で渡してください。
					"""
					.formatted(userKey));

				throw new HttpException(401, "ログインできませんでした");

			}

			// pending がセッション ID を振り直して保存まで済ませる
			Mfa.pending(context, principal);

			context.response().redirect(mfaPath);

			return;

		}

		// 振り直して保存する。捨てた途中の値も、ここで消えたまま保存される
		Auth.login(context, principal);

	}

	/**
	 * 戻ってきたものを受け取る
	 *
	 * @param context	コンテキスト
	 * @param target	プロバイダ
	 * @return	名乗ってきた相手
	 */
	private static OidcUser receive (WebContext context, OidcProvider target, Flow flow) {

		Data input = context.request().bodyAll();

		/*
		 * プロバイダが断ったとき（利用者が「許可しない」を押したなど）。
		 * <b>error を先に見る</b>——見ないと「code が無い」という分かりにくい話になる。
		 */
		String error = input.getStringOptional("error");

		if (!error.isEmpty()) {
			throw new OidcException("プロバイダが断りました: %s（%s）"
				.formatted(error, input.getStringOptional("error_description")));
		}

		String expectedState = flow.state();
		String startedWith = flow.provider();
		String nonce = flow.nonce();
		String verifier = flow.verifier();

		if (expectedState == null || expectedState.isEmpty()) {
			throw new OidcException("こちらの state がありません（セッションが切れています）");
		}

		/*
		 * <b>state はここで見る。</b>見ないと、
		 * <b>攻撃者が自分のコードを踏ませて、被害者を攻撃者のアカウントでログインさせられる</b>
		 * （ログイン CSRF）。以後の操作が全部その口座に残る。
		 */
		if (!MessageDigest.isEqual(
			expectedState.getBytes(StandardCharsets.UTF_8)
			, input.getStringOptional("state").getBytes(StandardCharsets.UTF_8))) {
			throw new OidcException("state が合いません");
		}

		/*
		 * <b>始めたプロバイダと、戻ってきた口が同じであること。</b>
		 * 違うと、<b>弱いプロバイダで始めさせて強いプロバイダの口へ持ち込む</b>
		 * 取り違えが通る。
		 */
		if (!target.name.equals(startedWith)) {
			throw new OidcException("始めたプロバイダと違います: %s → %s".formatted(startedWith, target.name));
		}

		String code = input.getStringOptional("code");

		if (code.isEmpty()) {
			throw new OidcException("code がありません");
		}

		Data tokens = exchange(target, code, verifier);

		Data claims = IdToken.verify(target, tokens.getStringOptional("id_token"), nonce);

		return new OidcUser(
			target.name
			, claims.getStringOptional("sub")
			, claims.getStringOptional("email")
			, claims.getBoolean("email_verified")
			, claims.getStringOptional("name")
			, claims);

	}

	/**
	 * コードをトークンに換える
	 *
	 * @param target	プロバイダ
	 * @param code		認可コード
	 * @param verifier	PKCE の検証子
	 * @return	返ってきたもの
	 */
	private static Data exchange (OidcProvider target, String code, String verifier) {

		if (verifier == null || verifier.isEmpty()) {
			throw new OidcException("PKCE の検証子がありません（セッションが切れています）");
		}

		HttpPostExecutor http = new HttpPostExecutor()
			.setUrl(target.tokenEndpoint)
			.setTimeout(10000)
			.addBodyForm("grant_type", "authorization_code")
			.addBodyForm("code", code)
			.addBodyForm("redirect_uri", target.redirectUri)
			.addBodyForm("client_id", target.clientId)
			.addBodyForm("code_verifier", verifier);

		if (!target.clientSecret.isEmpty()) {
			http.addBodyForm("client_secret", target.clientSecret);
		}

		http.execute();

		if (http.errorException != null) {
			throw new OidcException("トークンの口に繋がりませんでした", http.errorException);
		}

		Data body = http.getContentJson();

		if (http.responseCode != 200 || body == null) {
			throw new OidcException("トークンに換えられませんでした（%d）: %s".formatted(
				http.responseCode, body == null ? "" : body.getStringOptional("error")));
		}

		if (body.getStringOptional("id_token").isEmpty()) {
			throw new OidcException("ID トークンが返ってきませんでした（scope に openid が要ります）");
		}

		return body;

	}

	/**
	 * 認可の入口の URL
	 *
	 * @param target	プロバイダ
	 * @param state		state
	 * @param nonce		nonce
	 * @param verifier	PKCE の検証子
	 * @return	URL
	 */
	private static String authorizationUrl (
		OidcProvider target, String state, String nonce, String verifier) {

		StringBuilder url = new StringBuilder(target.authorizationEndpoint);

		url.append(target.authorizationEndpoint.contains("?") ? '&' : '?');

		append(url, "response_type", "code");
		append(url, "client_id", target.clientId);
		append(url, "redirect_uri", target.redirectUri);
		append(url, "scope", OidcConf.scopes(target.name));
		append(url, "state", state);
		append(url, "nonce", nonce);

		/*
		 * <b>PKCE は必ず付ける</b>（RFC 7636）。
		 * 付けないと、<b>認可コードを横取りされた時点で終わり</b>になる——
		 * 検証子を知らないと換えられない、という一手間がここで効く。
		 * <b>plain は使わない</b>（検証子がそのまま流れるので、付ける意味が薄い）。
		 */
		append(url, "code_challenge", challenge(verifier));
		append(url, "code_challenge_method", "S256");

		return url.substring(0, url.length() - 1);

	}

	/**
	 * PKCE の challenge
	 *
	 * @param verifier	検証子
	 * @return	challenge
	 */
	private static String challenge (String verifier) {

		try {

			return ENCODER.encodeToString(MessageDigest.getInstance("SHA-256")
				.digest(verifier.getBytes(StandardCharsets.US_ASCII)));

		} catch (Exception cause) {
			throw new IllegalStateException("SHA-256 が使えません", cause);
		}

	}

	/**
	 * クエリを足す
	 *
	 * @param url	URL
	 * @param key	鍵
	 * @param value	値
	 */
	private static void append (StringBuilder url, String key, String value) {

		url.append(key).append('=')
			.append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&');

	}

	/**
	 * 乱数の文字列
	 *
	 * @return	文字列
	 */
	private static String random () {

		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);

		return ENCODER.encodeToString(bytes);

	}

	/**
	 * 始めたときに置いたもの
	 *
	 * @param state		state
	 * @param nonce		nonce
	 * @param verifier	PKCE の検証子
	 * @param provider	どのプロバイダで始めたか
	 */
	private record Flow(String state, String nonce, String verifier, String provider) {}

	/**
	 * 途中の値を取り出して、セッションからは消す
	 *
	 * <p>
	 * <b>読むと同時に消す。</b>残すと<b>同じ state をもう一度使える</b>——
	 * 1回だけにするのが state の役目である。
	 * </p>
	 *
	 * <p><b>ここでは保存しない</b>（呼び出し側の説明を参照）。</p>
	 *
	 * @param context	コンテキスト
	 * @return	置いたもの
	 */
	private static Flow take (WebContext context) {

		Flow flow = new Flow(
			context.session().get(KEY_STATE)
			, context.session().get(KEY_NONCE)
			, context.session().get(KEY_VERIFIER)
			, context.session().get(KEY_PROVIDER));

		context.session().remove(KEY_STATE);
		context.session().remove(KEY_NONCE);
		context.session().remove(KEY_VERIFIER);
		context.session().remove(KEY_PROVIDER);

		return flow;

	}

	// endregion

}
