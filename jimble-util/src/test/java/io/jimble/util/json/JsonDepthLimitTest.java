package io.jimble.util.json;

import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JSON の入れ子の深さに上限がある（D-229）
 *
 * <p>かつては上限が無く、{@code [[[[...]]]]}（400KB ほど）で StackOverflowError になった。</p>
 */
class JsonDepthLimitTest {

	@Test
	@DisplayName("深すぎる入れ子は JsonParseException（StackOverflowError にしない）")
	void tooDeep () {

		String deep = "{\"a\":" + "[".repeat(200_000) + "]".repeat(200_000) + "}";

		assertThrows(JsonParseException.class, () -> Data.fromJsonString(deep));
		assertThrows(JsonParseException.class, () -> Dson.decodes("[".repeat(1000) + "]".repeat(1000), List.class));

	}

	@Test
	@DisplayName("上限までの入れ子は読める")
	void withinLimit () {

		int depth = 500;
		String json = "[".repeat(depth) + "1" + "]".repeat(depth);

		Object value = Dson.decodes(json, List.class);
		for (int i = 0; i < depth - 1; i++) {
			value = ((List<?>) value).getFirst();
		}

		assertEquals(List.of(1L).toString(), value.toString().replace("1.0", "1"));

	}


	@Test
	@DisplayName("上限ぎりぎりの入れ子も、仮想スレッド（リクエストを処理するスレッド）で読める")
	void withinLimitOnVirtualThread () throws Exception {

		String json = "[".repeat(JsonDepthLimitTestHolder.DEPTH) + "1" + "]".repeat(JsonDepthLimitTestHolder.DEPTH);

		Object[] result = {null};
		Thread thread = Thread.ofVirtual().start(() -> {
			try {
				result[0] = Dson.decodes(json, List.class);
			} catch (Throwable ex) {
				result[0] = ex;
			}
		});
		thread.join();

		assertEquals(true, result[0] instanceof List<?>, String.valueOf(result[0]));

	}

	/** 上限ぎりぎりの深さ */
	private static final class JsonDepthLimitTestHolder {
		static final int DEPTH = io.jimble.util.internal.json.decoder.stream.util.IInputStream.MAX_DEPTH;
	}

}
