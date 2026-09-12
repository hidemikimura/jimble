package io.jimble.db.sql;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.query.where.IWhere;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WHERE の演算子ひととおり（要件 F-D-06 / D-162）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>演算子を1つずつ壊して、誰が落ちるかを数えたら、半分が生き残った。</b>
 * {@code NOT IN} を {@code IN} にしても、{@code starts_with} を
 * {@code ends_with} にしても、{@code NOT LIKE} を {@code LIKE} にしても、
 * {@code EXISTS} を {@code NOT EXISTS} にしても、{@code BETWEEN} を
 * {@code NOT BETWEEN} にしても——<b>テストは全部通った</b>。
 * </p>
 *
 * <p>
 * <b>どれも「取れる行がちょうど逆になる」壊れ方である。</b>
 * SQL としては正しいので DB も落ちず、<b>件数が変わるだけ</b>——
 * <b>消してはいけない行を消し、見せてはいけない行を見せる</b>。
 * </p>
 *
 * <p>
 * ここは<b>組み立てた SQL の文字列とパラメータ</b>を見る。DB は要らない。
 * </p>
 */
class WhereConditionTest {

	/* site.id */
	private static final Column ID = TestSchema.Site.id;

	/* site.name */
	private static final Column NAME = TestSchema.Site.name;

	/* site.feed_count */
	private static final Column COUNT = TestSchema.Site.feed_count;

	/* site.deleted_at */
	private static final Column DELETED_AT = TestSchema.Site.deleted_at;

	/**
	 * 条件から SQL を組み立てる
	 *
	 * @param where	条件
	 * @return	組み立て済み
	 */
	private static SelectBuilder select (IWhere where) {

		return SQL.select(ID).from(TestSchema.Site.instance()).where(where);

	}

	/**
	 * 条件の SQL
	 *
	 * @param where	条件
	 * @return	SQL
	 */
	private static String sql (IWhere where) {

		return select(where).sql();

	}

	/**
	 * 条件のパラメータ
	 *
	 * @param where	条件
	 * @return	パラメータ
	 */
	private static List<Object> params (IWhere where) {

		return select(where).params();

	}

	// region 比べる

	@Test
	@DisplayName("= と <> は取り違えない")
	void equalityAndInequality () {

		assertTrue(sql(ID.eq(1L)).contains("`id` = ?"), sql(ID.eq(1L)));
		assertEquals(List.of(1L), params(ID.eq(1L)));

		/*
		 * <b>ここを取り違えると、選ぶつもりの行以外が全部返る。</b>
		 */
		assertTrue(sql(ID.not(1L)).contains("`id` <> ?"), sql(ID.not(1L)));
		assertFalse(sql(ID.not(1L)).contains("`id` = ?"), "= になっています");

	}

	@Test
	@DisplayName("> >= < <= は、それぞれ別のものになる")
	void theFourComparisons () {

		assertTrue(sql(COUNT.gt(10L)).contains("`feed_count` > ?"), sql(COUNT.gt(10L)));
		assertTrue(sql(COUNT.ge(10L)).contains("`feed_count` >= ?"), sql(COUNT.ge(10L)));
		assertTrue(sql(COUNT.lt(10L)).contains("`feed_count` < ?"), sql(COUNT.lt(10L)));
		assertTrue(sql(COUNT.le(10L)).contains("`feed_count` <= ?"), sql(COUNT.le(10L)));

		/*
		 * <b>境界の1件だけがずれる壊れ方は、目で見ても分からない。</b>
		 * {@code >} と {@code >=} を取り違えると<b>ちょうど 10 の行だけ</b>が
		 * 出たり出なかったりする。
		 */
		assertFalse(sql(COUNT.gt(10L)).contains(">="), ">= になっています: " + sql(COUNT.gt(10L)));
		assertFalse(sql(COUNT.lt(10L)).contains("<="), "<= になっています: " + sql(COUNT.lt(10L)));

	}

	// endregion

	// region 無いことを見る

	@Test
	@DisplayName("IS NULL と IS NOT NULL は取り違えない")
	void nullChecks () {

		assertTrue(sql(DELETED_AT.is_null()).contains("`deleted_at` IS NULL"), sql(DELETED_AT.is_null()));
		assertFalse(sql(DELETED_AT.is_null()).contains("IS NOT NULL"), "IS NOT NULL になっています");

		assertTrue(sql(DELETED_AT.is_not_null()).contains("`deleted_at` IS NOT NULL")
			, sql(DELETED_AT.is_not_null()));

		/*
		 * <b>取り違えると、消したものだけを見せる画面ができる。</b>
		 * 論理削除はどこにでもあるので、被害が広い。
		 */
		assertTrue(params(DELETED_AT.is_null()).isEmpty(), "値を渡していません");

	}

	// endregion

