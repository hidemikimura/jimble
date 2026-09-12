package io.jimble.web.auth.mfa;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Lockout;
import io.jimble.web.auth.Principal;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.session.SessionStores;
import io.jimble.web.support.Fakes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 二要素認証が実 DB に対して動くこと（要件 F-W-32）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 *
 * <pre>
 * ./gradlew :jimble-web:pgTest
 * </pre>
 */
@Tag("db")
class MfaIntegrationTest {

	/** 利用者 */
	private static final long USER_ID = 7;

	/* 元の設定 */
	private static Config originalConf;

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();

		originalConf = Conf.conf().config();

		/*
		 * セッションは none。ここで見たいのは<b>秘密鍵と回復コードの扱い</b>で、
		 * セッションが DB に載るかどうかではない。
		 *
		 * 秘密鍵を暗号化する鍵を入れる——<b>これが無いと enroll は断る</b>（それも下で確かめる）。
		 *
		 * <b>cipher.key ではない</b>（D-154）。あちらを流用すると
		 * hash.password.encrypt の既定が裏返り、<b>保存済みのパスワードが読めなくなる</b>。
		 */
		Conf.replace(ConfigFactory.parseString("""
			session.store = "none"
			auth.mfa.secret_key = "0123456789abcdef0123456789abcdef"
			""").withFallback(originalConf));

		assertTrue(DBUtil.load(Conf.conf().config(), MfaIntegrationTest.class)
			, "DB に接続できませんでした");

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.stop();

