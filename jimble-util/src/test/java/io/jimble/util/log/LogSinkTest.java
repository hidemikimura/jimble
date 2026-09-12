package io.jimble.util.log;

import io.jimble.util.data.Data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ログの出力先に例外が渡るか（D-155）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@link Log.Sink} の第5引数 {@code throwable} を埋める呼び出しが、1つも無かった。</b>
 * {@code Log.error(cause, "…")} と書いても、例外は {@code data} の中の1項目になるだけで、
 * <b>既定の SLF4J はスタックトレースを出さなかった</b>。
 * </p>
 *
 * <p>
 * <b>落ちないので気づけない。</b>ログは出ているし、メッセージも合っている。
 * 足りないのは<b>どこで落ちたか</b>だけで、それが要るのは
 * <b>あとから障害を追うとき</b>——いちばん取り返しがつかないときである。
 * </p>
 */
class LogSinkTest {

	/** 受け取ったもの */
	private record Written(String loggerName, Level level, String message, Data data, Throwable throwable) {}

	/* 受け取ったもの */
	private final List<Written> written = new ArrayList<>();

	@AfterEach
	void restore () {

		Log.resetSink();

	}

	/**
	 * 出力先を差し替えて、渡ってきたものを覚える
	 */
	private void capture () {

		Log.sink((loggerName, level, message, data, throwable) ->
			written.add(new Written(loggerName, level, message, data, throwable)));

	}

	@Test
	@DisplayName("D-155 Log.error(例外, …) が Sink に例外を渡す")
	void errorPassesTheThrowable () {

		capture();

		IllegalStateException cause = new IllegalStateException("保存できませんでした");

		Log.error(cause, "id=3");

		assertEquals(1, written.size(), "出ていません");

		Written one = written.getFirst();

		/*
		 * <b>ここが null だったのが不具合である。</b>
		 * 既定の SLF4J Sink は {@code throwable == null} なら
		 * <b>例外を引数に渡さない</b>ので、スタックトレースが1行も出ない。
		 */
		assertSame(cause, one.throwable(), "Sink に例外が渡っていません（スタックトレースが出ません）");

		assertEquals(Level.ERROR, one.level());
		assertEquals("保存できませんでした", one.message());

		// これまでどおり data にも入っている（構造化ログを読んでいる側を壊さない）
		assertSame(cause, one.data().get("throwable"), "data からも消えています");

	}

	@Test
	@DisplayName("D-155 appError(名前, 例外, …) も渡す")
	void appErrorPassesTheThrowable () {

		capture();

		RuntimeException cause = new RuntimeException("外部 API が落ちました");

		Log.appError("payment", cause, "order_id=9");

		assertEquals(1, written.size());
		assertSame(cause, written.getFirst().throwable());
		assertEquals("payment", written.getFirst().loggerName());

	}

	@Test
	@DisplayName("例外の無いログでは null のまま")
	void withoutThrowable () {

		capture();

		Log.info("ふつうのログ");

		assertEquals(1, written.size());
		assertNotNull(written.getFirst().data());

		/*
		 * <b>いつも埋めるわけではない。</b>
		 * 例外が無いときに何かを入れると、
		 * Sink 側が「例外があった」と読んでしまう。
		 */
		assertEquals(null, written.getFirst().throwable(), "例外が無いのに何か渡しています");

	}

	// region ここで固定していないこと

	/*
	 * - <b>既定の SLF4J Sink が実際にスタックトレースを出すところ</b>は見ていない。
	 *   ここで見ているのは<b>渡っているか</b>までで、その先は SLF4J の仕事である
	 *   （出力の形は LogbackErrorEncoderTest が見ている）
	 * - <b>{@code data} に入る "throwable" の形</b>も見ていない。
	 *   JSON にしたときの見た目は、これまでと変わっていない
	 */

	// endregion

}