	// region 並びの中から選ぶ

	@Test
	@DisplayName("D-162 IN と NOT IN は取り違えない")
	void inAndNotIn () {

		/*
		 * <b>{@code NOT IN} を {@code IN} に変えても、テストは全部通っていた。</b>
		 * <b>取れる行がちょうど逆になる</b>のに、SQL としては正しいので誰も落ちない。
		 */
		String in = sql(ID.in(List.of(1L, 2L, 3L)));

		assertTrue(in.contains("`id` IN (?, ?, ?)"), in);
		assertFalse(in.contains("NOT IN"), "NOT IN になっています: " + in);
		assertEquals(List.of(1L, 2L, 3L), params(ID.in(List.of(1L, 2L, 3L))));

		String notIn = sql(ID.not_in(List.of(1L, 2L)));

		assertTrue(notIn.contains("`id` NOT IN (?, ?)"), notIn);
		assertEquals(List.of(1L, 2L), params(ID.not_in(List.of(1L, 2L))));

	}

	@Test
	@DisplayName("D-162 IN には素の配列も渡せる")
	void inTakesPrimitiveArraysToo () {

		/*
		 * <b>以前は {@code long[]} も {@code int[]} も必ず落ちていた。</b>
		 * {@code isArray()} で受けておきながら {@code (Object[])} に
		 * キャストしていたためで、出るのは
		 * <b>{@code class [J cannot be cast to class [Ljava.lang.Object;}</b>——
		 * <b>読んでも何を直せばよいか分からない</b>。
		 */
		assertTrue(sql(ID.in(new long[] { 1L, 2L, 3L })).contains("`id` IN (?, ?, ?)")
			, sql(ID.in(new long[] { 1L, 2L, 3L })));

		assertEquals(List.of(1L, 2L, 3L), params(ID.in(new long[] { 1L, 2L, 3L })));

		assertEquals(List.of(1, 2), params(ID.in(new int[] { 1, 2 })));

		// 箱に入った配列は元から通っていた（そちらも落ちないこと）
		assertEquals(List.of(1L, 2L), params(ID.in(new Long[] { 1L, 2L })));

		assertTrue(sql(ID.not_in(new long[] { 1L })).contains("`id` NOT IN (?)")
			, sql(ID.not_in(new long[] { 1L })));

	}

	@Test
	@DisplayName("空の一覧は、読める言葉で落ちる")
	void anEmptyListFailsWithAMessage () {

		/*
		 * <b>{@code IN ()} は文法エラーになる製品がある</b>し、
		 * <b>{@code IN (NULL)} に潰すとどの行にも当たらない</b>——
		 * <b>0 件が返るだけで例外も出ない</b>ので、取り違えが表に出ない。
		 */
		for (Object empty : new Object[] { List.of(), new long[0], new Long[0], null }) {

			Exception ex = assertThrows(Exception.class, () -> sql(ID.in(empty)));

			assertTrue(ex.getMessage() != null && ex.getMessage().contains("IN")
				, "何が起きたか読めません: " + ex);

		}

	}

	@Test
	@DisplayName("D-162 BETWEEN は NOT BETWEEN にならない")
	void between () {

		String sql = sql(COUNT.between(1L, 10L));

		assertTrue(sql.contains("`feed_count` BETWEEN ? AND ?"), sql);
		assertFalse(sql.contains("NOT BETWEEN"), "NOT BETWEEN になっています: " + sql);

		/*
		 * <b>2つの値の順番も見る。</b>入れ替わると
		 * <b>SQL は通るが1件も返らない</b>（{@code BETWEEN 10 AND 1} は常に空）。
		 */
		assertEquals(List.of(1L, 10L), params(COUNT.between(1L, 10L)), "2つの値が入れ替わっています");

	}

	// endregion

	// region 文字で探す

	@Test
	@DisplayName("D-162 LIKE と NOT LIKE は取り違えない")
	void likeAndNotLike () {

		assertTrue(sql(NAME.like("%まとめ%")).contains("`name` LIKE ?"), sql(NAME.like("%まとめ%")));
		assertFalse(sql(NAME.like("%まとめ%")).contains("NOT LIKE"), "NOT LIKE になっています");

		assertTrue(sql(NAME.not_like("%広告%")).contains("`name` NOT LIKE ?")
			, sql(NAME.not_like("%広告%")));

		// LIKE は値をそのまま渡す（% を付け足さない）
		assertEquals(List.of("%まとめ%"), params(NAME.like("%まとめ%")));

	}

