package io.jimble.core.trace;

import io.jimble.core.context.Context;

import com.sun.management.ThreadMXBean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * トレースの口（{@link Tracing}／要件 NF-O-05）
 *
 * <p>
 * <b>いちばん大事なのは「登録していないときに何も起きないこと」である。</b>
 * トレースを使わないアプリのほうが多いので、そこが重くなるなら
 * この機能は入れないほうがましである。
 * </p>
 */
class TracingTest {

	@AfterEach
	void off () {

		Tracing.off();

	}

	// region 登録していないとき

	@Test
	@DisplayName("登録していなければ何も起きない")
	void disabled () {

		assertFalse(Tracing.enabled());

		try (Span span = Tracing.start("何か", SpanKind.internal)) {

			// <b>null は返らない。</b>呼ぶ側で分岐しなくてよい、が売りである
			assertSame(Span.NOOP, span);

			span.attribute("a", "b").attribute("c", 1).name("別の名前").error(new RuntimeException());

			assertNull(span.traceId());
			assertNull(span.spanId());
			assertNull(span.traceparent());

		}

		assertNull(Tracing.traceId());
		assertNull(Tracing.spanId());
		assertNull(Tracing.traceparent());
		assertSame(Span.NOOP, Tracing.current());

	}

	@Test
	@DisplayName("登録していなければ、1回あたり 0 byte")
	void costsNothing () {

		/*
		 * <b>ここが 0 でないと、トレースを使わないアプリに費用を押し付けることになる。</b>
		 * 実測は割り当て 0 byte（ベンチマークと同じ測り方。要件 NF-P-06）
		 */
		assertFalse(Tracing.enabled());

		ThreadMXBean threadMx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
		long id = Thread.currentThread().threadId();

		Runnable op = () -> {
			try (Span span = Tracing.start("名前", SpanKind.server)) {
				span.attribute("key", "value");
			}
		};

		for (int i = 0; i < 100_000; i++) {
			op.run();
		}

		long before = threadMx.getThreadAllocatedBytes(id);

		for (int i = 0; i < 100_000; i++) {
			op.run();
		}

		long bytes = (threadMx.getThreadAllocatedBytes(id) - before) / 100_000;

		assertEquals(0, bytes, "1回あたり %d byte 使っている".formatted(bytes));

	}

	// endregion

	// region 登録したとき

	@Test
	@DisplayName("登録すれば出す先に届く")
	void enabled () {

		RecordingTracer tracer = new RecordingTracer();
		Tracing.use(tracer);

		try (Span span = Tracing.start("処理", SpanKind.server)) {
			span.attribute("key", "value");
		}

		assertEquals(1, tracer.spans().size());

		RecordingTracer.Recorded recorded = tracer.spans().get(0);

		assertEquals("処理", recorded.name());
		assertEquals(SpanKind.server, recorded.kind());
		assertEquals("value", recorded.attributes().get("key"));
		assertTrue(recorded.closed(), "閉じられていない");

	}

	@Test
	@DisplayName("入れ子になる")
	void nested () {

		RecordingTracer tracer = new RecordingTracer();
		Tracing.use(tracer);

		try (Span parent = Tracing.start("親", SpanKind.server)) {
			try (Span child = Tracing.start("子", SpanKind.client)) {
				assertEquals(parent.spanId(), tracer.find("子").parent().spanId());
				assertEquals(parent.traceId(), child.traceId());
			}
		}

		assertNull(tracer.find("親").parent());

	}

	@Test
	@DisplayName("出す先が例外を投げても、処理は続く")
	void tracerThrows () {

		/*
		 * <b>トレースの都合でリクエストが 500 になってはいけない。</b>
		 * 送る先が落ちているだけでアプリが止まるのは本末転倒である
		 */
		Tracing.use((name, kind, traceparent, elapsedNanos) -> {
			throw new IllegalStateException("送り先が落ちています");
		});

		try (Span span = Tracing.start("何か", SpanKind.server)) {
			assertSame(Span.NOOP, span);
		}

		assertSame(Span.NOOP, Tracing.current());

	}

