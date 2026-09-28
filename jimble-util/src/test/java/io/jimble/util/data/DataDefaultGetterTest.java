package io.jimble.util.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 既定値つきの取り出し：既定値は「無い」ときだけ（要件 D-191）
 */
class DataDefaultGetterTest {

	private static Data data () {

		Data d = new Data();
		d.put("n", 7);
		d.put("s", " 12 ");
		d.put("big", 3_000_000_000L);
		d.put("dec", "1.5");
		d.put("exact", "2.0");
		d.put("abc", "abc");
		d.put("empty", "");
		d.put("blank", "  ");
		d.put("nul", null);
		d.put("t", "TRUE");
		d.put("one", 1);
		d.put("yes", "yes");
		d.put("two", 2);
		d.put("d", "3.25");
		d.put("nan", "NaN");
		return d;

	}

	@Test
	@DisplayName("D-191 無い・null・空文字は既定値")
	void missingIsDefault () {

		Data d = data();
		for (String key : new String[] {"missing", "nul", "empty", "blank"}) {
			assertEquals(-1, d.getInt(key, -1), key);
			assertEquals(-1L, d.getLong(key, -1L), key);
			assertEquals(-1.0, d.getDouble(key, -1.0), key);
			assertTrue(d.getBoolean(key, true), key);
			assertEquals("def", d.getString(key, "def"), key);
		}

	}

	@Test
	@DisplayName("D-191 読める値はそのまま（前後の空白・2.0 のような整数の小数表記も）")
	void presentIsRead () {

		Data d = data();
		assertEquals(7, d.getInt("n", -1));
		assertEquals(12, d.getInt("s", -1));
		assertEquals(2, d.getInt("exact", -1));
		assertEquals(3_000_000_000L, d.getLong("big", -1));
		assertEquals(3.25, d.getDouble("d", -1));
		assertEquals(7.0, d.getDouble("n", -1));
		assertTrue(d.getBoolean("t", false));
		assertTrue(d.getBoolean("one", false));
		assertEquals(" 12 ", d.getString("s", "def"));
		assertEquals("7", d.getString("n", "def"));

	}

	@Test
	@DisplayName("D-191 あるのに読めない値は例外（getInt(key) のように黙って 0 にしない）")
	void unreadableThrows () {

		Data d = data();
		DataConversionException e = assertThrows(DataConversionException.class, () -> d.getInt("abc", 0));
		assertEquals("abc", e.key());
		assertTrue(e.getMessage().contains("abc"), e.getMessage());

		assertThrows(DataConversionException.class, () -> d.getInt("dec", 0), "小数を丸めている");
		assertThrows(DataConversionException.class, () -> d.getInt("big", 0), "桁あふれを丸めている");
		assertThrows(DataConversionException.class, () -> d.getLong("dec", 0));
		assertThrows(DataConversionException.class, () -> d.getDouble("abc", 0));
		assertThrows(DataConversionException.class, () -> d.getDouble("nan", 0));
		assertThrows(DataConversionException.class, () -> d.getBoolean("yes", false));
		assertThrows(DataConversionException.class, () -> d.getBoolean("two", false));

		// 2.0 は既定値なしの取り出しも同じ読み方（要件 D-195）
		assertThrows(DataConversionException.class, () -> d.getInt("abc"));
		assertThrows(DataConversionException.class, () -> d.getBoolean("yes"));

	}

	@Test
	@DisplayName("D-191 既定値つきの取り出しは書き込まない（getStringOptional と違う）")
	void doesNotWrite () {

		Data d = new Data();
		d.getString("x", "def");
		d.getInt("y", 1);
		assertTrue(d.isEmpty(), d.keySet().toString());

	}

}