	@Test
	@DisplayName("D-162 contains / starts_with / ends_with は % の付き方で分かれる")
	void theThreeShorthands () {

		/*
		 * <b>3つとも SQL は同じ {@code LIKE ?} である。</b>
		 * 違うのは<b>渡す値の % の位置だけ</b>なので、
		 * <b>取り違えても SQL を見比べただけでは分からない</b>——
		 * {@code starts_with} のつもりが {@code ends_with} になっていても、
		 * <b>検索結果が違うだけ</b>である。
		 */
		assertEquals(List.of("%あ%"), params(NAME.contains("あ")));
		assertEquals(List.of("あ%"), params(NAME.starts_with("あ")));
		assertEquals(List.of("%あ"), params(NAME.ends_with("あ")));

		assertTrue(sql(NAME.contains("あ")).contains("`name` LIKE ?"), sql(NAME.contains("あ")));

	}

	@Test
	@DisplayName("D-162 探す文字の中の % と _ は、文字として探す")
	void wildcardsInTheSearchTermAreEscaped () {

		/*
		 * <b>渡すのは、たいてい利用者が検索欄に打った文字である。</b>
		 * 逃がさないと<b>打った記号が命令として効く</b>——
		 * {@code 50%} を探すと「50 で始まる何か」まで拾い、
		 * {@code a_c} は {@code abc} に当たる。
		 * <b>先頭に % を打たれると索引が効かなくなる</b>ので、
		 * 大きい表では<b>それだけで応答が返らなくなる</b>。
		 */
		assertEquals(List.of("%50!%%"), params(NAME.contains("50%")));
		assertEquals(List.of("%a!_c%"), params(NAME.contains("a_c")));

		// 逃がし文字そのものも逃がす（でないと次の1文字を食べる）
		assertEquals(List.of("%a!!b%"), params(NAME.contains("a!b")));

		assertEquals(List.of("50!%%"), params(NAME.starts_with("50%")));
		assertEquals(List.of("%!%off"), params(NAME.ends_with("%off")));

		/*
		 * <b>逃がし文字は SQL 側にも書く。</b>
		 * 書かないと製品の既定（多くは {@code \\}）で読まれ、
		 * <b>{@code !} がただの文字として残る</b>。
		 */
		assertTrue(sql(NAME.contains("あ")).contains("LIKE ? ESCAPE '!'"), sql(NAME.contains("あ")));

	}

	@Test
	@DisplayName("D-162 生のパターンを書きたいときは like() を使う")
	void likeStillTakesARawPattern () {

		/*
		 * <b>{@code like()} は逃がさない。</b>
		 * 「% を自分で書く」ための口なので、<b>ここで逃がすと使えなくなる</b>。
		 */
		assertEquals(List.of("%まとめ%"), params(NAME.like("%まとめ%")));

		assertFalse(sql(NAME.like("%あ%")).contains("ESCAPE"), sql(NAME.like("%あ%")));

	}

	// endregion

	// region つなぐ

	@Test
	@DisplayName("AND と OR は書いた順に並び、パラメータも同じ順で出る")
	void andOrKeepTheOrder () {

		SelectBuilder builder = select(
			ID.eq(1L)
				.and(NAME.like("%あ%"))
				.and(COUNT.gt(3L)));

		/*
		 * <b>パラメータの順番がずれると、値が別の列に入る。</b>
		 * 型が合っていれば DB も落ちないので、<b>静かに違う行が返る</b>。
		 */
		assertEquals(List.of(1L, "%あ%", 3L), builder.params(), builder.sql());

		String or = sql(ID.eq(1L).or(ID.eq(2L)));

		assertTrue(or.contains("OR"), or);

	}

	@Test
	@DisplayName("D-162 EXISTS は副問い合わせをそのまま抱える")
	void exists () {

		/*
		 * <b>{@code EXISTS} を {@code NOT EXISTS} に変えても、テストは全部通っていた。</b>
		 * 「関連があるものだけ」と「関連が無いものだけ」が入れ替わる形なので、
		 * <b>件数が変わるだけで例外は出ない</b>。
		 */
		SelectBuilder sub = SQL.select(TestSchema.Feed.id)
			.from(TestSchema.Feed.instance())
			.where(TestSchema.Feed.site_id.eq(ID).and(TestSchema.Feed.title.like("%あ%")));

		SelectBuilder builder = select(ID.exists(sub));

		String sql = builder.sql();

		assertTrue(sql.contains("EXISTS ("), sql);
		assertFalse(sql.contains("NOT EXISTS"), "NOT EXISTS になっています: " + sql);
		assertTrue(sql.contains("`feed`"), "副問い合わせが入っていません: " + sql);

		// 副問い合わせの中のパラメータも、外側と同じ並びに出る
		assertEquals(List.of("%あ%"), builder.params(), sql);

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>引用符の付き方</b>（{@code `id`} か {@code "id"} か）は製品で変わる。
	 *   ここは既定の並びで見ており、製品ごとの違いは
	 *   {@code SqlBuilderDialectTest} が見ている
	 */

	// endregion

}
