package io.jimble.web.auth.passkey;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FrameworkTables;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;
import io.jimble.util.log.Log;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Principal;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.router.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * パスキー（WebAuthn）でのログイン（D-261）
 *
 * <p>
 * <b>パスワードなしのログイン</b>に使う。パスキーは端末の本人確認（指紋・顔・PIN）込みなので、
 * 通った人は {@link Auth#login} でログインさせる（{@code Auth.FULL_AUTH} のルートにも入れる）。
 * <b>二要素認証のコードは聞かない</b>（パスキーそのものが「持っている端末」と「本人確認」の2つを満たす）。
 * </p>
 *
 * <h2>流れ</h2>
 * <pre>{@code
 * // 登録（ログインしている人。パスワードを入れ直した人だけにする）
 * post("/passkey/register/options", c -> c.response().json(Passkey.registrationOptions(c, "kimura@example.com")))
 *     .attribute(Auth.FULL_AUTH, true);
 * post("/passkey/register", c -> {
 *     Passkey.register(c, c.request().bodyJson(), "ノート PC");
 *     c.response().json("ok", true);
 * }).attribute(Auth.FULL_AUTH, true);
 *
 * // ログイン（誰でも。セッションは使う）
 * post("/passkey/login/options", c -> c.response().json(Passkey.loginOptions(c))).attribute(Auth.PUBLIC, true);
 * post("/passkey/login", c -> {
 *     if (!Passkey.login(c, c.request().bodyJson(), App::findPrincipal)) {
 *         throw new HttpException(401, "パスキーでログインできませんでした");
 *     }
 *     c.response().json("ok", true);
 * }).attribute(Auth.PUBLIC, true);
 *
 * // ブラウザ側の JS（navigator.credentials を呼ぶ）
 * get("/passkey.js", Passkey.script()).attribute(Auth.PUBLIC, true);
 * }</pre>
 *
 * <p>
 * <b>クライアントごとにドメインが違う SaaS</b> では、設定（{@code auth.passkey.rp_id}）の代わりに {@link PasskeyRp} を作り、
 * 4つのメソッドに同じものを渡す（D-262）。
 * </p>
 *
 * <h2>確かめること（WebAuthn Level 3 の 7.1 / 7.2）</h2>
 * <ul>
 *   <li>チャレンジ：セッションに置き、<b>1度しか使わせない</b>。{@code auth.passkey.timeout} を過ぎたものは断る</li>
 *   <li>clientDataJSON：{@code type}・{@code challenge}・{@code origin}（{@code auth.passkey.origins}）・{@code crossOrigin} が true でない</li>
 *   <li>authenticatorData：rpIdHash（{@code auth.passkey.rp_id}）・利用者がいた（UP）・本人確認をした（UV）・バックアップの印の矛盾</li>
 *   <li>ログイン：保存した公開鍵で署名を確かめ、利用者のハンドルが一致し、署名の回数が戻っていない（複製の疑い）</li>
 * </ul>
 *
 * <p>
 * <b>認証器の証明書（attestation）は確かめない</b>（{@code attestation: "none"}）。パスキーのほとんどは証明書を出さず、
 * 「どのメーカーの認証器か」で断る必要は、ふつうのアプリには無い。
 * </p>
 */
public final class Passkey {

	/* セッション：チャレンジ */
	private static final String KEY_CHALLENGE = "__passkey_challenge";

	/* セッション：何のチャレンジか（create / get） */
	private static final String KEY_PURPOSE = "__passkey_purpose";

	/* セッション：チャレンジを出したサイト（rp_id） */
	private static final String KEY_RP = "__passkey_rp";

	/* セッション：チャレンジを出した時刻（エポック秒） */
	private static final String KEY_ISSUED_AT = "__passkey_issued_at";

	/* セッション：登録しようとしている人（種別:ID） */
	private static final String KEY_SUBJECT = "__passkey_subject";

	/* セッション：登録の options で渡した利用者のハンドル（認証器はこれを覚え、ログインのときに返す） */
	private static final String KEY_HANDLE = "__passkey_user_handle";

	/* 登録 */
	private static final String CREATE = "create";

	/* ログイン */
	private static final String GET = "get";

	/* authenticatorData の印 */
	private static final int FLAG_UP = 0x01;
	private static final int FLAG_UV = 0x04;
	private static final int FLAG_BE = 0x08;
	private static final int FLAG_BS = 0x10;
	private static final int FLAG_AT = 0x40;
	private static final int FLAG_ED = 0x80;

	/** 資格情報の ID の長さの上限（WebAuthn の上限） */
	static final int MAX_CREDENTIAL_ID = 1023;

	/* 乱数 */
	private static final SecureRandom RANDOM = new SecureRandom();

	/* base64url */
	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

	/* 表を作ったか */
	private static volatile boolean initialized = false;

	private Passkey () {
	}

	// region 登録

	/**
	 * 登録の options（{@code navigator.credentials.create} に渡すもの）を作る
	 *
	 * <p>
	 * <b>ログインしている人</b>のパスキーを作る。パスワードを入れ直した人だけが呼べるルート
	 * （{@code attribute(Auth.FULL_AUTH, true)}）から呼ぶこと——セッションを盗んだ人が
	 * 自分のパスキーを足すと、パスワードを変えても入れてしまう。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param userName	パスキーの一覧に出る名前（ログイン ID やメール）
	 * @return	options（JSON にして返す）
	 * @throws HttpException	ログインしていない場合（401）
	 */
	public static Data registrationOptions (WebContext context, String userName) {

		return registrationOptions(context, PasskeyRp.fromConf(), userName);

	}

	/**
	 * 登録の options を作る（サイトを渡す。クライアントごとにドメインが違う SaaS 向け。D-262）
	 *
	 * @param context	コンテキスト
	 * @param rp		パスキーを結びつけるサイト
	 * @param userName	パスキーの一覧に出る名前（ログイン ID やメール）
	 * @return	options（JSON にして返す）
	 * @throws HttpException	ログインしていない場合（401）
	 */
	public static Data registrationOptions (WebContext context, PasskeyRp rp, String userName) {

		requireUsable();

		Principal principal = Auth.principal(context);

		if (!principal.isAuthenticated()) {
			throw new HttpException(401, "ログインしてください");
		}

		String realm = Auth.realmOf(context);
		String handle = userHandle(realm, rp.id(), principal.id());
		String challenge = issueChallenge(context, CREATE, rp, realm + ":" + principal.id(), handle);
		String name = userName == null || userName.isBlank() ? principal.name() : userName;

		List<Data> params = new ArrayList<>();
		for (int alg : new int[] { CoseKey.ES256, CoseKey.EDDSA, CoseKey.RS256 }) {
			params.add(new Data().putData("type", "public-key").putData("alg", alg));
		}

		List<Data> exclude = new ArrayList<>();
		for (String id : credentialIds(realm, rp.id(), principal.id())) {
			exclude.add(new Data().putData("type", "public-key").putData("id", id));
		}

		return new Data()
			.putData("challenge", challenge)
			.putData("rp", new Data().putData("id", rp.id()).putData("name", rp.name()))
			.putData("user", new Data()
				.putData("id", handle)
				.putData("name", name)
				.putData("displayName", name))
			.putData("pubKeyCredParams", params)
			.putData("timeout", PasskeyConf.timeout().toMillis())
			.putData("excludeCredentials", exclude)
			.putData("authenticatorSelection", new Data()
				.putData("residentKey", "required")
				.putData("requireResidentKey", true)
				.putData("userVerification", "required"))
			.putData("attestation", "none");

	}

	/**
	 * 登録する（{@code navigator.credentials.create} の結果を確かめて保存する）
	 *
	 * @param context		コンテキスト
	 * @param credential	ブラウザが返したもの（{@code PublicKeyCredential.toJSON()} の形）
	 * @param label			利用者が見分けるための名前（「ノート PC」など。空でもよい）
	 * @throws HttpException	確かめられなかった場合（400。理由はログにだけ出す）
	 */
	public static void register (WebContext context, Data credential, String label) {

		register(context, PasskeyRp.fromConf(), credential, label);

	}

	/**
	 * 登録する（サイトを渡す。options を出したときと同じサイトを渡すこと。D-262）
	 *
	 * @param context		コンテキスト
	 * @param rp			パスキーを結びつけるサイト
	 * @param credential	ブラウザが返したもの（{@code PublicKeyCredential.toJSON()} の形）
	 * @param label			利用者が見分けるための名前（「ノート PC」など。空でもよい）
	 * @throws HttpException	確かめられなかった場合（400。理由はログにだけ出す）
	 */
	public static void register (WebContext context, PasskeyRp rp, Data credential, String label) {

		requireUsable();

		Principal principal = Auth.principal(context);
		String realm = Auth.realmOf(context);

		if (!principal.isAuthenticated()) {
			throw new HttpException(401, "ログインしてください");
		}

		String handle = context.session().get(KEY_HANDLE);
		String challenge = takeChallenge(context, CREATE, rp, realm + ":" + principal.id());

		Registration registration;

		try {
			registration = verifyRegistration(rp.id(), rp.origins(), challenge, credential);
		} catch (IllegalArgumentException ex) {
			Log.warn("パスキーを登録できませんでした: %s / %s".formatted(who(realm, principal.id()), ex.getMessage()));
			throw new HttpException(400, "パスキーを登録できませんでした");
		}

		String credentialId = B64.encodeToString(registration.credentialId());
		long now = nowSeconds();

		try (DB db = DBUtil.getMainDB()) {

			db.insert(("INSERT INTO %s (credential_hash, realm, rp_id, user_id, credential_id, user_handle, public_key, algorithm"
				+ ", sign_count, backup_eligible, backed_up, label, created_at, last_used_at)"
				+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)").formatted(table(db))
				, hash(registration.credentialId()), realm, rp.id(), principal.id(), credentialId, handle
				, B64.encodeToString(registration.publicKey()), registration.algorithm(), registration.signCount()
				, registration.backupEligible() ? 1 : 0, registration.backedUp() ? 1 : 0
				, label == null ? "" : label.strip(), now);

		} catch (io.jimble.db.DuplicateKeyException ex) {
			// 同じ認証器をもう一度（excludeCredentials を無視するブラウザもある）
			throw new HttpException(409, "このパスキーはもう登録されています");
		}

	}

	/**
	 * 登録を確かめたもの
	 *
	 * @param credentialId		資格情報の ID
	 * @param publicKey			公開鍵（SubjectPublicKeyInfo）
	 * @param algorithm			COSE のアルゴリズムの番号
	 * @param signCount			署名の回数
	 * @param backupEligible	バックアップできる鍵か（同期するパスキー）
	 * @param backedUp			いまバックアップされているか
	 */
	record Registration(byte[] credentialId, byte[] publicKey, int algorithm, long signCount
		, boolean backupEligible, boolean backedUp) {}

	/**
	 * 登録の応答を確かめる（DB もセッションも触らない）
	 *
	 * @param rpId			パスキーを結びつけるドメイン
	 * @param origins		受け付けるオリジン
	 * @param challenge		出したチャレンジ（base64url）
	 * @param credential	ブラウザが返したもの
	 * @return	確かめたもの
	 * @throws IllegalArgumentException	確かめられなかった場合
	 */
	static Registration verifyRegistration (String rpId, List<String> origins, String challenge, Data credential) {

		Data response = response(credential);

		byte[] clientDataJson = b64(response.getString("clientDataJSON"), "clientDataJSON");
		checkClientData(clientDataJson, "webauthn.create", challenge, origins);

		if (!(Cbor.decode(b64(response.getString("attestationObject"), "attestationObject")) instanceof Map<?, ?> attestation)) {
			throw new IllegalArgumentException("attestationObject がマップではありません");
		}

		if (!(attestation.get("fmt") instanceof String)) {
			throw new IllegalArgumentException("attestationObject に fmt がありません");
		}

		if (!(attestation.get("authData") instanceof byte[] authData)) {
			throw new IllegalArgumentException("attestationObject に authData がありません");
		}

		// attStmt は見ない（attestation: "none"。認証器の証明書を信じない）

		AuthenticatorData data = AuthenticatorData.parse(authData);

		data.check(rpId);

		if (!data.has(FLAG_AT) || data.credentialId() == null) {
			throw new IllegalArgumentException("authenticatorData に資格情報がありません（AT）");
		}

		byte[] rawId = b64(credential.getString("rawId"), "rawId");

		if (!MessageDigest.isEqual(rawId, data.credentialId())) {
			throw new IllegalArgumentException("rawId と authenticatorData の資格情報の ID が違います");
		}

		CoseKey.Parsed key = CoseKey.parse(data.publicKey());

		return new Registration(data.credentialId(), key.key().getEncoded(), key.algorithm(), data.signCount()
			, data.has(FLAG_BE), data.has(FLAG_BS));

	}

	// endregion

	// region ログイン

	/**
	 * ログインの options（{@code navigator.credentials.get} に渡すもの）を作る
	 *
	 * <p>
	 * <b>ログイン ID を聞かない</b>（allowCredentials は空）。ブラウザが、この rp_id のパスキーの候補を出す。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @return	options（JSON にして返す）
	 */
	public static Data loginOptions (WebContext context) {

		return loginOptions(context, PasskeyRp.fromConf());

	}

	/**
	 * ログインの options を作る（サイトを渡す。クライアントごとにドメインが違う SaaS 向け。D-262）
	 *
	 * @param context	コンテキスト
	 * @param rp		パスキーを結びつけるサイト
	 * @return	options（JSON にして返す）
	 */
	public static Data loginOptions (WebContext context, PasskeyRp rp) {

		requireUsable();

		String challenge = issueChallenge(context, GET, rp, Auth.realmOf(context), "");

		return new Data()
			.putData("challenge", challenge)
			.putData("rpId", rp.id())
			.putData("timeout", PasskeyConf.timeout().toMillis())
			.putData("userVerification", "required")
			.putData("allowCredentials", List.of());

	}

	/**
	 * ログインさせる（{@code navigator.credentials.get} の結果を確かめて、{@link Auth#login} する）
	 *
	 * <p>
	 * 通ったら、そのルートの種別（{@code Auth.REALM}）でログインさせる。二要素認証のコードは聞かない。
	 * </p>
	 *
	 * @param context		コンテキスト
	 * @param credential	ブラウザが返したもの（{@code PublicKeyCredential.toJSON()} の形）
	 * @param lookup		利用者 ID からログインさせる人を引く（入れないなら null）
	 * @return	ログインした場合 = true。<b>断る理由は返さない</b>（ログにだけ出す）
	 */
	public static boolean login (WebContext context, Data credential, Function<Long, Principal> lookup) {

		return login(context, PasskeyRp.fromConf(), credential, lookup);

	}

	/**
	 * ログインさせる（サイトを渡す。options を出したときと同じサイトを渡すこと。D-262）
	 *
	 * <p>
	 * <b>そのサイト（rp_id）で登録したパスキーしか通さない。</b>別のクライアントで登録したパスキーは断る。
	 * </p>
	 *
	 * @param context		コンテキスト
	 * @param rp			パスキーを結びつけるサイト
	 * @param credential	ブラウザが返したもの（{@code PublicKeyCredential.toJSON()} の形）
	 * @param lookup		利用者 ID からログインさせる人を引く（入れないなら null）
	 * @return	ログインした場合 = true。<b>断る理由は返さない</b>（ログにだけ出す）
	 */
	public static boolean login (WebContext context, PasskeyRp rp, Data credential, Function<Long, Principal> lookup) {

		requireUsable();

		String realm = Auth.realmOf(context);
		String challenge = takeChallenge(context, GET, rp, realm);

		byte[] credentialId;

		try {
			credentialId = b64(credential.getString("rawId"), "rawId");
		} catch (IllegalArgumentException ex) {
			Log.warn("パスキーでログインできませんでした: " + ex.getMessage());
			return false;
		}

		Data row = row(credentialId);

		if (row == null || !realm.equals(row.getStringOptional("realm"))) {
			Log.warn("パスキーでログインできませんでした: 登録されていない資格情報です");
			return false;
		}

		// 別のサイト（クライアント）で登録したパスキーは通さない（D-262）
		if (!rp.id().equals(row.getStringOptional("rp_id"))) {
			Log.warn("パスキーでログインできませんでした: 別のサイト（%s）で登録したパスキーです: %s"
				.formatted(row.getStringOptional("rp_id"), rp.id()));
			return false;
		}

		long userId = row.getLong("user_id");
		long storedCount = row.getLong("sign_count");

		Assertion assertion;

		try {
			assertion = verifyAssertion(rp.id(), rp.origins(), challenge, credential
				, CoseKey.restore(B64_DECODER.decode(row.getString("public_key")), row.getInt("algorithm"))
				, row.getInt("algorithm"), row.getString("user_handle"), storedCount);
		} catch (IllegalArgumentException ex) {
			Log.warn("パスキーでログインできませんでした: %s / %s".formatted(who(realm, userId), ex.getMessage()));
			return false;
		}

		try (DB db = DBUtil.getMainDB()) {

			/*
			 * <b>読んだときと同じ回数のときだけ進める。</b>同じ応答を同時に2本送られても、通るのは1本だけ
			 * （チャレンジも1度しか使えないが、セッションを分けて送られたときのため）
			 */
			int updated = db.update(("UPDATE %s SET sign_count = ?, backed_up = ?, last_used_at = ?"
				+ " WHERE credential_hash = ? AND sign_count = ?").formatted(table(db))
				, assertion.signCount(), assertion.backedUp() ? 1 : 0, nowSeconds(), hash(credentialId), storedCount);

			if (updated == 0) {
				Log.warn("パスキーでログインできませんでした: 同時に使われました: " + who(realm, userId));
				return false;
			}

		}

		Principal principal = lookup.apply(userId);

		if (principal == null || !principal.isAuthenticated()) {
			Log.warn("パスキーでログインできませんでした: 利用者を引けません: " + who(realm, userId));
			return false;
		}

		if (principal.id() != userId) {
			throw new IllegalStateException("lookup が別の利用者を返しました（%d を引いて %d）".formatted(userId, principal.id()));
		}

		Auth.login(context, principal, realm);

		return true;

	}

	/**
	 * ログインを確かめたもの
	 *
	 * @param signCount	署名の回数
	 * @param backedUp	いまバックアップされているか
	 */
	record Assertion(long signCount, boolean backedUp) {}

	/**
	 * ログインの応答を確かめる（DB もセッションも触らない）
	 *
	 * @param rpId			パスキーを結びつけるドメイン
	 * @param origins		受け付けるオリジン
	 * @param challenge		出したチャレンジ（base64url）
	 * @param credential	ブラウザが返したもの
	 * @param key			保存した公開鍵
	 * @param algorithm		COSE のアルゴリズムの番号
	 * @param userHandle	保存した利用者のハンドル（base64url）
	 * @param storedCount	保存した署名の回数
	 * @return	確かめたもの
	 * @throws IllegalArgumentException	確かめられなかった場合
	 */
	static Assertion verifyAssertion (String rpId, List<String> origins, String challenge, Data credential
		, PublicKey key, int algorithm, String userHandle, long storedCount) {

		Data response = response(credential);

		/*
		 * <b>利用者のハンドルは必ず要る</b>。ログイン ID を聞かずに始めるので、
		 * 「この資格情報は、この利用者のもの」を確かめる手がかりはこれだけ
		 */
		byte[] handle = b64(response.getString("userHandle"), "userHandle");

		if (!MessageDigest.isEqual(handle, b64(userHandle, "保存した userHandle"))) {
			throw new IllegalArgumentException("userHandle が登録したものと違います");
		}

		byte[] clientDataJson = b64(response.getString("clientDataJSON"), "clientDataJSON");
		checkClientData(clientDataJson, "webauthn.get", challenge, origins);

		byte[] authData = b64(response.getString("authenticatorData"), "authenticatorData");
		AuthenticatorData data = AuthenticatorData.parse(authData);

		data.check(rpId);

		byte[] signed = new byte[authData.length + 32];
		System.arraycopy(authData, 0, signed, 0, authData.length);
		System.arraycopy(sha256(clientDataJson), 0, signed, authData.length, 32);

		if (!CoseKey.verify(key, algorithm, signed, b64(response.getString("signature"), "signature"))) {
			throw new IllegalArgumentException("署名が合いません");
		}

		/*
		 * <b>署名の回数が戻っていたら、複製を疑う</b>（WebAuthn 7.2 の 21）。
		 * 同期するパスキーはいつも 0 を返すので、どちらも 0 なら見ない
		 */
		if ((data.signCount() != 0 || storedCount != 0) && data.signCount() <= storedCount) {
			throw new IllegalArgumentException("署名の回数が戻っています（認証器の複製の疑い）: %d → %d"
				.formatted(storedCount, data.signCount()));
		}

		return new Assertion(data.signCount(), data.has(FLAG_BS));

	}

	// endregion

	// region 一覧と削除

	/**
	 * 登録しているパスキーの一覧（種別なし）
	 *
	 * @param userId	利用者 ID
	 * @return	一覧（{@code id}・{@code label}・{@code created_at}・{@code last_used_at}・{@code backed_up}。時刻はエポック秒）
	 */
	public static List<Data> list (long userId) {

		return list("", userId);

	}

	/**
	 * 登録しているパスキーの一覧
	 *
	 * @param realm		種別。空文字なら種別なし
	 * @param userId	利用者 ID
	 * @return	一覧（{@code id} は {@link #delete} に渡すもの。どのサイトのものも含む。{@code rp_id} で分かる）
	 */
	public static List<Data> list (String realm, long userId) {

		return list(realm, null, userId);

	}

	/**
	 * あるサイト（クライアント）で登録しているパスキーの一覧（D-262）
	 *
	 * @param realm		種別。空文字なら種別なし
	 * @param rp		サイト（null ならどのサイトのものも）
	 * @param userId	利用者 ID
	 * @return	一覧（{@code id} は {@link #delete} に渡すもの）
	 */
	public static List<Data> list (String realm, PasskeyRp rp, long userId) {

		requireDb();

		try (DB db = DBUtil.getMainDB()) {

			List<Data> rows = rp == null
				? db.selectList(("SELECT credential_hash, rp_id, label, created_at, last_used_at, backed_up FROM %s"
					+ " WHERE realm = ? AND user_id = ? ORDER BY created_at, credential_hash").formatted(table(db)), realm, userId)
				: db.selectList(("SELECT credential_hash, rp_id, label, created_at, last_used_at, backed_up FROM %s"
					+ " WHERE realm = ? AND rp_id = ? AND user_id = ? ORDER BY created_at, credential_hash").formatted(table(db))
					, realm, rp.id(), userId);

			List<Data> list = new ArrayList<>();

			for (Data row : rows) {
				list.add(new Data()
					.putData("id", row.getString("credential_hash"))
					.putData("rp_id", row.getString("rp_id"))
					.putData("label", row.getStringOptional("label"))
					.putData("created_at", row.getLong("created_at"))
					.putData("last_used_at", row.getLong("last_used_at"))
					.putData("backed_up", row.getInt("backed_up") == 1));
			}

			return list;

		}

	}

	/**
	 * 1つ消す（種別なし）
	 *
	 * @param userId	利用者 ID
	 * @param id		{@link #list} の {@code id}
	 * @return	消した場合 = true（ほかの人のものは消さない）
	 */
	public static boolean delete (long userId, String id) {

		return delete("", userId, id);

	}

	/**
	 * 1つ消す
	 *
	 * <p>
	 * <b>{@code Auth.FULL_AUTH} を付けたルートから呼ぶ。</b>最後の1つを消すと、その人はパスキーで入れなくなる
	 * （パスワードなど、ほかのログインの手段が残っているかはアプリが確かめる）。
	 * </p>
	 *
	 * @param realm		種別。空文字なら種別なし
	 * @param userId	利用者 ID
	 * @param id		{@link #list} の {@code id}
	 * @return	消した場合 = true（ほかの人のものは消さない）
	 */
	public static boolean delete (String realm, long userId, String id) {

		requireDb();

		try (DB db = DBUtil.getMainDB()) {
			return db.delete("DELETE FROM %s WHERE realm = ? AND user_id = ? AND credential_hash = ?".formatted(table(db))
				, realm, userId, id) > 0;
		}

	}

	/**
	 * その人のパスキーを全部消す（退会など）
	 *
	 * @param realm		種別。空文字なら種別なし
	 * @param userId	利用者 ID
	 * @return	消した数
	 */
	public static int deleteAll (String realm, long userId) {

		requireDb();

		try (DB db = DBUtil.getMainDB()) {
			return db.delete("DELETE FROM %s WHERE realm = ? AND user_id = ?".formatted(table(db)), realm, userId);
		}

	}

	// endregion

	// region ブラウザ側の JS

	/* 同梱の JS */
	private static volatile byte[] script;

	/**
	 * ブラウザ側の JS を返すハンドラ（{@code JimblePasskey.register(...)} / {@code JimblePasskey.login(...)}）
	 *
	 * <pre>{@code
	 * get("/passkey.js", Passkey.script()).attribute(Auth.PUBLIC, true);
	 * }</pre>
	 *
	 * @return	ハンドラ
	 */
	public static Handler script () {

		return context -> {
			context.response().setResponseHeader("Cache-Control", "public, max-age=3600");
			context.response().send(new String(scriptBytes(), StandardCharsets.UTF_8), "text/javascript; charset=utf-8");
		};

	}

	private static byte[] scriptBytes () {

		byte[] cached = script;

		if (cached != null) {
			return cached;
		}

		try (InputStream in = Passkey.class.getResourceAsStream("jimble-passkey.js")) {
			if (in == null) {
				throw new IllegalStateException("jimble-passkey.js が jar にありません");
			}
			cached = in.readAllBytes();
		} catch (IOException ex) {
			throw new IllegalStateException("jimble-passkey.js を読めません", ex);
		}

		script = cached;

		return cached;

	}

	// endregion

	// region チャレンジ

	/**
	 * チャレンジを出してセッションに置く
	 */
	private static String issueChallenge (WebContext context, String purpose, PasskeyRp rp, String subject, String handle) {

		if (!context.session().isAvailable()) {
			throw new IllegalStateException("パスキーにはセッションが要ります（session.store = db / redis / cookie）");
		}

		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String challenge = B64.encodeToString(bytes);

		context.session().put(KEY_CHALLENGE, challenge);
		context.session().put(KEY_PURPOSE, purpose);
		context.session().put(KEY_SUBJECT, subject);
		context.session().put(KEY_RP, rp.id());
		context.session().put(KEY_HANDLE, handle);
		context.session().put(KEY_ISSUED_AT, nowSeconds());
		context.session().save();

		return challenge;

	}

	/**
	 * セッションのチャレンジを取り出して消す（1度しか使わせない）
	 *
	 * @throws HttpException	無い・目的や相手やサイトが違う・時間切れの場合（400）
	 */
	private static String takeChallenge (WebContext context, String purpose, PasskeyRp rp, String subject) {

		String challenge = context.session().get(KEY_CHALLENGE);
		String issuedPurpose = context.session().get(KEY_PURPOSE);
		String issuedSubject = context.session().get(KEY_SUBJECT);
		String issuedRp = context.session().get(KEY_RP);
		long issuedAt = context.session().getLong(KEY_ISSUED_AT);

		if (challenge != null && !challenge.isEmpty()) {
			context.session().remove(KEY_CHALLENGE);
			context.session().remove(KEY_PURPOSE);
			context.session().remove(KEY_SUBJECT);
			context.session().remove(KEY_RP);
			context.session().remove(KEY_HANDLE);
			context.session().remove(KEY_ISSUED_AT);
			context.session().save();
		}

		if (challenge == null || challenge.isEmpty() || !purpose.equals(issuedPurpose) || !subject.equals(issuedSubject)
			|| !rp.id().equals(issuedRp)) {
			throw new HttpException(400, "パスキーの手続きを最初からやり直してください");
		}

		if (nowSeconds() - issuedAt > PasskeyConf.timeout().toSeconds()) {
			throw new HttpException(400, "時間が経ちすぎました。パスキーの手続きを最初からやり直してください");
		}

		return challenge;

	}

	// endregion

	// region 中身を読む

	/**
	 * clientDataJSON を確かめる
	 */
	static void checkClientData (byte[] clientDataJson, String type, String challenge, List<String> origins) {

		Data client;

		try {
			client = Dson.decodes(new String(clientDataJson, StandardCharsets.UTF_8), Data.class);
		} catch (RuntimeException ex) {
			throw new IllegalArgumentException("clientDataJSON を読めません", ex);
		}

		if (client == null || !type.equals(client.getStringOptional("type"))) {
			throw new IllegalArgumentException("clientDataJSON の type が %s ではありません".formatted(type));
		}

		if (!MessageDigest.isEqual(challenge.getBytes(StandardCharsets.US_ASCII)
			, client.getStringOptional("challenge").getBytes(StandardCharsets.US_ASCII))) {
			throw new IllegalArgumentException("チャレンジが違います");
		}

		String origin = client.getStringOptional("origin");

		if (!origins.contains(origin)) {
			throw new IllegalArgumentException("受け付けないオリジンです: " + origin);
		}

		// 別のサイトの iframe の中からの呼び出しは断る
		if ("true".equalsIgnoreCase(client.getStringOptional("crossOrigin"))) {
			throw new IllegalArgumentException("crossOrigin の呼び出しは受け付けません");
		}

	}

	/**
	 * authenticatorData（WebAuthn 6.1）
	 *
	 * @param rpIdHash		rp_id の SHA-256
	 * @param flags			印
	 * @param signCount		署名の回数
	 * @param credentialId	資格情報の ID（登録のときだけ）
	 * @param publicKey		公開鍵（登録のときだけ）
	 */
	record AuthenticatorData(byte[] rpIdHash, int flags, long signCount, byte[] credentialId, Map<?, ?> publicKey) {

		/**
		 * 読む
		 *
		 * @param bytes	authenticatorData
		 * @return	読んだもの
		 * @throws IllegalArgumentException	読めない場合
		 */
		static AuthenticatorData parse (byte[] bytes) {

			if (bytes.length < 37) {
				throw new IllegalArgumentException("authenticatorData が短すぎます");
			}

			byte[] rpIdHash = java.util.Arrays.copyOfRange(bytes, 0, 32);
			int flags = bytes[32] & 0xff;
			long signCount = ((bytes[33] & 0xffL) << 24) | ((bytes[34] & 0xffL) << 16) | ((bytes[35] & 0xffL) << 8) | (bytes[36] & 0xffL);

			int position = 37;
			byte[] credentialId = null;
			Map<?, ?> publicKey = null;

			if ((flags & FLAG_AT) != 0) {

				// AAGUID（16）+ 長さ（2）
				if (bytes.length < position + 18) {
					throw new IllegalArgumentException("authenticatorData の資格情報が短すぎます");
				}

				position += 16;
				int length = ((bytes[position] & 0xff) << 8) | (bytes[position + 1] & 0xff);
				position += 2;

				if (length == 0 || length > MAX_CREDENTIAL_ID || bytes.length < position + length) {
					throw new IllegalArgumentException("資格情報の ID の長さが違います: " + length);
				}

				credentialId = java.util.Arrays.copyOfRange(bytes, position, position + length);
				position += length;

				Cbor.Decoded key = Cbor.decodePrefix(bytes, position);

				if (!(key.value() instanceof Map<?, ?> map)) {
					throw new IllegalArgumentException("公開鍵が COSE_Key ではありません");
				}

				publicKey = map;
				position = key.end();

			}

			if ((flags & FLAG_ED) != 0) {

				Cbor.Decoded extensions = Cbor.decodePrefix(bytes, position);

				if (!(extensions.value() instanceof Map)) {
					throw new IllegalArgumentException("authenticatorData の拡張がマップではありません");
				}

				position = extensions.end();

			}

			if (position != bytes.length) {
				throw new IllegalArgumentException("authenticatorData の後ろに余りがあります");
			}

			return new AuthenticatorData(rpIdHash, flags, signCount, credentialId, publicKey);

		}

		/**
		 * 印が立っているか
		 *
		 * @param flag	印
		 * @return	立っていれば true
		 */
		boolean has (int flag) {

			return (flags & flag) != 0;

		}

		/**
		 * rp_id・UP・UV・バックアップの印を確かめる
		 *
		 * @param rpId	パスキーを結びつけるドメイン
		 */
		void check (String rpId) {

			if (!MessageDigest.isEqual(rpIdHash, sha256(rpId.getBytes(StandardCharsets.UTF_8)))) {
				throw new IllegalArgumentException("rpIdHash が rp_id（%s）と違います".formatted(rpId));
			}

			if (!has(FLAG_UP)) {
				throw new IllegalArgumentException("利用者がいたこと（UP）が確かめられていません");
			}

			if (!has(FLAG_UV)) {
				throw new IllegalArgumentException("本人確認（UV）がされていません");
			}

			if (has(FLAG_BS) && !has(FLAG_BE)) {
				throw new IllegalArgumentException("バックアップの印が矛盾しています（BS なのに BE でない）");
			}

		}

	}

	private static Data response (Data credential) {

		if (credential == null) {
			throw new IllegalArgumentException("資格情報がありません");
		}

		if (!"public-key".equals(credential.getStringOptional("type"))) {
			throw new IllegalArgumentException("type が public-key ではありません");
		}

		Data response = credential.getDataOptional("response");

		if (response == null || response.isEmpty()) {
			throw new IllegalArgumentException("response がありません");
		}

		return response;

	}

	private static byte[] b64 (String value, String name) {

		if (value == null || value.isEmpty()) {
			throw new IllegalArgumentException(name + " がありません");
		}

		try {
			return B64_DECODER.decode(value);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException(name + " が base64url ではありません", ex);
		}

	}

	static byte[] sha256 (byte[] bytes) {

		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}

	}

	// endregion

	// region DB

	/**
	 * 利用者のハンドル（その人のパスキーで共通。無ければ作る）
	 *
	 * <p>利用者 ID そのものは使わない（認証器に個人を表す値を置かない。WebAuthn 14.6.1）。</p>
	 */
	private static String userHandle (String realm, String rpId, long userId) {

		try (DB db = DBUtil.getMainDB()) {

			Data row = db.select("SELECT user_handle FROM %s WHERE realm = ? AND rp_id = ? AND user_id = ? ORDER BY created_at LIMIT 1"
				.formatted(table(db)), realm, rpId, userId).orElse(null);

			if (row != null) {
				return row.getString("user_handle");
			}

		}

		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);

		return B64.encodeToString(bytes);

	}

	private static List<String> credentialIds (String realm, String rpId, long userId) {

		try (DB db = DBUtil.getMainDB()) {
			return db.selectList("SELECT credential_id FROM %s WHERE realm = ? AND rp_id = ? AND user_id = ?".formatted(table(db))
				, realm, rpId, userId)
				.stream().map(row -> row.getString("credential_id")).toList();
		}

	}

	private static Data row (byte[] credentialId) {

		try (DB db = DBUtil.getMainDB()) {
			return db.select("SELECT * FROM %s WHERE credential_hash = ?".formatted(table(db)), hash(credentialId)).orElse(null);
		}

	}

	/**
	 * 資格情報の ID の SHA-256（主キー。ID は 1,023 バイトまであり、そのままでは索引に入らない）
	 */
	static String hash (byte[] credentialId) {

		return HexFormat.of().formatHex(sha256(credentialId));

	}

	private static String table (DB db) {

		return db.dialect().identifier(FrameworkTables.AUTH_PASSKEY);

	}

	private static String who (String realm, long userId) {

		return realm.isEmpty() ? "id=" + userId : "%s:%d".formatted(realm, userId);

	}

	private static long nowSeconds () {

		return System.currentTimeMillis() / 1000;

	}

	private static void requireUsable () {

		if (!PasskeyConf.enabled()) {
			throw new IllegalStateException("パスキーが無効です: " + PasskeyConf.KEY_ENABLED);
		}

		requireDb();

	}

	private static void requireDb () {

		if (!DBUtil.isUseDB()) {
			throw new IllegalStateException("パスキーには DB が要ります");
		}

		install();

	}

	/**
	 * 表を作る
	 */
	private static void install () {

		if (initialized) {
			return;
		}

		synchronized (Passkey.class) {

			if (initialized) {
				return;
			}

			String name = FrameworkTables.AUTH_PASSKEY;
			DBVersion version = new DBVersion(name, "パスキー");

			version.add(1)
				.mysql("""
					create table `%s`
					(
						credential_hash  varchar(64)   not null primary key
						, realm            varchar(64)   not null
						, rp_id            varchar(253)  not null
						, user_id          bigint        not null
						, credential_id    varchar(1400) not null
						, user_handle      varchar(64)   not null
						, public_key       varchar(2048) not null
						, algorithm        int           not null
						, sign_count       bigint        not null
						, backup_eligible  smallint      not null
						, backed_up        smallint      not null
						, label            varchar(255)  not null
						, created_at       bigint        not null
						, last_used_at     bigint        not null
					) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
					""".formatted(name, version.placeholder())
					, "create index %s__user on `%s` (realm, rp_id, user_id)".formatted(name, name))
				.postgresql("""
					create table "%s"
					(
						credential_hash  varchar(64)   not null primary key
						, realm            varchar(64)   not null
						, rp_id            varchar(253)  not null
						, user_id          bigint        not null
						, credential_id    varchar(1400) not null
						, user_handle      varchar(64)   not null
						, public_key       varchar(2048) not null
						, algorithm        int           not null
						, sign_count       bigint        not null
						, backup_eligible  smallint      not null
						, backed_up        smallint      not null
						, label            varchar(255)  not null
						, created_at       bigint        not null
						, last_used_at     bigint        not null
					)
					""".formatted(name)
					, "create index %s__user on \"%s\" (realm, rp_id, user_id)".formatted(name, name));

			if (!version.apply(DBUtil.getMainDB())) {
				throw new IllegalStateException("パスキーの表を作れませんでした（直前のエラーログを見てください）");
			}

			initialized = true;

		}

	}

	// endregion

}
