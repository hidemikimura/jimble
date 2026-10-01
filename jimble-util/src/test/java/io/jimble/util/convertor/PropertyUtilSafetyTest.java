package io.jimble.util.convertor;

import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean に移すとき、書いてはいけない項目に書かない（D-226）
 *
 * <p>
 * 名前は外から来る（リクエストのキー）ので、static / transient の項目には書かないことを確かめる。
 * 知らないキーは、かつては上限なしに覚え続けた（キーを変えて送るたびにメモリが増えた）。
 * </p>
 */
class PropertyUtilSafetyTest {

	/** 移す先 */
	public static class Form {

		public static String global = "original";

		public static final String CONSTANT = "constant";

		private String name;

		public transient String cache = "kept";

		public String name () {
			return name;
		}

	}

	@Test
	@DisplayName("static / transient の項目には書かない。ふつうの項目（private を含む）には書く")
	void skipsStaticAndTransient () throws Exception {

		Data request = new Data();
		request.put("global", "pwned");
		request.put("cache", "pwned");
		request.put("name", "きむら");

		Form form = request.convertClass(Form.class);

		assertEquals("original", Form.global, "static の項目を書き換えています");
		assertEquals("kept", form.cache, "transient の項目を書き換えています");
		assertEquals("きむら", form.name());

	}

	@Test
	@DisplayName("知らないキーを覚える数に上限がある")
	void boundedUnknownCache () throws Exception {

		Data request = new Data();
		for (int i = 0; i < PropertyUtil.MAX_NO_FIELD_CACHE * 3; i++) {
			request.put("unknown_" + i, i);
		}

		request.convertClass(Form.class);

		java.lang.reflect.Field field = PropertyUtil.class.getDeclaredField("NO_FIELD_CACHE");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<Class<?>, Set<String>> cache = (Map<Class<?>, Set<String>>) field.get(null);

		assertTrue(cache.getOrDefault(Form.class, Set.of()).size() <= PropertyUtil.MAX_NO_FIELD_CACHE
			, "知らないキーを上限なしに覚えています: " + cache.get(Form.class).size());

	}

}
