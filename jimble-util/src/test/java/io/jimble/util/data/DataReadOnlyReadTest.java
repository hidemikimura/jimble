package io.jimble.util.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 読むだけの getter が、読むだけであること（D-173）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code getStringOptional} は、無かったキーを {@code ""} で書き込んでいた。</b>
 * 返る値は正しいので、その場では誰も困らない。困るのは<b>あとで同じ Data を出すとき</b>である——
 * </p>
 *
 * <ul>
 *   <li>リクエストのボディをログに出すと、<b>読んだキーだけ空文字で増えている</b></li>
 *   <li>レスポンスとして返すと、<b>読んだキーが JSON に生える</b></li>
 *   <li>ハッシュを取って比べると、<b>読んだかどうかで値が変わる</b></li>
 * </ul>
 *
 * <p>
 * <b>「読んだかどうかで中身が変わる」</b>のが、いちばん追いにくい壊れ方である。
 * </p>
 */
class DataReadOnlyReadTest {

	/** 無いキーを読んでも増えないこと */
	@Test
	@DisplayName("getStringOptional は、無いキーを書き込まない")
	void getStringOptionalDoesNotWrite () {

		Data data = new Data();
		data.put("name", "田中");

		assertEquals("", data.getStringOptional("無い"), "空文字が返らない");

		assertFalse(data.containsKey("無い"), "読んだだけでキーが増えている: " + data.keySet());
		assertEquals(1, data.size(), "件数が増えている: " + data);

	}

	/** JSON に増えないこと */
	@Test
	@DisplayName("読んだだけでは JSON が変わらない")
	void jsonIsUnchangedAfterReading () {

		Data data = new Data();
		data.put("id", 1L);

		String before = data.getJsonString();

		data.getStringOptional("title");
		data.getStringOptional("body");

		assertEquals(before, data.getJsonString(), "読んだだけで JSON が変わっている");

	}

	/** 有るキーはそのまま返ること */
	@Test
	@DisplayName("有るキーはそのまま返る")
	void existingKeyIsReturned () {

		Data data = new Data();
		data.put("name", "田中");

		assertEquals("田中", data.getStringOptional("name"));
		assertEquals(1, data.size());

	}

}
