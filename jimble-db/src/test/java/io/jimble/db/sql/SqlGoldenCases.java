package io.jimble.db.sql;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.db.dialect.PostgreSqlDialect;
import io.jimble.db.sql.DslSchema.Item;
import io.jimble.db.sql.TestSchema.Customer;
import io.jimble.db.sql.TestSchema.Feed;
import io.jimble.db.sql.TestSchema.Site;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sql.query.where.WhereQuery;
import io.jimble.db.sqlcache.SqlCache;
import io.jimble.util.data.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * SQL の組み立ての「答え合わせ」に使う組み立て一式（2.7.5）
 *
 * <p>
 * <b>2.7.4 の公開版で出した字面と 1 文字ずつ比べるためのもの。</b>
 * 2.7.5 で組み立ての中身（全列の SELECT 句・パラメータの集め方・キャッシュのキー）を作り直したので、
 * ここに並べた組み立ての SQL・パラメータ・キャッシュのキーを 2.7.4 の jar で書き出したものを
 * {@code src/test/resources/golden/sql-2.7.4.txt} に置き、{@link SqlGoldenTest} が今の出力と比べる。
 * </p>
 *
 * <p>
 * <b>ここは 2.7.4 にもある API だけで書く</b>（同じファイルを 2.7.4 の jar で動かして答えを作るため）。
 * 組み立てを足したら答えも作り直す（手順は {@link SqlGoldenTest} の javadoc）。
 * </p>
 */
public final class SqlGoldenCases {

	/** キャッシュのキーに使うデータソース名 */
	static final String DATA_SOURCE = "golden_db";

	/** 方言 */
	private static final List<Dialect> DIALECTS = List.of(MySqlDialect.INSTANCE, PostgreSqlDialect.INSTANCE);

	/** 組み立て（名前と、毎回新しく作るもの） */
	private record Case (String name, Supplier<?> builder) {}

	private SqlGoldenCases () {
	}

	// region 生成物と同じ形のテーブル（列一覧を宣言する。instance() は毎回 new）

	/**
	 * 生成物と同じ形のスキーマ
	 *
	 * <p>
	 * TestSchema / DslSchema はリフレクションで列を集めるので、
	 * 2.7.5 の「宣言した列一覧ごとに全列の字面を覚える」道を通らない。こちらはその道を通す。
	 * </p>
	 */
	public static final class GenSchema extends io.jimble.db.sql.definition.schema.AbstractSchema {

		@Override
		public String name () {
			return "jimble_gen";
		}

	}

	/** 商品（生成物と同じ形） */
	public static class GenProduct extends io.jimble.db.sql.definition.table.Table {

		public static final io.jimble.db.sql.definition.column.Column id = new io.jimble.db.sql.definition.column.Column(instance(), "id", long.class, false, null, true);
		public static final io.jimble.db.sql.definition.column.Column shop_id = new io.jimble.db.sql.definition.column.Column(instance(), "shop_id", long.class, false, null, false);
		public static final io.jimble.db.sql.definition.column.Column name = new io.jimble.db.sql.definition.column.Column(instance(), "name", String.class, false, null, false);
		public static final io.jimble.db.sql.definition.column.Column price = new io.jimble.db.sql.definition.column.Column(instance(), "price", BigDecimal.class, false, null, false);
		public static final io.jimble.db.sql.definition.column.Column deleted_at = new io.jimble.db.sql.definition.column.Column(instance(), "deleted_at", java.util.Date.class, true, null, false);

		private static final List<io.jimble.db.sql.definition.column.Column> COLUMNS = List.of(id, shop_id, name, price, deleted_at);

		@Override
		protected List<io.jimble.db.sql.definition.column.Column> declareColumns () { return COLUMNS; }

		public GenProduct (io.jimble.util.data.definition.ISchema schema, String name) { super(schema, name); }

		public static GenProduct instance () { return new GenProduct(new GenSchema(), "product"); }

	}

