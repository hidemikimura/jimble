package io.jimble.web.auth.mfa;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.FrameworkTables;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.conf.Conf;
import io.jimble.util.crypto.Aead;
import io.jimble.util.data.Data;
import io.jimble.util.hash.Hash;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Lockout;
import io.jimble.web.auth.Principal;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.router.Router;
import io.jimble.web.session.SessionStores;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 二要素認証の種別（realm。D-182）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * 運用者の画面と利用者の管理画面のように、<b>別々の表から引く ID</b> を持つアプリでは、
 * {@code staff.id = 1} と {@code member.id = 1} が<b>同じ「利用者 1」</b>として扱われていた。
 * 片方が登録すると<b>もう片方の秘密鍵を上書きし</b>（相手の認証アプリが黙って使えなくなる）、
 * <b>片方のコードでもう片方のログインが通る</b>。
 * </p>
 *
 * <p>
 * もう1つ固定したいのは<b>「種別を渡さないアプリは、何も変わらない」</b>ことである。
 * 表に列を足すので、<b>上げる前に登録していた人が、上げたあとも同じコードで入れる</b>ことを
 * 版1の表から実際に上げて確かめる。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。{@code ./gradlew :jimble-web:pgTest}</p>
 */
@Tag("db")
@SuppressWarnings("removal")  // 1.x の書き方も確かめている（2.0 で消す。要件 D-192）
class MfaRealmIntegrationTest {

	/** 2つの種別で同じ数字を持つ利用者 */
	private static final long USER_ID = 9101;

	/** 種別 */
	private static final String OPERATOR = "operator";

	/** 種別 */
	private static final String MEMBER = "member";

	/** 秘密鍵を暗号化する鍵 */
	private static final String SECRET_KEY = "0123456789abcdef0123456789abcdef";

	/* 元の設定 */
	private static Config originalConf;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();

		originalConf = Conf.conf().config();

		Conf.replace(ConfigFactory.parseString("""
			session.store = "none"
			auth.mfa.secret_key = "%s"
			""".formatted(SECRET_KEY)).withFallback(originalConf));

