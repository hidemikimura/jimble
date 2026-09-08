package io.jimble.db.sqlcache;

import io.jimble.db.sql.DeleteBuilder;
import io.jimble.db.sql.IBuilder;
import io.jimble.db.sql.InsertBuilder;
import io.jimble.db.sql.SelectBuilder;
import io.jimble.db.sql.UpdateBuilder;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.db.sql.query.where.WhereTerm;
import io.jimble.db.sql.query.where.WhereTerms;
import io.jimble.util.data.Data;
import io.jimble.util.data.definition.IColumn;
import io.jimble.util.data.definition.ITable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * キャッシュの依存を表すタグ（要件 F-D-28）
 *
 * <h2>2種類ある</h2>
 * <ul>
 *   <li><b>行タグ</b> {@code customer#id#1} — その1行に依存している</li>
 *   <li><b>テーブルタグ</b> {@code customer#*} — そのテーブルの何が変わっても影響する</li>
 * </ul>
 *
 * <h2>SELECT 側（何に依存しているか）</h2>
 * <p>
 * 結果の行から、テーブルごとに<b>主キーと一意キーの値</b>を拾って行タグにする。
 * SELECT の結果はテーブル名でネストしている（要件 F-D-02）ので、
 * <b>結合先のキーもそのまま拾える</b>。
 * </p>
 *
 * <p>次のどれかに当たるテーブルには、あわせて<b>テーブルタグ</b>を付ける。</p>
 * <ul>
 *   <li>そのテーブルの行が1つに決まっていない
 *       （WHERE がキーの等値・IN で絞っておらず、結合条件でもキーが縛られていない）
 *       — <b>INSERT で行が増えると結果が変わる</b>ため</li>
 *   <li>結果に<b>キーの列が入っていない</b>（{@code SELECT id, name} のように列を絞った）
 *       — その欠けたキーで絞った更新を取りこぼすため</li>
 *   <li>結果が<b>0件</b> — どの行に依存しているのか分からないため</li>
 * </ul>
 *
 * <h2>UPDATE / DELETE 側（何を消すか）</h2>
 * <p>
 * WHERE から<b>キーの等値・IN</b>を読めたら、その行タグだけを消す。
 * 読めなければ<b>テーブルタグ</b>を消す（そのテーブルに触る全部が消える）。
 * INSERT は行が増えるので、常にテーブルタグを消す。
 * </p>
 *
 * <h2>値のそろえ方</h2>
 * <p>
 * SELECT 側は DB が返した値、更新側は書いたリテラルなので、
 * <b>そのまま文字にすると {@code 1} と {@code 1L} が別のタグになる</b>。
 * 数値は整数と小数に寄せ、文字は小文字にそろえる。
 * 小文字にそろえるのは、MySQL の既定の照合順序が大小を区別しないためで、
 * <b>そろえすぎる（余分に消える）側に倒している</b>。
 * </p>
 */
public final class SqlCacheTags {

	/** 行が増減したら影響するもの（一覧）の印 */
	public static final String ROWS = "*";

	/** そのテーブルを読んでいるもの全部の印 */
	public static final String READ = "@";

	/* タグの区切り */
	private static final String SEPARATOR = "#";

	/* 値の区切り（複合キー） */
	private static final String VALUE_SEPARATOR = "|";

	/**
	 * コンストラクタ
	 */
	private SqlCacheTags () {

	}

	// region タグを作る

	/**
	 * 一覧タグ（行が増減したら影響する）
	 *
	 * <p>
	 * <b>そのテーブルの行の集合に依存しているもの</b>に付ける。
	 * INSERT / DELETE と、絞れない UPDATE で消える。
	 * </p>
	 *
	 * @param table	テーブル
	 * @return	タグ
	 */
	public static String rowsTag (String dbName, ITable table) {

		return dbName + "/" + table.name() + SEPARATOR + ROWS;

	}

	/**
	 * 読み取りタグ（そのテーブルを読んでいる）
	 *
	 * <p>
	 * <b>そのテーブルに触ったキャッシュ全部</b>に付ける。
	 * <b>どの行に当たるか読めない更新</b>のときだけ消す。
	 * </p>
	 *
	 * <p>
	 * これが無いと、{@code WHERE id = 1} で引いた行キャッシュが
	 * {@code UPDATE customer ... WHERE shop_id = 1}（キーで絞れない）を
	 * <b>すり抜けて古いまま残る</b>。
	 * </p>
	 *
	 * @param table	テーブル
	 * @return	タグ
	 */
	public static String readTag (String dbName, ITable table) {

		return dbName + "/" + table.name() + SEPARATOR + READ;

	}

	/**
	 * 行タグ
	 *
	 * @param table		テーブル
	 * @param key		キーの列
	 * @param values	値（キーの列と同じ並び）
	 * @return	タグ。値が1つでも無ければ null
	 */
	public static String rowTag (String dbName, ITable table, List<Column> key, List<Object> values) {

		if (key.isEmpty() || key.size() != values.size()) {
			return null;
		}

		StringBuilder names = new StringBuilder();
		StringBuilder texts = new StringBuilder();

		for (int i = 0; i < key.size(); i++) {

			Object value = values.get(i);

			if (value == null) {
				return null;
			}

			if (i > 0) {
				names.append(VALUE_SEPARATOR);
				texts.append(VALUE_SEPARATOR);
			}

			names.append(key.get(i).name());
			texts.append(normalize(value));

		}

		return dbName + "/" + table.name() + SEPARATOR + names + SEPARATOR + texts;

	}

	/**
	 * 値をそろえる
	 *
	 * @param value	値
	 * @return	文字
	 */
	static String normalize (Object value) {

		if (value == null) {
			return "";
		}

		if (value instanceof Number number) {

			// 1 と 1L と "1" を同じにする
			if (number instanceof Byte || number instanceof Short
				|| number instanceof Integer || number instanceof Long) {
				return String.valueOf(number.longValue());
			}

			return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();

		}

		if (value instanceof Boolean bool) {
			return bool ? "1" : "0";
		}

		/*
		 * 日時は文字にすると書式が揃わない。
		 * DB が返す Timestamp は "2024-01-01 10:00:00.0"、
		 * アプリが書く Date は "Mon Jan 01 ..." になり、<b>同じ瞬間なのに別のタグになる</b>。
		 */
		if (value instanceof java.util.Date date) {
			return String.valueOf(date.getTime());
		}

		// byte[] は toString() が identity hash になり、毎回別のタグになる
		if (value instanceof byte[] bytes) {
			return java.util.HexFormat.of().formatHex(bytes);
		}

		// Data の toString() はキー名と型だけの要約。中身が違っても同じ文字列になる
		if (value instanceof io.jimble.util.data.Data data) {
			return data.getJsonString();
		}

		String text = String.valueOf(value);

		/*
		 * 数字だけの文字列は数値と同じにそろえる。
		 * DB が bigint を返し、更新側が "1" を書いた、という食い違いを吸収する。
		 */
		if (text.matches("-?\\d{1,18}")) {
			return String.valueOf(Long.parseLong(text));
		}

		return text.toLowerCase(Locale.ROOT);

	}

	// endregion

	// region SELECT 側

	/**
	 * この SELECT が依存しているものを求める（要件 F-D-28）
	 *
	 * @param dbName	データベース名
	 * @param builder	SELECT
	 * @param rows		結果
	 * @return	タグ。キャッシュできないなら空
	 */
	public static Set<String> of (String dbName, SelectBuilder builder, List<Data> rows) {

		Set<String> tags = new LinkedHashSet<>();

		List<ITable> tables = builder.tableList();

		if (tables.isEmpty()) {
			// FROM が読めない（自由 SQL など）。何にも紐づけられないのでキャッシュしない
			return Set.of();
		}

		List<Table> targets = new ArrayList<>();

		for (ITable table : tables) {

			if (!(table instanceof Table target)) {
				// 仮テーブル・サブクエリ。中身が分からないのでキャッシュしない
				return Set.of();
			}

			targets.add(target);

		}

		WhereTerms where = readTerms(builder.whereList());

		WhereTerms on = new WhereTerms();
		if (builder.from() != null) {
			builder.from().onTerms(on);
		}

		Set<String> pinned = where.hasOr() ? Set.of() : pinnedTables(targets, where, on);

		/*
		 * 件数を絞っていると、<b>返る行そのものが入れ替わる</b>。
		 * ORDER BY name LIMIT 1 は、別の行の名前が変わるだけで結果が変わるので、
		 * 「1行に決まっている」とは言えない。
		 */
		boolean limited = builder.hasRowLimit();

		for (Table target : targets) {

			// このテーブルに触っている、という印は必ず付ける
			tags.add(readTag(dbName, target));

			List<List<Column>> keys = target.getKeyList();

			if (keys.isEmpty()) {
				// キーが分からないテーブル。安全側に倒す
				tags.add(rowsTag(dbName, target));
				continue;
			}

			boolean complete = addRowTags(dbName, target, keys, rows, tags);

			if (!complete || rows.isEmpty() || limited || !pinned.contains(target.name())) {
				tags.add(rowsTag(dbName, target));
			}

		}

		return tags;

	}

	/**
	 * WHERE を読む
	 *
	 * <p>
	 * <b>いちばん外側の OR も見る。</b>
	 * {@code where(a.eq(1), Dsl.or(b.eq(2)))} は
	 * {@code (a = 1) OR (b = 2)} になるので、
	 * a = 1 に絞り込めているとは言えない。
	 * </p>
	 *
	 * @param whereList	WHERE
	 * @return	読めた条件
	 */
	private static WhereTerms readTerms (List<IWhere> whereList) {

		WhereTerms terms = new WhereTerms();

		for (IWhere condition : whereList) {

			if ("OR".equalsIgnoreCase(String.valueOf(condition.logicalOperator()))) {
				terms.markOr();
			}

			condition.terms(terms);

		}

		return terms;

	}

	/**
	 * 行が1つに決まっているテーブルを求める
	 *
	 * <p>
	 * <b>決まっていれば、INSERT で行が増えても結果は変わらない。</b>
	 * </p>
	 *
	 * <ol>
	 *   <li>WHERE がキーの列すべてを<b>値で</b>縛っているテーブルは決まっている</li>
	 *   <li>結合条件がキーの列すべてを<b>決まっているテーブルの列</b>と縛っていれば、そこも決まる。
	 *       これを増えなくなるまで繰り返す</li>
	 * </ol>
	 *
	 * <p>
	 * 2つ目で「相手も決まっていること」を見ないと、
	 * {@code customer INNER JOIN customer_detail ON (customer.id = customer_detail.id)}
	 * のような主キー同士の結合で<b>両方とも決まっている扱いになる</b>。
	 * WHERE が無ければどちらも全件なので、それは誤りである。
	 * </p>
	 *
	 * @param targets	テーブル
	 * @param where		WHERE から読めた条件
	 * @param on		結合条件から読めた条件
	 * @return	決まっているテーブルの名前
	 */
	private static Set<String> pinnedTables (List<Table> targets, WhereTerms where, WhereTerms on) {

		Set<String> pinned = new LinkedHashSet<>();

		for (Table target : targets) {
			if (isBoundByValue(target, where.terms())) {
				pinned.add(target.name());
			}
		}

		boolean changed = true;

		while (changed) {

			changed = false;

			for (Table target : targets) {

				if (pinned.contains(target.name())) {
					continue;
				}

				if (isBoundByPinned(target, on.terms(), pinned)) {
					pinned.add(target.name());
					changed = true;
				}

			}

		}

		return pinned;

	}

	/**
	 * どれかのキーが、すべて値で縛られているか
	 *
	 * @param table	テーブル
	 * @param terms	条件
	 * @return	縛られていれば true
	 */
	private static boolean isBoundByValue (Table table, List<WhereTerm> terms) {

		for (List<Column> key : table.getKeyList()) {

			boolean all = true;

			for (Column column : key) {
				if (!hasValueFor(table, column, terms)) {
					all = false;
					break;
				}
			}

			if (all && !key.isEmpty()) {
				return true;
			}

		}

		return false;

	}

	/**
	 * この列が値で縛られているか
	 *
	 * <p>
	 * <b>値であることを確かめる。</b>{@code IN (サブクエリ)} や
	 * {@code = 他テーブルの列} は、行を1つに決めない。
	 * </p>
	 *
	 * @param table		テーブル
	 * @param column	列
	 * @param terms		条件
	 * @return	縛られていれば true
	 */
	private static boolean hasValueFor (ITable table, Column column, List<WhereTerm> terms) {

		for (WhereTerm term : terms) {

			if (!matches(table, column, term.column())) {
				continue;
			}

			if (term.isEq() && isLiteral(term.value())) {
				return true;
			}

			if (term.isIn() && term.value() instanceof Collection<?> collection && !collection.isEmpty()) {

				boolean all = true;

				for (Object value : collection) {
					if (!isLiteral(value)) {
						all = false;
						break;
					}
				}

				if (all) {
					return true;
				}

			}

		}

		return false;

	}

	/**
	 * どれかのキーが、決まっているテーブルの列と縛られているか
	 *
	 * @param table		テーブル
	 * @param terms		結合条件
	 * @param pinned	決まっているテーブル
	 * @return	縛られていれば true
	 */
	private static boolean isBoundByPinned (Table table, List<WhereTerm> terms, Set<String> pinned) {

		for (List<Column> key : table.getKeyList()) {

			if (key.isEmpty()) {
				continue;
			}

			boolean all = true;

			for (Column column : key) {
				if (!hasPinnedFor(table, column, terms, pinned)) {
					all = false;
					break;
				}
			}

			if (all) {
				return true;
			}

		}

		return false;

	}

	/**
	 * この列が、決まっているテーブルの列と縛られているか
	 *
	 * @param table		テーブル
	 * @param column	列
	 * @param terms		結合条件
	 * @param pinned	決まっているテーブル
	 * @return	縛られていれば true
	 */
	private static boolean hasPinnedFor (ITable table, Column column, List<WhereTerm> terms, Set<String> pinned) {

		for (WhereTerm term : terms) {

			if (!term.isEq()) {
				continue;
			}

			if (matches(table, column, term.column())
				&& term.value() instanceof IColumn other && pinned.contains(other.table().name())) {
				return true;
			}

			if (matches(table, column, term.value())
				&& term.column() != null && pinned.contains(term.column().table().name())) {
				return true;
			}

		}

		return false;

	}

	/**
	 * 値か（列でもサブクエリでも DSL でもないか）
	 *
	 * @param value	値
	 * @return	値なら true
	 */
	private static boolean isLiteral (Object value) {

		return value != null
			&& !(value instanceof IColumn)
			&& !(value instanceof ISelect)
			&& !(value instanceof IDsl)
			&& !(value instanceof IBuilder)
			&& !(value instanceof IWhere);

	}

	/**
	 * 結果の行から行タグを作る
	 *
	 * @param dbName	データベース名
	 * @param table		テーブル
	 * @param keys		キー
	 * @param rows		結果
	 * @param tags		足す先
	 * @return	すべての行ですべてのキーを作れたら true
	 */
	private static boolean addRowTags (
		String dbName, Table table, List<List<Column>> keys, List<Data> rows, Set<String> tags) {

		boolean complete = true;

		for (Data row : rows) {

			Data values = row.getData(table.name());

			if (values == null) {
				/*
				 * LEFT JOIN で相手が居ない行。
				 * その行についてはキーを作れないが、
				 * 「居ないこと」に依存しているのでテーブルタグに倒す。
				 */
				complete = false;
				continue;
			}

			for (List<Column> key : keys) {

				List<Object> keyValues = new ArrayList<>();

				for (Column column : key) {
					keyValues.add(values.containsKey(column.name()) ? values.get(column.name()) : null);
				}

				String tag = rowTag(dbName, table, key, keyValues);

				if (tag == null) {
					// キーの列を SELECT していない（または NULL）
					complete = false;
					continue;
				}

				tags.add(tag);

			}

		}

		return complete;

	}

	/**
	 * 同じ列か
	 *
	 * @param table		テーブル
	 * @param column	列
	 * @param other		比べる相手
	 * @return	同じなら true
	 */
	private static boolean matches (ITable table, Column column, Object other) {

		if (!(other instanceof IColumn target)) {
			return false;
		}

		return table.name().equals(target.table().name()) && column.name().equals(target.name());

	}

	// endregion

	// region 更新側

	/**
	 * この UPDATE で消すもの（要件 F-D-28）
	 *
	 * @param builder	UPDATE
	 * @return	タグ
	 */
	public static Set<String> of (String dbName, UpdateBuilder builder) {

		return affected(dbName, builder.table(), builder.whereList());

	}

	/**
	 * この DELETE で消すもの（要件 F-D-28）
	 *
	 * @param builder	DELETE
	 * @return	タグ
	 */
	public static Set<String> of (String dbName, DeleteBuilder builder) {

		return affected(dbName, builder.table(), builder.whereList());

	}

	/**
	 * この INSERT で消すもの（要件 F-D-28）
	 *
	 * <p>
	 * <b>行が増えるので、そのテーブルに触るキャッシュは全部消す。</b>
	 * 増えた行がどの一覧に入るかは、入れる前には分からない。
	 * </p>
	 *
	 * @param builder	INSERT
	 * @return	タグ
	 */
	public static Set<String> of (String dbName, InsertBuilder builder) {

		ITable table = builder.getTable();

		if (table == null) {
			return Set.of();
		}

		/*
		 * <b>ON DUPLICATE KEY UPDATE は既存の行を書き換える。</b>
		 * どの行に当たるかは入れてみるまで分からないので、
		 * そのテーブルに触るキャッシュを全部消す。
		 * INSERT ... SELECT も、読み元が分からないので同じ扱いにする。
		 */
		if (builder.isUpsert() || builder.hasSelect()) {
			return Set.of(rowsTag(dbName, table), readTag(dbName, table));
		}

		/*
		 * <b>行が増えるので一覧だけ消す。</b>
		 * 主キー・一意キーで1行に絞ってあるキャッシュは、
		 * 増えた行がそこに入りようがないので消さなくてよい。
		 */
		return Set.of(rowsTag(dbName, table));

	}

	/**
	 * このバッチで消すもの（要件 F-D-28）
	 *
	 * <p>
	 * <b>1つでも読めなければ全部読めなかったことにする。</b>
	 * バッチは同じ SQL の繰り返しなので、
	 * 1つが「絞れない」なら残りも絞れない。
	 * </p>
	 *
	 * @param builderList	ビルダー
	 * @return	タグ。読めなければ null（全部消す）
	 */
	public static Set<String> of (String dbName, List<? extends IBuilder> builderList) {

		Set<String> tags = new LinkedHashSet<>();

		for (IBuilder builder : builderList) {

			Set<String> one = switch (builder) {
				case UpdateBuilder update -> of(dbName, update);
				case DeleteBuilder delete -> of(dbName, delete);
				case InsertBuilder insert -> of(dbName, insert);
				default -> null;
			};

			if (one == null) {
				return null;
			}

			tags.addAll(one);

		}

		return tags;

	}

	/**
	 * 影響する行を求める
	 *
	 * @param table		テーブル
	 * @param whereList	WHERE
	 * @return	タグ
	 */
	private static Set<String> affected (String dbName, ITable table, List<IWhere> whereList) {

		if (!(table instanceof Table target)) {
			return table == null ? Set.of() : Set.of(rowsTag(dbName, table), readTag(dbName, table));
		}

		WhereTerms terms = readTerms(whereList);

		if (terms.hasOr()) {
			// OR が混ざっていたら絞り込めていない
			return Set.of(rowsTag(dbName, target), readTag(dbName, target));
		}

		Set<String> tags = new LinkedHashSet<>();

		for (List<Column> key : target.getKeyList()) {

			Set<String> rows = rowTagsOf(dbName, target, key, terms.terms());

			if (rows != null) {
				tags.addAll(rows);
			}

		}

		/*
		 * どのキーでも絞れなかった。
		 * <b>どの行に当たるか分からないので、そのテーブルに触るものを全部消す。</b>
		 */
		if (tags.isEmpty()) {
			return Set.of(rowsTag(dbName, target), readTag(dbName, target));
		}

		/*
		 * 絞れていても一覧は消す。
		 * <b>条件に使っている列が変わると、一覧に入る行が変わる</b>ためである
		 * （{@code WHERE shop_id = 1} の一覧に、shop_id を変えた行が出入りする）。
		 */
		tags.add(rowsTag(dbName, target));

		return tags;

	}

	/**
	 * キーの等値・IN から行タグを作る
	 *
	 * @param table	テーブル
	 * @param key	キー
	 * @param terms	条件
	 * @return	タグ。このキーで絞れていなければ null
	 */
	private static Set<String> rowTagsOf (String dbName, Table table, List<Column> key, List<WhereTerm> terms) {

		List<List<Object>> valueLists = new ArrayList<>();

		for (Column column : key) {

			List<Object> values = valuesOf(table, column, terms);

			if (values == null || values.isEmpty()) {
				return null;
			}

			valueLists.add(values);

		}

		/*
		 * 複合キーで IN が2つ以上あると組み合わせが増える。
		 * 増やしても正しいが、数が読めないので<b>単純な形だけ扱う</b>。
		 * それ以外は絞れなかった扱い（テーブルごと消す）にする。
		 */
		int combinations = 1;
		for (List<Object> values : valueLists) {
			combinations *= values.size();
			if (combinations > 100) {
				return null;
			}
		}

		Set<String> tags = new LinkedHashSet<>();

		buildTags(dbName, table, key, valueLists, 0, new ArrayList<>(), tags);

		return tags.isEmpty() ? null : tags;

	}

	/**
	 * 組み合わせを展開する
	 *
	 * @param table			テーブル
	 * @param key			キー
	 * @param valueLists	列ごとの値
	 * @param index			いま見ている列
	 * @param current		組み立て中
	 * @param tags			足す先
	 */
	private static void buildTags (
		String dbName
		, Table table
		, List<Column> key
		, List<List<Object>> valueLists
		, int index
		, List<Object> current
		, Set<String> tags
	) {

		if (index == key.size()) {

			String tag = rowTag(dbName, table, key, current);

			if (tag != null) {
				tags.add(tag);
			}

			return;

		}

		for (Object value : valueLists.get(index)) {

			current.add(value);
			buildTags(dbName, table, key, valueLists, index + 1, current, tags);
			current.removeLast();

		}

	}

	/**
	 * この列に指定されている値
	 *
	 * @param table		テーブル
	 * @param column	列
	 * @param terms		条件
	 * @return	値。指定されていなければ null
	 */
	private static List<Object> valuesOf (Table table, Column column, List<WhereTerm> terms) {

		for (WhereTerm term : terms) {

			if (!matches(table, column, term.column())) {
				continue;
			}

			if (term.isEq()) {

				// 列同士の比較（結合条件）やサブクエリは値ではない
				if (!isLiteral(term.value())) {
					continue;
				}

				return List.of(term.value());

			}

			if (term.isIn() && term.value() instanceof Collection<?> collection) {

				List<Object> values = new ArrayList<>();

				for (Object value : collection) {
					if (!isLiteral(value)) {
						return null;
					}
					values.add(value);
				}

				return values;

			}

		}

		return null;

	}

	// endregion

}