	/** ショップ（生成物と同じ形。名前に引用符を含む列を持つ） */
	public static class GenShop extends io.jimble.db.sql.definition.table.Table {

		public static final io.jimble.db.sql.definition.column.Column id = new io.jimble.db.sql.definition.column.Column(instance(), "id", long.class, false, null, true);
		public static final io.jimble.db.sql.definition.column.Column code = new io.jimble.db.sql.definition.column.Column(instance(), "code", String.class, false, null, false);
		public static final io.jimble.db.sql.definition.column.Column odd = new io.jimble.db.sql.definition.column.Column(instance(), "we`ird\"name", String.class, true, null, false);

		private static final List<io.jimble.db.sql.definition.column.Column> COLUMNS = List.of(id, code, odd);

		@Override
		protected List<io.jimble.db.sql.definition.column.Column> declareColumns () { return COLUMNS; }

		public GenShop (io.jimble.util.data.definition.ISchema schema, String name) { super(schema, name); }

		public static GenShop instance () { return new GenShop(new GenSchema(), "shop"); }

	}

	/** 列を宣言しているが、列が 1 つも無いテーブル */
	public static class GenEmpty extends io.jimble.db.sql.definition.table.Table {

		@Override
		protected List<io.jimble.db.sql.definition.column.Column> declareColumns () { return List.of(); }

		public GenEmpty (io.jimble.util.data.definition.ISchema schema, String name) { super(schema, name); }

		public static GenEmpty instance () { return new GenEmpty(new GenSchema(), "empty"); }

	}

	/** 宣言を毎回作り直すテーブル（同じ列を新しいリストで返す） */
	public static class GenFresh extends io.jimble.db.sql.definition.table.Table {

		@Override
		protected List<io.jimble.db.sql.definition.column.Column> declareColumns () { return new ArrayList<>(List.of(GenProduct.id, GenProduct.name)); }

		public GenFresh (io.jimble.util.data.definition.ISchema schema, String name) { super(schema, name); }

		public static GenFresh instance () { return new GenFresh(new GenSchema(), "fresh"); }

	}

	/** 宣言が書き換えられるテーブル（static な ArrayList を返す） */
	public static class GenMutable extends io.jimble.db.sql.definition.table.Table {

		static final List<io.jimble.db.sql.definition.column.Column> COLUMNS = new ArrayList<>(List.of(GenProduct.id, GenProduct.name, GenProduct.price));

		@Override
		protected List<io.jimble.db.sql.definition.column.Column> declareColumns () { return COLUMNS; }

		public GenMutable (io.jimble.util.data.definition.ISchema schema, String name) { super(schema, name); }

		public static GenMutable instance () { return new GenMutable(new GenSchema(), "mutable"); }

	}

	/** 宣言で落ちるテーブル */
	public static class GenBroken extends io.jimble.db.sql.definition.table.Table {

		@Override
		protected List<io.jimble.db.sql.definition.column.Column> declareColumns () { throw new IllegalStateException("broken"); }

		public GenBroken (io.jimble.util.data.definition.ISchema schema, String name) { super(schema, name); }

		public static GenBroken instance () { return new GenBroken(new GenSchema(), "broken"); }

	}

	/** パラメータを持つ FROM（アプリが Table を継承して上書きした形） */
	public static class GenFunctionTable extends io.jimble.db.sql.definition.table.Table {

		public GenFunctionTable () { super(new GenSchema(), "fn_table"); }

		@Override
		protected List<io.jimble.db.sql.definition.column.Column> declareColumns () { return List.of(GenProduct.id); }

		@Override
		public void fromSql (io.jimble.db.dialect.SqlWriter sb) { sb.append(" fn_table(?, ?)"); }

		@Override
		public boolean hasParameter () { return true; }

		@Override
		public Object getParameter () { return List.of("from-a", 42L); }

	}

	/** 継承した列（字面と値を上書きする。暗号化した列を復号して読むような形） */
	public static class GenDecryptColumn extends io.jimble.db.sql.definition.column.Column {

