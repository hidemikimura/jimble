package io.jimble.web.router;

import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

	// region レキシカルスコープ（要件 D-69）

	@Test
	@DisplayName("D-69 同じパスでも、別のスコープで登録したルートには効かない")
	void hooksDoNotLeakToAnotherScopeOnTheSamePath () throws Exception {

		Router router = new Router();

		// 認証つきの /admin
		Router guarded = router.path("/admin");
		guarded.before(record("auth"));
		guarded.get("/users", context -> { });

		// 認証なしの /admin（別のスコープ。ログイン画面など）
		Router open = router.path("/admin");
		open.get("/login", context -> { });

		runHooks(router.match("GET", "/admin/users"));
		assertEquals(List.of("auth", "--handler--"), log);

		log.clear();

		runHooks(router.match("GET", "/admin/login"));
		assertEquals(List.of("--handler--"), log, "別のスコープの before が効いてはいけない");

	}

	@Test
	@DisplayName("D-69 install した子コントローラの before は、親の他のルートに効かない")
	void installedChildHooksDoNotLeakToSiblings () throws Exception {

		class Guarded extends Controller {
			{
				path("/admin", () -> {
					before(record("auth"));
					get("/users", context -> { });
				});
			}
		}

		class Spa extends Controller {
			{
				get("/admin", context -> { });
			}
		}

		class App extends Controller {
			{
				install(Guarded::new);
				install(Spa::new);
			}
		}

		Router router = new App().router();

		runHooks(router.match("GET", "/admin/users"));
		assertEquals(List.of("auth", "--handler--"), log);

		log.clear();

		runHooks(router.match("GET", "/admin"));
		assertEquals(List.of("--handler--"), log, "SPA のほうに認証が効いてはいけない");

	}

	@Test
	@DisplayName("D-69 install した子の error は 404 には効かない（一番外側だけ）")
	void installedChildErrorIsNotUsedForUnmatched () {

		class Child extends Controller {
			{
				error(recordError("error:child"));
				get("/child", context -> { });
			}
		}

		class App extends Controller {
			{
				error(recordError("error:app"));
				install(Child::new);
			}
		}

		Router router = new App().router();

		assertEquals(2, router.match("GET", "/child").errorHooks().size(), "子のルートには両方効く");
		assertEquals(1, router.match("GET", "/nope").errorHooks().size(), "404 は一番外側だけ");

	}

	@Test
	@DisplayName("D-69 スコープの中なら、書いた順は問わない")
	void orderInsideScopeDoesNotMatter () throws Exception {

		Router router = new Router();

		Router admin = router.path("/admin");
		admin.get("/users", context -> { });
		admin.before(record("auth"));		// ルートより後に書いても効く

		runHooks(router.match("GET", "/admin/users"));

		assertEquals(List.of("auth", "--handler--"), log);

	}

	@Test
	@DisplayName("D-69 フックはルートごとに1度だけ組み立てる（毎回作り直さない）")
	void hooksAreResolvedOnce () {

		Router router = new Router();
		router.before(record("root"));
		router.get("/x", context -> { });

		List<Handler> first = router.match("GET", "/x").beforeHooks();
		List<Handler> second = router.match("GET", "/x").beforeHooks();

		assertSame(first, second, "リクエストのたびに組み立て直している");

	}

	@Test
	@DisplayName("D-69 確定した後にフックを足したら落ちる（足したのに効かない、を作らない）")
	void addingHookAfterSealThrows () {

		Router router = new Router();
		router.get("/x", context -> { });

		router.match("GET", "/x");

		IllegalStateException ex = assertThrows(
			IllegalStateException.class, () -> router.before(record("late")));

		assertTrue(ex.getMessage().contains("before"), ex.getMessage());

	}

	@Test
	@DisplayName("D-69 同じコントローラを2か所に install したら落ちる")
	void installingSameInstanceTwiceThrows () {

		class Child extends Controller {
			{
				get("/x", context -> { });
			}
		}

		Child child = new Child();

		Router router = new Router();
		router.merge(child.router());

		assertThrows(IllegalStateException.class, () -> router.path("/other").merge(child.router()));

	}

	// endregion

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
