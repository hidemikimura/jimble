package io.jimble.db.sql;

import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sql.query.select.SelectQuery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 式を左辺にした条件（要件 F-D-09 / D-135）
 *
 * <p>
 * <b>集計を比べられなかった。</b>{@code HAVING SUM(...) >= ?} を組む道が無く、
 * 無理に組むと <b>{@code HAVING ( >= ?)} という壊れた SQL が
 * 例外も警告も無しに組み上がっていた</b>。
 * </p>
 */
class AggregateConditionTest {

	@Test
	@DisplayName("D-135 HAVING に集計を書ける（左辺が消えない）")
	void havingWithAggregate () {

		SelectBuilder builder = SQL.select(
				TestSchema.Site.group_id
				, Dsl.sum(TestSchema.Site.feed_count).as("total"))
			.from(TestSchema.Site.instance())
			.groupBy(TestSchema.Site.group_id)
			.having(Dsl.sum(TestSchema.Site.feed_count).ge(100L));

		String sql = builder.sql();

		/*
		 * <b>ここが「HAVING ( >= ?)」になっていたら元に戻っている。</b>
		 * 左辺が消えても<b>例外は出ない</b>ので、SQL の字面で見張るしかない
		 */
		assertTrue(sql.contains("HAVING (SUM("), sql);
		assertTrue(sql.contains(">= ?"), sql);

		assertEquals(List.of(100L), builder.params());

	}

	@Test
	@DisplayName("D-135 CASE の条件にも集計を書ける")
	void caseWithAggregate () {

		SelectBuilder builder = SQL.select(
				TestSchema.Site.group_id
				, new SelectQuery().dsl(Dsl.caseWhen()
					.when(Dsl.sum(TestSchema.Site.feed_count).ge(500L)).then("大")
					.elseCase("小")).as("size"))
			.from(TestSchema.Site.instance())
			.groupBy(TestSchema.Site.group_id);

		String sql = builder.sql();

		assertTrue(sql.contains("CASE WHEN"), sql);
		assertTrue(sql.contains("SUM("), sql);

		// 500（条件）→ 大 → 小 の順で渡ること
		assertEquals(List.of(500L, "大", "小"), builder.params());

	}

	@Test
	@DisplayName("D-135 パラメータの順が SQL の並びと合う")
	void parameterOrder () {

		/*
		 * <b>左辺の式にもパラメータが入ることがある。</b>
		 * 並びが狂うと<b>比べる値と式の値が入れ替わって渡る</b>——
		 * 例外にならず、答えだけ静かに変わる
		 */
		SelectBuilder builder = SQL.select()
			.from(TestSchema.Site.instance())
			.groupBy(TestSchema.Site.group_id)
			.having(Dsl.sum(Dsl.ifThenElse(
				TestSchema.Site.feed_count.gt(0L), 1L, 0L)).ge(3L));

		assertEquals(List.of(0L, 1L, 0L, 3L), builder.params(), builder.sql());

	}

	@Test
	@DisplayName("D-135 比較のひととおりが書ける")
	void allComparisons () {

		assertTrue(having(Dsl.count(TestSchema.Site.id).gt(1L)).contains("> ?"));
		assertTrue(having(Dsl.count(TestSchema.Site.id).lt(1L)).contains("< ?"));
		assertTrue(having(Dsl.count(TestSchema.Site.id).le(1L)).contains("<= ?"));
		assertTrue(having(Dsl.count(TestSchema.Site.id).eq(1L)).contains("= ?"));
		assertTrue(having(Dsl.max(TestSchema.Site.feed_count).between(1L, 9L)).contains("BETWEEN"));
		assertTrue(having(Dsl.max(TestSchema.Site.feed_count).is_not_null()).contains("NOT NULL"));

	}

	@Test
	@DisplayName("列のときの書き方は変わっていない")
	void columnIsUnchanged () {

		/*
		 * <b>Column は ISelect でもある。</b>既定のほうが効いてしまうと、
		 * 列の条件が<b>全部この形に化ける</b>
		 */
		String sql = SQL.select()
			.from(TestSchema.Site.instance())
			.where(TestSchema.Site.id.ge(1L))
			.sql();

		assertTrue(sql.contains("`site`.`id` >= ?"), sql);

	}

	// region ここで固定していないこと

	/*
	 * - <b>実 DB では流していない。</b>ここは組み立てた SQL の字面を見るだけである。
	 *   実際に走ることは examples/approval-list の結合テストが見る
	 * - <b>like / contains は足していない。</b>集計に対しては
	 *   「書けるけれど意味の無い組み合わせ」が増えるだけなので、比較だけにした
	 */

	// endregion

	/**
	 * HAVING つきの SQL を組む
	 *
	 * @param having	条件
	 * @return	SQL
	 */
	private static String having (io.jimble.db.sql.query.where.IWhere having) {

		return SQL.select()
			.from(TestSchema.Site.instance())
			.groupBy(TestSchema.Site.group_id)
			.having(having)
			.sql();

	}

}
