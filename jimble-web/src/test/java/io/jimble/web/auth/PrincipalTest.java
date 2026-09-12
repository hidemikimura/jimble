package io.jimble.web.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Principal} が record をやめても値として振る舞うか（要件 D-157）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>record をやめたので、{@code equals} / {@code hashCode} を自分で書いている。</b>
 * record のときは<b>書き忘れようが無かった</b>ものが、
 * いまは<b>項目を足したときに、ここへ足し忘れられる</b>。
 * </p>
 *
 * <p>
 * <b>足し忘れても誰も落ちない。</b>起きるのは
 * <b>「別人が同じ人として扱われる」</b>で、
 * セッションの比較にも、テストの {@code assertEquals} にも出てこない。
 * </p>
 *
 * <p>
 * <b>record をやめた理由のほうは、テストにできない。</b>
 * 「あとから項目を足せる」は<b>足すときにしか分からない</b>ので、
 * ここでは<b>やめた代金だけ</b>を見張っている。
 * </p>
 */
class PrincipalTest {

	@Test
	@DisplayName("D-157 3つとも同じなら同じ人、1つでも違えば違う人")
	void equalityLooksAtEveryField () {

		Principal base = Principal.of(1, "田中", "admin");

		assertEquals(base, Principal.of(1, "田中", "admin"));
		assertEquals(base.hashCode(), Principal.of(1, "田中", "admin").hashCode());

		/*
		 * <b>1つずつ変える。</b>まとめて変えると、
		 * <b>equals が id しか見ていなくても通ってしまう。</b>
		 */
		assertNotEquals(base, Principal.of(2, "田中", "admin"), "id を見ていません");
		assertNotEquals(base, Principal.of(1, "佐藤", "admin"), "name を見ていません");
		assertNotEquals(base, Principal.of(1, "田中", "member"), "role を見ていません");

	}

	@Test
	@DisplayName("違う型・null とは等しくない")
	void equalityIsSafe () {

		Principal one = Principal.of(1, "田中", "admin");

		assertNotEquals(one, null);
		assertNotEquals(one, "Principal[id=1, name=田中, role=admin]");
		assertEquals(one, one);

	}

	@Test
	@DisplayName("null は空文字になる")
	void nullBecomesEmpty () {

		/*
		 * <b>ここが崩れると {@code hasRole} が落ちる。</b>
		 * {@code this.role.equals(...)} と書いてあるので、
		 * null が入ると NullPointerException になる。
		 */
		Principal one = Principal.of(1, null, null);

		assertEquals("", one.name());
		assertEquals("", one.role());
		assertFalse(one.hasRole("admin"));

	}

	@Test
	@DisplayName("2引数の of は役割なしになる")
	void twoArgumentsMeansNoRole () {

		Principal one = Principal.of(7, "田中");

		assertEquals("", one.role());
		assertTrue(one.isAuthenticated());

	}

	@Test
	@DisplayName("ANONYMOUS はログインしていない")
	void anonymousIsNotAuthenticated () {

		assertFalse(Principal.ANONYMOUS.isAuthenticated());
		assertEquals(0, Principal.ANONYMOUS.id());
		assertEquals("", Principal.ANONYMOUS.role());

		// id が 0 なら、名前が入っていてもログインしていない
		assertFalse(Principal.of(0, "田中", "admin").isAuthenticated());

	}

	@Test
	@DisplayName("役割は完全一致で見る")
	void roleIsAnExactMatch () {

		Principal admin = Principal.of(1, "田中", "admin");

		assertTrue(admin.hasRole("admin"));

		/*
		 * <b>「admin は editor を含む」は無い。</b>
		 * 対応表を持たないのは意図した設計である
		 * （<a href="https://jimble.io/ja/auth">認証</a>）。
		 */
		assertFalse(admin.hasRole("editor"));
		assertFalse(admin.hasRole("ADMIN"));

	}

	@Test
	@DisplayName("D-157 外から new できない")
	void theConstructorIsNotPublic () {

		/*
		 * <b>ここが公開に戻ると、あとから項目を足せなくなる。</b>
		 * 引数の数が増えて、呼び出し側が一斉に壊れる——
		 * <b>record をやめた意味がそこで消える。</b>
		 */
		for (Constructor<?> constructor : Principal.class.getDeclaredConstructors()) {

			assertFalse(Modifier.isPublic(constructor.getModifiers())
				, "コンストラクタが公開されています（of() を使わせてください）");

		}

		assertTrue(Modifier.isFinal(Principal.class.getModifiers())
			, "継承できてしまいます");

	}

	@Test
	@DisplayName("toString に3つとも出る")
	void toStringShowsEverything () {

		String text = Principal.of(3, "田中", "admin").toString();

		assertTrue(text.contains("3"), "id が出ていません: " + text);
		assertTrue(text.contains("田中"), "name が出ていません: " + text);
		assertTrue(text.contains("admin"), "role が出ていません: " + text);

	}

	// region ここで固定していないこと

	/*
	 * - <b>セッションに何が入るか</b>は見ていない。{@code Auth} が
	 *   {@code id} / {@code name} / {@code role} を<b>別々のキー</b>で入れており、
	 *   そこは {@code AuthGuardTest} と {@code RememberIntegrationTest} が見ている
	 * - <b>直列化</b>も見ていない。{@code Principal} を丸ごと直列化している場所は
	 *   リポジトリに1つも無い（record をやめても保存の形が変わらないのはこのため）
	 */

	// endregion

}
