package io.jimble.web.auth.mfa;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FrameworkTables;
import io.jimble.db.version.DBVersion;
import io.jimble.util.data.Data;
import io.jimble.util.crypto.Aead;
import io.jimble.util.hash.Hash;
import io.jimble.util.log.Log;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Lockout;
import io.jimble.web.auth.Principal;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 二要素認証（TOTP。要件 F-W-32）
 *
 * <h2>ログインの流れ</h2>
 * <pre>
 * // パスワードが合ったあと
 * if (Mfa.isActive(staffId)) {
 *     Mfa.pending(context, principal);          // <b>まだログインさせない</b>
 *     context.response().redirect("/login/code");
 *     return;
 * }
 *
 * Auth.login(context, principal);
 * </pre>
 *
 * <pre>
 * // POST /login/code
 * if (!Mfa.complete(context, code)) {           // 通れば中で Auth.login まで済む
 *     context.flash().put("message", "コードが違います");
 *     context.response().redirect("/login/code");
 * }
 * </pre>
 *
 * <p>
 * <b>コードを入れるまでは「ログインしていない」。</b>
 * {@link Auth#principal} は {@link Principal#ANONYMOUS} を返す——
 * <b>ルートを足した人が何もしなければ入れない</b>（{@code Auth.PUBLIC} の既定と同じ考え）。
 * </p>
 *
 * <h2>登録の流れ</h2>
 * <pre>
 * Mfa.Enrollment enrollment = Mfa.enroll(staffId, "member1@example.com");
 *
 * // enrollment.uri() を QR にして見せる。回復コードは<b>この一度だけ</b>見せる
 * // 認証アプリに入れてもらってから
 * Mfa.activate(staffId, code);
 * </pre>
 *
 * <h2>秘密鍵は暗号化して持つ</h2>
 * <p>
 * <b>{@code auth.mfa.secret_key} が無ければ有効化を断る。</b>
 * TOTP の秘密鍵はパスワードのハッシュと違って<b>漏れたらその場で使える</b>——
 * 解読の手間が無い。「2FA を入れたのに DB が漏れたら全員突破される」を作らせない。
 * </p>
 *
 * <p>
 * <b>{@code cipher.key} は流用しない</b>（D-154）。流用すると、
 * その鍵を持っていなかったアプリが二要素認証を入れた瞬間に
 * <b>保存済みのパスワードハッシュが読めなくなり、全員がログインできなくなる</b>——
 * 理由は {@link MfaConf} に書いてある。暗号化は
 * {@link Aead}（AES-256-GCM）で、<b>改ざんも検知する</b>。
 * </p>
 */
public final class Mfa {

	/** セッションに入れる鍵：途中の利用者 ID */
	private static final String KEY_PENDING_ID = "__mfa_pending_id";

	/** セッションに入れる鍵：途中の表示名 */
	private static final String KEY_PENDING_NAME = "__mfa_pending_name";

	/** セッションに入れる鍵：途中の役割 */
	private static final String KEY_PENDING_ROLE = "__mfa_pending_role";

	/** セッションに入れる鍵：いつ始めたか */
	private static final String KEY_PENDING_AT = "__mfa_pending_at";

	/** 乱数 */
	private static final SecureRandom RANDOM = new SecureRandom();

	/** 回復コードの文字（紛らわしいものを外してある） */
	private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	/* テーブルを作ったか */
	private static volatile boolean initialized = false;

	private Mfa () {
	}

	/**
	 * 有効にするときに出すもの
	 *
	 * @param secret		秘密鍵（base32）
	 * @param uri			認証アプリに読ませる {@code otpauth://} の URI
	 * @param recoveryCodes	回復コード。<b>ここでしか見られない</b>
	 */
	public record Enrollment(String secret, String uri, List<String> recoveryCodes) {}

	// region 登録

	/**
	 * 有効にする準備をする
	 *
	 * <p>
	 * <b>まだ有効にはならない。</b>{@link #activate} でコードが合って初めて有効になる——
	 * <b>認証アプリに入れ損ねた人が締め出される</b>のを防ぐため。
	 * </p>
	 *
	 * <p><b>回復コードはここでしか見られない。</b>DB にはハッシュしか残らない。</p>
	 *
	 * @param userId		利用者 ID
	 * @param accountName	認証アプリに出る名前（ログイン ID やメール）
	 * @return	出すもの
	 */
	public static Enrollment enroll (long userId, String accountName) {

		requireUsable();

		/*
		 * <b>暗号鍵が無ければ断る。</b>
		 * 秘密鍵を平文で持つと、DB が漏れた時点で<b>全員の2要素が無効になる</b>——
		 * しかもパスワードと違って<b>破る手間がゼロ</b>である。
		 */
		String key = MfaConf.secretKey();

		if (key.isEmpty()) {
			throw new IllegalStateException("""
				二要素認証には %s が要ります（秘密鍵を平文で持たないため）。
				  application.conf に次を足すか、環境変数で渡してください。
				    auth { mfa { secret_key = ${?MFA_SECRET_KEY} } }

				  cipher.key を流用しないでください。hash.password.encrypt の既定が
				  「cipher.key があれば true」なので、いま平文の BCrypt を保存しているなら
				  全員がログインできなくなります。
				""".formatted(MfaConf.KEY_SECRET_KEY));
		}

		byte[] secret = Totp.secret();
		String base32 = Totp.toBase32(secret);

		List<String> codes = recoveryCodes();
		long now = nowSeconds();

		try (DB db = DBUtil.getMainDB()) {

			// 途中でやめた人の残りを消してから入れ直す
			db.delete("DELETE FROM %s WHERE user_id = ?".formatted(table(db, FrameworkTables.AUTH_MFA)), userId);
			db.delete("DELETE FROM %s WHERE user_id = ?".formatted(table(db, FrameworkTables.AUTH_MFA_RECOVERY)), userId);

			db.insert("INSERT INTO %s (user_id, secret, activated_at, last_counter) VALUES (?, ?, 0, 0)"
				.formatted(table(db, FrameworkTables.AUTH_MFA)), userId, Aead.encrypt(base32, key));

			for (String code : codes) {
				db.insert("INSERT INTO %s (user_id, code_hash, created_at) VALUES (?, ?, ?)"
					.formatted(table(db, FrameworkTables.AUTH_MFA_RECOVERY)), userId, Hash.sha256(code), now);
			}

		} catch (Exception cause) {
			throw new IllegalStateException("二要素認証を登録できませんでした", cause);
		}

		return new Enrollment(base32, uri(base32, accountName), codes);

	}

	/**
	 * コードが合ったら有効にする
	 *
	 * @param userId	利用者 ID
	 * @param code		認証アプリのコード
	 * @return	有効になった場合 = true
	 */
	public static boolean activate (long userId, String code) {

		requireUsable();

		Data row = row(userId);

		if (row == null) {
			return false;
		}

		long counter = Totp.verify(secretOf(row), code, nowSeconds()
			, MfaConf.period(), MfaConf.digits(), MfaConf.window());

		if (counter == Totp.NO_MATCH) {
			return false;
		}

		try (DB db = DBUtil.getMainDB()) {
			db.update("UPDATE %s SET activated_at = ?, last_counter = ? WHERE user_id = ?"
				.formatted(table(db, FrameworkTables.AUTH_MFA)), nowSeconds(), counter, userId);
		} catch (Exception cause) {
			throw new IllegalStateException("二要素認証を有効にできませんでした", cause);
		}

		return true;

	}

	/**
	 * 有効になっているか
	 *
	 * @param userId	利用者 ID
	 * @return	有効な場合 = true
	 */
	public static boolean isActive (long userId) {

		if (!MfaConf.enabled() || !DBUtil.isUseDB()) {
			return false;
		}

		install();

		Data row = row(userId);

		return row != null && row.getLong("activated_at") > 0;

	}

	/**
	 * やめる
	 *
	 * <p>
	 * <b>秘密鍵も回復コードも消える。</b>
	 * 呼ぶ前に<b>本人であることを確かめること</b>——
	 * ここが緩いと、2要素を入れた意味が無くなる（{@code Auth.FULL_AUTH} を付けたルートから呼ぶ）。
	 * </p>
	 *
	 * @param userId	利用者 ID
	 */
	public static void disable (long userId) {

		if (!DBUtil.isUseDB()) {
			return;
		}

		install();

		try (DB db = DBUtil.getMainDB()) {
			db.delete("DELETE FROM %s WHERE user_id = ?".formatted(table(db, FrameworkTables.AUTH_MFA)), userId);
			db.delete("DELETE FROM %s WHERE user_id = ?".formatted(table(db, FrameworkTables.AUTH_MFA_RECOVERY)), userId);
		} catch (Exception cause) {
			throw new IllegalStateException("二要素認証をやめられませんでした", cause);
		}

		Lockout.clear(lockoutKey(userId));

	}

	// endregion

	// region 確かめる

	/**
	 * コードを確かめる（認証アプリのコード、または回復コード）
	 *
	 * <p>
	 * <b>総当たりは {@link Lockout} で抑える。</b>6桁は 100 万通りしかないので、
	 * 抑えないと<b>1日あれば当たる</b>。
	 * </p>
	 *
	 * @param userId	利用者 ID
	 * @param code		入力されたもの
	 * @return	合っていた場合 = true
	 */
	public static boolean verify (long userId, String code) {

		requireUsable();

		String lockoutKey = lockoutKey(userId);

		if (Lockout.waitSeconds(lockoutKey) > 0) {
			throw new HttpException(429, "しばらく待ってからやり直してください");
		}

		Data row = row(userId);

		if (row == null || row.getLong("activated_at") <= 0) {
			Lockout.fail(lockoutKey);
			return false;
		}

		if (verifyTotp(userId, row, code) || verifyRecovery(userId, code)) {
			Lockout.clear(lockoutKey);
			return true;
		}

		Lockout.fail(lockoutKey);

		return false;

	}

	/**
	 * 認証アプリのコードを見る
	 *
	 * @param userId	利用者 ID
	 * @param row		行
	 * @param code		入力されたもの
	 * @return	合っていた場合 = true
	 */
	private static boolean verifyTotp (long userId, Data row, String code) {

		long counter = Totp.verify(secretOf(row), code, nowSeconds()
			, MfaConf.period(), MfaConf.digits(), MfaConf.window());

		if (counter == Totp.NO_MATCH) {
			return false;
		}

		/*
		 * <b>同じコードを二度通さない。</b>1つのコードは30秒生きているので、
		 * 抑えないと<b>肩越しに見た人が、その30秒のあいだ同じ数字で入れる</b>。
		 *
		 * 進んだ窓だけを通す（＝一度使った窓とそれ以前は通らない）。
		 */
		if (counter <= row.getLong("last_counter")) {
			Log.warn("二要素のコードが使い回されました: user_id=%d".formatted(userId));
			return false;
		}

		try (DB db = DBUtil.getMainDB()) {

			/*
			 * <b>進んだときだけ書く。</b>同時に2本来ても、
			 * 後から来た古いほうが上書きしない（＝戻さない）。
			 */
			int updated = db.update(
				"UPDATE %s SET last_counter = ? WHERE user_id = ? AND last_counter < ?"
					.formatted(table(db, FrameworkTables.AUTH_MFA)), counter, userId, counter);

			if (updated == 0) {
				// 同時に来たもう1本が先に使った
				return false;
			}

		} catch (Exception cause) {
			throw new IllegalStateException("二要素認証の窓を進められませんでした", cause);
		}

		return true;

	}

	/**
	 * 回復コードを見る
	 *
	 * @param userId	利用者 ID
	 * @param code		入力されたもの
	 * @return	合っていた場合 = true
	 */
	private static boolean verifyRecovery (long userId, String code) {

		if (code == null) {
			return false;
		}

		String normalized = code.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);

		if (normalized.isEmpty()) {
			return false;
		}

		try (DB db = DBUtil.getMainDB()) {

			/*
			 * <b>消せた1件だけを「使えた」とする。</b>
			 * 「引いてから消す」にすると、<b>同時に2回使える</b>。
			 */
			int deleted = db.delete("DELETE FROM %s WHERE user_id = ? AND code_hash = ?"
				.formatted(table(db, FrameworkTables.AUTH_MFA_RECOVERY)), userId, Hash.sha256(normalized));

			if (deleted <= 0) {
				return false;
			}

			Log.warn("回復コードで入りました: user_id=%d（残り %d 個）"
				.formatted(userId, remainingRecoveryCodes(db, userId)));

			return true;

		} catch (Exception cause) {
			throw new IllegalStateException("回復コードを確かめられませんでした", cause);
		}

	}

	/**
	 * 残っている回復コードの数
	 *
	 * @param userId	利用者 ID
	 * @return	数
	 */
	public static int remainingRecoveryCodes (long userId) {

		if (!DBUtil.isUseDB()) {
			return 0;
		}

		install();

		try (DB db = DBUtil.getMainDB()) {
			return remainingRecoveryCodes(db, userId);
		} catch (Exception cause) {
			throw new IllegalStateException("回復コードを数えられませんでした", cause);
		}

	}

	/**
	 * 残っている回復コードの数
	 *
	 * @param db		DB
	 * @param userId	利用者 ID
	 * @return	数
	 */
	private static int remainingRecoveryCodes (DB db, long userId) {

		Data row = db.select("SELECT count(*) as cnt FROM %s WHERE user_id = ?"
			.formatted(table(db, FrameworkTables.AUTH_MFA_RECOVERY)), userId);

		return row == null ? 0 : row.getInt("cnt");

	}

	// endregion

	// region ログインの途中

	/**
	 * パスワードは合ったが、コードがまだ、という状態にする
	 *
	 * <p>
	 * <b>ここでは {@link Auth#login} を呼ばない。</b>
	 * 呼んでしまうと、<b>コードを入れる前にログインできている</b>ことになる。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param principal	パスワードが合った人
	 */
	public static void pending (WebContext context, Principal principal) {

		if (principal == null || !principal.isAuthenticated()) {
			throw new IllegalArgumentException("待たせる相手がいません（id が 0 です）");
		}

		/*
		 * <b>ここでも振り直す。</b>「パスワードが通った」時点で
		 * セッションの中身が変わるので、仕込まれた ID をここで切る（要件 F-S-13）。
		 */
		context.session().regenerateId();

		context.session().put(KEY_PENDING_ID, principal.id());
		context.session().put(KEY_PENDING_NAME, principal.name());
		context.session().put(KEY_PENDING_ROLE, principal.role());
		context.session().put(KEY_PENDING_AT, nowSeconds());

		context.session().save();

	}

	/**
	 * 途中の人がいるか
	 *
	 * @param context	コンテキスト
	 * @return	いる場合 = true
	 */
	public static boolean isPending (WebContext context) {

		return pendingPrincipal(context).isAuthenticated();

	}

	/**
	 * 途中の人
	 *
	 * <p><b>{@code null} は返さない。</b>猶予を過ぎていれば {@link Principal#ANONYMOUS}。</p>
	 *
	 * @param context	コンテキスト
	 * @return	途中の人
	 */
	public static Principal pendingPrincipal (WebContext context) {

		long id = context.session().getLong(KEY_PENDING_ID);

		if (id <= 0) {
			return Principal.ANONYMOUS;
		}

		/*
		 * <b>猶予を過ぎたら無かったことにする。</b>
		 * 「パスワードだけ通った状態」を長く残さない。
		 */
		if (nowSeconds() - context.session().getLong(KEY_PENDING_AT) > MfaConf.pendingSeconds()) {
			return Principal.ANONYMOUS;
		}

		return new Principal(id
			, context.session().get(KEY_PENDING_NAME), context.session().get(KEY_PENDING_ROLE));

	}

	/**
	 * コードを受け取ってログインまで済ませる
	 *
	 * @param context	コンテキスト
	 * @param code		入力されたもの
	 * @return	ログインできた場合 = true
	 */
	public static boolean complete (WebContext context, String code) {

		Principal principal = pendingPrincipal(context);

		if (!principal.isAuthenticated()) {
			throw new HttpException(401, "はじめからやり直してください");
		}

		if (!verify(principal.id(), code)) {
			return false;
		}

		/*
		 * <b>途中の印は消してから。</b>消さないと、
		 * ログインしたあとも「途中の人」がセッションに残る。
		 *
		 * <b>ここで save は呼ばない</b>——保存は1リクエストに1回で、
		 * 呼ぶと<b>そのあとの Auth.login の保存が黙って捨てられる</b>。
		 */
		context.session().remove(KEY_PENDING_ID);
		context.session().remove(KEY_PENDING_NAME);
		context.session().remove(KEY_PENDING_ROLE);
		context.session().remove(KEY_PENDING_AT);

		Auth.login(context, principal);

		return true;

	}

	/**
	 * 途中の状態をやめる
	 *
	 * @param context	コンテキスト
	 */
	public static void cancel (WebContext context) {

		context.session().remove(KEY_PENDING_ID);
		context.session().remove(KEY_PENDING_NAME);
		context.session().remove(KEY_PENDING_ROLE);
		context.session().remove(KEY_PENDING_AT);

		context.session().save();

	}

	// endregion

	// region 中身

	/**
	 * {@code otpauth://} の URI
	 *
	 * @param secret		秘密鍵（base32）
	 * @param accountName	認証アプリに出る名前
	 * @return	URI
	 */
	private static String uri (String secret, String accountName) {

		String issuer = MfaConf.issuer();

		return "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d".formatted(
			encode(issuer), encode(accountName), secret, encode(issuer)
			, MfaConf.digits(), MfaConf.period());

	}

	/**
	 * URI に載せる形にする
	 *
	 * @param value	値
	 * @return	載せる形
	 */
	private static String encode (String value) {

		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);

	}

	/**
	 * 回復コードを作る
	 *
	 * @return	回復コード
	 */
	private static List<String> recoveryCodes () {

		List<String> codes = new ArrayList<>();

		for (int i = 0; i < MfaConf.recoveryCodes(); i++) {

			StringBuilder code = new StringBuilder();

			// 10 文字。32^10 ≒ 2^50 通りあるので、総当たりでは当たらない
			for (int letter = 0; letter < 10; letter++) {
				code.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
			}

			codes.add(code.toString());

		}

		return codes;

	}

	/**
	 * 1件読む
	 *
	 * @param userId	利用者 ID
	 * @return	行。無ければ null
	 */
	private static Data row (long userId) {

		try (DB db = DBUtil.getMainDB()) {
			return db.select("SELECT secret, activated_at, last_counter FROM %s WHERE user_id = ?"
				.formatted(table(db, FrameworkTables.AUTH_MFA)), userId);
		} catch (Exception cause) {
			/*
			 * <b>ここで false を返して「コードが違います」にしない。</b>
			 * DB が落ちているのに<b>入力を疑わせる</b>のは、いちばん時間を無駄にさせる。
			 */
			throw new IllegalStateException("二要素認証の設定を読めませんでした", cause);
		}

	}

	/**
	 * 行から秘密鍵を戻す
	 *
	 * @param row	行
	 * @return	秘密鍵
	 */
	private static byte[] secretOf (Data row) {

		String secret = Aead.decrypt(row.getString("secret"), MfaConf.secretKey());

		/*
		 * <b>{@code Aead.decrypt} は失敗すると null を返す</b>（例外ではない）。
		 * ここで気づかずに進むと <b>NPE の 500</b> になり、
		 * <b>鍵を変えたことが原因だと分からない</b>——
		 * 利用者には「コードが違います」に見える。
		 */
		if (secret == null) {
			throw new IllegalStateException(
				"二要素認証の秘密鍵を復号できません。%s が変わっていませんか"
					.formatted(MfaConf.KEY_SECRET_KEY));
		}

		return Totp.fromBase32(secret);

	}

	/**
	 * 総当たりを数える単位
	 *
	 * @param userId	利用者 ID
	 * @return	単位
	 */
	private static String lockoutKey (long userId) {

		return "mfa:" + userId;

	}

	/**
	 * いま（秒）
	 *
	 * @return	秒
	 */
	private static long nowSeconds () {

		return Instant.now().getEpochSecond();

	}

	/**
	 * 使えることを確かめる
	 */
	private static void requireUsable () {

		if (!MfaConf.enabled()) {
			throw new IllegalStateException("二要素認証が無効です: " + MfaConf.KEY_ENABLED);
		}

		if (!DBUtil.isUseDB()) {
			throw new IllegalStateException("二要素認証には DB が要ります");
		}

		install();

	}

	/**
	 * テーブル名（製品ごとの囲みを付ける）
	 *
	 * @param db	DB
	 * @param name	テーブル名
	 * @return	テーブル名
	 */
	private static String table (DB db, String name) {

		return db.dialect().identifier(name);

	}

	/**
	 * テーブルを作る
	 */
	private static void install () {

		if (initialized) {
			return;
		}

		synchronized (Mfa.class) {

			if (initialized) {
				return;
			}

			DBVersion secret = new DBVersion(FrameworkTables.AUTH_MFA, "二要素認証");

			secret.add(1)
				.mysql("""
					create table `%s`
					(
						user_id      bigint      not null primary key
						, secret       varchar(512) not null
						, activated_at bigint      not null
						, last_counter bigint      not null
					) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
					""".formatted(FrameworkTables.AUTH_MFA, secret.placeholder()))
				.postgresql("""
					create table "%s"
					(
						user_id      bigint       not null primary key
						, secret       varchar(512) not null
						, activated_at bigint       not null
						, last_counter bigint       not null
					)
					""".formatted(FrameworkTables.AUTH_MFA));

			secret.apply(DBUtil.getMainDB());

			DBVersion recovery = new DBVersion(FrameworkTables.AUTH_MFA_RECOVERY, "二要素認証の回復コード");

			recovery.add(1)
				.mysql("""
					create table `%s`
					(
						user_id    bigint      not null
						, code_hash  varchar(64) not null
						, created_at bigint      not null
						, primary key (user_id, code_hash)
					) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
					""".formatted(FrameworkTables.AUTH_MFA_RECOVERY, recovery.placeholder()))
				.postgresql("""
					create table "%s"
					(
						user_id    bigint      not null
						, code_hash  varchar(64) not null
						, created_at bigint      not null
						, primary key (user_id, code_hash)
					)
					""".formatted(FrameworkTables.AUTH_MFA_RECOVERY));

			recovery.apply(DBUtil.getMainDB());

			initialized = true;

		}

	}

	// endregion

}
