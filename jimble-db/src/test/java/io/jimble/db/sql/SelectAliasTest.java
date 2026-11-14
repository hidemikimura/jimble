package io.jimble.db.sql;

import com.typesafe.config.ConfigValueFactory;
import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.internal.sql.select.SelectAccess;
import io.jimble.db.internal.sql.select.SelectAlias;
import io.jimble.db.sql.SqlGoldenCases.GenProduct;
import io.jimble.db.sql.SqlGoldenCases.GenShop;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SELECT 句の列に {@code AS `表__列`} を付けない形（3.0.0 の既定）
 *
 * <p>
 * 別名を付けたときの字面は {@link SqlGoldenTest} が 2.7.4 と比べている。ここでは、ゴールデンの SELECT の形すべてで
 * <b>付けない字面 = 付けた字面から、結果のキーになっている列の {@code AS 別名} だけを取り除いたもの</b>であること、
 * <b>位置ごとの結果のキーが、付けたときの別名と同じ</b>であることを見る。
 * </p>
 */
class SelectAliasTest {

	private static final Dialect[] DIALECTS = { Dialects.of("mysql"), Dialects.of("postgresql") };

	@AfterEach
	void restore () {

		Conf.reload();

	}

	private static void alias (boolean on) {

		Conf.replace(Conf.conf().config().withValue(SelectAlias.KEY, ConfigValueFactory.fromAnyRef(on)));

	}

	/** 組み立てた字面とキー（組み立てで落ちたら、その例外の名前） */
	private record Built (String sql, List<String> labels) {}

	private static Built build (Supplier<?> shape, Dialect dialect) {

		SelectBuilder builder = (SelectBuilder) shape.get();
		try {
			String sql = builder.sql(dialect);
			return new Built(sql, SelectAccess.labels(builder));
		} catch (RuntimeException ex) {
			return new Built("!" + ex.getClass().getSimpleName(), null);
		}

	}

	@Test
	@DisplayName("ゴールデンの SELECT の形すべてで、付けない字面は付けた字面から列の AS 別名を取り除いたもので、キーは同じ（MySQL / PostgreSQL）")
	void sameAsAliasedWithoutColumnAliases () {

		int compared = 0;
		int removed = 0;

		for (Map.Entry<String, Supplier<?>> shape : SqlGoldenCases.selectShapes().entrySet()) {
			for (Dialect dialect : DIALECTS) {

				String where = shape.getKey() + " [" + dialect.name() + "]";

				alias(true);
				Built aliased = build(shape.getValue(), dialect);
				alias(false);
				Built plain = build(shape.getValue(), dialect);

				if (aliased.sql().startsWith("!")) {
					// 組み立てで落ちる形は、付けなくても同じく落ちる
					assertEquals(aliased.sql(), plain.sql(), where);
					continue;
				}

				assertEquals(aliased.labels(), plain.labels(), where + " のキー");

				// 付けた字面から、表__列 の形の AS 別名（IN の中のサブクエリの列も）を、付けない字面に残っていない数だけ取り除く
				String expected = aliased.sql();
				java.util.Set<String> clauses = new java.util.LinkedHashSet<>();
				java.util.regex.Matcher m = ALIAS.matcher(aliased.sql());
				while (m.find()) {
					clauses.add(m.group());
				}
				for (String clause : clauses) {
					if (!clause.contains("__")) {
						continue;
					}
					int extra = count(aliased.sql(), clause) - count(plain.sql(), clause);
					for (int i = 0; i < extra; i++) {
						expected = expected.replaceFirst(java.util.regex.Pattern.quote(clause), "");
						removed++;
					}
				}

				assertEquals(expected, plain.sql(), where);
				compared++;

			}
		}

		assertTrue(compared > 50, "比べた形が少ない: " + compared);
		assertTrue(removed > 100, "取り除いた別名が少ない（付けない形になっていない）: " + removed);

	}

	@Test
	@DisplayName("全列の SELECT は列ごとに `表`.`列` だけを並べ、キーは 表__列（結合の 2 つ目の表も）")
	void allColumns () {

		alias(false);

		SelectBuilder builder = SQL.select().from(GenProduct.instance())
			.inner(GenShop.instance()).on(GenShop.id.eq(GenProduct.shop_id))
			.where(GenProduct.id.eq(1L));

		String sql = builder.sql(DIALECTS[0]);

		assertFalse(sql.contains(" AS "), sql);
		assertTrue(sql.startsWith("SELECT `product`.`id`, `product`.`shop_id`, `product`.`name`, `product`.`price`, `product`.`deleted_at`, `shop`.`id`, `shop`.`code`, `shop`.`we``ird\"name` FROM"), sql);
		assertEquals(List.of("product__id", "product__shop_id", "product__name", "product__price", "product__deleted_at",
			"shop__id", "shop__code", "shop__we`ird\"name"), SelectAccess.labels(builder));

	}

	@Test
	@DisplayName("列を名指ししたときも AS を付けず、別名を付けた式は AS を残し、別名の無い式のキーは null（ドライバの列名）")
	void namedColumnsAndExpressions () {

		alias(false);

		SelectBuilder builder = SQL.select(GenProduct.id, GenProduct.name.as("n"), io.jimble.db.sql.query.dsl.Dsl.count())
			.from(GenProduct.instance());

		String sql = builder.sql(DIALECTS[0]);

		assertEquals("SELECT `product`.`id`, `product`.`name` AS `n`, COUNT(*) FROM `product`", sql);
		assertEquals(Arrays.asList("product__id", "n", null), SelectAccess.labels(builder));

	}

	@Test
	@DisplayName("設定で付ける（sql.select_alias = true）と、2.x と同じ字面")
	void legacySetting () {

		alias(true);

		String sql = SQL.select(GenProduct.id).from(GenProduct.instance()).sql(DIALECTS[0]);

		assertEquals("SELECT `product`.`id` AS `product__id` FROM `product`", sql);

	}

	@Test
	@DisplayName("template も、作ったときの設定の字面と位置ごとのキーを持つ")
	void template () {

		alias(false);

		SelectTemplate template = SQL.template(SQL.select().from(GenProduct.instance())
			.where(GenProduct.id.eq(io.jimble.db.sql.query.dsl.Dsl.param("id"))));
		BoundSelect bound = template.bind("id", 1L);

		assertFalse(bound.sql(DIALECTS[0]).contains(" AS "), bound.sql(DIALECTS[0]));
		assertEquals(List.of("product__id", "product__shop_id", "product__name", "product__price", "product__deleted_at"),
			io.jimble.db.internal.sql.template.TemplateShapes.labels(bound, DIALECTS[0]));

	}

	/* AS のあとの囲んだ別名（MySQL は `、PostgreSQL は "。中の囲み文字は 2 つ重ねる） */
	private static final java.util.regex.Pattern ALIAS = java.util.regex.Pattern.compile(" AS (`(?:``|[^`])*`|\"(?:\"\"|[^\"])*\")");

	private static int count (String text, String part) {

		int count = 0;
		int from = 0;
		while ((from = text.indexOf(part, from)) >= 0) {
			count++;
			from += part.length();
		}
		return count;

	}


}
