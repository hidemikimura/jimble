package io.jimble.db.sql;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.sql.SqlGoldenCases.GenProduct;
import io.jimble.db.sql.SqlGoldenCases.GenShop;
import io.jimble.db.sql.query.dsl.Dsl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 形を決めて値だけ差し替える SELECT（{@link SelectTemplate}。3.0.0）
 *
 * <p>
 * <b>今のビルダーに同じ値を入れて組み立てたときと、SQL もパラメータも 1 文字も変わらない</b>ことを、
 * 形ごとに MySQL・PostgreSQL の両方で見る。答えは同じ形を今のビルダーで組み立てたもの
 * （その字面は {@link SqlGoldenTest} が 2.7.4 の公開版と比べている）。
 * </p>
 */
class SelectTemplateTest {

	private static final Dialect MYSQL = Dialects.of("mysql");

	private static final Dialect POSTGRESQL = Dialects.of("postgresql");

	private static final Dialect[] DIALECTS = { MYSQL, POSTGRESQL };

	/**
	 * 同じ形を、値の印で作った template と、値を入れたビルダーとで組み立てて比べる
	 *
	 * @param shape		形（引数が値の印か本物の値かで、同じ形のビルダーを作る）
	 * @param values	名前と値（bind に渡すもの）
	 */
	private static void assertSameAsBuilder (Function<Function<String, Object>, SelectBuilder> shape, Map<String, Object> values) {

		SelectTemplate template = SQL.template(shape.apply(name -> values.get(name) instanceof List<?> ? Dsl.params(name) : Dsl.param(name)));
		BoundSelect bound = template.bind(values);

		for (Dialect dialect : DIALECTS) {
			SelectBuilder builder = shape.apply(values::get);
			assertEquals(builder.sql(dialect), bound.sql(dialect), dialect.name());
			assertEquals(builder.params(), bound.params(), dialect.name());
		}

	}

	@Nested
	@DisplayName("今のビルダーと同じ SQL・パラメータになる")
	class SameAsBuilder {

		@Test
		@DisplayName("全列・主キー 1 行")
		void pk () {

			assertSameAsBuilder(v -> SQL.select().from(GenProduct.instance())
				.where(GenProduct.id.eq(v.apply("id")))
				.where(GenProduct.deleted_at.is_null())
				, Map.of("id", 10L));

		}

		@Test
		@DisplayName("2 表の全列・ON・決まった値（値の印でないもの）・ORDER BY・LIMIT / OFFSET")
		void joinWithConstants () {

			assertSameAsBuilder(v -> SQL.select().from(GenProduct.instance())
				.inner(GenShop.instance()).on(GenShop.id.eq(GenProduct.shop_id))
				.where(GenProduct.shop_id.eq(v.apply("shop")))
				.where(GenProduct.price.ge(new BigDecimal("100")))
				.orderBy(GenProduct.id.desc())
				.limit(20).offset(40)
				, Map.of("shop", 3L));

		}

		@Test
		@DisplayName("列を名指し・別名・同じ名前の印を 2 か所に置く")
		void namedColumnsAndSameNameTwice () {

			assertSameAsBuilder(v -> SQL.select(GenProduct.id, GenProduct.name.as("n"), GenShop.odd)
				.from(GenProduct.instance())
				.left(GenShop.instance()).on(GenShop.id.eq(GenProduct.shop_id))
				.where(GenProduct.price.ge(v.apply("price")))
				.where(GenProduct.price.lt(Dsl.coalesce(GenProduct.price, v.apply("price"))))
				, Map.of("price", new BigDecimal("12.5")));

		}

		@Test
		@DisplayName("IN の値の並び（値の数を変えても、その数の ? が並ぶ）")
		void inList () {

			for (int n = 1; n <= 4; n++) {
				List<Long> ids = new ArrayList<>();
				for (long i = 1; i <= n; i++) {
					ids.add(i);
				}
				Map<String, Object> values = new LinkedHashMap<>();
				values.put("shop", 7L);
				values.put("ids", ids);
				assertSameAsBuilder(v -> SQL.select().from(GenProduct.instance())
					.where(GenProduct.shop_id.eq(v.apply("shop")))
					.where(GenProduct.id.in(v.apply("ids")))
					.where(GenProduct.name.not_in(List.of("a", "b")))
					, values);
			}

		}

