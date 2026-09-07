package io.jimble.web.session;

import io.jimble.util.crypto.Aead;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;
import io.jimble.web.context.WebContext;
import io.jimble.web.cookie.Cookie;
import io.jimble.web.cookie.CookieConf;

import java.nio.charset.StandardCharsets;

/**
 * Cookie セッション（要件 F-S-01 / F-S-08）
 *
 * <p>
 * サーバー側に何も置かない。中身を <b>AES-256-GCM で暗号化</b>して Cookie 1つに載せる。
 * 暗号化と改ざん検知が同時に効く（{@link Aead}）。
 * </p>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li><b>暗号化していなかった。</b>移送元は {@code session__<キー>} という Cookie に
 *       値をそのまま入れていた。署名はされても<b>中身は誰でも読める。</b>
 *       要件 F-S-08 は「署名と暗号化の両方」なので、まとめて1つの暗号文にした</li>
 *   <li><b>キーごとに Cookie を作っていた。</b>項目が増えるほど Cookie が増え、
 *       消すときも1つずつだった。1つの Cookie にまとめた</li>
 *   <li><b>サイズ上限が無かった。</b>Cookie は 4KB を超えるとブラウザに黙って捨てられる。
 *       超えたら {@link IllegalStateException} で落とす（要件 F-S-08「超過時は明確に失敗させる」）</li>
 * </ol>
 */
public final class CookieSessionStore implements SessionStore {

	/** Cookie 名 */
	public static final String COOKIE_NAME = "session";

	/**
	 * 値の上限（バイト）
	 *
	 * <p>Cookie 1つの上限は 4096 バイト。名前と属性の分を引いて余裕を見る。</p>
	 */
	public static final int MAX_VALUE_BYTES = 3800;

	/* タイムアウト（分） */
	private final long timeoutMinutes;

	/* 暗号鍵 */
	private final String secret;

	/**
	 * コンストラクタ（設定から作る）
	 */
	public CookieSessionStore () {

		this(SessionConf.secret(), SessionConf.timeoutMinutes());

	}

	/**
	 * コンストラクタ
	 *
	 * @param secret			暗号鍵
	 * @param timeoutMinutes	タイムアウト（分）
	 */
	public CookieSessionStore (String secret, long timeoutMinutes) {

		if (secret == null || secret.isEmpty()) {
			throw new IllegalStateException(
				"Cookie セッションには暗号鍵が要ります（%s）".formatted(SessionConf.KEY_SECRET));
		}

		this.secret = secret;
		this.timeoutMinutes = timeoutMinutes;

	}

	// region SessionStore

	@Override
	public SessionEntry load (WebContext context) {

		String encrypted = context.cookies().get(COOKIE_NAME);
		if (encrypted == null || encrypted.isEmpty()) {
			return SessionEntry.empty();
		}

		// 改ざんされていたら null。空の新規セッションとして扱う
		String json = Aead.decrypt(encrypted, secret);
		if (json == null) {
			return SessionEntry.empty();
		}

		Data data = Dson.decodes(json, Data.class);

		return new SessionEntry(data == null ? new Data() : data, true);

	}

	@Override
	public void save (WebContext context, SessionEntry entry) {

		if (entry.data().isEmpty()) {
			destroy(context);
			return;
		}

		String encrypted = Aead.encrypt(Dson.encodes(entry.data()), secret);

		int bytes = encrypted.getBytes(StandardCharsets.UTF_8).length;
		if (bytes > MAX_VALUE_BYTES) {
			// 黙って捨てられるより、書いた側に気づかせる
			throw new IllegalStateException(
				"Cookie セッションが大きすぎます（%d > %d バイト）。保存先を db か redis にしてください"
					.formatted(bytes, MAX_VALUE_BYTES));
		}

		Cookie cookie = CookieConf.create(COOKIE_NAME, encrypted, timeoutMinutes * 60);
		context.cookies().put(cookie, encrypted);

	}

	@Override
	public void touch (WebContext context, SessionEntry entry) {

		String encrypted = context.cookies().get(COOKIE_NAME);
		if (encrypted == null || encrypted.isEmpty()) {
			return;
		}

		// 中身はそのまま。有効期限だけ延ばす
		context.cookies().put(CookieConf.create(COOKIE_NAME, encrypted, timeoutMinutes * 60), encrypted);

	}

	@Override
	public void destroy (WebContext context) {

		context.cookies().remove(COOKIE_NAME);

	}

	// endregion

}
