package io.jimble.db.sql.definition.table;

import io.jimble.util.annotation.CheckReturnValue;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.util.data.definition.ITable;

import io.jimble.util.data.Data;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.column.TemporaryColumn;
import io.jimble.util.data.definition.ISchema;
import io.jimble.db.internal.sql.query.from.FromQuery;
import io.jimble.db.internal.sql.query.from.IFrom;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.util.log.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * table
 */
public class Table implements ITable, IFrom {

	// region ITable

	/* スキーマ */
	private final ISchema schema;

	/* テーブル名 */
	private final String name;

	/**
	 * コンストラクタ
	 *
	 * @param schema	スキーマ
	 * @param name		テーブル名
	 */
	public Table(ISchema schema, String name) {

		this.schema = schema;
		this.name = name;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISchema schema() {

		return this.schema;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name() {

		return this.name;

	}

	// endregion

	// region IFrom

	// region インデックスのヒント（MySQL。D-265）

	/**
	 * このインデックスを使わせる（{@code FROM t FORCE INDEX (i)}）
	 *
	 * <pre>{@code
	 * SQL.select().from(Orders.instance().forceIndex("orders__created_at")).where(...)
	 * SQL.select().from(Orders.instance()).left(Customer.instance().forceIndex("customer__code")).on(...)
	 * }</pre>
	 *
	 * <p>
	 * <b>最後の手段にする。</b>オプティマイザが選び違えるのは、統計が古い・索引が足りない・条件が索引に合っていない、のどれかが多い。
	 * ヒントを書くと、データが増えて別の索引のほうが速くなっても、ずっとこちらを使い続ける。
	 * <b>PostgreSQL にはヒントが無いので、組み立てたところで {@code DialectException}</b>（黙って外さない）。
	 * </p>
	 *
	 * @param indexNames	インデックスの名前（1つ以上）
	 * @return	FROM や JOIN に渡すもの
	 * @throws io.jimble.db.sql.SqlBuildException	名前が無い・形が違う場合
	 */
	public IFrom forceIndex (String... indexNames) {

		return indexHint(io.jimble.db.dialect.IndexHint.FORCE, indexNames);

	}

	/**
	 * このインデックスの中から選ばせる（{@code USE INDEX (i)}）
	 *
	 * @param indexNames	インデックスの名前（1つ以上）
	 * @return	FROM や JOIN に渡すもの
	 * @throws io.jimble.db.sql.SqlBuildException	名前が無い・形が違う場合
	 */
	public IFrom useIndex (String... indexNames) {

		return indexHint(io.jimble.db.dialect.IndexHint.USE, indexNames);

	}

	/**
	 * このインデックスを使わせない（{@code IGNORE INDEX (i)}）
	 *
	 * @param indexNames	インデックスの名前（1つ以上）
	 * @return	FROM や JOIN に渡すもの
	 * @throws io.jimble.db.sql.SqlBuildException	名前が無い・形が違う場合
	 */
	public IFrom ignoreIndex (String... indexNames) {

		return indexHint(io.jimble.db.dialect.IndexHint.IGNORE, indexNames);

	}

	private IFrom indexHint (io.jimble.db.dialect.IndexHint hint, String... indexNames) {

		if (indexNames == null || indexNames.length == 0) {
			throw new io.jimble.db.sql.SqlBuildException("%s にインデックスの名前がありません: %s".formatted(hint.keyword(), name));
		}

		for (String indexName : indexNames) {
			// 識別子として囲んで書くが、名前に見えないもの（空・空白・括弧・引用符）はここで断る
			if (indexName == null || !indexName.matches("[A-Za-z0-9_$]{1,64}")) {
				throw new io.jimble.db.sql.SqlBuildException("%s のインデックスの名前の形が違います: %s / %s".formatted(hint.keyword(), name, indexName));
			}
		}

		return new io.jimble.db.internal.sql.query.from.IndexHintFrom(this, hint, java.util.List.of(indexNames));

	}

	// endregion

	/**
	 * {@inheritDoc}
	 */
	@Override
	@CheckReturnValue
	public IFrom inner(IFrom from) {

		return new FromQuery(this).inner(from);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@CheckReturnValue
	public IFrom left(IFrom from) {

		return new FromQuery(this).left(from);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@CheckReturnValue
	public IFrom on(IWhere...where) {

		return new FromQuery(this).on(where);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void fromSql (SqlWriter sb) {

		sb.append(' ');
		sb.identifier(name);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		return false;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		return null;

	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<ITable> getTableList() {

		List<ITable> tableList = new ArrayList<>();
		tableList.add(this);
		return tableList;

	}

	// endregion

	// region 空Data取得

	/**
	 * 空Data取得
	 *
	 * @return  空Data
	 */
	public Data emptyData () {

		Data row = new Data();

		Data data = row.getDataOptional(name());
		for (Column column : getColumnList()) {

			if (column.isNullable()) {
				data.put(column.name(), column.defaultValue());
			} else if (column.defaultValue() == null) {
				if (String.class.equals(column.clazz())) {
					data.put(column.name(), "");
				} else if (int.class.equals(column.clazz())) {
					data.put(column.name(), 0);
				} else if (long.class.equals(column.clazz())) {
					data.put(column.name(), 0L);
				} else if (double.class.equals(column.clazz())) {
					data.put(column.name(), 0D);
				} else if (Date.class.equals(column.clazz())) {
					data.put(column.name(), new Date());
				}
			} else {
				data.put(column.name(), column.defaultValue());
			}

		}

		return row;

	}

	// endregion

	// region 列一覧

	/*
	 * 列一覧取得済み判定。
	 * <b>volatile にする</b>（D-296）。鍵の外で読むので、そうしないと別のスレッドから
	 * 「取得済み」だけが先に見えて、詰め終わる前の columnList を返しうる
	 */
	private volatile boolean isGetColumnList = false;

	/* 列一覧 */
	private final List<Column> columnList = new ArrayList<>();

	/* 列一覧取得ロック */
	private final ReentrantLock columnLock = new ReentrantLock();

	/**
	 * 列一覧を取得する
	 *
	 * @return	列一覧
	 */
	public List<Column> getColumnList() {

		if (isGetColumnList) {
			return columnList;
		}

		try {

			columnLock.lock();

			if (isGetColumnList) {
				return columnList;
			}

			getColumnListInner();

		} catch (Exception ex) {

			Log.error(ex);

		} finally {

			columnLock.unlock();

		}

		return columnList;

	}

	/**
	 * 列一覧を宣言する
	 *
	 * <p>
	 * <b>生成コードはこれを override して静的な一覧を返す</b>（D-17）。
	 * override しなかった場合はリフレクションで {@link Column} 型のフィールドを集める。
	 * </p>
	 *
	 * @return	列一覧（宣言しない場合は null）
	 */
	protected List<Column> declareColumns () {

		return null;

	}

	/**
	 * 列一覧を取得する
	 *
	 * <p>
	 * {@link #declareColumns()} を実装していればそれを使う。
	 * していなければリフレクションで集める（手書きのテーブル定義向けの後方互換）。
	 * </p>
	 */
	private void getColumnListInner () {

		List<Column> declared = declareColumns();
		if (declared != null) {
			columnList.addAll(declared);
			isGetColumnList = true;
			return;
		}

		try {

			Field[] fields = getClass().getDeclaredFields();
			for (Field field : fields) {
				if (Column.class.isAssignableFrom(field.getType())) {
					// 非公開クラス・非公開フィールドでも読めるようにする。
					// 移送元はここで IllegalAccessException になり、
					// Log.error に落として空のリストを返していた（原因が分からず追いにくい）
					field.setAccessible(true);
					columnList.add((Column) field.get(this));
				}
			}

		} catch (Exception ex) {

			Log.error(ex);

		}

		if (columnList.isEmpty()) {
			Log.warn("列一覧が空です。declareColumns() を実装してください: " + getClass().getName());
		}

		isGetColumnList = true;

	}

	// endregion

	// region SELECT 句（全列。2.7.5）

	/*
	 * 全列の SELECT 句の字面。<b>テーブルのクラスと方言の組ごとに 1 つ覚える</b>。
	 *
	 * select() で列を並べないと全列を {@code `t`.`c` AS `t__c`} の形で書く。
	 * 2.7.4 までは要求のたびに列ごとに SelectQuery を作って書き出していて、
	 * 主キー 1 行の SELECT を組み立てる CPU の 4 分の 3 がここだった。
	 *
	 * - 覚えるのは declareColumns() で列一覧を宣言したテーブルだけ（生成物）。
	 *   使う前に、覚えたときの列と今の宣言を<b>1 つずつ同一性で比べ</b>、違えば作り直す
	 *   （宣言を毎回作り直すテーブルや、同じクラスで名前の違うテーブルでも、字面は今の列から作ったものになる）
	 * - Column そのもの（と TemporaryColumn）以外の列があれば覚えない。継承した列は selectSql などを
	 *   上書きしているかもしれないので、2.7.4 と同じ道（column.as(...)）で書く
	 * - getColumnList() を一度でも呼んだインスタンスは覚えた字面を使わない。返した一覧は書き換えられるので、
	 *   2.7.4 と同じく、そのインスタンスの一覧から書く
	 * - ClassValue に置くので、アプリのクラスを読み直してもクラスローダーを引き留めない。数はテーブルのクラスの数で止まる
	 */
	private static final ClassValue<Map<io.jimble.db.dialect.Dialect, SelectAll>> SELECT_ALL = new ClassValue<>() {

		@Override
		protected Map<io.jimble.db.dialect.Dialect, SelectAll> computeValue (Class<?> type) {
			return new ConcurrentHashMap<>();
		}

	};

	/**
	 * 覚えた全列の字面
	 *
	 * @param columns	作ったときの列（同一性で比べる）
	 * @param sql		字面
	 */
	private record SelectAll (Column[] columns, String sql) {

		/**
		 * 今の列から作ったものと同じか
		 *
		 * @param current	今の列
		 * @return	同じなら true
		 */
		boolean matches (List<Column> current) {

			if (current.size() != columns.length) {
				return false;
			}

			for (int i = 0; i < columns.length; i++) {
				if (current.get(i) != columns[i]) {
					return false;
				}
			}

			return true;

		}

	}

	/**
	 * 全列を SELECT 句に書く
	 *
	 * <p>
	 * 列ごとに {@code " `t`.`c` AS `t__c`"} を {@code ","} でつないで書く
	 * （{@code SelectBuilder} が {@code select(column.as(column.getJoinSelectName()))} を並べたときと同じ字面）。
	 * 列が無ければ何も書かない。
	 * </p>
	 *
	 * @param sb	書き出し先
	 * @return	1 列でも書いたら true
	 */
	public boolean selectAllSql (SqlWriter sb) {

		List<Column> declared = isGetColumnList ? null : declaredColumnsOrNull();

		if (declared == null || !isPlainColumns(declared)) {
			List<Column> columns = getColumnList();
			writeSelectAll(sb, columns);
			return !columns.isEmpty();
		}

		if (declared.isEmpty()) {
			return false;
		}

		Map<io.jimble.db.dialect.Dialect, SelectAll> byDialect = SELECT_ALL.get(getClass());
		SelectAll cached = byDialect.get(sb.dialect());

		if (cached == null || !cached.matches(declared)) {
			SqlWriter written = new SqlWriter(sb.dialect());
			writeSelectAll(written, declared);
			cached = new SelectAll(declared.toArray(new Column[0]), written.toString());
			byDialect.put(sb.dialect(), cached);
		}

		sb.append(cached.sql());

		return true;

	}

	/**
	 * 全列を SELECT 句に書いたときのパラメータを足す
	 *
	 * <p>
	 * {@link #selectAllSql(SqlWriter)} で書いた列の値（継承して値を持たせた列だけにある）を、書いた順に足す。
	 * 2.7.4 は全列を {@code select(column.as(...))} で並べてから集めていたので、同じ値になる。
	 * Column そのもの（と TemporaryColumn）は値を持たないので、生成物では何もしない。
	 * </p>
	 *
	 * @param out	足す先
	 */
	public void selectAllParameters (List<Object> out) {

		List<Column> declared = isGetColumnList ? null : declaredColumnsOrNull();

		if (declared != null && isPlainColumns(declared)) {
			return;
		}

		for (Column column : getColumnList()) {
			if (column.getClass() != Column.class && column.getClass() != TemporaryColumn.class) {
				column.as(column.getJoinSelectName()).collectParameters(out);
			}
		}

	}

	/**
	 * 宣言した列一覧（宣言していないか、宣言で落ちたら null）
	 *
	 * @return	列一覧
	 */
	private List<Column> declaredColumnsOrNull () {

		try {
			return declareColumns();
		} catch (RuntimeException ex) {
			// 2.7.4 と同じく getColumnList() の道で（ログに出して）読む
			return null;
		}

	}

	/**
	 * どの列も Column そのもの（か TemporaryColumn）か
	 *
	 * @param columns	列一覧
	 * @return	そうなら true
	 */
	private static boolean isPlainColumns (List<Column> columns) {

		for (Column column : columns) {
			if (column == null
				|| (column.getClass() != Column.class && column.getClass() != TemporaryColumn.class)) {
				return false;
			}
		}

		return true;

	}

	/**
	 * 全列を書き出す
	 *
	 * @param sb		書き出し先
	 * @param columns	列一覧
	 */
	private static void writeSelectAll (SqlWriter sb, List<Column> columns) {

		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append(" ");
			Column column = columns.get(i);
			if (column.getClass() == Column.class || column.getClass() == TemporaryColumn.class) {
				column.selectAsJoinNameSql(sb);
			} else {
				// 継承した列は 2.7.4 と同じ道で書く（selectSql / getJoinSelectName の上書きが効くように）
				column.as(column.getJoinSelectName()).selectSql(sb);
			}
		}

	}

	// endregion

	// region キー（要件 F-D-28）

	/**
	 * 一意キーを宣言する
	 *
	 * <p>
	 * <b>生成コードがこれを override する</b>（D-94）。
	 * 1つのキーが複数列なら、その列を並べたリストにする。
	 * </p>
	 *
	 * <p>
	 * override しなかった場合は「一意キーを知らない」ことになり、
	 * SQL 結果のキャッシュは<b>安全側（テーブルごと消す）に倒れる</b>。
	 * </p>
	 *
	 * @return	一意キーの一覧（宣言しない場合は null）
	 */
	protected List<List<Column>> declareUniqueKeys () {

		return null;

	}

	/**
	 * 主キー
	 *
	 * @return	主キーの列（無ければ空）
	 */
	public List<Column> getPrimaryKeyList () {

		List<Column> keys = new ArrayList<>();

		for (Column column : getColumnList()) {
			if (column.isPrimaryKey()) {
				keys.add(column);
			}
		}

		return keys;

	}

	/**
	 * 一意キー（主キーを含まない）
	 *
	 * @return	一意キーの一覧
	 */
	public List<List<Column>> getUniqueKeyList () {

		List<List<Column>> declared = declareUniqueKeys();

		return declared == null ? List.of() : declared;

	}

	/**
	 * 1行を特定できるキー（主キー + 一意キー）
	 *
	 * <p>
	 * <b>SQL 結果のキャッシュはこれを見て「どの行か」を決める</b>（要件 F-D-28）。
	 * </p>
	 *
	 * @return	キーの一覧（1つのキーは1列とは限らない）
	 */
	public List<List<Column>> getKeyList () {

		List<List<Column>> keys = new ArrayList<>();

		List<Column> primary = getPrimaryKeyList();
		if (!primary.isEmpty()) {
			keys.add(primary);
		}

		keys.addAll(getUniqueKeyList());

		return keys;

	}

	// endregion

	// region デフォルトデータ

	/**
	 * デフォルトデータを取得する
	 *
	 * @return	デフォルトデータ
	 */
	public Data defaultData () {

		Data row = new Data();

		for (Column column : getColumnList()) {
			row.putData(column, column.defaultValue());
		}

		return row;

	}

	// endregion


	/**
	 * 同じテーブルか（D-175）
	 *
	 * <p>
	 * <b>スキーマ名とテーブル名が同じなら同じテーブルである。</b>
	 * インスタンスが同じかどうかは見ない——生成物の {@code instance()} は
	 * <b>呼ぶたびに新しいものを返す</b>ので（D-174）、
	 * 同一性で比べると <b>{@code Staff.id.table()} と {@code Staff.instance()} が
	 * 別物になる</b>。「同じテーブルか」を見ているところが、静かに外れる。
	 * </p>
	 *
	 * <p>
	 * <b>仮テーブル（{@code TemporaryTable}）とは等しくならない。</b>
	 * あちらのスキーマは {@code empty} なので、名前が同じでもスキーマ名で分かれる——
	 * <b>名前だけで作ったものと、定義から来たものを混ぜない</b>ためである。
	 * </p>
	 *
	 * @param other	比べる相手
	 * @return	同じテーブルなら true
	 */
	@Override
	public boolean equals (Object other) {

		if (this == other) {
			return true;
		}

		if (!(other instanceof Table that)) {
			return false;
		}

		return java.util.Objects.equals(schemaName(), that.schemaName())
			&& java.util.Objects.equals(name(), that.name());

	}

	@Override
	public int hashCode () {

		return java.util.Objects.hash(schemaName(), name());

	}

	/**
	 * スキーマ名（無ければ null）
	 *
	 * @return	スキーマ名
	 */
	private String schemaName () {

		return schema() == null ? null : schema().name();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {

		return name();

	}

	/**
	 * SQLでテーブル内に含めるための名前を取得する
	 *
	 * @param name	名前
	 * @return	SQL as名
	 */
	public String sqlColumn (String name) {

		return name() + Column.SPLITTER + name;

	}

	/**
	 * カスタムカラム
	 *
	 * @param name	カラム名
	 * @return	カスタムカラム
	 */
	public Column customColumn (String name) {

		return new TemporaryColumn(this, name);

	}

}
