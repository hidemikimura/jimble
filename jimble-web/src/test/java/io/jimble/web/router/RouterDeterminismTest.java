package io.jimble.web.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ルート探索の決定性のテスト
 */
class RouterDeterminismTest {

	/* 何もしないハンドラ */
	private static final Handler NOOP = context -> { };

	@Test
	@DisplayName("T-5 【回帰】同一階層に複数のパスパラメータがあるとき、登録順に試される")
	void variableNodesAreTriedInRegistrationOrder () {

		for (int attempt = 0; attempt < 100; attempt++) {

			Router router = new Router();
			Route first = router.get("/{id}/a", NOOP);
			router.get("/{name}/a", NOOP);

			RouteMatch match = router.match("GET", "/x/a");

			assertSame(first, match.route(), "先に登録した {id} が勝つこと");
			assertEquals("x", match.variables().get("id"));
			assertNull(match.variables().get("name"), "負けた側の変数は入らないこと");

		}

	}

	@Test
	@DisplayName("T-5 登録順を入れ替えると結果も入れ替わる（名前順ではない）")
	void reversedRegistrationOrderChangesWinner () {

		Router router = new Router();
		Route first = router.get("/{name}/a", NOOP);
		router.get("/{id}/a", NOOP);

		RouteMatch match = router.match("GET", "/x/a");

		assertSame(first, match.route());
		assertEquals("x", match.variables().get("name"));

	}

	@Test
	@DisplayName("T-5 100回のマッチで結果が変わらない")
	void repeatedMatchIsStable () {

		Router router = new Router();
		router.get("/{a}/x", NOOP);
		router.get("/{b}/x", NOOP);
		router.get("/{c}/y", NOOP);

		Route expected = router.match("GET", "/v/x").route();

		for (int attempt = 0; attempt < 100; attempt++) {
			assertSame(expected, router.match("GET", "/v/x").route());
		}

	}

}