		public GenDecryptColumn () { super(GenProduct.instance(), "secret", String.class, true, null, false); }

		@Override
		public void selectSql (io.jimble.db.dialect.SqlWriter sb) { sb.append("AES_DECRYPT("); super.selectSql(sb); sb.append(", ?)"); }

		@Override
		public boolean hasParameter () { return true; }

		@Override
		public Object getParameter () { return "key-1"; }

		@Override
		public String getJoinSelectName () { return "product__secret_plain"; }

	}

	/** 継承した列を持つテーブル */
	public static class GenWithDecrypt extends io.jimble.db.sql.definition.table.Table {

		static final io.jimble.db.sql.definition.column.Column SECRET = new GenDecryptColumn();

		@Override
		protected List<io.jimble.db.sql.definition.column.Column> declareColumns () { return List.of(GenProduct.id, SECRET); }

		public GenWithDecrypt (io.jimble.util.data.definition.ISchema schema, String name) { super(schema, name); }

		public static GenWithDecrypt instance () { return new GenWithDecrypt(new GenSchema(), "product"); }

	}

	// endregion

	/**
	 * 全部の組み立てを書き出す
	 *
	 * @return	書き出したもの（行ごと）
	 */
	public static String render () {

		StringBuilder out = new StringBuilder();

		for (Case c : selectCases()) {
			for (Dialect dialect : DIALECTS) {
				out.append("== ").append(c.name()).append(" [").append(dialect.name()).append("]\n");
				renderSelect(out, c, dialect);
			}
		}

		for (Case c : sequenceCases()) {
			for (Dialect dialect : DIALECTS) {
				out.append("== ").append(c.name()).append(" [").append(dialect.name()).append("]\n");
				out.append(attempt(() -> ((Function<Dialect, String>) c.builder().get()).apply(dialect))).append('\n');
			}
		}

		for (Case c : writeCases()) {
			for (Dialect dialect : DIALECTS) {
				out.append("== ").append(c.name()).append(" [").append(dialect.name()).append("]\n");
				IBuilder builder = (IBuilder) c.builder().get();
				String sql = attempt(() -> builder.sql(dialect));
				out.append("sql: ").append(sql).append('\n');
				out.append("params: ").append(attempt(() -> params(builder.params()))).append('\n');
			}
		}

		return out.toString();

	}

	// region 書き出し

	private static void renderSelect (StringBuilder out, Case c, Dialect dialect) {

		SelectBuilder builder = (SelectBuilder) c.builder().get();

		String sql = attempt(() -> builder.sql(dialect));
		out.append("sql: ").append(sql).append('\n');

		List<Object> params;
		try {
			params = builder.params();
			out.append("params: ").append(params(params)).append('\n');
		} catch (RuntimeException ex) {
			params = null;
			out.append("params: ").append(failure(ex)).append('\n');
		}

		if (params != null && !sql.startsWith("!")) {
			out.append("key: ").append(SqlCache.key(DATA_SOURCE, sql, params)).append('\n');
		}

		out.append("rowCount: ").append(attempt(() -> builder.rowCountSql(dialect))).append('\n');
		out.append("rowCountParams: ").append(attempt(() -> params(builder.rowCountParams()))).append('\n');
		out.append("simple: ").append(attempt(() -> builder.simpleSql(dialect))).append('\n');
		// simpleSql のあとも sql() は同じ（写しで組むので、元に PK 列が増えない）
		out.append("sqlAgain: ").append(attempt(() -> builder.sql(dialect))).append('\n');
		out.append("paramsAgain: ").append(attempt(() -> params(builder.params()))).append('\n');

	}

	private static String attempt (Supplier<String> body) {

		try {
			return body.get();
		} catch (RuntimeException ex) {
			return failure(ex);
		}

	}

	private static String failure (RuntimeException ex) {

		return "!" + ex.getClass().getSimpleName() + ": " + ex.getMessage();

	}

