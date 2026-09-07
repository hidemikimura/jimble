package io.jimble.web.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ルート属性のテスト
 */
class RouteAttributeTest {

	/* 認証スキップ */
	private static final AttributeKey<Boolean> NO_AUTH = new AttributeKey<>("no_auth", false);

	/* 上限件数 */
	private static final AttributeKey<Integer> MAX_ITEMS = new AttributeKey<>("max_items", 100);

	@Test
	@DisplayName("T-11 未設定の属性はキーの既定値を返す")
	void defaultValue () {

		Router router = new Router();
		Route route = router.get("/x", context -> { });

		assertFalse(route.attribute(NO_AUTH), "既定値 false が返ること");
		assertEquals(100, route.attribute(MAX_ITEMS));

	}

	@Test
	@DisplayName("T-11 設定した属性が取れる")
	void setAndGet () {

		Router router = new Router();
		Route route = router.post("/session", context -> { })
			.attribute(NO_AUTH, true)
			.attribute(MAX_ITEMS, 10);

		assertTrue(route.attribute(NO_AUTH));
		assertEquals(10, route.attribute(MAX_ITEMS));

	}

	@Test
	@DisplayName("T-11 マッチ結果から属性が読める")
	void readFromMatch () {

		Router router = new Router();
		router.post("/session", context -> { }).attribute(NO_AUTH, true);
		router.get("/users", context -> { });

		assertTrue(router.match("POST", "/session").route().attribute(NO_AUTH));
		assertFalse(router.match("GET", "/users").route().attribute(NO_AUTH), "未設定は既定値");

	}

	@Test
	@DisplayName("タグを付けられる")
	void tags () {

		Router router = new Router();
		Route route = router.get("/x", context -> { }).tag("admin", "internal");

		assertEquals(List.of("admin", "internal"), route.tags());

	}

}