		@Test
		@DisplayName("IN の値の数が、覚える上限（32）をまたいで行き来しても、その数の ? が並ぶ")
		void inListAcrossSizedLimit () {

			for (int n : new int[] { 31, 32, 33, 40, 1, 32, 33, 2 }) {
				List<Long> ids = new ArrayList<>();
				for (long i = 1; i <= n; i++) {
					ids.add(i);
				}
				assertSameAsBuilder(v -> SQL.select().from(GenProduct.instance()).where(GenProduct.id.in(v.apply("ids")))
					, Map.of("ids", ids));
			}

		}

		@Test
		@DisplayName("値の並びの印が 2 つ（それぞれの値の数で ? が並ぶ）")
		void twoLists () {

			Map<String, Object> values = new LinkedHashMap<>();
			values.put("ids", List.of(1L, 2L, 3L));
			values.put("shops", List.of(9L));
			assertSameAsBuilder(v -> SQL.select().from(GenProduct.instance())
				.where(GenProduct.id.in(v.apply("ids")))
				.where(GenProduct.shop_id.not_in(v.apply("shops")))
				, values);

		}

		@Test
		@DisplayName("NOT IN の値の並び・IN のサブクエリの中の印")
		void notInAndSubquery () {

			Map<String, Object> values = new LinkedHashMap<>();
			values.put("ex", List.of(5L, 6L));
			values.put("code", "c1");
			assertSameAsBuilder(v -> SQL.select(GenProduct.id).from(GenProduct.instance())
				.where(GenProduct.id.not_in(v.apply("ex")))
				.where(GenProduct.shop_id.in(SQL.select(GenShop.id).from(GenShop.instance()).where(GenShop.code.eq(v.apply("code")))))
				, values);

		}

	}

	@Nested
	@DisplayName("bind で断るもの")
	class BindErrors {

		private final SelectTemplate template = SQL.template(SQL.select().from(GenProduct.instance())
			.where(GenProduct.shop_id.eq(Dsl.param("shop")))
			.where(GenProduct.id.in(Dsl.params("ids"))));