	@Test
	@DisplayName("null を返す出す先でも落ちない")
	void tracerReturnsNull () {

		Tracing.use((name, kind, traceparent, elapsedNanos) -> null);

		try (Span span = Tracing.start("何か", SpanKind.server)) {
			assertSame(Span.NOOP, span);
		}

	}

	@Test
	@DisplayName("off で戻る")
	void offAgain () {

		Tracing.use(new RecordingTracer());
		assertTrue(Tracing.enabled());

		Tracing.off();
		assertFalse(Tracing.enabled());

		assertSame(Span.NOOP, Tracing.start("何か", SpanKind.server));

	}

	// endregion

	// region SQL

	@Test
	@DisplayName("SQL は操作と表までを名前にする（全文は付帯情報へ）")
	void sqlSpanName () {

		RecordingTracer tracer = new RecordingTracer();
		Tracing.use(tracer);

		Context.recordSqlExecution(1_000_000L, "SELECT * FROM post WHERE id = ?");
		Context.recordSqlExecution(1_000_000L, "INSERT INTO `comment` (a, b) VALUES (?, ?)");
		Context.recordSqlExecution(1_000_000L, "UPDATE post SET title = ? WHERE id = ?");
		Context.recordSqlExecution(1_000_000L, "DELETE FROM \"post_tag\" WHERE post_id = ?");
		Context.recordSqlExecution(1_000_000L, "  select id from post_view limit 1");

		assertEquals(
			java.util.List.of("SELECT post", "INSERT comment", "UPDATE post", "DELETE post_tag", "select post_view")
			, tracer.spans().stream().map(s -> s.name()).toList());

		assertEquals("SELECT * FROM post WHERE id = ?", tracer.spans().get(0).attributes().get("db.statement"));

	}

	@Test
	@DisplayName("SQL のスパンは、かかった時間ぶんさかのぼって残す")
	void sqlSpanIsPast () {

		RecordingTracer tracer = new RecordingTracer();
		Tracing.use(tracer);

		Context.recordSqlExecution(12_345_678L, "SELECT * FROM post");

		RecordingTracer.Recorded recorded = tracer.spans().get(0);

		assertEquals(12_345_678L, recorded.elapsedNanos());
		assertEquals(SpanKind.client, recorded.kind());

		// <b>「いまのスパン」にはしない。</b>すぐ閉じるものなので、印を置くと外し忘れの元になる
		assertFalse(recorded.wasCurrent());

	}

	@Test
	@DisplayName("表が読み取れなければ操作だけの名前にする")
	void sqlSpanWithoutTable () {

		RecordingTracer tracer = new RecordingTracer();
		Tracing.use(tracer);

		Context.recordSqlExecution(1_000_000L, "COMMIT");
		Context.recordSqlExecution(1_000_000L, "SET autocommit = 0");

		assertEquals("COMMIT", tracer.spans().get(0).name());
		assertEquals("SET", tracer.spans().get(1).name());

	}

	@Test
	@DisplayName("SQL を渡さなければスパンは残さない")
	void sqlWithoutStatement () {

		RecordingTracer tracer = new RecordingTracer();
		Tracing.use(tracer);

		Context.recordSqlExecution(1_000_000L);

		assertTrue(tracer.spans().isEmpty());

	}

	@Test
	@DisplayName("トレースが無効なら SQL のスパンも作らない")
	void sqlWhenDisabled () {

		// 例外にならないことだけを見る（数える側はコンテキストの外なので何もしない）
		Context.recordSqlExecution(1_000_000L, "SELECT * FROM post");

		assertFalse(Tracing.enabled());

	}

	// endregion

	// region いまのスパン

	@Test
	@DisplayName("いまのスパンから ID を引ける")
	void current () {

		RecordingTracer tracer = new RecordingTracer();
		Tracing.use(tracer);

		assertNull(Tracing.traceId());

		try (Span span = Tracing.start("いま", SpanKind.server)) {

			assertNotNull(Tracing.traceId());
			assertEquals(span.traceId(), Tracing.traceId());
			assertEquals(span.spanId(), Tracing.spanId());
			assertEquals(span.traceparent(), Tracing.traceparent());

		}

		assertNull(Tracing.traceId(), "抜けたのに残っている");

	}

	// endregion

}
