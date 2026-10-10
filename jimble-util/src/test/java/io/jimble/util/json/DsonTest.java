package io.jimble.util.json;

import io.jimble.util.convertor.Configration;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
	@DisplayName("D-195 壊れた JSON は JsonParseException（1.x は黙って null か、途中までの値）")
	void decodeBroken () {

		assertThrows(JsonParseException.class, () -> Dson.decodes("これは JSON ではない", Data.class));
		assertThrows(JsonParseException.class, () -> Dson.decodes("{", Data.class), "閉じていないオブジェクトが空の Data になっている");
		assertThrows(JsonParseException.class, () -> Dson.decodes("[1,2", Data.class), "閉じていない配列がそこまでの要素になっている");
		assertThrows(JsonParseException.class, () -> Dson.decodes("{\"a\":1} x", Data.class), "後ろの余分なものを捨てている");

		// 空と null は「何も無い」（例外にしない）
		assertNull(Dson.decodes("", Data.class));
		assertNull(Dson.decodes("null", Data.class));

		/*
		 * <b>ここは残る落とし穴</b>：読み手は寛容なので、括弧が閉じていれば
		 * 値の無い "a": のような文法の誤りは通る。形（括弧・余分なもの）だけを見ている。
		 */
		assertNotNull(Dson.decodes("{\"a\":}", Data.class));

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

	// region 読み込み

	@Test
	@DisplayName("ストリームから読める（設定つき）")
	void decodeStreamWithConfigration () {

		/*
		 * <b>ここは「値が合っているか」より前の話を見ている。</b>
		 *
		 * この形は<b>自分自身を呼んでいた</b>ので、呼べば {@code StackOverflowError} だった
		 * （{@code decodes} が {@code new Dson().decodes(...)} と書かれていた）。
		 * 隣の3引数版は {@code decode} を呼んでいて正しかったので、
		 * <b>4引数版だけが、誰も呼ばないまま壊れていた</b>。
		 *
		 * 落ちる場所が「JSON を読むところ」なので、
		 * <b>渡した JSON が悪いのだと思って探すことになる</b>——
		 * それが分かるまでの時間を、このテストが肩代わりする。
		 */
		InputStream stream = new ByteArrayInputStream(
			"{\"id\":7,\"name\":\"きむら\"}".getBytes(StandardCharsets.UTF_8));

		Data data = new Dson().decodes(new Configration(), stream, "UTF-8", Data.class);

		assertNotNull(data, "読めていません");
		assertEquals(7, data.getInt("id"));
		assertEquals("きむら", data.getString("name"));

	}

	@Test
	@DisplayName("ストリームから読める（設定なし）")
	void decodeStream () {

		InputStream stream = new ByteArrayInputStream(
			"{\"id\":8}".getBytes(StandardCharsets.UTF_8));

		Data data = new Dson().decodes(stream, "UTF-8", Data.class);

		assertNotNull(data);
		assertEquals(8, data.getInt("id"));

	}

	// endregion

	// region 数値の型（D-298）

	/**
	 * JSON の数値を1つ読む
	 *
	 * @param number	数値の書き方
	 * @return	読んだ値
	 */
	private static Object number (String number) {

		return Data.fromJsonString("{\"v\":" + number + "}").get("v");

	}

	@Test
	@DisplayName("D-298 整数は int・long の上限と下限の両方を見て、収まる型で返す。収まらなければ BigDecimal")
	void integerRange () {

		assertEquals(Integer.MIN_VALUE, number("-2147483648"));
		assertEquals(Integer.MAX_VALUE, number("2147483647"));
		assertEquals(-2147483649L, number("-2147483649"));
		assertEquals(2147483648L, number("2147483648"));
		assertEquals(-10000000000L, number("-10000000000"));
		assertEquals(Long.MIN_VALUE, number("-9223372036854775808"));
		assertEquals(new java.math.BigDecimal("-9223372036854775809"), number("-9223372036854775809"));
		assertEquals(new java.math.BigDecimal("12345678901234567890"), number("12345678901234567890"));
		assertEquals(1000, number("1e3"));

	}

	@Test
	@DisplayName("D-298 小数は Double で返す（Float では桁が落ちる）。Double に収まらなければ BigDecimal")
	void decimalPrecision () {

		assertEquals(1234567.89d, number("1234567.89"));
		assertEquals(0.30000000000000004d, number("0.30000000000000004"));
		assertEquals(0.1d, Data.fromJsonString("{\"v\":0.1}").getDouble("v"));
		assertEquals(new java.math.BigDecimal("0.1"), Data.fromJsonString("{\"v\":0.1}").getBigDecimal("v"));
		assertEquals(-1.5d, number("-1.5"));

		// 「.」の無い指数の小数（かつては整数として切り捨てて 0 になっていた）
		assertEquals(0.001d, number("1e-3"));

		assertEquals(new java.math.BigDecimal("-1.0e309"), number("-1.0e309"));

	}

	// endregion

}
