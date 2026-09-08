package io.jimble.db.sqlcache;

import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * キャッシュのキーの確認（要件 F-D-28）
 *
 * <p>
 * <b>値が違えば別のキーになる</b>ことを固定する。
 * ここが緩いと、2回目の SELECT が<b>1回目の別のクエリの結果</b>を返す。
 * </p>
 */
class SqlCacheKeyTest {

	/** SQL */
	private static final String SQL = "SELECT * FROM t WHERE a = ?";

	@Test
	@DisplayName("値が違えば別のキー")
	void differentValues () {

		assertNotEquals(SqlCache.key(SQL, List.of(1)), SqlCache.key(SQL, List.of(2)));

	}

	@Test
	@DisplayName("同じ値なら同じキー")
	void sameValues () {

		assertEquals(SqlCache.key(SQL, List.of(1)), SqlCache.key(SQL, List.of(1)));

	}

	/**
	 * <p>
	 * {@code Data#toString()} は<b>キー名と型だけ</b>を出す要約なので、
	 * そのまま混ぜると中身の違う Data が同じキーになる。
	 * </p>
	 */
	@Test
	@DisplayName("Data は中身で区別する（要約でまとめない）")
	void dataValues () {

		Data one = new Data().putData("user_id", 1);
		Data two = new Data().putData("user_id", 2);

		assertNotEquals(SqlCache.key(SQL, List.of(one)), SqlCache.key(SQL, List.of(two)));

		assertEquals(
			SqlCache.key(SQL, List.of(new Data().putData("user_id", 1)))
			, SqlCache.key(SQL, List.of(new Data().putData("user_id", 1))));

	}

	/**
	 * <p>{@code byte[]} の {@code toString()} は identity hash になる。</p>
	 */
	@Test
	@DisplayName("byte[] は中身で区別する（毎回別のキーにしない）")
	void byteArrayValues () {

		assertEquals(
			SqlCache.key(SQL, List.of(new byte[] { 1, 2, 3 }))
			, SqlCache.key(SQL, List.of(new byte[] { 1, 2, 3 })));

		assertNotEquals(
			SqlCache.key(SQL, List.of(new byte[] { 1, 2, 3 }))
			, SqlCache.key(SQL, List.of(new byte[] { 1, 2, 4 })));

	}

	@Test
	@DisplayName("SQL が違えば別のキー")
	void differentSql () {

		assertNotEquals(
			SqlCache.key("SELECT * FROM a WHERE x = ?", List.of(1))
			, SqlCache.key("SELECT * FROM b WHERE x = ?", List.of(1)));

	}

	@Test
	@DisplayName("null を含んでいても作れる")
	void nullValues () {

		assertNotEquals(
			SqlCache.key(SQL, Arrays.asList((Object) null))
			, SqlCache.key(SQL, List.of("")));

	}

}
