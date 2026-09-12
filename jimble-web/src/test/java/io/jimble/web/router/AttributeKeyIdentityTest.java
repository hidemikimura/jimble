package io.jimble.web.router;

import io.jimble.web.auth.Auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 属性キーはインスタンスそのものである（要件 D-157）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@link AttributeKey} は record だった。</b>record の {@code equals} は
 * <b>名前と既定値の両方</b>を見るので、外れ方が2通りあった。
 * </p>
 *
 * <ol>
 *   <li><b>名前も既定値も同じなら、別に作っても同じキーになった。</b>
 *       アプリが偶然 {@code new AttributeKey<>("auth_public", false)} と書くと、
 *       <b>{@code Auth.PUBLIC} の枠を奪う</b>——
 *       書いた覚えのないルートが<b>公開になる</b></li>
 *   <li><b>名前が同じで既定値が違えば、別のキーになった。</b>
 *       片方に入れて、もう片方から読むと<b>既定値が返る</b></li>
 * </ol>
 *
 * <p>
 * <b>どちらもコンパイルは通り、例外も出ず、属性だけが効かない。</b>
 * しかも <b>1 は「開く側」に倒れる</b>ので、
 * 気づくのは<b>入れてはいけない人が入ったあと</b>である。
 * </p>
 */
class AttributeKeyIdentityTest {

	@Test
	@DisplayName("D-157 名前も既定値も同じでも、別に作れば別のキー")
	void sameNameAndDefaultIsStillAnotherKey () {

		AttributeKey<Boolean> mine = new AttributeKey<>("auth_public", false);

		/*
		 * <b>ここが等しくなっていたのが事故の正体である。</b>
		 * Auth.PUBLIC と同じ名前・同じ既定値で作っただけで、同じキーになっていた。
		 */
		assertNotEquals(Auth.PUBLIC, mine, "枠組みのキーを奪えます（ルートが公開になります）");

		Router router = new Router();
		Route route = router.get("/secret", context -> { }).attribute(mine, true);

		assertTrue(route.attribute(mine), "自分のキーでは読めます");
		assertFalse(route.attribute(Auth.PUBLIC), "自分のキーで Auth.PUBLIC を立てています");

	}

	@Test
	@DisplayName("D-157 名前が同じで既定値が違っても、書いたところから読める")
	void differentDefaultsDoNotSplitTheKey () {

		/*
		 * <b>record のときは、この2つが「別のキー」だった。</b>
		 * 名前は同じなのでログでは見分けが付かず、
		 * <b>入れたのに既定値が返る</b>という形で外れた。
		 * いまはどちらもインスタンスで見分けるので、書いたところから読める。
		 */
		AttributeKey<Integer> one = new AttributeKey<>("max_items", 100);
		AttributeKey<Integer> another = new AttributeKey<>("max_items", 50);

		Router router = new Router();
		Route route = router.get("/x", context -> { }).attribute(one, 10);

		assertEquals(10, route.attribute(one), "入れたところから読めません");
		assertEquals(50, route.attribute(another), "別のキーの既定値が返っていません");

	}

	@Test
	@DisplayName("同じキーは何度使っても同じキー")
	void theSameInstanceIsTheSameKey () {

		AttributeKey<Boolean> key = new AttributeKey<>("no_auth", false);

		assertEquals(key, key);
		assertEquals(key.hashCode(), key.hashCode());

		Router router = new Router();

		router.get("/a", context -> { }).attribute(key, true);
		router.get("/b", context -> { }).attribute(key, true);

		assertTrue(router.match("GET", "/a").route().attribute(key));
		assertTrue(router.match("GET", "/b").route().attribute(key));

	}

	@Test
	@DisplayName("D-157 同じ名前のキーが2つあれば、起動時に落ちる")
	void duplicatedNamesFailAtStartup () {

		AttributeKey<Boolean> one = new AttributeKey<>("tenant", false);
		AttributeKey<Boolean> another = new AttributeKey<>("tenant", true);

		Router router = new Router();

		router.get("/a", context -> { }).attribute(one, true);
		router.get("/b", context -> { }).attribute(another, true);

		/*
		 * <b>振る舞いは正しい。</b>別のキーとして扱われるので、事故は起きない。
		 * それでも落とすのは、<b>ログで見分けが付かない</b>ためである——
		 * "tenant" が2つ出てきたとき、読み手はどちらがどちらか決められない。
		 */
		IllegalStateException thrown = assertThrows(IllegalStateException.class, router::seal
			, "同じ名前のキーが2つあるのに、黙って起動しています");

		assertTrue(thrown.getMessage().contains("tenant"), thrown.getMessage());

	}

	@Test
	@DisplayName("D-157 ブロックに書いたキーも数える")
	void keysOnBlocksAreCounted () {

		AttributeKey<Boolean> one = new AttributeKey<>("scoped", false);
		AttributeKey<Boolean> another = new AttributeKey<>("scoped", false);

		Router router = new Router();

		Router admin = router.path("/admin");
		admin.attribute(one, true);
		admin.get("/users", context -> { });

		router.get("/x", context -> { }).attribute(another, true);

		assertThrows(IllegalStateException.class, router::seal
			, "ブロックに書いたキーを見ていません");

	}

	@Test
	@DisplayName("名前が違えば、いくつあっても起動する")
	void differentNamesAreFine () {

		AttributeKey<Boolean> one = new AttributeKey<>("a", false);
		AttributeKey<Boolean> another = new AttributeKey<>("b", false);

		Router router = new Router();

		router.get("/a", context -> { }).attribute(one, true);
		router.get("/b", context -> { }).attribute(another, true);

		router.seal();

		assertTrue(router.match("GET", "/a").route().attribute(one));

	}

	@Test
	@DisplayName("名前は表示のためだけに持っている")
	void theNameIsForReadingOnly () {

		AttributeKey<Boolean> key = new AttributeKey<>("no_auth", false);

		assertEquals("no_auth", key.name());
		assertEquals(false, key.defaultValue());
		assertTrue(key.toString().contains("no_auth"), key.toString());

	}

	// region ここで固定していないこと

	/*
	 * - <b>型引数のずれ</b>は見ていない。{@code AttributeKey<Boolean>} に
	 *   {@code String} を入れることはコンパイラが止めるが、
	 *   <b>生の型で書けば通ってしまい、読んだ側で ClassCastException になる</b>。
	 *   {@code Class<T>} を持たせる案は、{@code WsRoutes.HANDLER} のような
	 *   総称型のキーが書けなくなるので採らなかった（{@code docs/design-1.0.md} 3.3）
	 * - <b>ブロックからルートへの配り方</b>も見ていない。そこは
	 *   {@code ScopeAttributeTest} が見ている（要件 F-R-26 / D-69）
	 */

	// endregion

}
