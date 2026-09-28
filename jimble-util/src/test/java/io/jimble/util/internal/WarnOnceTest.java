package io.jimble.util.internal;

import io.jimble.util.log.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同じ警告は1度だけ。呼び出し元を添える（要件 D-192）
 */
class WarnOnceTest {

	private final List<String> warns = new ArrayList<>();

	@AfterEach
	void reset () {

		Log.resetSink();
		WarnOnce.reset();

	}

	@Test
	@DisplayName("D-192 鍵ごとに1度だけ出し、呼び出し元（テストのクラスと行）を添える")
	void once () {

		WarnOnce.reset();
		Log.sink((logger, level, message, data, throwable) -> warns.add(message));

		assertTrue(WarnOnce.warn("a", "一つ目"));
		assertFalse(WarnOnce.warn("a", "一つ目"));
		assertTrue(WarnOnce.warn("b", "二つ目"));

		assertEquals(2, warns.size(), warns.toString());
		assertTrue(warns.getFirst().startsWith("一つ目（呼び出し元: io.jimble.util.internal.WarnOnceTest.once:"), warns.getFirst());
		assertTrue(WarnOnce.warned("a"));

		WarnOnce.reset();
		assertFalse(WarnOnce.warned("a"));

	}

}