		@Test
		@DisplayName("足りない名前・知らない名前・名前と値の組になっていない引数")
		void names () {

			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "ids", List.of(1L), "other", 2L));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "ids"));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "shop", 2L, "ids", List.of(1L)));
			assertThrows(SqlBuildException.class, () -> template.bind(1L, "shop", "ids", List.of(1L)));

		}

		@Test
		@DisplayName("1 つの値の場所にリスト・値の並びの場所に 1 つの値か空の一覧か入れ子")
		void shapes () {

			assertThrows(SqlBuildException.class, () -> template.bind("shop", List.of(1L, 2L), "ids", List.of(1L)));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "ids", 1L));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "ids", List.of()));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "ids", null));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "ids", List.of(List.of(1L))));

		}

		@Test
		@DisplayName("null（今のビルダーの eq(null) と同じく断る。= NULL はどの行にも当たらない）")
		void nullValue () {

			assertThrows(SqlBuildException.class, () -> template.bind("shop", null, "ids", List.of(1L)));

			Map<String, Object> values = new LinkedHashMap<>();
			values.put("shop", null);
			values.put("ids", List.of(1L));
			assertThrows(SqlBuildException.class, () -> template.bind(values));

		}

		@Test
		@DisplayName("SQL の部品（列・ビルダー・関数）を値として渡す（印の場所は ? に決まっているので、部品そのものがバインドされてしまう）")
		void sqlParts () {

			assertThrows(SqlBuildException.class, () -> template.bind("shop", GenProduct.shop_id, "ids", List.of(1L)));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", SQL.select(GenShop.id).from(GenShop.instance()), "ids", List.of(1L)));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", Dsl.now(), "ids", List.of(1L)));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", Dsl.param("shop"), "ids", List.of(1L)));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "ids", List.of(1L, GenProduct.id)));

		}

		@Test
		@DisplayName("Map のキーが文字列でない")
		@SuppressWarnings({ "rawtypes", "unchecked" })
		void nonStringKey () {

			Map raw = new LinkedHashMap();
			raw.put("shop", 1L);
			raw.put("ids", List.of(1L));
			raw.put(1, 2L);

			assertThrows(SqlBuildException.class, () -> template.bind(raw));

		}

		@Test
		@DisplayName("値の並びは配列でも渡せる")
		void arrayForList () {

			BoundSelect bound = template.bind("shop", 1L, "ids", new long[] { 3L, 4L });

			assertTrue(bound.sql(MYSQL).endsWith("IN (?, ?))"), bound.sql(MYSQL));
			assertEquals(List.of(1L, 3L, 4L), bound.params());

		}

	}

	@Nested
	@DisplayName("template を作るときに断るもの")
	class TemplateErrors {

		@Test
		@DisplayName("値の並びの印を IN / NOT IN 以外に置く")
		void listParamOutsideIn () {

			assertThrows(SqlBuildException.class, () -> SQL.template(SQL.select().from(GenProduct.instance())
				.where(GenProduct.id.eq(Dsl.params("ids")))));

		}

		@Test
		@DisplayName("同じ名前を 1 つの値と値の並びの両方に使う")
		void sameNameBothKinds () {

			assertThrows(SqlBuildException.class, () -> SQL.template(SQL.select().from(GenProduct.instance())
				.where(GenProduct.id.in(Dsl.params("x")))
				.where(GenProduct.shop_id.eq(Dsl.param("x")))));

		}

		@Test
		@DisplayName("starts_with / contains / ends_with に値の印を置く（文字列にされて「:名前」が送られてしまう）。template の外でも中でも断る")
		void likeHelpers () {

			assertThrows(SqlBuildException.class, () -> SQL.select().from(GenProduct.instance()).where(GenProduct.name.starts_with(Dsl.param("q"))));
			assertThrows(SqlBuildException.class, () -> SQL.select().from(GenProduct.instance()).where(GenProduct.name.contains(Dsl.param("q"))));
			assertThrows(SqlBuildException.class, () -> SQL.select().from(GenProduct.instance()).where(GenProduct.name.ends_with(Dsl.param("q"))));

			// like は印を置ける（% を付けた値を bind する）
			SelectTemplate like = SQL.template(SQL.select().from(GenProduct.instance()).where(GenProduct.name.like(Dsl.param("q"))));
			assertEquals(List.of("ab%"), like.bind("q", "ab%").params());

		}

		@Test
		@DisplayName("既定の方言で書けない形（PostgreSQL が既定で、MySQL のインデックスのヒント）も作れ、書ける方言で使える")
		void otherDialectOnly () {

			Dialects.defaultDialect(POSTGRESQL);

			try {

				SelectTemplate template = SQL.template(SQL.select().from(GenProduct.instance().forceIndex("product__shop_id"))
					.where(GenProduct.shop_id.eq(Dsl.param("shop"))));
				SelectBuilder builder = SQL.select().from(GenProduct.instance().forceIndex("product__shop_id"))
					.where(GenProduct.shop_id.eq(5L));

				assertEquals(builder.sql(MYSQL), template.bind("shop", 5L).sql(MYSQL));
				assertThrows(io.jimble.db.dialect.DialectException.class, () -> template.bind("shop", 5L).sql(POSTGRESQL));

			} finally {
				Dialects.reset();
			}

		}

		@Test
		@DisplayName("形の受け渡し口は、SelectTemplate のほかからは決められない")
		void shapesInstallIsGuarded () {

			assertThrows(IllegalStateException.class, () -> io.jimble.db.internal.sql.template.TemplateShapes.install(new io.jimble.db.internal.sql.template.TemplateShapes.Access(bound -> null, (bound, dialect) -> null, (bound, dialect) -> false)));

		}

		@Test
		@DisplayName("使えない名前")
		void badNames () {

			assertThrows(SqlBuildException.class, () -> Dsl.param(null));
			assertThrows(SqlBuildException.class, () -> Dsl.param(""));
			assertThrows(SqlBuildException.class, () -> Dsl.param("a b"));
			assertThrows(SqlBuildException.class, () -> Dsl.params("x;"));

		}

		@Test
		@DisplayName("値の印の無い template も作れる（値は決まったまま）")
		void noParams () {

			SelectTemplate template = SQL.template(SQL.select().from(GenProduct.instance()).where(GenProduct.id.eq(1L)));

			assertEquals(List.of(1L), template.bind().params());
			assertEquals(SQL.select().from(GenProduct.instance()).where(GenProduct.id.eq(1L)).sql(MYSQL), template.bind().sql(MYSQL));

		}

	}

	@Nested
	@DisplayName("LIMIT / OFFSET の値を差し替える（3.0.2）")
	class Paging {

		private static SelectBuilder shape () {

			return SQL.select().from(GenProduct.instance())
				.where(GenProduct.shop_id.eq(Dsl.param("shop")))
				.orderBy(GenProduct.id.asc());

		}

		private final SelectTemplate template = SQL.template(shape().limit(Dsl.param("per")).offset(Dsl.param("skip")));

		@Test
		@DisplayName("ページごとに、値を入れたビルダーと同じ SQL・パラメータになる")
		void sameAsBuilder () {

			for (long page = 0; page < 3; page++) {
				BoundSelect bound = template.bind("shop", 1L, "per", 20, "skip", page * 20);
				for (Dialect dialect : DIALECTS) {
					SelectBuilder builder = SQL.select().from(GenProduct.instance())
						.where(GenProduct.shop_id.eq(1L)).orderBy(GenProduct.id.asc()).limit(20).offset(page * 20);
					assertEquals(builder.sql(dialect), bound.sql(dialect), dialect.name());
					assertEquals(builder.params(), bound.params(), dialect.name());
				}
			}

		}

		@Test
		@DisplayName("LIMIT だけ印にして、OFFSET は決まった値でもよい")
		void limitOnly () {

			SelectTemplate limitOnly = SQL.template(shape().limit(Dsl.param("per")).offset(40));
			SelectBuilder builder = SQL.select().from(GenProduct.instance())
				.where(GenProduct.shop_id.eq(1L)).orderBy(GenProduct.id.asc()).limit(10).offset(40);

			assertEquals(builder.sql(MYSQL), limitOnly.bind("shop", 1L, "per", 10L).sql(MYSQL));
			assertEquals(builder.params(), limitOnly.bind("shop", 1L, "per", 10L).params());
			assertEquals(List.of("shop", "per"), limitOnly.names());

		}

		@Test
		@DisplayName("数は Long にそろえて送る（ビルダーの limit(long) と同じ）")
		void countsAreLong () {

			List<Object> params = template.bind("shop", 1L, "per", (short) 5, "skip", 0).params();

			assertEquals(List.of(1L, 5L, 0L), params);
			assertEquals(Long.class, params.get(1).getClass());
			assertEquals(Long.class, params.get(2).getClass());

		}

		@Test
		@DisplayName("数でない値・負の数・null・小数は断る")
		void badCounts () {

			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "per", -1, "skip", 0));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "per", 20, "skip", -20L));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "per", "20", "skip", 0));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "per", 20.0, "skip", 0));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "per", new BigDecimal("20"), "skip", 0));
			assertThrows(SqlBuildException.class, () -> template.bind("shop", 1L, "per", List.of(20), "skip", 0));

			Map<String, Object> values = new LinkedHashMap<>();
			values.put("shop", 1L);
			values.put("per", null);
			values.put("skip", 0);
			assertThrows(SqlBuildException.class, () -> template.bind(values));

		}

		@Test
		@DisplayName("値の並びの印は LIMIT / OFFSET に置けない")
		void listParam () {

			assertThrows(SqlBuildException.class, () -> shape().limit(Dsl.params("per")));
			assertThrows(SqlBuildException.class, () -> shape().offset(Dsl.params("skip")));
			assertThrows(SqlBuildException.class, () -> shape().limit((TemplateParam) null));

		}

		@Test
		@DisplayName("LIMIT が無いのに OFFSET だけ印にすると、template を作るときに断る（OFFSET が SQL に出ない）")
		void offsetWithoutLimit () {

			assertThrows(SqlBuildException.class, () -> SQL.template(shape().offset(Dsl.param("skip"))));

		}

		@Test
		@DisplayName("印を置いたビルダーをそのまま組み立てると断る")
		void outsideTemplate () {

			SelectBuilder builder = SQL.select().from(GenProduct.instance()).limit(Dsl.param("per"));

			assertThrows(SqlBuildException.class, builder::params);

		}

		@Test
		@DisplayName("あとから limit(long)・offset(long)・paging で数を入れると、印は外れる")
		void numberReplacesParam () {

			SelectBuilder builder = SQL.select().from(GenProduct.instance())
				.limit(Dsl.param("per")).offset(Dsl.param("skip")).limit(10).offset(5);

			assertEquals(List.of(10L, 5L), builder.params());

			SelectBuilder removed = SQL.select().from(GenProduct.instance())
				.limit(Dsl.param("per")).offset(Dsl.param("skip")).offset(-1).limit(-1);

			assertTrue(removed.sql(MYSQL).endsWith("`product`"), removed.sql(MYSQL));
			assertEquals(List.of(), removed.params());

		}

		@Test
		@DisplayName("写したビルダーにも印が移る")
		void copied () {

			SelectBuilder original = shape().limit(Dsl.param("per")).offset(Dsl.param("skip"));
			SelectTemplate fromCopy = SQL.template(original.copy());

			assertEquals(List.of("shop", "per", "skip"), fromCopy.names());

		}

	}

	@Test
	@DisplayName("template を作ったあとに元のビルダーへ足しても、template は変わらない")
	void isolatedFromLaterChanges () {

		SelectBuilder builder = SQL.select().from(GenProduct.instance()).where(GenProduct.id.eq(Dsl.param("id")));
		SelectTemplate template = SQL.template(builder);
		String before = template.bind("id", 1L).sql(MYSQL);

		builder.where(GenProduct.shop_id.eq(2L)).orderBy(GenProduct.id.asc());

		assertEquals(before, template.bind("id", 1L).sql(MYSQL));
		assertEquals(List.of(1L), template.bind("id", 1L).params());

	}

	@Test
	@DisplayName("bind ごとに値の並びは別のリスト（書き換えても template に響かない）")
	void paramsAreFresh () {

		SelectTemplate template = SQL.template(SQL.select().from(GenProduct.instance()).where(GenProduct.id.eq(Dsl.param("id"))));

		List<Object> first = template.bind("id", 1L).params();
		first.set(0, 99L);

		assertEquals(List.of(1L), template.bind("id", 1L).params());
		assertNotSame(first, template.bind("id", 1L).params());

	}

	@Test
	@DisplayName("仮想スレッドから同時に・方言と値の数を入れ替えながら使っても、ビルダーと同じになる")
	void concurrent () throws Exception {

		SelectTemplate template = SQL.template(SQL.select().from(GenProduct.instance())
			.inner(GenShop.instance()).on(GenShop.id.eq(GenProduct.shop_id))
			.where(GenProduct.shop_id.eq(Dsl.param("shop")))
			.where(GenProduct.id.in(Dsl.params("ids"))));

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

			List<Future<List<String>>> futures = new ArrayList<>();
			for (int t = 0; t < 64; t++) {
				int offset = t;
				futures.add(executor.submit(() -> {
					List<String> mismatches = new ArrayList<>();
					for (int r = 0; r < 100; r++) {
						int n = (offset + r) % 5 + 1;
						List<Long> ids = new ArrayList<>();
						for (long i = 0; i < n; i++) {
							ids.add(offset + i);
						}
						Dialect dialect = DIALECTS[(offset + r) % 2];
						BoundSelect bound = template.bind("shop", (long) offset, "ids", ids);
						SelectBuilder builder = SQL.select().from(GenProduct.instance())
							.inner(GenShop.instance()).on(GenShop.id.eq(GenProduct.shop_id))
							.where(GenProduct.shop_id.eq((long) offset))
							.where(GenProduct.id.in(ids));
						String expected = builder.sql(dialect) + builder.params();
						String actual = bound.sql(dialect) + bound.params();
						if (!expected.equals(actual)) {
							mismatches.add(actual);
						}
					}
					return mismatches;
				}));
			}

			for (Future<List<String>> future : futures) {
				assertEquals(List.of(), future.get());
			}

		}

	}

}
