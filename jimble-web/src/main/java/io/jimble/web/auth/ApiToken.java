package io.jimble.web.auth;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FrameworkTables;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.router.AttributeKey;
import io.jimble.web.router.Handler;
import io.jimble.web.session.SessionStores;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * API のトークン（{@code Authorization: Bearer}。D-264）
 *
 * <p>
 * <b>DB に持つ不透明なトークン</b>である（JWT ではない。失効でき、鍵の管理が要らない）。
 * 利用者が連携やスクリプトのために発行し、<b>一度だけ</b>見せる。DB にはハッシュしか残さない。
 * </p>
 *
 * <pre>{@code
 * before(ApiToken.authenticate(App::findPrincipal));   // Auth::guard と Csrf::verify より先に
 * before(Auth::guard);
 *
 * path("/api", () -> {
 *     attribute(ApiToken.ACCEPT, true);                 // トークンを受けるのは、宣言したルートだけ
 *     get("/requests", Api::list).attribute(ApiToken.SCOPE, "requests:read");
 *     post("/requests", Api::create).attribute(ApiToken.SCOPE, "requests:write");
 * });
 *
 * // 発行（パスワードを入れて入った人の画面から）
 * ApiToken.Issued issued = ApiToken.issue(me.id(), "経費の連携", Set.of("requests:read"), Duration.ofDays(90));
 * // issued.token() を一度だけ見せる
 * }</pre>
 *
 * <h2>トークンで入った人</h2>
 * <ul>
 *   <li><b>そのリクエストのあいだだけログインしている</b>（セッションの Cookie は出さない）。役割（{@code Auth.ROLE}）はふつうに効く</li>
 *   <li><b>{@code Auth.FULL_AUTH} のルートには入れない</b>（パスワードの変更・退会などは、トークンではさせない）</li>
 *   <li>ルートの {@link #SCOPE} がトークンに無ければ 403。セッションで入った人には、スコープは関係ない</li>
 *   <li>{@code Csrf.verify} は見ない（ブラウザは Authorization を勝手に付けないので、別のサイトからは送れない）</li>
 * </ul>
 *
 * <h2>止まるとき</h2>
 * <ul>
 *   <li>期限を過ぎた・{@link #revoke} で消した</li>
 *   <li><b>{@code Auth.revoke} / {@code Auth.revokeOthers} を呼んだ</b>（セッションと同じ世代で見る。パスワードを変えたらトークンも止まる）</li>
 *   <li>{@code lookup} が null を返した（利用者を止めた・消した）</li>
 * </ul>
 */
public final class ApiToken {

	/** ルート属性：トークンを受けるか（既定は受けない） */
	public static final AttributeKey<Boolean> ACCEPT = new AttributeKey<>("auth_api_token", false);

	/** ルート属性：トークンに要るスコープ（空なら問わない。セッションで入った人には効かない） */
	public static final AttributeKey<String> SCOPE = new AttributeKey<>("auth_api_token_scope", "");

	/** トークンの頭（漏れたトークンを見つける道具が拾えるように） */
	public static final String PREFIX = "jbt_";

	/* セッション（そのリクエストだけのもの）：トークンの ID とスコープ */
	private static final String KEY_TOKEN_ID = "__api_token_id";
	private static final String KEY_TOKEN_SCOPES = "__api_token_scopes";

	/* スコープの形 */
	private static final String SCOPE_PATTERN = "[a-z0-9][a-z0-9:._-]{0,63}";

	/* スコープの数の上限 */
	private static final int MAX_SCOPES = 32;

	/* 最後に使った時刻を書き直す間隔（リクエストのたびには書かない） */
	private static final long TOUCH_INTERVAL_SECONDS = 60;

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

	/* 表を作ったか */
	private static volatile boolean initialized = false;

	private ApiToken () {
	}

	/**
	 * 発行したもの
	 *
	 * @param id	トークンの ID（一覧と削除に使う。秘密ではない）
	 * @param token	トークン（<b>ここでしか見られない</b>。DB にはハッシュだけ）
	 */
	public record Issued(String id, String token) {

		@Override
		public String toString () {

			// うっかりログに出しても、トークンそのものは出さない
			return "ApiToken.Issued[id=" + id + "]";

		}

	}

	// region 発行・一覧・削除

	/**
	 * 発行する（種別なし）
	 *
	 * @param userId	利用者 ID
	 * @param name		利用者が見分けるための名前（「経費の連携」など）
	 * @param scopes	スコープ（{@code requests:read} など。空ならスコープを問わないルートだけで使える）
	 * @param validFor	期限（{@link Duration#ZERO} なら無期限。無期限は勧めない）
	 * @return	発行したもの
	 */
	public static Issued issue (long userId, String name, Collection<String> scopes, Duration validFor) {

		return issue("", userId, name, scopes, validFor);

	}

	/**
	 * 発行する
	 *
	 * <p>
	 * <b>{@code Auth.FULL_AUTH} を付けたルートから呼ぶ</b>（セッションを盗んだ人にトークンを作らせない）。
	 * </p>
	 *
	 * @param realm		種別。空文字なら種別なし
	 * @param userId	利用者 ID
	 * @param name		利用者が見分けるための名前
	 * @param scopes	スコープ
	 * @param validFor	期限（{@link Duration#ZERO} なら無期限）
	 * @return	発行したもの
	 */
	public static Issued issue (String realm, long userId, String name, Collection<String> scopes, Duration validFor) {

		requireDb();

		if (userId <= 0) {
			throw new IllegalArgumentException("トークンを発行する相手がいません（id が 0 です）");
		}

		if (validFor == null || validFor.isNegative()) {
			throw new IllegalArgumentException("トークンの期限が違います（無期限なら Duration.ZERO）: " + validFor);
		}

		String scopeText = String.join(" ", checkScopes(scopes));
		String label = name == null ? "" : name.strip();

		if (label.length() > 255) {
			throw new IllegalArgumentException("トークンの名前が長すぎます（255 文字まで）");
		}

		byte[] secret = new byte[32];
		RANDOM.nextBytes(secret);
		String token = PREFIX + B64.encodeToString(secret);

		byte[] idBytes = new byte[16];
		RANDOM.nextBytes(idBytes);
		String id = HexFormat.of().formatHex(idBytes);

		long now = nowSeconds();

		try (DB db = DBUtil.getMainDB()) {
			db.insert(("INSERT INTO %s (id, token_hash, realm, user_id, name, scopes, generation, created_at, expires_at, last_used_at)"
				+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)").formatted(table(db))
				, id, hash(token), realm, userId, label, scopeText, Revocations.forLogin(realm, userId), now
				, validFor.isZero() ? 0 : now + validFor.toSeconds());
		}

		return new Issued(id, token);

	}

	/**
	 * 発行しているトークンの一覧（トークンそのものは無い）
	 *
	 * @param realm		種別。空文字なら種別なし
	 * @param userId	利用者 ID
	 * @return	一覧（{@code id}・{@code name}・{@code scopes}・{@code created_at}・{@code expires_at}（0 なら無期限）・{@code last_used_at}。時刻はエポック秒）
	 */
	public static List<Data> list (String realm, long userId) {

		requireDb();

		try (DB db = DBUtil.getMainDB()) {

			List<Data> list = new ArrayList<>();

			for (Data row : db.selectList(("SELECT id, name, scopes, created_at, expires_at, last_used_at FROM %s"
				+ " WHERE realm = ? AND user_id = ? ORDER BY created_at, id").formatted(table(db)), realm, userId)) {
				list.add(new Data()
					.putData("id", row.getString("id"))
					.putData("name", row.getStringOptional("name"))
					.putData("scopes", splitScopes(row.getStringOptional("scopes")))
					.putData("created_at", row.getLong("created_at"))
					.putData("expires_at", row.getLong("expires_at"))
					.putData("last_used_at", row.getLong("last_used_at")));
			}

			return list;

		}

	}

	/**
	 * 1つ消す（すぐ使えなくなる）
	 *
	 * @param realm		種別。空文字なら種別なし
	 * @param userId	利用者 ID
	 * @param id		{@link #list} の {@code id}
	 * @return	消した場合 = true（ほかの人のものは消さない）
	 */
	public static boolean revoke (String realm, long userId, String id) {

		requireDb();

		try (DB db = DBUtil.getMainDB()) {
			return db.delete("DELETE FROM %s WHERE realm = ? AND user_id = ? AND id = ?".formatted(table(db)), realm, userId, id) > 0;
		}

	}

	/**
	 * その人のトークンを全部消す
	 *
	 * @param realm		種別。空文字なら種別なし
	 * @param userId	利用者 ID
	 * @return	消した数
	 */
	public static int revokeAll (String realm, long userId) {

		requireDb();

		try (DB db = DBUtil.getMainDB()) {
			return db.delete("DELETE FROM %s WHERE realm = ? AND user_id = ?".formatted(table(db)), realm, userId);
		}

	}

	// endregion

	// region 確かめる

	/**
	 * {@code Authorization: Bearer} を確かめる before（{@code Auth::guard} と {@code Csrf::verify} より先に置く）
	 *
	 * <ul>
	 *   <li>Authorization が無ければ何もしない（セッションで入る人は、そのまま {@code Auth::guard} が見る）</li>
	 *   <li>{@link #ACCEPT} の無いルートに Bearer が来たら 401（トークンを受けないルート）</li>
	 *   <li>トークンが違う・期限切れ・止めた・締め出した、なら 401（{@code WWW-Authenticate: Bearer error="invalid_token"}）</li>
	 *   <li>ルートの {@link #SCOPE} がトークンに無ければ 403（{@code error="insufficient_scope"}）</li>
	 * </ul>
	 *
	 * @param lookup	利用者 ID から、いまの利用者を引く（止めた・消した人なら null）
	 * @return	before に置くもの
	 */
	public static Handler authenticate (Function<Long, Principal> lookup) {

		return context -> {

			if (context.route() == null || !context.route().matched()) {
				return;
			}

			String authorization = context.request().header().getStringOptional("authorization");

			if (authorization == null || authorization.isEmpty()) {
				return;
			}

			if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
				// Basic などはここでは見ない（BasicAuth のルートがある）
				return;
			}

			if (!context.route().route().attribute(ACCEPT)) {
				throw unauthorized(context, "このルートは API のトークンを受け付けません");
			}

			String token = authorization.substring(7).trim();
			String realm = Auth.realmOf(context);
			Data row = token.startsWith(PREFIX) && token.length() <= 100 ? row(hash(token), realm) : null;

			if (row == null) {
				throw unauthorized(context, "API のトークンが違います");
			}

			long userId = row.getLong("user_id");
			long expiresAt = row.getLong("expires_at");

			if (expiresAt > 0 && nowSeconds() >= expiresAt) {
				throw unauthorized(context, "API のトークンの期限が切れています");
			}

			// 締め出した・パスワードを変えた（セッションと同じ世代で見る）
			if (Revocations.isRevoked(realm, userId, row.getLong("generation"))) {
				throw unauthorized(context, "API のトークンは止められています");
			}

			Principal principal = lookup.apply(userId);

			if (principal == null || !principal.isAuthenticated()) {
				throw unauthorized(context, "API のトークンの利用者がいません");
			}

			if (principal.id() != userId) {
				throw new IllegalStateException("lookup が別の利用者を返しました（%d を引いて %d）".formatted(userId, principal.id()));
			}

			Set<String> scopes = new LinkedHashSet<>(splitScopes(row.getStringOptional("scopes")));
			String required = context.route().route().attribute(SCOPE);

			if (!required.isEmpty() && !scopes.contains(required)) {
				context.response().setResponseHeader("WWW-Authenticate"
					, "Bearer error=\"insufficient_scope\", scope=\"%s\"".formatted(required));
				throw new HttpException(403, "API のトークンに %s がありません".formatted(required));
			}

			/*
			 * <b>そのリクエストのあいだだけログインさせる</b>。セッションは「なし」にして Cookie を出さない。
			 * FULL_AUTH は付けない（トークンではパスワードの変更などをさせない）
			 */
			try {
				context.sessionStore(SessionStores.none());
			} catch (IllegalStateException ex) {
				throw new IllegalStateException(
					"ApiToken.authenticate(...) は before のいちばん先に置いてください（Remember.restore や Auth::guard より前。"
						+ "先にセッションを読まれると、トークンのリクエストにセッションの Cookie が出てしまう）", ex);
			}
			Auth.loginForRequest(context, principal, realm, row.getLong("generation"));
			context.session().put(KEY_TOKEN_ID, row.getString("id"));
			context.session().put(KEY_TOKEN_SCOPES, String.join(" ", scopes));

			touch(row.getString("id"), row.getLong("last_used_at"));

		};

	}

	/**
	 * このリクエストを、トークンで入った人が送ってきたか
	 *
	 * @param context	コンテキスト
	 * @return	トークンで入った場合 = true
	 */
	public static boolean isTokenRequest (WebContext context) {

		/*
		 * <b>Bearer が無ければ、セッションに触らずに false</b>。Csrf.verify から毎回呼ばれるので、
		 * ふつうのリクエストでセッションを読み始めると、あとの Auth.guard が NO_SESSION のルートで
		 * 保存先を変えられなくなる
		 */
		if (!hasBearer(context)) {
			return false;
		}

		String id = context.session().get(KEY_TOKEN_ID);

		return id != null && !id.isEmpty();

	}

	private static boolean hasBearer (WebContext context) {

		String authorization = context.request().header().getStringOptional("authorization");

		return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);

	}

	/**
	 * いまのトークンのスコープ
	 *
	 * @param context	コンテキスト
	 * @return	スコープ（トークンで入っていなければ空）
	 */
	public static Set<String> scopes (WebContext context) {

		if (!isTokenRequest(context)) {
			return Set.of();
		}

		return new LinkedHashSet<>(splitScopes(context.session().get(KEY_TOKEN_SCOPES)));

	}

	/**
	 * Bearer を送ってきたのに、トークンで入っていないか（authenticate を置き忘れたとき）
	 *
	 * @param context	コンテキスト
	 * @return	置き忘れの疑いがある場合 = true
	 */
	static boolean bearerIgnored (WebContext context) {

		return hasBearer(context) && !isTokenRequest(context);

	}

	private static HttpException unauthorized (WebContext context, String message) {

		context.response().setResponseHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
		Log.info("API のトークンを断りました: " + message);

		return new HttpException(401, message);

	}

	// endregion

	// region 下回り

	private static List<String> checkScopes (Collection<String> scopes) {

		if (scopes == null) {
			return List.of();
		}

		Set<String> unique = new LinkedHashSet<>();

		for (String scope : scopes) {
			String value = scope == null ? "" : scope.strip().toLowerCase(Locale.ROOT);
			if (!value.matches(SCOPE_PATTERN)) {
				throw new IllegalArgumentException("スコープの形が違います（英小文字・数字・: . _ -、64 文字まで）: " + scope);
			}
			unique.add(value);
		}

		if (unique.size() > MAX_SCOPES) {
			throw new IllegalArgumentException("スコープが多すぎます（%d まで）".formatted(MAX_SCOPES));
		}

		return List.copyOf(unique);

	}

	private static List<String> splitScopes (String value) {

		return value == null || value.isBlank() ? List.of() : Arrays.asList(value.trim().split(" "));

	}

	private static Data row (String tokenHash, String realm) {

		requireDb();

		try (DB db = DBUtil.getMainDB()) {
			return db.select("SELECT * FROM %s WHERE token_hash = ? AND realm = ?".formatted(table(db)), tokenHash, realm).orElse(null);
		}

	}

	/**
	 * 最後に使った時刻を書く（前に書いてから間が空いていれば）
	 */
	private static void touch (String id, long lastUsedAt) {

		long now = nowSeconds();

		if (now - lastUsedAt < TOUCH_INTERVAL_SECONDS) {
			return;
		}

		try (DB db = DBUtil.getMainDB()) {
			db.update("UPDATE %s SET last_used_at = ? WHERE id = ? AND last_used_at < ?".formatted(table(db))
				, now, id, now - TOUCH_INTERVAL_SECONDS);
		} catch (RuntimeException ex) {
			// 使った時刻は記録だけ。書けなくてもリクエストは通す
			Log.warn("API のトークンの最後に使った時刻を書けませんでした: " + ex.getMessage());
		}

	}

	/**
	 * トークンの SHA-256（トークンは 256 ビットの乱数なので、塩も鍵も要らない）
	 */
	static String hash (String token) {

		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}

	}

	private static String table (DB db) {

		return db.dialect().identifier(FrameworkTables.AUTH_API_TOKEN);

	}

	private static long nowSeconds () {

		return System.currentTimeMillis() / 1000;

	}

	private static void requireDb () {

		if (!DBUtil.isUseDB()) {
			throw new IllegalStateException("API のトークンには DB が要ります");
		}

		install();

	}

	private static void install () {

		if (initialized) {
			return;
		}

		synchronized (ApiToken.class) {

			if (initialized) {
				return;
			}

			String name = FrameworkTables.AUTH_API_TOKEN;
			DBVersion version = new DBVersion(name, "API のトークン");

			version.add(1)
				.mysql("""
					create table `%s`
					(
						id             varchar(32)   not null primary key
						, token_hash     varchar(64)   not null
						, realm          varchar(64)   not null
						, user_id        bigint        not null
						, name           varchar(255)  not null
						, scopes         varchar(2200) not null
						, generation     bigint        not null
						, created_at     bigint        not null
						, expires_at     bigint        not null
						, last_used_at   bigint        not null
						, unique key %s__hash (token_hash)
					) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
					""".formatted(name, name, version.placeholder())
					, "create index %s__user on `%s` (realm, user_id)".formatted(name, name))
				.postgresql("""
					create table "%s"
					(
						id             varchar(32)   not null primary key
						, token_hash     varchar(64)   not null unique
						, realm          varchar(64)   not null
						, user_id        bigint        not null
						, name           varchar(255)  not null
						, scopes         varchar(2200) not null
						, generation     bigint        not null
						, created_at     bigint        not null
						, expires_at     bigint        not null
						, last_used_at   bigint        not null
					)
					""".formatted(name)
					, "create index %s__user on \"%s\" (realm, user_id)".formatted(name, name));

			if (!version.apply(DBUtil.getMainDB())) {
				throw new IllegalStateException("API のトークンの表を作れませんでした（直前のエラーログを見てください）");
			}

			initialized = true;

		}

	}

	// endregion

}
