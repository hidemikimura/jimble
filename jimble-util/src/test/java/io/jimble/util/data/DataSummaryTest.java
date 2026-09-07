package io.jimble.util.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Data.toString()} の要約表示（要件 F-D-26 / O-3）
 */
class DataSummaryTest {

	@Test
	@DisplayName("toString は値を出さない")
	void toStringHasNoValues () {

		/*
		 * 移送元は toString() が getJsonString() だった。
		 * ログに1行出しただけで中身が全部出る。
		 */
		Data data = new Data()
			.putData("id", 1L)
			.putData("password_hash", "$2a$10$ひみつ")
			.putData("token", "ひみつのトークン");

		String text = data.toString();

		assertFalse(text.contains("ひみつ"), text);
		assertTrue(text.contains("password_hash=String"), text);
		assertTrue(text.contains("token=String"), text);
		assertTrue(text.contains("id=Long"), text);

	}

	@Test
	@DisplayName("件数が分かる")
	void toStringHasSize () {

		assertEquals("Data(0件)", new Data().toString());
		assertTrue(new Data().putData("a", 1).toString().startsWith("Data(1件)"));

	}

	@Test
	@DisplayName("入れ子は型と件数だけ")
	void nested () {

		Data data = new Data()
			.putData("child", new Data().putData("x", 1))
			.putData("items", List.of(1, 2, 3))
			.putData("nothing", null);

		String text = data.toString();

		assertTrue(text.contains("child=Data"), text);
		assertTrue(text.contains("items=ListN(3件)") || text.contains("items=ImmutableCollections"), text);
		assertTrue(text.contains("nothing=null"), text);

	}

	@Test
	@DisplayName("キーが多いときは打ち切る")
	void manyKeys () {

		Data data = new Data();

		for (int i = 0; i < Data.SUMMARY_MAX_KEYS + 10; i++) {
			data.putData("key" + i, i);
		}

		String text = data.toString();

		assertTrue(text.endsWith("...}"), text);
		assertTrue(text.startsWith("Data(%d件)".formatted(Data.SUMMARY_MAX_KEYS + 10)), text);

	}

	@Test
	@DisplayName("JSON が要るときは getJsonString")
	void jsonIsExplicit () {

		Data data = new Data().putData("name", "値");

		assertEquals("{\"name\":\"値\"}", data.getJsonString());

	}

	@Test
	@DisplayName("ログのデータだけは JSON のまま")
	void logDataKeepsJson () {

		/*
		 * テキスト形式のログは引数を toString() で描く。
		 * ここまで要約にすると開発中のログから情報が消える。
		 */
		io.jimble.util.log.LogData logData = new io.jimble.util.log.LogData();
		logData.putData("request_id", "abc");

		assertTrue(logData.toString().contains("\"abc\""), logData.toString());

	}

}
