package io.jimble.util.json;

import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自前の JSON（要件 F-D-25 / O-14）
 *
 * <p>外部の JSON ライブラリに依存しない。</p>
 */
class DsonTest {

	// region 書き出し

	@Test
	@DisplayName("Data を JSON にする")
	void encodeData () {

		Data data = new Data()
			.putData("id", 1L)
			.putData("name", "きむら")
			.putData("published", true)
			.putData("score", 1.5);

		assertEquals("{\"id\":1,\"name\":\"きむら\",\"published\":true,\"score\":1.5}", Dson.encodes(data));

	}

	@Test
	@DisplayName("入れ子とリストを書き出せる")
	void encodeNested () {

		Data data = new Data()
			.putData("user", new Data().putData("name", "A"))
			.putData("tags", List.of("x", "y"));

		assertEquals("{\"user\":{\"name\":\"A\"},\"tags\":[\"x\",\"y\"]}", Dson.encodes(data));

	}

	@Test
	@DisplayName("挿入順を保つ（要件 F-D-20）")
	void keepsOrder () {

		Data data = new Data();

		for (String key : List.of("z", "a", "m")) {
			data.putData(key, 1);
		}

		assertEquals("{\"z\":1,\"a\":1,\"m\":1}", Dson.encodes(data));

	}

	@Test
	@DisplayName("エスケープする")
	void escapes () {

		Data data = new Data()
			.putData("quote", "he said \"hi\"")
			.putData("slash", "a/b")
			.putData("newline", "1\n2")
			.putData("tab", "1\t2");

		String json = Dson.encodes(data);

		assertTrue(json.contains("\\\""), json);
		assertTrue(json.contains("\\n"), json);
		assertTrue(json.contains("\\t"), json);

		// 往復して同じになること
		Data back = Data.fromJsonString(json);

		assertEquals("he said \"hi\"", back.getString("quote"));
		assertEquals("a/b", back.getString("slash"));
		assertEquals("1\n2", back.getString("newline"));

	}

	@Test
	@DisplayName("null を書き出せる")
	void encodeNull () {

		Data data = new Data();
		data.put("nothing", null);

		assertEquals("{\"nothing\":null}", Dson.encodes(data));

	}

	// endregion

	// region 読み込み

	@Test
	@DisplayName("JSON を Data にする")
	void decodeData () {

		Data data = Dson.decodes("{\"id\":1,\"name\":\"きむら\",\"ok\":true}", Data.class);

		assertNotNull(data);
		assertEquals(1L, data.getLong("id"));
		assertEquals("きむら", data.getString("name"));
		assertTrue(data.getBoolean("ok"));

	}

	@Test
	@DisplayName("入れ子とリストを読める")
	void decodeNested () {

		Data data = Dson.decodes(
			"{\"user\":{\"name\":\"A\",\"tags\":[\"x\",\"y\"]},\"items\":[{\"id\":1},{\"id\":2}]}"
			, Data.class);

		assertEquals("A", data.getDataOptional("user").getString("name"));
		assertEquals(2, data.getDataOptional("user").getObjectListOptional("tags", Object.class).size());
		assertEquals(2L, data.getDataList("items").get(1).getLong("id"));

	}

	@Test
	@DisplayName("壊れた JSON は黙って通ることがある（落とし穴）")
	void decodeBroken () {

		/*
		 * 移送元からの挙動をそのまま記録しておく。
		 *
		 * <b>途中で切れた JSON が、例外にもならず、部分的な結果になる。</b>
		 * 気づかずに先へ進むので、要件 NF-D-04（落とし穴のページ）に載せる。
		 * Dson 自体を厳しくするのは影響範囲が広いので別途。
		 */

		// JSON ですらないものは null
		assertNull(Dson.decodes("これは JSON ではない", Data.class));
		assertNull(Dson.decodes("", Data.class));
		assertNull(Dson.decodes("null", Data.class));

		// 閉じていないオブジェクトは「空の Data」になる
		Data truncatedObject = Dson.decodes("{", Data.class);
		assertNotNull(truncatedObject);
		assertTrue(truncatedObject.isEmpty());

		// 値が無くても通る
		assertNotNull(Dson.decodes("{\"a\":}", Data.class));

		// 閉じていない配列は、そこまでの要素が返る
		Data truncatedArray = Dson.decodes("[1,2", Data.class);
		assertNotNull(truncatedArray);
		assertEquals(2, truncatedArray.size());

	}

	@Test
	@DisplayName("往復して同じになる")
	void roundTrip () {

		Data data = new Data()
			.putData("id", 1L)
			.putData("name", "俺的まとめ＠速報")
			.putData("nested", new Data().putData("deep", List.of(1L, 2L, 3L)));

		Data back = Data.fromJsonString(data.getJsonString());

		assertEquals(data.getJsonString(), back.getJsonString());

	}

	@Test
	@DisplayName("Map と List も書き出せる")
	void encodeCollections () {

		assertEquals("[1,2,3]", Dson.encodes(List.of(1, 2, 3)));
		assertEquals("{\"a\":1}", Dson.encodes(Map.of("a", 1)));

	}

	@Test
	@DisplayName("日付は文字列になる")
	void encodeDate () {

		Data data = new Data().putData("at", new Date(0));

		String json = Dson.encodes(data);

		assertTrue(json.startsWith("{\"at\":\""), json);

	}

	// endregion

}
