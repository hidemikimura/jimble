package io.jimble.util.data;

import io.jimble.util.json.Dson;
import io.jimble.util.json.JsonParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.0 の Data の読み方（要件 D-195）
 *
 * <ul>
 *   <li>無い（キーが無い・null・空文字）は 0 / false / null のまま（design-2.0.md 9.3：「無ければ 0」を残す）</li>
 *   <li>あるのに読めない値は DataConversionException（1.x は黙って 0 / false / null）</li>
 *   <li>型を変えて読んだものは書き戻さない。Optional 版は「無ければ作って入れる」を名前どおり残す</li>
 * </ul>
 */
class Data2StrictReadTest {

	enum Status { draft, published }

	private static Data data () {

		Data d = new Data();
		d.put("n", 7);
		d.put("s", " 12 ");
		d.put("abc", "abc");
		d.put("dec", "1.5");
		d.put("big", 3_000_000_000L);
		d.put("empty", "");
		d.put("nul", null);
		d.put("yes", "yes");
		d.put("t", "true");
		d.put("zero", 0);
		return d;

	}

	@Test
	@DisplayName("D-195 無いキーは 0 / false / null のまま（9.3 の決定）")
	void missingStaysZero () {

		Data d = data();
		for (String key : new String[] {"missing", "nul", "empty"}) {
			assertEquals(0, d.getInt(key), key);
			assertEquals(0L, d.getLong(key), key);
			assertEquals(0, d.getShort(key), key);
			assertEquals(0, d.getByte(key), key);
			assertEquals(0.0, d.getDouble(key), key);
			assertEquals(0.0f, d.getFloat(key), key);
			assertFalse(d.getBoolean(key), key);
			assertEquals(BigDecimal.ZERO, d.getBigDecimal(key), key);
			assertNull(d.getIntObject(key), key);
			assertNull(d.getLongObject(key), key);
			assertNull(d.getBooleanObject(key), key);
			assertNull(d.getDate(key), key);
			assertNull(d.getEnum(key, Status.class), key);
		}

	}

	@Test
	@DisplayName("D-195 読める値はそのまま（前後の空白・本当の 0）")
	void readable () {

		Data d = data();
		assertEquals(7, d.getInt("n"));
		assertEquals(12, d.getInt("s"));
		assertEquals(12L, d.getLongObject("s"));
		assertEquals(0, d.getIntObject("zero"), "本当の 0 は null ではない");
		assertTrue(d.getBoolean("t"));
		assertEquals(1.5, d.getDouble("dec"));
		assertEquals(new BigDecimal("1.5"), d.getBigDecimal("dec"));
		assertEquals(3_000_000_000L, d.getLong("big"));

	}

	@Test
	@DisplayName("D-195 あるのに読めない値は例外（1.x は黙って 0 / false / null）")
	void unreadableThrows () {

		Data d = data();
		assertThrows(DataConversionException.class, () -> d.getInt("abc"));
		assertThrows(DataConversionException.class, () -> d.getIntObject("abc"));
		assertThrows(DataConversionException.class, () -> d.getInt("dec"), "小数を丸めている");
		assertThrows(DataConversionException.class, () -> d.getInt("big"), "桁あふれを丸めている");
		assertThrows(DataConversionException.class, () -> d.getShort("big"));
		assertThrows(DataConversionException.class, () -> d.getByte("big"));
		assertThrows(DataConversionException.class, () -> d.getLong("abc"));
		assertThrows(DataConversionException.class, () -> d.getDouble("abc"));
		assertThrows(DataConversionException.class, () -> d.getFloat("abc"));
		assertThrows(DataConversionException.class, () -> d.getBigDecimal("abc"));
		assertThrows(DataConversionException.class, () -> d.getBoolean("yes"), "yes を false にしている");
		assertThrows(DataConversionException.class, () -> d.getBooleanObject("n"), "7 を false にしている");
		assertThrows(DataConversionException.class, () -> d.getDate("abc"));

	}