		if (originalConf != null) {
			Conf.replace(originalConf);
		}

	}

	@BeforeEach
	void clean () {

		SessionStores.reset();
		Mfa.disable(USER_ID);
		Lockout.clear("mfa:" + USER_ID);

	}

	// region 登録

	@Test
	@DisplayName("F-W-32 登録すると秘密鍵と URI と回復コードが出る")
	void enrolls () {

		Mfa.Enrollment enrollment = Mfa.enroll(USER_ID, "member1@example.com");

		assertEquals(32, enrollment.secret().length(), "20 バイトの base32 は 32 文字");
		assertTrue(enrollment.uri().startsWith("otpauth://totp/"), enrollment.uri());
		assertTrue(enrollment.uri().contains("secret=" + enrollment.secret()), enrollment.uri());
		assertTrue(enrollment.uri().contains("period=30"), enrollment.uri());
		assertEquals(10, enrollment.recoveryCodes().size());

		// この時点ではまだ有効ではない
		assertFalse(Mfa.isActive(USER_ID), "コードを合わせる前に有効になっている");

	}

	@Test
	@DisplayName("F-W-32 秘密鍵は平文で保存しない")
	void storesSecretEncrypted () {

		Mfa.Enrollment enrollment = Mfa.enroll(USER_ID, "member1@example.com");

		Data row = DBUtil.getMainDB().select("SELECT secret FROM %s WHERE user_id = ?"
			.formatted(DBUtil.getMainDB().dialect().identifier("auth_mfa")), USER_ID);

		/*
		 * <b>ここが平文だと、DB が漏れた時点で全員の2要素が無効になる。</b>
		 * パスワードのハッシュと違って<b>破る手間がゼロ</b>である。
		 */
		assertNotEquals(enrollment.secret(), row.getString("secret"), "秘密鍵が平文で入っている");
		assertFalse(row.getString("secret").contains(enrollment.secret()), "秘密鍵が読める形で入っている");

	}

	@Test
	@DisplayName("F-W-32 回復コードも平文で保存しない")
	void storesRecoveryCodesHashed () {

		Mfa.Enrollment enrollment = Mfa.enroll(USER_ID, "member1@example.com");

		String first = enrollment.recoveryCodes().get(0);

		Data row = DBUtil.getMainDB().select("SELECT count(*) as cnt FROM %s WHERE user_id = ? AND code_hash = ?"
			.formatted(DBUtil.getMainDB().dialect().identifier("auth_mfa_recovery")), USER_ID, first);

		assertEquals(0, row.getInt("cnt"), "回復コードが平文で入っている");

	}

	@Test
	@DisplayName("F-W-32 暗号鍵が無ければ有効にさせない")
	void refusesWithoutTheSecretKey () {

		Config withKey = Conf.conf().config();

		try {

			Conf.replace(ConfigFactory.parseString("auth.mfa.secret_key = \"\"")
				.withFallback(originalConf));

			assertThrows(IllegalStateException.class
				, () -> Mfa.enroll(USER_ID, "member1@example.com")
				, "暗号鍵が無いのに秘密鍵を持とうとしている");

		} finally {
			Conf.replace(withKey);
		}

	}

	@Test
	@DisplayName("F-W-32 cipher.key では有効にならない（パスワードの鍵を流用させない）")
	void cipherKeyIsNotTheMfaKey () {

		Config withKey = Conf.conf().config();

		try {

			/*
			 * <b>これは「設定の名前が違う」だけの話ではない。</b>
			 *
			 * {@code hash.password.encrypt} の既定は
			 * <b>「{@code cipher.key} が設定されていれば true」</b>である。
			 * 二要素認証がこの鍵を要求すると、<b>いままで平文の BCrypt を保存していたアプリが
			 * 二要素認証を入れた瞬間に、全員ログインできなくなる</b>——
			 * 返るのは「IDかパスワードが違います」だけなので、原因に辿り着けない。
			 *
			 * <b>サンプル（examples/approval-auth）で実際に起きた</b>：
			 * conf に cipher を足したら、二要素と関係のない結合テストが16本落ちた（D-154）。
			 *
			 * ここが通ってしまうと、<b>「流用してよい」に静かに戻る</b>。
			 */
			Conf.replace(ConfigFactory.parseString("""
				auth.mfa.secret_key = ""
				cipher {
					key = "0123456789abcdef0123456789abcdef"
					iv  = "abcdef9876543210"
				}
				""").withFallback(originalConf));

			assertThrows(IllegalStateException.class
				, () -> Mfa.enroll(USER_ID, "member1@example.com")
				, "cipher.key で二要素が有効になっている（パスワードの鍵と縛り合っている）");

		} finally {
			Conf.replace(withKey);
		}

	}

	@Test
	@DisplayName("F-W-32 暗号鍵が無いときに登録しようとしても、既にある設定を壊さない")
	void failedEnrollKeepsTheExistingSetup () {

		Mfa.Enrollment enrollment = enrollAndActivate();

		Config withKey = Conf.conf().config();

		try {

			Conf.replace(ConfigFactory.parseString("auth.mfa.secret_key = \"\"")
				.withFallback(originalConf));

			assertThrows(IllegalStateException.class, () -> Mfa.enroll(USER_ID, "member1@example.com"));

		} finally {
			Conf.replace(withKey);
		}

		/*
		 * <b>ここが要点である。</b>登録は「古いのを消してから入れ直す」順なので、
		 * <b>暗号鍵が無いことに気づくのが遅いと、消したところで落ちる</b>——
		 * 利用者は<b>2要素が外れた状態</b>で残される（回復コードも一緒に消えている）。
		 * 手を付ける前に断ること。
		 */
		assertTrue(Mfa.isActive(USER_ID), "失敗した登録が、既にある設定を壊している");
		assertEquals(10, Mfa.remainingRecoveryCodes(USER_ID), "回復コードまで消えている");

	}

	@Test
	@DisplayName("F-W-32 コードが合って初めて有効になる")
	void activates () {

		Mfa.Enrollment enrollment = Mfa.enroll(USER_ID, "member1@example.com");

		assertFalse(Mfa.activate(USER_ID, "000000"), "違うコードで有効になっている");
		assertFalse(Mfa.isActive(USER_ID));

		assertTrue(Mfa.activate(USER_ID, codeFor(enrollment)));
		assertTrue(Mfa.isActive(USER_ID));

	}

	@Test
	@DisplayName("F-W-32 登録し直すと、前の回復コードは消える")
	void reEnrollReplacesEverything () {

		Mfa.Enrollment first = Mfa.enroll(USER_ID, "member1@example.com");
		Mfa.activate(USER_ID, codeFor(first));

		Mfa.Enrollment second = Mfa.enroll(USER_ID, "member1@example.com");

		assertNotEquals(first.secret(), second.secret());
		assertEquals(10, Mfa.remainingRecoveryCodes(USER_ID), "前の回復コードが残っている");

	}

	// endregion

	// region 確かめる

	@Test
	@DisplayName("F-W-32 認証アプリのコードで通る")
	void verifies () {

		Mfa.Enrollment enrollment = enrollAndActivate();

		assertTrue(Mfa.verify(USER_ID, codeFor(enrollment)));

	}

	@Test
	@DisplayName("F-W-32 同じコードは二度通らない")
	void rejectsReplay () {

		Mfa.Enrollment enrollment = enrollAndActivate();

		String code = codeFor(enrollment);

		/*
		 * <b>1つのコードは30秒生きている。</b>抑えないと、
		 * 肩越しに見た人がその30秒のあいだ同じ数字で入れる。
		 */
		assertTrue(Mfa.verify(USER_ID, code), "1度目が通らない");
		assertFalse(Mfa.verify(USER_ID, code), "同じコードで二度入れている");

	}

	@Test
	@DisplayName("F-W-32 同じコードで同時に来ても、通るのは1本だけ")
	void concurrentUseOfTheSameCodePassesOnce () {

		Mfa.Enrollment enrollment = enrollAndActivate();

		String code = codeFor(enrollment);

		/*
		 * <b>ここは「読んでから書く」では守れない。</b>
		 * 8本が同時に来ると、どれも「まだ使われていない」を読んでから進めるので、
		 * <b>全部通ってしまう</b>——盗んだ側と本人が同時に打てば、両方入れる。
		 *
		 * 窓を進める UPDATE に「まだ進んでいなければ」を付けて、DB 側で1本に絞っている。
		 */
		java.util.concurrent.atomic.AtomicInteger passed = new java.util.concurrent.atomic.AtomicInteger();

		/*
		 * <b>同時に走らせないと意味が無い。</b>順に走ると、読む側の判定だけで止まってしまい、
		 * <b>DB 側の守りが要ることが見えない</b>。合図で一斉に入る。
		 */
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);

		Thread[] threads = new Thread[16];

		for (int i = 0; i < threads.length; i++) {

			threads[i] = new Thread(() -> {
				try {
					start.await();
					if (Mfa.verify(USER_ID, code)) {
						passed.incrementAndGet();
					}
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				} catch (HttpException ignored) {
					// 待たされたぶんは数えない（総当たり対策が先に効いただけ）
				}
			});

			threads[i].start();

		}

		start.countDown();

		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}

		assertEquals(1, passed.get()
			, "同じコードで %d 本が通った（並列だと使い回しを止められていない）".formatted(passed.get()));

	}

	@Test
	@DisplayName("F-W-32 有効にしていない人は通らない")
	void rejectsWhenNotActive () {

		Mfa.Enrollment enrollment = Mfa.enroll(USER_ID, "member1@example.com");

		assertFalse(Mfa.verify(USER_ID, codeFor(enrollment)), "有効にしていないのに通っている");

	}

	@Test
	@DisplayName("F-W-32 何度も間違えると待たされる")
	void locksOut () {

		enrollAndActivate();

		for (int i = 0; i < 4; i++) {
			assertFalse(Mfa.verify(USER_ID, "000000"));
		}

		/*
		 * <b>6桁は 100 万通りしかない。</b>抑えないと1日あれば当たる。
		 */
		assertThrows(HttpException.class, () -> Mfa.verify(USER_ID, "000000")
			, "総当たりが素通りしている");

	}

	@Test
	@DisplayName("F-W-32 有効化に使ったコードは、そのままでは通らない")
	void activationCodeCannotBeReused () {

		Mfa.Enrollment enrollment = Mfa.enroll(USER_ID, "member1@example.com");

		String code = codeFor(enrollment);

		assertTrue(Mfa.activate(USER_ID, code));

		/*
		 * <b>有効化も「1回使った」に数える。</b>
		 * 数えないと、登録画面を肩越しに見た人が<b>その30秒のあいだ入れる</b>。
		 */
		assertFalse(Mfa.verify(USER_ID, code), "有効化に使ったコードで入れている");

	}

	// endregion

	// region 回復コード

	@Test
	@DisplayName("F-W-32 回復コードで入れて、1回で消える")
	void recoveryCodeIsSingleUse () {

		Mfa.Enrollment enrollment = enrollAndActivate();

		String code = enrollment.recoveryCodes().get(0);

		assertTrue(Mfa.verify(USER_ID, code), "回復コードで入れない");
		assertEquals(9, Mfa.remainingRecoveryCodes(USER_ID));

		assertFalse(Mfa.verify(USER_ID, code), "同じ回復コードで二度入れている");

	}

	@Test
	@DisplayName("F-W-32 回復コードは空白や小文字でも読む")
	void recoveryCodeIsForgiving () {

		Mfa.Enrollment enrollment = enrollAndActivate();

		String code = enrollment.recoveryCodes().get(0);
		String typed = code.substring(0, 5).toLowerCase() + "-" + code.substring(5);

		assertTrue(Mfa.verify(USER_ID, typed), "紙から書き写した形で通らない");

	}

	@Test
	@DisplayName("F-W-32 やめると秘密鍵も回復コードも消える")
	void disableRemovesEverything () {

		enrollAndActivate();

		Mfa.disable(USER_ID);

		assertFalse(Mfa.isActive(USER_ID));
		assertEquals(0, Mfa.remainingRecoveryCodes(USER_ID));

	}

	// endregion

	// region ログインの途中

	@Test
	@DisplayName("F-W-32 コードを入れるまではログインしていない")
	void pendingIsNotLoggedIn () {

		Mfa.Enrollment enrollment = enrollAndActivate();

		try (WebContext context = Fakes.context("POST", "/login")) {

			Mfa.pending(context, Principal.of(USER_ID, "申請 太郎", "member"));

			/*
			 * <b>ここが要点である。</b>パスワードが通っただけの人は、
			 * <b>Auth から見て「いない」</b>——ルートを足した人が何もしなければ入れない。
			 */
			assertFalse(Auth.principal(context).isAuthenticated(), "コードの前にログインできている");
			assertTrue(Mfa.isPending(context));
			assertEquals(USER_ID, Mfa.pendingPrincipal(context).id());

			assertTrue(Mfa.complete(context, codeFor(enrollment)));

			assertTrue(Auth.principal(context).isAuthenticated(), "コードが合ったのに入れていない");
			assertFalse(Mfa.isPending(context), "途中の印が残っている");

		}

	}

	@Test
	@DisplayName("F-W-32 コードが違えばログインしない")
	void wrongCodeDoesNotLogIn () {

		enrollAndActivate();

		try (WebContext context = Fakes.context("POST", "/login")) {

			Mfa.pending(context, Principal.of(USER_ID, "申請 太郎", "member"));

			assertFalse(Mfa.complete(context, "000000"));
			assertFalse(Auth.principal(context).isAuthenticated(), "違うコードで入れている");

		}

	}

	@Test
	@DisplayName("F-W-32 途中の人がいなければ、コードだけでは入れない")
	void codeAloneDoesNotLogIn () {

		enrollAndActivate();

		try (WebContext context = Fakes.context("POST", "/login")) {

			assertThrows(HttpException.class, () -> Mfa.complete(context, "000000")
				, "パスワードを通らずにコードだけで入ろうとしている");

		}

	}

	@Test
	@DisplayName("F-W-32 猶予を過ぎたら、途中の人はいなくなる")
	void pendingExpires () {

		enrollAndActivate();

		try (WebContext context = Fakes.context("POST", "/login")) {

			Mfa.pending(context, Principal.of(USER_ID, "申請 太郎", "member"));

			// 始めた時刻を猶予より前にする（時間を進める代わり）
			context.session().put("__mfa_pending_at", Instant.now().getEpochSecond() - 3600);

			assertFalse(Mfa.isPending(context), "「パスワードだけ通った状態」が残り続けている");

		}

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * <b>次の3つは、外してもテストが落ちない。</b>どれも<b>二重の守り</b>だからである。
	 *
	 * - <b>使い回しを止める守りが2つある</b>——読む側の {@code counter <= last_counter} と、
	 *   窓を進める UPDATE の {@code AND last_counter < ?}。
	 *   <b>どちらを外してもテストは通る</b>（もう片方が弾くため）。
	 *
	 *   <b>16本を合図で一斉に走らせても分けられなかった</b>。
	 *   コネクションプールが順番を作ってしまい、
	 *   2本目が読むころには1本目の UPDATE が終わっているためである。
	 *
	 *   <b>それでも両方残す。</b>正しさの根拠は UPDATE 側にある——
	 *   読んでから書くまでに間が空けば、読む側の判定はすり抜ける。
	 *   いまの構成でたまたま先に止まっているだけで、
	 *   <b>プールを広げたり DB を替えたりすれば順番は変わる</b>。
	 *   読む側を残しているのは<b>ログに「使い回されました」と出る</b>ためと、
	 *   DB へ1往復せずに済むためである。
	 *
	 *   「通るのは1本だけ」という性質そのものは、上のテストが見ている。
	 *
	 * - <b>桁数の判定</b>（{@code trimmed.length() != digits}）：
	 *   桁が違えば、どのみち文字列として一致しない
	 *
	 * - <b>{@code Math.floorDiv}</b>：1970年より前の時刻でしか {@code /} と違わない。
	 *   時計が壊れていなければ通らない道である
	 *
	 * <b>「通らないこと」自体は上のテストで固定してある。</b>
	 * ここで固定できないのは「どちらの守りが効いたか」で、それは見なくてよい。
	 */

	// endregion

	// region 補助

	/**
	 * 登録して有効にする
	 *
	 * @return	登録したもの
	 */
	private static Mfa.Enrollment enrollAndActivate () {

		Mfa.Enrollment enrollment = Mfa.enroll(USER_ID, "member1@example.com");

		/*
		 * <b>1つ前の窓のコードで有効にする。</b>
		 * 有効化に使ったコードは<b>使い回しとして弾かれる</b>ので、
		 * いまのコードで有効にすると、そのあとのテストが「1つも通らない」になる。
		 * 実際にも、コードが切り替わる直前に打った人はこうなる。
		 */
		assertTrue(Mfa.activate(USER_ID, codeAt(enrollment, -MfaConf.period())));

		return enrollment;

	}

	/**
	 * ずらした時刻のコード
	 *
	 * @param enrollment	登録したもの
	 * @param offset		ずらす秒数
	 * @return	コード
	 */
	private static String codeAt (Mfa.Enrollment enrollment, long offset) {

		return Totp.at(Totp.fromBase32(enrollment.secret())
			, Instant.now().getEpochSecond() + offset, MfaConf.period(), MfaConf.digits());

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