		assertTrue(DBUtil.load(Conf.conf().config(), MfaRealmIntegrationTest.class)
			, "DB に接続できませんでした");

	}

	@AfterAll
	static void stopDataSource () {

		cleanAll();

		DBUtil.stop();

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

	}

	@BeforeEach
	void clean () {

		SessionStores.reset();
		cleanAll();

	}

	/**
	 * 3つの種別の登録とロックアウトを消す
	 */
	private static void cleanAll () {

		for (String realm : new String[] { "", OPERATOR, MEMBER }) {
			Mfa.disable(realm, USER_ID);
		}

		Lockout.clear("mfa:" + USER_ID);
		Lockout.clear("mfa:" + OPERATOR + ":" + USER_ID);
		Lockout.clear("mfa:" + MEMBER + ":" + USER_ID);

	}

	// region 種別ごとに分かれる

	@Test
	@DisplayName("D-182 同じ ID でも、種別が違えば別の秘密鍵になる（上書きしない）")
	void sameIdInTwoRealmsKeepsTwoSecrets () {

		Mfa.Enrollment operator = enrollAndActivate(OPERATOR);
		Mfa.Enrollment member = enrollAndActivate(MEMBER);

		assertNotEquals(operator.secret(), member.secret());

		/*
		 * <b>ここが報告の形である。</b>種別が無いと、member の登録が operator の行を消して入れ直すので、
		 * operator の認証アプリのコードが<b>黙って通らなくなる</b>。
		 */
		assertTrue(Mfa.isActive(OPERATOR, USER_ID), "あとから登録した別の種別に消されています");
		assertTrue(Mfa.verify(OPERATOR, USER_ID, codeFor(operator)), "operator のコードが通りません");
		assertTrue(Mfa.verify(MEMBER, USER_ID, codeFor(member)), "member のコードが通りません");

	}

	@Test
	@DisplayName("D-182 片方の種別のコードで、もう片方には入れない")
	void codeOfOneRealmDoesNotOpenTheOther () {

		Mfa.Enrollment operator = enrollAndActivate(OPERATOR);
		enrollAndActivate(MEMBER);

		assertFalse(Mfa.verify(MEMBER, USER_ID, codeFor(operator))
			, "operator の認証アプリで member として入れています");

	}

	@Test
	@DisplayName("D-182 片方を有効にしても、もう片方の（まだ有効にしていない）登録は有効にならない")
	void activatingOneRealmDoesNotActivateTheOther () {

		// operator は登録しただけ（認証アプリに入れる前）
		Mfa.enroll(OPERATOR, USER_ID, "ops@example.com");

		enrollAndActivate(MEMBER);

		/*
		 * <b>有効にする UPDATE が種別を見ていないと、ここが true になる。</b>
		 * operator は一度もコードを合わせていないのに二要素が掛かり、
		 * <b>認証アプリに入れ損ねていれば締め出される</b>。
		 */
		assertFalse(Mfa.isActive(OPERATOR, USER_ID), "member を有効にしたら operator まで有効になりました");

	}

	@Test
	@DisplayName("D-182 やめるのはその種別だけ")
	void disableOnlyTouchesItsRealm () {

		Mfa.Enrollment operator = enrollAndActivate(OPERATOR);
		enrollAndActivate(MEMBER);

		Mfa.disable(MEMBER, USER_ID);

		assertFalse(Mfa.isActive(MEMBER, USER_ID));
		assertTrue(Mfa.isActive(OPERATOR, USER_ID), "別の種別まで消えています");
		assertEquals(10, Mfa.remainingRecoveryCodes(OPERATOR, USER_ID), "別の種別の回復コードまで消えています");
		assertTrue(Mfa.verify(OPERATOR, USER_ID, codeFor(operator)));

	}

	@Test
	@DisplayName("D-182 回復コードも種別ごと")
	void recoveryCodesAreKeptPerRealm () {

		Mfa.Enrollment operator = enrollAndActivate(OPERATOR);
		enrollAndActivate(MEMBER);

		String code = operator.recoveryCodes().getFirst();

		assertFalse(Mfa.verify(MEMBER, USER_ID, code), "operator の回復コードで member に入れています");
		assertTrue(Mfa.verify(OPERATOR, USER_ID, code));
		assertEquals(9, Mfa.remainingRecoveryCodes(OPERATOR, USER_ID));
		assertEquals(10, Mfa.remainingRecoveryCodes(MEMBER, USER_ID), "別の種別の回復コードが減っています");

	}

	@Test
	@DisplayName("D-182 種別を渡さない呼び方は、どの種別とも別の1つの種別として動く")
	void noRealmIsItsOwnRealm () {

		Mfa.Enrollment plain = enrollAndActivate("");

		assertTrue(Mfa.isActive(USER_ID), "種別を渡さない呼び方で有効になっていません");
		assertTrue(Mfa.isActive("", USER_ID), "空文字と「渡さない」が違うものになっています");
		assertFalse(Mfa.isActive(OPERATOR, USER_ID), "種別なしの登録が operator に見えています");

		assertTrue(Mfa.verify(USER_ID, codeFor(plain)));

	}

	// endregion

	// region ログインの途中

	@Test
	@DisplayName("D-182 complete() は pending で渡した種別で確かめる")
	void completeUsesTheRealmGivenAtPending () {

		Mfa.Enrollment operator = enrollAndActivate(OPERATOR);
		Mfa.Enrollment member = enrollAndActivate(MEMBER);

		try (WebContext context = Fakes.context("POST", "/ops/login")) {

			Mfa.pending(context, Principal.of(USER_ID, "運用 太郎", "ops"), OPERATOR);

			/*
			 * <b>コードを受け取る側は種別を選べない。</b>選べると、
			 * operator のパスワードを通したあとで member の秘密鍵で確かめさせる道ができる。
			 */
			assertFalse(Mfa.complete(context, codeFor(member)), "別の種別のコードでログインしています");
			assertFalse(Auth.principal(context).isAuthenticated());

			assertTrue(Mfa.complete(context, codeFor(operator)), "pending の種別で確かめていません");
			assertTrue(Auth.principal(context).isAuthenticated());

			// 途中の印は種別まで消える
			assertEquals("", context.session().get("__mfa_pending_realm"), "途中の種別が残っています");

		}

	}

	@Test
	@DisplayName("D-182 種別を渡さずに始めた途中の人は、これまでどおり種別なしで確かめる")
	void pendingWithoutRealmStillWorks () {

		Mfa.Enrollment plain = enrollAndActivate("");

		try (WebContext context = Fakes.context("POST", "/login")) {

			Mfa.pending(context, Principal.of(USER_ID, "申請 太郎", "member"));

			assertTrue(Mfa.complete(context, codeFor(plain)));
			assertTrue(Auth.principal(context).isAuthenticated());

		}

	}

	@Test
	@DisplayName("D-185 ルートにログインの種別があれば、complete() はその種別にだけログインさせる")
	void completeLogsIntoRouteRealm () {

		Mfa.Enrollment operator = enrollAndActivate(OPERATOR);

		Router router = new Router();
		Router ops = router.path("/ops");
		ops.attribute(Auth.REALM, OPERATOR);
		ops.post("/login/code", context -> { });
		router.seal();

		try (WebContext context = Fakes.context("POST", "/ops/login/code")) {

			context.route(router.match("POST", "/ops/login/code"));

			Mfa.pending(context, Principal.of(USER_ID, "運用 太郎", "ops"), OPERATOR);

			// 途中の状態は種別の置き場所にある（種別なしの置き場所は空）
			assertEquals(USER_ID, context.session().getLong("__mfa_pending_id@" + OPERATOR));
			assertEquals(0, context.session().getLong("__mfa_pending_id"));

			assertTrue(Mfa.complete(context, codeFor(operator)));

			assertEquals(USER_ID, Auth.principal(context, OPERATOR).id());
			assertFalse(Auth.principal(context, "").isAuthenticated(), "種別なしの置き場所にログインしています");
			assertFalse(Mfa.isPending(context));

		}

	}

	// endregion

	// region 総当たり

	@Test
	@DisplayName("D-182 総当たりの数えも種別ごと")
	void lockoutIsCountedPerRealm () {

		enrollAndActivate(OPERATOR);
		Mfa.Enrollment member = enrollAndActivate(MEMBER);

		for (int i = 0; i < 4; i++) {
			assertFalse(Mfa.verify(OPERATOR, USER_ID, "000000"));
		}

		assertThrows(HttpException.class, () -> Mfa.verify(OPERATOR, USER_ID, "000000"), "operator が止まっていません");

		// 同じ ID の member は止まらない
		assertDoesNotThrow(() -> assertTrue(Mfa.verify(MEMBER, USER_ID, codeFor(member)))
			, "operator の打ち間違いで member まで止まっています");

	}

	@Test
	@DisplayName("D-182 種別なしのロックアウトの単位は、これまでと同じ形（mfa:<ID>）")
	void lockoutKeyWithoutRealmIsUnchanged () {

		/*
		 * <b>形を変えると、上げた瞬間にロックアウトの数えが途切れる</b>
		 * （上げる直前まで打ち続けていた相手が、上げたとたんにまた打てる）。
		 */
		enrollAndActivate("");

		for (int i = 0; i < 5; i++) {
			try {
				Mfa.verify(USER_ID, "000000");
			} catch (HttpException expected) {
				// 5回目で止まってもよい
			}
		}

		assertTrue(Lockout.waitSeconds("mfa:" + USER_ID) > 0, "種別なしのロックアウトの単位が変わっています");

	}

	// endregion

	// region 種別の形

	@Test
	@DisplayName("D-182 使えない種別は落とす（null・区切り文字・長すぎ）")
	void invalidRealmIsRejected () {

		assertThrows(IllegalArgumentException.class, () -> Mfa.isActive(null, USER_ID));
		assertThrows(IllegalArgumentException.class, () -> Mfa.isActive("ops:admin", USER_ID));
		assertThrows(IllegalArgumentException.class, () -> Mfa.isActive("ops\nadmin", USER_ID));
		assertThrows(IllegalArgumentException.class, () -> Mfa.isActive("a".repeat(65), USER_ID));

		assertDoesNotThrow(() -> Mfa.isActive("a".repeat(64), USER_ID));
		assertDoesNotThrow(() -> Mfa.isActive("ops_admin-2", USER_ID));

	}

	// endregion

	// region 上げたあとも、これまでの登録で入れる

	@Test
	@DisplayName("D-182 版1の表から上げても、登録済みの人は同じコードで入れる（種別なしになる）")
	void existingEnrollmentSurvivesTheUpgrade () throws Exception {

		DB db = DBUtil.getMainDB();

		// 版1の表を作り直し、上げる前の登録を1人ぶん入れる
		String base32 = Totp.toBase32(Totp.secret());
		String recovery = "ABCDEFGHJK";

		recreateVersion1(db);

		db.execute("INSERT INTO %s (user_id, secret, activated_at, last_counter) VALUES (?, ?, ?, 0)"
			.formatted(db.dialect().identifier(FrameworkTables.AUTH_MFA))
			, USER_ID, Aead.encrypt(base32, SECRET_KEY), Instant.now().getEpochSecond());
		db.execute("INSERT INTO %s (user_id, code_hash, created_at) VALUES (?, ?, ?)"
			.formatted(db.dialect().identifier(FrameworkTables.AUTH_MFA_RECOVERY))
			, USER_ID, Hash.sha256(recovery), Instant.now().getEpochSecond());

		// 上げたあとの起動を再現する：表の版を読み直させ、Mfa に「まだ作っていない」と思わせる
		forgetTableVersions(db);
		DBVersion.load(db);
		resetInstalled();

		// ここで版2が当たる
		assertTrue(Mfa.isActive(USER_ID), "上げる前の登録が、上げたあとに見えていません");
		assertFalse(Mfa.isActive(OPERATOR, USER_ID), "上げる前の登録が operator に見えています");

		Data row = db.select("SELECT realm FROM %s WHERE user_id = ?"
			.formatted(db.dialect().identifier(FrameworkTables.AUTH_MFA)), USER_ID);
		assertEquals("", row.getString("realm"), "上げる前の行が種別なしになっていません");

		String code = Totp.at(Totp.fromBase32(base32), Instant.now().getEpochSecond()
			, MfaConf.period(), MfaConf.digits());
		assertTrue(Mfa.verify(USER_ID, code), "上げる前の秘密鍵のコードで入れません");
		assertEquals(1, Mfa.remainingRecoveryCodes(USER_ID), "上げる前の回復コードが消えています");

		// 主キーが張り直されているので、同じ ID の別の種別を入れられる
		assertDoesNotThrow(() -> enrollAndActivate(OPERATOR), "主キーが種別つきになっていません");
		assertTrue(Mfa.isActive(USER_ID), "別の種別を入れたら、種別なしの登録が消えました");

	}

	/**
	 * 表を版1の形で作り直す
	 *
	 * @param db	DB
	 */
	private static void recreateVersion1 (DB db) {

		boolean mysql = !"postgresql".equals(db.dialect().name());

		String mfa = db.dialect().identifier(FrameworkTables.AUTH_MFA);
		String recovery = db.dialect().identifier(FrameworkTables.AUTH_MFA_RECOVERY);

		db.execute("DROP TABLE IF EXISTS " + mfa);
		db.execute("DROP TABLE IF EXISTS " + recovery);

		db.execute("""
			create table %s (
				user_id bigint not null primary key
				, secret varchar(512) not null
				, activated_at bigint not null
				, last_counter bigint not null
			)%s""".formatted(mfa, mysql ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin" : ""));
		db.execute("""
			create table %s (
				user_id bigint not null
				, code_hash varchar(64) not null
				, created_at bigint not null
				, primary key (user_id, code_hash)
			)%s""".formatted(recovery, mysql ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin" : ""));

		// 版は表のコメントで持っている
		db.execute(db.dialect().setTableCommentSql(FrameworkTables.AUTH_MFA, "二要素認証:1"));
		db.execute(db.dialect().setTableCommentSql(FrameworkTables.AUTH_MFA_RECOVERY, "二要素認証の回復コード:1"));

		assertFalse(db.isError(), String.valueOf(db.getError()));

	}

	/**
	 * 表の版の控えを捨てる
	 *
	 * <p>
	 * {@code DBVersion.load} は<b>1度読んだら読み直さない</b>（起動のときに1回だけ呼ばれる前提）。
	 * 起動し直した JVM と同じ状態にするため、控えだけを捨てる。
	 * </p>
	 *
	 * @param db	DB
	 * @throws Exception	例外
	 */
	@SuppressWarnings("unchecked")
	private static void forgetTableVersions (DB db) throws Exception {

		Field field = DBVersion.class.getDeclaredField("dbTableInfoMap");
		field.setAccessible(true);
		((java.util.Map<String, ?>) field.get(null)).remove(db.getDBName());

	}

	/**
	 * 表を作ったかどうかの印を戻す
	 *
	 * @throws Exception	例外
	 */
	private static void resetInstalled () throws Exception {

		Field field = Mfa.class.getDeclaredField("initialized");
		field.setAccessible(true);
		field.setBoolean(null, false);

	}

	// endregion

	// region 補助

	/**
	 * 登録して有効にする（1つ前の窓のコードで。いまのコードをテストで使えるように）
	 *
	 * @param realm	種別
	 * @return	登録したもの
	 */
	private static Mfa.Enrollment enrollAndActivate (String realm) {

		Mfa.Enrollment enrollment = Mfa.enroll(realm, USER_ID, realm + "@example.com");

		assertTrue(Mfa.activate(realm, USER_ID, Totp.at(Totp.fromBase32(enrollment.secret())
			, Instant.now().getEpochSecond() - MfaConf.period(), MfaConf.period(), MfaConf.digits())));

		return enrollment;

	}

	/**
	 * いまのコード
	 *
	 * @param enrollment	登録したもの
	 * @return	コード
	 */
	private static String codeFor (Mfa.Enrollment enrollment) {

		return Totp.at(Totp.fromBase32(enrollment.secret())
			, Instant.now().getEpochSecond(), MfaConf.period(), MfaConf.digits());

	}

	// endregion

}