	@Test
	@DisplayName("D-195 getEnum は一致しなければ例外、getEnumOptional は無ければ空")
	void enums () {

		Data d = new Data();
		d.put("ok", "published");
		d.put("typo", "Published");
		d.put("value", Status.draft);

		assertEquals(Status.published, d.getEnum("ok", Status.class));
		assertEquals(Status.draft, d.getEnum("value", Status.class));
		DataConversionException e = assertThrows(DataConversionException.class, () -> d.getEnum("typo", Status.class));
		assertEquals("typo", e.key());

		assertEquals(Optional.of(Status.published), d.getEnumOptional("ok", Status.class));
		assertEquals(Optional.empty(), d.getEnumOptional("missing", Status.class));
		assertThrows(DataConversionException.class, () -> d.getEnumOptional("typo", Status.class));

	}

	@Test
	@DisplayName("D-195 型を変えて読んだものは書き戻さない（読んだだけで JSON が変わらない）")
	void conversionsAreNotWrittenBack () {

		Data d = new Data();
		List<Integer> numbers = new ArrayList<>(List.of(1, 2));
		d.put("numbers", numbers);
		d.put("json", "{\"a\":1}");
		String before = d.getJsonString();

		assertEquals(List.of("1", "2"), d.getStringList("numbers"));
		assertEquals(List.of("1", "2"), d.getStringListOptional("numbers"));
		d.getObjectList("numbers", Long.class);
		d.getData("json");

		assertSame(numbers, d.get("numbers"), "書き戻されている");
		assertEquals("{\"a\":1}", d.get("json"), "文字がオブジェクトに書き換わっている");
		assertEquals(before, d.getJsonString());

	}

	@Test
	@DisplayName("D-195 Map は Data に置き換える（中身は同じ）。返したものへの書き込みが元に届く")
	void mapBecomesData () {

		Data d = new Data();
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("a", 1);
		d.put("m", map);
		d.put("list", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("b", 2)))));

		d.getData("m").put("x", 9);
		assertEquals(9, d.getData("m").getInt("x"));
		d.getDataList("list").getFirst().put("y", 8);
		assertEquals(8, d.getDataList("list").getFirst().getInt("y"));

	}

	@Test
	@DisplayName("D-195 Optional 版は名前どおり「無ければ作って入れる」（取り出して足した値が残る）")
	void optionalCreatesWhenMissing () {

		Data d = new Data();
		d.getDataOptional("settings").put("a", 1);
		d.getStringListOptional("tags").add("x");
		d.getDataListOptional("rows").add(new Data());

		assertEquals(1, d.getData("settings").getInt("a"));
		assertEquals(List.of("x"), d.getStringList("tags"));
		assertEquals(1, d.getDataList("rows").size());

	}

	@Test
	@DisplayName("D-195 読めない値を Data として読むと例外（1.x の getDataOptional は空の Data で元の値を上書きしていた）")
	void unreadableDataThrowsAndDoesNotOverwrite () {

		Data d = new Data();
		d.put("x", "abc");

		assertThrows(DataConversionException.class, () -> d.getData("x"));
		assertThrows(DataConversionException.class, () -> d.getDataOptional("x"));
		assertEquals("abc", d.get("x"), "元の値が上書きされている");

		d.put("n", 5);
		assertThrows(DataConversionException.class, () -> d.getDataList("n"));

	}

	@Test
	@DisplayName("D-195 getValue は読めない値で例外（無ければ null）")
	void getValue () {

		Data d = new Data();
		d.put("n", "12");
		d.put("abc", "abc");
		Integer n = d.getValue("n");
		assertEquals(12, n);
		Integer missing = d.getValue("missing");
		assertNull(missing);
		assertThrows(DataConversionException.class, () -> { Integer x = d.getValue("abc"); });

	}

	@Test
	@DisplayName("D-195 fromJsonString / Dson.decodes は壊れた JSON で JsonParseException（1.x は黙って null か空）")
	void brokenJsonThrows () {

		assertEquals(1, Data.fromJsonString("{\"a\":1}").getInt("a"));
		assertTrue(Data.fromJsonString("{}").isEmpty());
		assertTrue(Data.fromJsonString("  { }  ").isEmpty());
		assertNull(Data.fromJsonString(null));

		for (String broken : new String[] {"{\"a\":", "{\"a\" 1}", "not json", "{\"a\":1"}) {
			assertThrows(JsonParseException.class, () -> Data.fromJsonString(broken), broken);
		}

		assertThrows(JsonParseException.class, () -> Dson.decodes("{\"a\":", Data.class));

	}

}
