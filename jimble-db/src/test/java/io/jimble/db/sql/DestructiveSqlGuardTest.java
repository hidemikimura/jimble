package io.jimble.db.sql;

import io.jimble.db.dialect.Dialects;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 条件が消えたまま DELETE / UPDATE を組まない（D-173。要件 F-D-06）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>これは「間違った SQL」ではない。</b>{@code DELETE FROM t} は文法として正しく、
 * DB も受け取り、例外も出ない。<b>おかしいのは、消えたあとの表だけ</b>である。
 * </p>
 *
 * <p>
 * 条件が消える道は2つあった。
 * </p>
 *
 * <ol>
 *   <li>{@code where(Data)} に<b>表に無い演算子</b>を書く（{@code id|gte} と書いた。正しくは {@code ge}）。
 *       {@code default: break;} で黙って捨てられ、条件が1つも残らない</li>
 *   <li>条件を1つも足さないまま {@code sql()} を呼ぶ（値が null で足さなかった、変数が空だった）</li>
 * </ol>
 *
 * <p>
 * 1つ目はその場で落とす。2つ目は<b>「本当に全行」と言った場合だけ</b>通す。
 * </p>
 */
class DestructiveSqlGuardTest {

	/** where(Data) を組み立てる */
	private static Data where (String key, Object value) {

		Data column = new Data();
		column.put(key, value);

		Data table = new Data();
		table.putData("site", column);

		Data query = new Data();
		query.putData("where", table);

		return query;

	}

	/** 知らない演算子はその場で落ちること */
	@Test
	@DisplayName("where(Data) の知らない演算子は、その場で落ちる")
	void unknownOperatorFails () {

		SqlBuildException ex = assertThrows(SqlBuildException.class
			, () -> SQL.delete(TestSchema.Site.instance()).where(where("id|gte", 3)));

		assertTrue(ex.getMessage().contains("gte"), ex.getMessage());

	}

	/** 知っている演算子は通ること */
	@Test
	@DisplayName("知っている演算子はそのまま通る")
	void knownOperatorPasses () {

		assertDoesNotThrow(() -> SQL.delete(TestSchema.Site.instance()).where(where("id|ge", 3)));

	}

	/**
	 * 条件が1つも無い DELETE が組めないこと
	 *
	 * <p><b>ここが表を丸ごと消す道である。</b></p>
	 */
	@Test
	@DisplayName("条件が1つも無い DELETE は組めない")
	void deleteWithoutWhereFails () {

		SqlBuildException ex = assertThrows(SqlBuildException.class
			, () -> SQL.delete(TestSchema.Site.instance()).sql(Dialects.of("mysql")));

		assertTrue(ex.getMessage().contains("allRows"), ex.getMessage());

	}

	/** 条件が1つも無い UPDATE が組めないこと */
	@Test
	@DisplayName("条件が1つも無い UPDATE は組めない")
	void updateWithoutWhereFails () {

		assertThrows(SqlBuildException.class
			, () -> SQL.update(TestSchema.Site.instance())
				.set(TestSchema.Site.name, "x")
				.sql(Dialects.of("mysql")));

	}

	/** 本当に全行なら、そう言えば通ること */
	@Test
	@DisplayName("allRows() と言えば、全行が対象の DELETE も組める")
	void allRowsIsAllowedWhenSaidSo () {

		String sql = SQL.delete(TestSchema.Site.instance()).allRows().sql(Dialects.of("mysql"));

		assertTrue(sql.startsWith("DELETE FROM "), sql);
		assertTrue(!sql.contains(" WHERE "), sql);

	}

	/** 条件があるときは、これまでどおり組めること */
	@Test
	@DisplayName("条件があれば、これまでどおり組める")
	void withWhereStillWorks () {

		String sql = SQL.delete(TestSchema.Site.instance())
			.where(TestSchema.Site.id.eq(3))
			.sql(Dialects.of("mysql"));

		assertTrue(sql.contains(" WHERE "), sql);

	}

}
