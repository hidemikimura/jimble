package io.jimble.web.router;

import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * before / after / error のテスト
 */
class RouterHookTest {

	/* 実行ログ */
	private final List<String> log = new ArrayList<>();

	/**
	 * ログを記録するハンドラ
	 */
	private Handler record (String name) {

		return context -> log.add(name);

	}

	/**
	 * ログを記録するエラーハンドラ
	 */
	private ErrorHandler recordError (String name) {

		return (context, cause, statusCode) -> log.add(name + ":" + statusCode);

	}

	/**
	 * フックを実行する
	 */
	private void runHooks (RouteMatch match) throws Exception {

		try (WebContext context = Fakes.context("GET", "/")) {
			for (Handler hook : match.beforeHooks()) {
				hook.handle(context);
			}
			log.add("--handler--");
			for (Handler hook : match.afterHooks()) {
				hook.handle(context);
			}
		}

	}

	@Test
	@DisplayName("T-3 before は外側→内側、after は内側→外側")
	void hookOrder () throws Exception {

		Router router = new Router();
		router.before(record("before:root"));
		router.after(record("after:root"));

		Router admin = router.path("/admin");
		admin.before(record("before:/admin"));
		admin.after(record("after:/admin"));

		Router users = admin.path("/users");
		users.before(record("before:/admin/users"));
		users.after(record("after:/admin/users"));
		users.get("/{id}", context -> { });

		runHooks(router.match("GET", "/admin/users/1"));

		assertEquals(
			List.of(
				"before:root"
				, "before:/admin"
				, "before:/admin/users"
				, "--handler--"
				, "after:/admin/users"
				, "after:/admin"
				, "after:root"
			)
			, log);

	}

	@Test
	@DisplayName("T-3 error は内側→外側。ステータスコードが渡る")
	void errorOrder () throws Exception {

		Router router = new Router();
		router.error(recordError("error:root"));

		Router admin = router.path("/admin");
		admin.error(recordError("error:/admin"));
		admin.get("/x", context -> { });

		RouteMatch match = router.match("GET", "/admin/x");

		assertEquals(2, match.errorHooks().size());

		try (WebContext context = Fakes.context("GET", "/admin/x")) {
			for (ErrorHandler hook : match.errorHooks()) {
				hook.handle(context, new IllegalStateException("boom"), 500);
			}
		}

		assertEquals(List.of("error:/admin:500", "error:root:500"), log, "内側が先");

	}

	@Test
	@DisplayName("T-4 【回帰】マッチに失敗した枝の before が混ざらない")
	void backtrackDoesNotLeakHooks () throws Exception {

		Router router = new Router();

		// /admin 配下（認証あり）
		Router admin = router.path("/admin");
		admin.before(record("admin-auth"));
		admin.get("/users", context -> { });

		// /{slug} 配下（認証なし）
		Router slug = router.path("/{slug}");
		slug.before(record("slug-before"));
		slug.get("/detail", context -> { });

		// /admin/detail は /admin 配下では見つからず /{slug}/detail にマッチする
		RouteMatch match = router.match("GET", "/admin/detail");

		assertTrue(match.matched());
		assertEquals("admin", match.variables().get("slug"));

		runHooks(match);

		assertEquals(
			List.of("slug-before", "--handler--")
			, log
			, "失敗した /admin 枝の before が混ざってはいけない");

	}

	@Test
	@DisplayName("T-4 【回帰】マッチに失敗した枝の after / error も混ざらない")
	void backtrackDoesNotLeakAfterAndError () {

		Router router = new Router();

		Router admin = router.path("/admin");
		admin.after(record("admin-after"));
		admin.error(recordError("admin-error"));
		admin.get("/users", context -> { });

		Router slug = router.path("/{slug}");
		slug.get("/detail", context -> { });

		RouteMatch match = router.match("GET", "/admin/detail");

		assertEquals(0, match.afterHooks().size(), "失敗した枝の after が混ざってはいけない");
		assertEquals(0, match.errorHooks().size(), "失敗した枝の error が混ざってはいけない");

	}

	@Test
	@DisplayName("T-4 【回帰】失敗した枝のパス変数が混ざらない")
	void backtrackDoesNotLeakVariables () {

		Router router = new Router();

		// /{a}/x にマッチさせたいが、先に /{b}/y を試して失敗する形にする
		router.get("/{b}/y", context -> { });
		router.get("/{a}/x", context -> { });

		RouteMatch match = router.match("GET", "/v/x");

		assertEquals("v", match.variables().get("a"));
		assertEquals(1, match.variables().values().size(), "失敗した枝の変数 b が残ってはいけない: " + match.variables());

	}

	@Test
	@DisplayName("F-R-09b 未マッチでもトップレベルの error は適用される")
	void unmatchedKeepsRootErrorHooks () {

		Router router = new Router();
		router.error(recordError("error:root"));

		Router admin = router.path("/admin");
		admin.error(recordError("error:/admin"));
		admin.get("/x", context -> { });

		RouteMatch match = router.match("GET", "/nope");

		assertTrue(!match.matched());
		assertEquals(1, match.errorHooks().size(), "トップレベルの error だけが残ること");

	}

}