	/**
	 * パラメータを型つきで書く
	 *
	 * @param params	パラメータ
	 * @return	字面
	 */
	static String params (List<Object> params) {

		List<String> parts = new ArrayList<>();

		for (Object param : params) {
			if (param == null) {
				parts.add("null");
			} else if (param instanceof byte[] bytes) {
				parts.add("byte[]:" + HexFormat.of().formatHex(bytes));
			} else if (param instanceof Data data) {
				parts.add("Data:" + data.getJsonString());
			} else if (param instanceof java.util.Date date) {
				parts.add(param.getClass().getSimpleName() + ":" + date.getTime());
			} else {
				parts.add(param.getClass().getSimpleName() + ":" + param);
			}
		}

		return parts.toString();

	}

	// endregion

	// region SELECT

	private static List<Case> selectCases () {

		List<Case> cases = new ArrayList<>();

		cases.add(new Case("pk / all columns", () -> SQL.select()
			.from(Site.instance())
			.where(Site.id.eq(1L))));

		cases.add(new Case("named columns and alias", () -> SQL.select(Site.id, Site.name.as("site_name"), Site.feed_count)
			.from(Site.instance())
			.where(Site.group_id.eq(3L))
			.where(Site.deleted_at.is_null())));

		cases.add(new Case("inner join on / all columns", () -> new SelectBuilder()
			.from(Site.instance())
			.inner(Feed.instance())
			.on(Feed.site_id.eq(Site.id))
			.where(Site.id.eq(5L))));

		cases.add(new Case("left join on with value / named", () -> SQL.select(Site.id, Feed.title)
			.from(Site.instance())
			.left(Feed.instance())
			.on(Feed.site_id.eq(Site.id))
			.on(Feed.title.like("%news%"))
			.where(Site.group_id.eq(1L))
			.orderByDesc(Site.id)));

		cases.add(new Case("three tables / all columns", () -> new SelectBuilder()
			.from(Site.instance())
			.inner(Feed.instance())
			.on(Feed.site_id.eq(Site.id))
			.left(Customer.instance())
			.on(Customer.id.eq(Site.group_id))
			.where(Customer.code.eq("C-1"))));

		cases.add(new Case("in / not in / between / like", () -> SQL.select()
			.from(Site.instance())
			.where(Site.id.in(List.of(1L, 2L, 3L)))
			.where(Site.group_id.not_in(new long[] { 7L, 8L }))
			.where(Site.feed_count.between(10L, 20L))
			.where(Site.name.contains("a_b%"))
			.where(Site.name.starts_with("x"))
			.where(Site.name.ends_with("y"))
			.where(Site.name.not_like("%z%"))));

		cases.add(new Case("comparisons", () -> SQL.select(Site.id)
			.from(Site.instance())
			.where(Site.id.gt(1L))
			.where(Site.id.lt(100L))
			.where(Site.feed_count.ge(0L))
			.where(Site.feed_count.le(9L))
			.where(Site.name.not("bad"))
			.where(Site.name.is_not_null())));

		cases.add(new Case("and / or groups", () -> SQL.select(Site.id)
			.from(Site.instance())
			.where(Site.id.eq(1L).or(Site.id.eq(2L)))
			.where(Site.name.eq("a").and(Site.group_id.eq(4L)).or(Site.feed_count.gt(5L)))
			.where(Site.deleted_at.is_null().and(Site.id.in(List.of(9L, 10L)).or(Site.group_id.eq(11L))))));

		cases.add(new Case("in subquery", () -> SQL.select(Site.id, Site.name)
			.from(Site.instance())
			.where(Site.id.in(SQL.select(Feed.site_id).from(Feed.instance()).where(Feed.title.eq("t"))))
			.where(Site.group_id.eq(2L))));

		cases.add(new Case("group by / having / order by", () -> SQL.select(Item.group_id, Dsl.sum(Item.amount).as("total"), Dsl.count().as("cnt"))
			.from(Item.instance())
			.where(Item.at.ge(new java.util.Date(1_700_000_000_000L)))
			.groupBy(Item.group_id)
			.having(Dsl.sum(Item.amount).gt(100L))
			.orderBy(Item.group_id.asc())
			.orderByDesc(Dsl.count())));

		cases.add(new Case("arithmetic with parameters", () -> SQL.select(Item.id, Item.amount.plus(1L).multiply(2L).as("calc"), Item.amount.minus(Item.id).as("diff"))
			.from(Item.instance())
			.where(Item.amount.plus(10L).gt(50L))));

		cases.add(new Case("functions", () -> SQL.select(
				Item.id
				, Dsl.coalesce(Item.name, "none").as("n")
				, Dsl.ifThenElse(Item.amount.gt(10L), "big", "small").as("size")
				, Dsl.lower(Item.name).as("lower_name")
				, Dsl.abs(Item.amount).as("abs_amount")
				, Dsl.countDistinct(Item.group_id).as("groups"))
			.from(Item.instance())
			.groupBy(Item.id, Item.name, Item.amount)));

		cases.add(new Case("limit / offset", () -> SQL.select()
			.from(Item.instance())
			.where(Item.group_id.eq(1L))
			.orderBy(Item.id.asc())
			.limit(20)
			.offset(40)));

		cases.add(new Case("limit only", () -> SQL.select(Item.id)
			.from(Item.instance())
			.limit(5)));

		cases.add(new Case("for update", () -> SQL.select()
			.from(Site.instance())
			.where(Site.id.eq(1L))
			.forUpdate()));

		cases.add(new Case("for update nowait", () -> SQL.select(Site.id)
			.from(Site.instance())
			.where(Site.id.eq(1L))
			.forUpdateNoWait()));

		cases.add(new Case("for update skip locked", () -> SQL.select(Site.id)
			.from(Site.instance())
			.where(Site.id.eq(1L))
			.forUpdateSkipLocked()));

		cases.add(new Case("where(Data)", () -> {
			Data columns = new Data();
			columns.put("id|in", List.of(1, 2));
			columns.put("name|like", "%a%");
			columns.put("group_id", 3);
			columns.put("deleted_at|is_null", true);
			Data tables = new Data();
			tables.put("site", columns);
			Data where = new Data();
			where.put("where", tables);
			return SQL.select().from(Site.instance()).where(where);
		}));

		cases.add(new Case("apply(Data) with order", () -> {
			Data columns = new Data();
			columns.put("feed_count|between", List.of("2024-01-01", "2024-12-31"));
			Data tables = new Data();
			tables.put("site", columns);
			Data orderColumns = new Data();
			orderColumns.put("name", "desc");
			orderColumns.put("id", "asc");
			Data orderTables = new Data();
			orderTables.put("site", orderColumns);
			Data data = new Data();
			data.put("where", tables);
			data.put("order", orderTables);
			return SQL.select(Site.id).from(Site.instance()).apply(data);
		}));

		cases.add(new Case("force index", () -> SQL.select()
			.from(Site.instance().forceIndex("idx_group"))
			.where(Site.group_id.eq(1L))));

		cases.add(new Case("odd values", () -> SQL.select(Item.id)
			.from(Item.instance())
			.where(Item.name.eq("O'Reilly \"q\" `b` \\ 日本語"))
			.where(Item.amount.eq(new BigDecimal("12.30")))
			.where(Item.id.in(new Object[] { 1, 2L, "3" }))
			.where(Item.group_id.eq(true))));

		cases.add(new Case("generated / pk all columns", () -> new SelectBuilder()
			.from(GenProduct.instance())
			.where(GenProduct.id.eq(7L))));

		cases.add(new Case("generated / join all columns", () -> new SelectBuilder()
			.from(GenProduct.instance())
			.inner(GenShop.instance())
			.on(GenShop.id.eq(GenProduct.shop_id))
			.where(GenProduct.shop_id.eq(1L))
			.where(GenProduct.id.in(List.of(1L, 2L, 3L)))
			.where(GenProduct.deleted_at.is_null())
			.orderBy(GenProduct.price.desc())
			.limit(20)
			.offset(0)));

		cases.add(new Case("generated / empty table first", () -> new SelectBuilder()
			.from(GenEmpty.instance())
			.inner(GenProduct.instance())
			.on(GenProduct.id.eq(1L))));

		cases.add(new Case("generated / empty table last", () -> new SelectBuilder()
			.from(GenProduct.instance())
			.left(GenEmpty.instance())
			.on(GenProduct.id.eq(2L))));

		cases.add(new Case("generated / empty table only", () -> new SelectBuilder()
			.from(GenEmpty.instance())));

		cases.add(new Case("generated / mixed with reflected", () -> new SelectBuilder()
			.from(GenProduct.instance())
			.left(Site.instance())
			.on(Site.id.eq(GenProduct.shop_id))
			.where(GenShop.odd.eq("x"))));

		cases.add(new Case("generated / named columns", () -> SQL.select(GenProduct.id, GenShop.odd, GenProduct.name.as("n"))
			.from(GenProduct.instance())
			.inner(GenShop.instance())
			.on(GenShop.id.eq(GenProduct.shop_id))
			.orderBy(GenShop.odd.asc())));

		cases.add(new Case("subclass / table with parameters", () -> new SelectBuilder()
			.from(new GenFunctionTable())
			.where(GenProduct.id.eq(3L))));

		cases.add(new Case("subclass / column overriding sql and parameters", () -> SQL.select(GenProduct.id, GenWithDecrypt.SECRET)
			.from(GenWithDecrypt.instance())
			.where(GenWithDecrypt.SECRET.eq("x"))
			.orderBy(GenWithDecrypt.SECRET.asc())));

		cases.add(new Case("subclass / all columns with overriding column", () -> new SelectBuilder()
			.from(GenWithDecrypt.instance())
			.where(GenProduct.id.eq(4L))));

		cases.add(new Case("subclass / where query", () -> SQL.select(GenProduct.id)
			.from(GenProduct.instance())
			.where(new WhereQuery(GenProduct.name) {
				@Override public void whereSql (io.jimble.db.dialect.SqlWriter sb) { super.whereSql(sb); sb.append(" OR 1 = ?"); }
				@Override public boolean hasParameter () { return true; }
				@Override public Object getParameter () { return List.of(super.getParameter(), "extra"); }
			}.eq("n"))));

		cases.add(new Case("subclass / order by query", () -> SQL.select(GenProduct.id)
			.from(GenProduct.instance())
			.orderBy(new io.jimble.db.internal.sql.query.order_by.OrderByQuery(GenProduct.price) {
				@Override public void orderBySql (io.jimble.db.dialect.SqlWriter sb) { sb.append("FIELD(?, 1) "); super.orderBySql(sb); }
				@Override public boolean hasParameter () { return true; }
				@Override public Object getParameter () { return 9L; }
			}.desc())));

		cases.add(new Case("declared / fresh list each call", () -> new SelectBuilder()
			.from(GenFresh.instance())
			.where(GenProduct.id.eq(5L))));

		cases.add(new Case("declared / throws", () -> new SelectBuilder()
			.from(GenBroken.instance())));

		cases.add(new Case("no columns no from", SelectBuilder::new));

		cases.add(new Case("select literal without from", () -> SQL.select(Dsl.now().as("now"))));

		return cases;

	}

