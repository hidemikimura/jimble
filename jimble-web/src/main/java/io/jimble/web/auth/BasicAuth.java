package io.jimble.web.auth;

import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.router.Handler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Basic 認証（要件 F-W-13）
 *
 * <p>{@code before} に挿す。ルート単位で掛けられる。</p>
 *
 * <pre>
 * path("/admin", () -&gt; {
 *     before(BasicAuth.of("admin", "secret"));
 *     get("/", context -&gt; ...);
 * });
 * </pre>
 *
 * <p>自前で照合したいときはハンドラを渡す。</p>
 *
 * <pre>
 * before(BasicAuth.of((context, username, password) -&gt; users.verify(username, password)));
 * </pre>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>ヘッダの形を確かめずに切り出していた。</b>移送元は {@code header.substring(6)} で、
 *       {@code Basic } で始まるかを見ていない。<b>短いヘッダが来ると
 *       {@code StringIndexOutOfBoundsException} で 500 になる</b>し、
 *       {@code Bearer xxx} を Basic の値として扱ってしまう</li>
 *   <li><b>パスワードの比較が {@code equals} だった。</b>先頭から一致した長さで
 *       処理時間が変わるので、定数時間比較にした</li>
 *   <li><b>ユーザー名とパスワードを分けていなかった。</b>ハンドラに渡るのが
 *       Base64 文字列のままで、実装側が毎回デコードしていた</li>
 *   <li>セッションに認証済みを覚える鍵が<b>インスタンスごとのランダム UUID</b> だったため、
 *       アプリを再起動するとログインし直しになっていた。固定のキーにした</li>
 * </ol>
 */
public final class BasicAuth implements Handler {

	/** 認証を求めるステータスコード */
	public static final int STATUS_CODE = 401;

	/** ヘッダの接頭辞 */
	public static final String PREFIX = "Basic ";

	/** セッションに覚えるキー */
	public static final String SESSION_KEY = "__basic_auth";

	/** 既定の realm */
	public static final String DEFAULT_REALM = "jimble";

	/* 照合 */
	private final Verifier verifier;

	/* realm */
	private final String realm;

	/**
	 * 照合
	 */
	@FunctionalInterface
	public interface Verifier {

		/**
		 * 照合する
		 *
		 * @param context	コンテキスト
		 * @param username	ユーザー名
		 * @param password	パスワード
		 * @return	通す場合 = true
		 */
		boolean verify (WebContext context, String username, String password);

	}

	/**
	 * コンストラクタ
	 *
	 * @param verifier	照合
	 * @param realm		realm
	 */
	private BasicAuth (Verifier verifier, String realm) {

		this.verifier = verifier;
		this.realm = realm;

	}

	// region 生成

	/**
	 * ユーザー名とパスワードで作る
	 *
	 * @param username	ユーザー名
	 * @param password	パスワード
	 * @return	ハンドラ
	 */
	public static BasicAuth of (String username, String password) {

		return of(username, password, DEFAULT_REALM);

	}

	/**
	 * ユーザー名とパスワードで作る
	 *
	 * @param username	ユーザー名
	 * @param password	パスワード
	 * @param realm		realm
	 * @return	ハンドラ
	 */
	public static BasicAuth of (String username, String password, String realm) {

		return new BasicAuth(
			(context, inputUsername, inputPassword) ->
				equalsConstantTime(username, inputUsername) && equalsConstantTime(password, inputPassword)
			, realm
		);

	}

	/**
	 * 自前の照合で作る
	 *
	 * @param verifier	照合
	 * @return	ハンドラ
	 */
	public static BasicAuth of (Verifier verifier) {

		return new BasicAuth(verifier, DEFAULT_REALM);

	}

	// endregion

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handle (WebContext context) {

		// 認証済みならそのまま通す（セッションを使っていない構成では毎回照合する）
		if (!context.session().get(SESSION_KEY).isEmpty()) {
			return;
		}

		String header = context.request().header().getStringOptional("authorization");

		if (header.startsWith(PREFIX)) {

			String[] credentials = decode(header.substring(PREFIX.length()).trim());

			if (credentials != null && verifier.verify(context, credentials[0], credentials[1])) {
				context.session().put(SESSION_KEY, "1");
				context.session().save();
				return;
			}

		}

		context.response().setResponseHeader("WWW-Authenticate", "Basic realm=\"%s\"".formatted(realm));

		throw new HttpException(STATUS_CODE, "認証が必要です");

	}

	/**
	 * Base64 をユーザー名とパスワードに分ける
	 *
	 * @param encoded	Base64
	 * @return	{@code [ユーザー名, パスワード]}（読めなければ null）
	 */
	private static String[] decode (String encoded) {

		try {

			String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

			// パスワードに「:」が入ることがあるので最初の1つだけで分ける
			int index = decoded.indexOf(':');
			if (index < 0) {
				return null;
			}

			return new String[]{decoded.substring(0, index), decoded.substring(index + 1)};

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * 定数時間で比べる
	 *
	 * @param expected	期待値
	 * @param actual	入力
	 * @return	一致する場合 = true
	 */
	private static boolean equalsConstantTime (String expected, String actual) {

		if (expected == null || actual == null) {
			return false;
		}

		return MessageDigest.isEqual(
			expected.getBytes(StandardCharsets.UTF_8)
			, actual.getBytes(StandardCharsets.UTF_8));

	}

}
