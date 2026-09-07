package io.jimble.web.router;

import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller と install のテスト
 */
class ControllerInstallTest {

	/* 何もしないハンドラ */
	private static final Handler NOOP = context -> { };

	// region テスト用コントローラ

	/**
	 * 子コントローラ
	 */
	static final class ChildController extends Controller {

		{
			path("/child", () -> {
				get("/list", NOOP);
				post("/save", NOOP);
			});
		}

	}

	/**
	 * 孫コントローラ
	 */
	static final class GrandChildController extends Controller {

		{
			get("/deep", NOOP);
		}

	}

	/**
	 * ルート定義を1つも書かないコントローラ
	 */
	static final class EmptyController extends Controller {

	}

	/**
	 * 親コントローラ
	 */
	static final class ParentController extends Controller {

		{
			path("/parent", () -> {
				before(NOOP);
				get("/index", NOOP);

				install(ChildController::new);
				install(EmptyController::new);

				path("/nested", () -> install(GrandChildController::new));
			});
		}

	}

	// endregion

	@Test
	@DisplayName("T-6 install で子コントローラのルートが親のパス配下に入る")
	void installMergesRoutes () {

		Router router = new ParentController().router();

		List<String> paths = router.routes().stream().map(RouteInfo::toString).toList();

		assertEquals(
			List.of(
				"GET     /parent/child/list"
				, "POST    /parent/child/save"
				, "GET     /parent/index"
				, "GET     /parent/nested/deep"
			)
			, paths);

	}

	@Test
	@DisplayName("T-6 【回帰】ルート定義を1つも書かないコントローラを install しても壊れない")
	void installEmptyController () {

		Router router = new Router();

		router.merge(new EmptyController().router());

		assertEquals(0, router.routes().size());

	}

	@Test
	@DisplayName("T-6 install した子の before は親のスコープに乗る")
	void installedChildInheritsParentHooks () throws Exception {

		List<String> log = new ArrayList<>();

		class Child extends Controller {
			{
				get("/x", context -> log.add("child-handler"));
			}
		}

		class Parent extends Controller {
			{
				path("/p", () -> {
					before(context -> log.add("parent-before"));
					install(Child::new);
				});
			}
		}

		Router router = new Parent().router();
		RouteMatch match = router.match("GET", "/p/x");

		assertTrue(match.matched());

		try (WebContext context = Fakes.context("GET", "/p/x")) {
			for (Handler hook : match.beforeHooks()) {
				hook.handle(context);
			}
			match.route().handler().handle(context);
		}

		assertEquals(List.of("parent-before", "child-handler"), log);

	}

	@Test
	@DisplayName("T-6 【回帰】複数のコントローラを並行に組み立てても混ざらない")
	void parallelConstructionIsIsolated () throws Exception {

		int count = 64;

		try (ExecutorService pool = Executors.newFixedThreadPool(8)) {

			List<Callable<List<String>>> tasks = new ArrayList<>();
			for (int index = 0; index < count; index++) {
				tasks.add(() -> new ParentController().router().routes().stream()
					.map(RouteInfo::toString).toList());
			}

			List<Future<List<String>>> futures = pool.invokeAll(tasks);

			List<String> expected = new ParentController().router().routes().stream()
				.map(RouteInfo::toString).toList();

			for (Future<List<String>> future : futures) {
				assertEquals(expected, future.get(), "並行に組み立てても同じ結果になること");
			}

		}

	}

	@Test
	@DisplayName("T-6 同じコントローラを2回 install するとルート重複で例外")
	void installTwiceThrows () {

		Router router = new Router();
		router.merge(new ChildController().router());

		assertThrowsIllegalState(() -> router.merge(new ChildController().router()));

	}

	@Test
	@DisplayName("path() のネストはスコープを正しく戻す")
	void pathScopeIsRestored () {

		class Sample extends Controller {
			{
				path("/a", () -> get("/inside", NOOP));
				get("/outside", NOOP);
			}
		}

		Router router = new Sample().router();

		assertTrue(router.match("GET", "/a/inside").matched());
		assertTrue(router.match("GET", "/outside").matched());
		assertFalse(router.match("GET", "/a/outside").matched());

	}

	/**
	 * IllegalStateException を期待する
	 */
	private static void assertThrowsIllegalState (Runnable runnable) {

		try {
			runnable.run();
		} catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError("IllegalStateException が投げられませんでした");

	}

}
