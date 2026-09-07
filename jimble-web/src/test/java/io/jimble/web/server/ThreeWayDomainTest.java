package io.jimble.web.server;

import io.jimble.core.context.BatchContext;
import io.jimble.core.context.Context;
import io.jimble.core.context.MqContext;
import io.jimble.core.context.ScopeCache;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Web / バッチ / MQ の3経路から同じ Domain 層を呼べることのテスト
 *
 * <p>M2 の完了条件。</p>
 */
class ThreeWayDomainTest {

	/* Domain の読み込み回数 */
	private static final AtomicInteger LOAD_COUNT = new AtomicInteger();

	/**
	 * 業務ロジック
	 *
	 * <p>
	 * <b>HTTP を知らない。</b>実行経路（Web / バッチ / MQ）によらず同じコードが動く。
	 * </p>
	 */
	static final class FeedDomain {

		/**
		 * サイト名を取得する
		 *
		 * @param siteId	サイトID
		 * @return	サイト名
		 */
		static String siteName (long siteId) {

			// 実行スコープキャッシュ。1実行単位につき1回しか読み込まない
			return ScopeCache.current().get("site:" + siteId, () -> {
				LOAD_COUNT.incrementAndGet();
				return "site-" + siteId;
			});

		}

		/**
		 * 実行IDつきの処理結果
		 *
		 * @param siteId	サイトID
		 * @return	結果
		 */
		static String describe (long siteId) {

			return "%s@%s".formatted(siteName(siteId), Context.current().executionId());

		}

	}

	@Test
	@DisplayName("M2 同じ Domain 層が Web / バッチ / MQ の3経路から呼べる")
	void sameDomainFromThreeExecutionKinds () {

		LOAD_COUNT.set(0);

		List<String> results = new ArrayList<>();

		// 1. Web
		JimbleApp app = new JimbleApp() {
			{
				get("/feed/{id}", context -> {
					long siteId = Long.parseLong(context.route().variables().get("id"));
					results.add(FeedDomain.describe(siteId));
					context.response().send();
				});
			}
		};

		Dispatcher dispatcher = new Dispatcher(app);
		try (WebContext context = Fakes.context("GET", "/feed/1")) {
			dispatcher.dispatch(context);
		}

		// 2. バッチ
		try (BatchContext context = new BatchContext("RSSフィード取得")) {
			context.run(() -> results.add(FeedDomain.describe(1L)));
		}

		// 3. MQ
		try (MqContext context = new MqContext("mq_get_rss", 99L, 1)) {
			context.run(() -> results.add(FeedDomain.describe(1L)));
		}

		assertEquals(3, results.size());

		for (String result : results) {
			assertTrue(result.startsWith("site-1@"), result);
		}

		assertEquals(3, LOAD_COUNT.get(), "実行単位ごとにキャッシュが分かれること");

		assertEquals(3, results.stream().distinct().count(), "実行IDは実行単位ごとに異なること");

	}

	@Test
	@DisplayName("M2 同一実行単位の中ではキャッシュが効く")
	void cacheIsSharedWithinOneExecution () {

		LOAD_COUNT.set(0);

		try (BatchContext context = new BatchContext("バッチ")) {
			context.run(() -> {
				FeedDomain.siteName(1L);
				FeedDomain.siteName(1L);
				FeedDomain.siteName(2L);
			});
		}

		assertEquals(2, LOAD_COUNT.get(), "同じキーは1回だけ読み込まれること");

	}

	@Test
	@DisplayName("M2 バッチ・MQ から HTTP の API には触れない（コンパイル時に防がれる）")
	void batchCannotTouchHttp () {

		try (BatchContext context = new BatchContext("バッチ")) {
			context.run(() -> {
				Context<?> current = Context.current();

				// WebContext ではないので、request() / response() には型として到達できない
				assertFalse(current instanceof WebContext);

				// 型を指定した取得は明確に失敗する
				try {
					Context.current(WebContext.class);
					throw new AssertionError("例外が投げられるべき");
				} catch (IllegalStateException expected) {
					assertTrue(expected.getMessage().contains("WebContext"), expected.getMessage());
				}
			});
		}

	}

}