	// endregion

	// region 状態をまたぐ（全列を書いたあとの select / copy / join）

	private static List<Case> sequenceCases () {

		List<Case> cases = new ArrayList<>();

		cases.add(new Case("sql twice", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = SQL.select().from(Site.instance()).where(Site.id.eq(1L));
			return builder.sql(dialect) + "\n" + builder.sql(dialect) + "\n" + params(builder.params());
		}));

		cases.add(new Case("sql then select more", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = SQL.select().from(Site.instance()).where(Site.id.eq(1L));
			String first = builder.sql(dialect);
			builder.select(Dsl.count().as("cnt"));
			return first + "\n" + builder.sql(dialect) + "\n" + params(builder.params());
		}));

		cases.add(new Case("sql then copy then where", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = SQL.select().from(Site.instance()).where(Site.id.eq(1L));
			String first = builder.sql(dialect);
			SelectBuilder copy = builder.copy().where(Site.group_id.eq(2L)).select(Site.name.as("again"));
			return first + "\n" + copy.sql(dialect) + "\n" + params(copy.params()) + "\n" + builder.sql(dialect);
		}));

		cases.add(new Case("sql then join", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = new SelectBuilder().from(Site.instance()).where(Site.id.eq(1L));
			String first = builder.sql(dialect);
			builder.inner(Feed.instance()).on(Feed.site_id.eq(Site.id));
			return first + "\n" + builder.sql(dialect) + "\n" + params(builder.params());
		}));

		cases.add(new Case("sql then from", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = new SelectBuilder().from(Site.instance());
			String first = builder.sql(dialect);
			builder.from(Feed.instance());
			return first + "\n" + builder.sql(dialect);
		}));

		cases.add(new Case("copy before sql", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = new SelectBuilder().from(Site.instance()).where(Site.id.eq(1L));
			SelectBuilder copy = builder.copy().select(Site.id);
			return builder.sql(dialect) + "\n" + copy.sql(dialect) + "\n" + builder.simpleSql(dialect);
		}));

		cases.add(new Case("sql then simpleSql then sql", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = new SelectBuilder().from(Feed.instance()).where(Feed.site_id.eq(3L));
			return builder.sql(dialect) + "\n" + builder.simpleSql(dialect) + "\n" + builder.sql(dialect);
		}));

		cases.add(new Case("empty in", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = SQL.select(Site.id).from(Site.instance()).where(Site.id.in(List.of()));
			return attempt(() -> builder.sql(dialect)) + "\n" + attempt(() -> params(builder.params()));
		}));

		cases.add(new Case("list where one value", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = SQL.select(Site.id).from(Site.instance()).where(Site.name.eq(List.of("a", "b")));
			return attempt(() -> builder.sql(dialect)) + "\n" + attempt(() -> params(builder.params()));
		}));

		cases.add(new Case("generated / sql then select then copy", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = new SelectBuilder().from(GenProduct.instance()).where(GenProduct.id.eq(1L));
			String first = builder.sql(dialect);
			SelectBuilder copy = builder.copy();
			builder.select(GenProduct.price.multiply(2L).as("double_price"));
			return first + "\n" + builder.sql(dialect) + "\n" + params(builder.params()) + "\n" + copy.sql(dialect)
				+ "\n" + builder.simpleSql(dialect) + "\n" + builder.rowCountSql(dialect);
		}));

		cases.add(new Case("generated / mysql then postgresql on one builder", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = new SelectBuilder().from(GenProduct.instance()).inner(GenShop.instance()).on(GenShop.id.eq(GenProduct.shop_id));
			return builder.sql(MySqlDialect.INSTANCE) + "\n" + builder.sql(PostgreSqlDialect.INSTANCE) + "\n" + builder.sql(dialect);
		}));

		cases.add(new Case("column list / mutated on one instance", () -> (Function<Dialect, String>) dialect -> {
			GenProduct table = GenProduct.instance();
			table.getColumnList().remove(GenProduct.price);
			String mutated = new SelectBuilder().from(table).sql(dialect);
			String fresh = new SelectBuilder().from(GenProduct.instance()).sql(dialect);
			return mutated + "\n" + fresh;
		}));

		cases.add(new Case("column list / static declaration changed", () -> (Function<Dialect, String>) dialect -> {
			String before = new SelectBuilder().from(GenMutable.instance()).sql(dialect);
			GenMutable.COLUMNS.remove(GenProduct.price);
			String after = new SelectBuilder().from(GenMutable.instance()).sql(dialect);
			GenMutable.COLUMNS.add(GenProduct.price);
			String restored = new SelectBuilder().from(GenMutable.instance()).sql(dialect);
			return before + "\n" + after + "\n" + restored;
		}));

		cases.add(new Case("from query changed after sql", () -> (Function<Dialect, String>) dialect -> {
			io.jimble.db.internal.sql.query.from.IFrom from = GenProduct.instance().inner(GenShop.instance()).on(GenShop.id.eq(GenProduct.shop_id));
			SelectBuilder builder = new SelectBuilder().from(from);
			String first = builder.sql(dialect);
			from.left(Site.instance());
			String second = builder.sql(dialect);
			SelectBuilder copy = builder.copy();
			return first + "\n" + second + "\n" + copy.sql(dialect) + "\n" + params(copy.params());
		}));

		cases.add(new Case("cache key namespaces", () -> (Function<Dialect, String>) dialect -> {
			SelectBuilder builder = SQL.select().from(Site.instance()).where(Site.id.eq(1L));
			String sql = builder.sql(dialect);
			List<Object> params = builder.params();
			List<Object> bytes = new ArrayList<>(params);
			bytes.add(new byte[] { 1, 2, (byte) 0xff });
			Data json = new Data();
			json.put("k", "v");
			List<Object> withData = new ArrayList<>(params);
			withData.add(json);
			withData.add(null);
			return SqlCache.key(sql, params) + "\n"
				+ SqlCache.key("other_db", sql, params) + "\n"
				+ SqlCache.key(null, sql, params) + "\n"
				+ SqlCache.key(DATA_SOURCE, sql, bytes) + "\n"
				+ SqlCache.key(DATA_SOURCE, sql, withData) + "\n"
				+ SqlCache.key(DATA_SOURCE, "SELECT 'é日本'", List.of("é", 1, 2L));
		}));

		return cases;

	}

	// endregion

	// region INSERT / UPDATE / DELETE

	private static List<Case> writeCases () {

		List<Case> cases = new ArrayList<>();

		cases.add(new Case("insert values", () -> SQL.insert(Site.instance())
			.value(Site.id, 1L)
			.value(Site.group_id, 2L)
			.value(Site.name, "名前")
			.value(Site.deleted_at, null)));

		cases.add(new Case("insert upsert", () -> SQL.insert(Customer.instance())
			.value(Customer.id, 1L)
			.value(Customer.code, "C")
			.value(Customer.name, "n")
			.onDuplicateKeyUpdateValues(Customer.name)
			.onDuplicateKeyUpdate(Customer.code, "D")));

		cases.add(new Case("insert select", () -> SQL.insert(Feed.instance())
			.value(Feed.site_id, Feed.title)
			.value(SQL.select(Site.id, Site.name).from(Site.instance()).where(Site.group_id.eq(5L)))));

		cases.add(new Case("update set where", () -> SQL.update(Site.instance())
			.set(Site.name, "new")
			.set(Site.feed_count, Site.feed_count.plus(1L))
			.where(Site.id.in(List.of(1L, 2L)))
			.where(Site.deleted_at.is_null())));

		cases.add(new Case("update bytes and data", () -> {
			Data json = new Data();
			json.put("a", 1);
			return SQL.update(Item.instance())
				.set(Item.name, new byte[] { 0x41, 0x42 })
				.set(Item.amount, json)
				.where(Item.id.eq(9L));
		}));

		cases.add(new Case("delete where", () -> SQL.delete(Feed.instance())
			.where(Feed.site_id.eq(3L))
			.where(Feed.title.like("old%"))));

		cases.add(new Case("delete all rows", () -> SQL.delete(Feed.instance()).allRows()));

		return cases;

	}

	// endregion

}
