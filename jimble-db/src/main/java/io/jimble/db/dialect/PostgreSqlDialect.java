package io.jimble.db.dialect;

import io.jimble.util.geometry.GeometryUtil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.List;
import java.util.Locale;

/**
 * PostgreSQL（要件 F-D-30）
 *
 * <h2>書けないもの</h2>
 * <p>
 * MySQL の全文検索（{@code MATCH ... AGAINST}）は、PostgreSQL に
 * <b>同じ振る舞いをするものが無い</b>。{@code to_tsvector} に置き換えると
 * 語彙の分割もスコアも変わるので、<b>黙って別物にせず例外にする</b>。
 * </p>
 */
public final class PostgreSqlDialect implements Dialect {

	/** 製品名 */
	public static final String NAME = "postgresql";

	/** インスタンス */
	public static final PostgreSqlDialect INSTANCE = new PostgreSqlDialect();

	/**
	 * コンストラクタ
	 */
	private PostgreSqlDialect () {

	}

	@Override
	public String name () {

		return NAME;

	}

	@Override
	public void identifier (StringBuilder sb, String name) {

		sb.append('"').append(name.replace("\"", "\"\"")).append('"');

	}

	@Override
	public String function (SqlFunction function) {

		return switch (function) {
			case NOW -> "NOW";
			case RAND -> "RANDOM";
			case COUNT -> "COUNT";
			case SUM -> "SUM";
			case MIN -> "MIN";
			case MAX -> "MAX";
			case AVG -> "AVG";
			case CEILING -> "CEILING";
			case FLOOR -> "FLOOR";
			case ROUND -> "ROUND";
			case TRUNCATE -> "TRUNC";
			case CONCAT -> "CONCAT";
			case IFNULL -> "COALESCE";
			case DATE_FORMAT -> "to_char";
			case ST_GEOM_FROM_TEXT -> "ST_GeomFromText";
			case ST_DISTANCE_SPHERE -> "ST_DistanceSphere";
			case ST_WITHIN -> "ST_Within";
			case LOWER -> "LOWER";
			case UPPER -> "UPPER";
			case TRIM -> "TRIM";
			case LTRIM -> "LTRIM";
			case RTRIM -> "RTRIM";
			// PostgreSQL の LENGTH は文字数。バイト数は OCTET_LENGTH
			case CHAR_LENGTH -> "LENGTH";
			case BYTE_LENGTH -> "OCTET_LENGTH";
			case SUBSTRING -> "SUBSTRING";
			case REPLACE -> "REPLACE";
			case LEFT -> "LEFT";
			case RIGHT -> "RIGHT";
			case LPAD -> "LPAD";
			case RPAD -> "RPAD";
			case REVERSE -> "REVERSE";
			case REPEAT -> "REPEAT";
			case CONCAT_WS -> "CONCAT_WS";
			case MD5 -> "MD5";
			case ABS -> "ABS";
			case MOD -> "MOD";
			case POWER -> "POWER";
			case SQRT -> "SQRT";
			case SIGN -> "SIGN";
			case EXP -> "EXP";
			case LN -> "LN";
			case LOG10 -> "LOG10";
			case GREATEST -> "GREATEST";
			case LEAST -> "LEAST";
			case STDDEV -> "STDDEV_SAMP";
			case VARIANCE -> "VAR_SAMP";
			case ROW_NUMBER -> "ROW_NUMBER";
			case RANK -> "RANK";
			case DENSE_RANK -> "DENSE_RANK";
			case NTILE -> "NTILE";
			case LAG -> "LAG";
			case LEAD -> "LEAD";
			case FIRST_VALUE -> "FIRST_VALUE";
			case LAST_VALUE -> "LAST_VALUE";
		};

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>{@code numeric} に寄せてから丸める。</b>
	 * PostgreSQL の {@code round(x, n)} は {@code numeric} にしか無く、
	 * {@code double precision} を渡すと「関数が無い」で落ちる。
	 * </p>
	 */
	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@code concat()} ではなく {@code ||} を使う。
	 * <b>PostgreSQL の {@code concat()} は NULL を空文字として飲み込む</b>ので、
	 * MySQL と結果が変わる。{@code ||} なら MySQL と同じく
	 * 1つでも NULL があれば NULL になる。
	 * </p>
	 */
	@Override
	public void concat (StringBuilder sb, List<Runnable> values) {

		if (values.isEmpty()) {
			sb.append("''");
			return;
		}

		sb.append('(');

		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(" || ");
			}
			values.get(i).run();
		}

		sb.append(')');

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>置き換えない。</b>{@code to_char} は書式の言語が違う
	 * （{@code %Y-%m-%d} と {@code YYYY-MM-DD}）。
	 * 名前だけ置き換えると、例外にならずに違う文字列が返る。
	 * <b>「動くけれど値が違う」がいちばん気づけない。</b>
	 * </p>
	 *
	 * <p>
	 * 日付の書式はアプリ側（Java）で整えるか、
	 * {@code Dsl.freeSql} でその製品の書き方を直接書く。
	 * </p>
	 */
	@Override
	public void dateFormat (StringBuilder sb, Runnable value, String format) {

		throw unsupported("日付の書式（DATE_FORMAT）");

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * PostgreSQL に {@code YEAR(x)} のような関数は無い。{@code EXTRACT} を使う。
	 * {@code EXTRACT} は {@code numeric} を返すので、
	 * <b>MySQL と同じく整数で受け取れるように</b> int に落とす。
	 * </p>
	 */
	@Override
	public void datePart (StringBuilder sb, DatePart part, Runnable value) {

		String name = switch (part) {
			case YEAR -> "YEAR";
			case MONTH -> "MONTH";
			case DAY -> "DAY";
			case HOUR -> "HOUR";
			case MINUTE -> "MINUTE";
			case SECOND -> "SECOND";
			case QUARTER -> "QUARTER";
			case DAY_OF_WEEK -> "DOW";
			case DAY_OF_YEAR -> "DOY";
			case WEEK -> "WEEK";
		};

		sb.append('(');

		/*
		 * EXTRACT(SECOND) は<b>小数秒を含む numeric</b> を返す。
		 * そのまま ::int にすると四捨五入されるので、
		 * 10:00:30.7 が MySQL 30 / PostgreSQL 31、59.5 秒なら 60 になる。
		 */
		if (part == DatePart.SECOND) {
			sb.append("FLOOR(");
		}

		sb.append("EXTRACT(").append(name).append(" FROM ");
		value.run();
		sb.append(')');

		if (part == DatePart.SECOND) {
			sb.append(')');
		}

		/*
		 * 曜日は起点が違う。
		 * MySQL の DAYOFWEEK は日曜が 1、PostgreSQL の DOW は日曜が 0。
		 * <b>ここで揃えないと、同じコードが別の曜日を指す。</b>
		 */
		if (part == DatePart.DAY_OF_WEEK) {
			sb.append(" + 1");
		}

		sb.append(")::int");

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>PostgreSQL の {@code INTERVAL} はリテラルしか取らないので、掛ける形にする。</p>
	 */
	@Override
	public void dateAdd (StringBuilder sb, Runnable value, DateUnit unit, boolean subtract) {

		sb.append('(');
		value.run();
		sb.append(subtract ? " - " : " + ");
		sb.append("(? * INTERVAL '1 ").append(unit.name()).append("'))");

	}

	@Override
	public void dateDiffDays (StringBuilder sb, Runnable from, Runnable to) {

		sb.append("((");
		from.run();
		sb.append(")::date - (");
		to.run();
		sb.append(")::date)");

	}

	@Override
	public void dateDiffSeconds (StringBuilder sb, Runnable from, Runnable to) {

		sb.append("(EXTRACT(EPOCH FROM ((");
		from.run();
		sb.append(")::timestamp - (");
		to.run();
		sb.append(")::timestamp)))::bigint");

	}

	@Override
	public void toDate (StringBuilder sb, Runnable value) {

		sb.append('(');
		value.run();
		sb.append(")::date");

	}

	@Override
	public String currentDate () {

		return "CURRENT_DATE";

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@code CURRENT_TIME} は<b>時差付きの {@code time with time zone}</b> なので、
	 * MySQL の {@code CURTIME()} と型が揃わない。{@code LOCALTIME} を使う。
	 * </p>
	 */
	@Override
	public String currentTime () {

		return "LOCALTIME";

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>接続のタイムゾーンで読む。</b>MySQL の {@code UNIX_TIMESTAMP} は
	 * DATETIME をセッションのタイムゾーンとして解釈するが、
	 * PostgreSQL の {@code timestamp} は素で UTC 扱いになる。
	 * {@code AT TIME ZONE} を挟まないと、UTC でない環境で
	 * <b>時差のぶんだけ黙ってずれる</b>。
	 * </p>
	 */
	@Override
	public void unixTimestamp (StringBuilder sb, Runnable value) {

		sb.append("(EXTRACT(EPOCH FROM ((");
		value.run();
		sb.append(")::timestamp AT TIME ZONE current_setting('TimeZone'))))::bigint");

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>{@link #unixTimestamp} の逆。接続のタイムゾーンで返す。</p>
	 */
	@Override
	public void fromUnixTime (StringBuilder sb, Runnable value) {

		sb.append("(TO_TIMESTAMP(");
		value.run();
		sb.append(") AT TIME ZONE current_setting('TimeZone'))");

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@code STRPOS(対象, 探すもの)} ではなく
	 * <b>{@code POSITION(探すもの IN 対象)}</b> を使う。
	 * 意味は同じだが、{@code STRPOS} は引数の並びが MySQL の {@code LOCATE} と逆で、
	 * <b>書き出す順とバインドする順がずれる</b>
	 * （両方が {@code ?} のとき、値が入れ替わって黙って 0 が返る）。
	 * </p>
	 */
	@Override
	public void locate (StringBuilder sb, Runnable needle, Runnable haystack) {

		sb.append("POSITION(");
		needle.run();
		sb.append(" IN ");
		haystack.run();
		sb.append(')');

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>PostgreSQL に {@code IF} は無い。</p>
	 */
	@Override
	public void ifThenElse (StringBuilder sb, Runnable condition, Runnable whenTrue, Runnable whenFalse) {

		sb.append("(CASE WHEN ");
		condition.run();
		sb.append(" THEN ");
		whenTrue.run();
		sb.append(" ELSE ");
		whenFalse.run();
		sb.append(" END)");

	}

	@Override
	public void cast (StringBuilder sb, Runnable value, CastType type, int precision, int scale) {

		sb.append("CAST(");
		value.run();
		sb.append(" AS ");

		sb.append(switch (type) {
			case STRING -> "text";
			case INT -> "integer";
			case BIGINT -> "bigint";
			case DECIMAL -> "numeric(%d,%d)".formatted(precision, scale);
			case DATE -> "date";
			case DATETIME -> "timestamp";
			case BOOLEAN -> "boolean";
			case JSON -> "jsonb";
		});

		sb.append(')');

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@code STRING_AGG} は<b>文字列でないと受け取らない</b>
	 * （bigint の列を渡すと「関数が無い」で落ちる）ので、こちらで {@code text} にする。
	 * </p>
	 */
	@Override
	public void groupConcat (StringBuilder sb, Runnable value, String separator, boolean distinct) {

		sb.append("STRING_AGG(");

		if (distinct) {
			sb.append("DISTINCT ");
		}

		sb.append('(');
		value.run();
		sb.append(")::text, '").append(separator.replace("'", "''")).append("')");

	}

	@Override
	public void regexp (StringBuilder sb, Runnable value, boolean ignoreCase) {

		value.run();
		sb.append(ignoreCase ? " ~* ?" : " ~ ?");

	}

	@Override
	public void round (StringBuilder sb, Runnable value, int digits) {

		sb.append("ROUND((");
		value.run();
		sb.append(")::numeric, ").append(digits).append(')');

	}

	@Override
	public void truncate (StringBuilder sb, Runnable value, int digits) {

		sb.append("TRUNC((");
		value.run();
		sb.append(")::numeric, ").append(digits).append(')');

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * MySQL の {@code '$.a.b'} を PostgreSQL の {@code -> 'a' -> 'b'} に読み替える。
	 * 配列の添字（{@code $.a[0]}）や {@code $[*]} のようなものは
	 * <b>読み替えると意味が変わる</b>ので例外にする。
	 * </p>
	 */
	@Override
	public void jsonExtract (StringBuilder sb, Runnable value, String path, boolean unquote) {

		List<String> keys = jsonPathKeys(path);

		sb.append('(');
		value.run();

		for (int i = 0; i < keys.size(); i++) {

			// 最後だけ ->> にすると引用符が外れる
			boolean last = i == keys.size() - 1;

			sb.append(unquote && last ? " ->> '" : " -> '").append(keys.get(i).replace("'", "''")).append('\'');

		}

		sb.append(')');

	}

	/**
	 * JSON のパスを分解する
	 *
	 * @param path	パス（{@code $.a.b}）
	 * @return	キー
	 * @throws DialectException	読み替えられない形の場合
	 */
	private List<String> jsonPathKeys (String path) {

		if (path == null || !path.startsWith("$.")) {
			throw unsupported("JSON のパス " + path);
		}

		String body = path.substring(2);

		/*
		 * 配列（[）とワイルドカード（*）は読み替えられない。
		 * 引用符つきのキー（$."a.b"）も、ここで . で切ると別のキーになり
		 * <b>例外にならずに NULL が返る</b>ので弾く。
		 */
		if (body.isEmpty() || body.contains("[") || body.contains("*") || body.contains("\"")) {
			throw unsupported("JSON のパス " + path);
		}

		return List.of(body.split("\\."));

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * PostgreSQL の {@code INTERVAL} はリテラルしか取らないので、
	 * <b>秒数を掛ける形</b>にする（{@code ? * INTERVAL '1 SECOND'}）。
	 * </p>
	 */
	@Override
	public void intervalFromNow (StringBuilder sb, String unit, boolean ago) {

		sb.append("CURRENT_TIMESTAMP ").append(ago ? "- " : "+ ")
			.append("(? * INTERVAL '1 ").append(unit).append("')");

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>置き換えない。</b>{@code to_tsvector} に寄せると、
	 * 語彙の分割・ストップワード・スコアが変わる。
	 * 「動くけれど検索結果が違う」がいちばん気づけない。
	 * </p>
	 */
	@Override
	public void fullTextMatch (StringBuilder sb, Runnable columns, String modifier) {

		throw unsupported("全文検索（MATCH ... AGAINST）");

	}

	@Override
	public String insertIgnorePrefix () {

		return "";

	}

	@Override
	public String insertIgnoreSuffix () {

		return " ON CONFLICT DO NOTHING";

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>どのキーで重複を見るかを書く必要がある。</b>
	 * MySQL は「どれかの一意キー」で勝手に判断するが、PostgreSQL は書かせる。
	 * </p>
	 */
	@Override
	public String onDuplicateKeyUpdate (List<String> keyColumns) {

		if (keyColumns == null || keyColumns.isEmpty()) {
			throw unsupported("重複キーの指定が無い upsert（PostgreSQL は ON CONFLICT に列が要ります）");
		}

		StringBuilder sb = new StringBuilder(" ON CONFLICT (");

		for (int i = 0; i < keyColumns.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			identifier(sb, keyColumns.get(i));
		}

		sb.append(") DO UPDATE SET ");

		return sb.toString();

	}

	@Override
	public void insertedValue (StringBuilder sb, String table, String column) {

		sb.append("EXCLUDED.");
		identifier(sb, column);

	}

	@Override
	public void bindBoolean (PreparedStatement statement, int index, boolean value) throws Exception {

		statement.setBoolean(index, value);

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@code json} / {@code jsonb} の列に文字列をそのまま渡すと型が合わない。
	 * {@link Types#OTHER} で渡すとドライバが変換する。
	 * </p>
	 */
	@Override
	public void bindJson (PreparedStatement statement, int index, String json) throws Exception {

		statement.setObject(index, json, Types.OTHER);

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>行を丸ごと返すので、1列目が採番列とは限らない。</b>
	 * {@code id} があればそれを、無ければ1列目を使う。
	 * </p>
	 */
	@Override
	public long generatedKey (ResultSet resultSet) throws Exception {

		/*
		 * MySQL の getGeneratedKeys は<b>自動採番の1列だけ</b>を返すが、
		 * PostgreSQL は RETURNING * 相当で<b>行の全列</b>を返す。
		 * そのため1列目が文字列の主キーということが普通に起きる
		 * （migration_code の version など）。数として読める列を探し、
		 * 無ければ 0 を返す（採番していないのだから、採番値は無い）。
		 */
		try {
			return resultSet.getLong("id");
		} catch (Exception ignore) {
			// id という列が無い
		}

		/*
		 * ここから先は<b>当てにいかない</b>。
		 *
		 * 「最初の数の列」を返すと、たとえば db_cache（cache_key, content,
		 * group_key, content_type, content_length）で content_length が
		 * 採番値として返る。<b>採番していないのに、それらしい数が返る</b>のが
		 * いちばん困る。列が1つだけのときは MySQL と同じ形なので、それだけ返す。
		 */
		ResultSetMetaData meta = resultSet.getMetaData();

		if (meta.getColumnCount() == 1) {
			switch (meta.getColumnType(1)) {
				case Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT -> {
					return resultSet.getLong(1);
				}
				default -> {
					// 数ではない列は採番値ではない
				}
			}
		}

		return 0;

	}

	@Override
	public String versionSql () {

		return "SELECT VERSION() AS version";

	}

	@Override
	public boolean isUnknownDatabase (String message) {

		if (message == null) {
			return false;
		}

		// database "jimble_test" does not exist / データベース"x"は存在しません
		return message.contains("database")
			&& (message.contains("does not exist") || message.contains("存在しません"))
			|| message.contains("データベース") && message.contains("存在しません");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SqlSyntax sqlSyntax () {

		return SqlSyntax.POSTGRESQL;

	}

	@Override
	public boolean isJsonType (String typeName) {

		return "json".equalsIgnoreCase(typeName) || "jsonb".equalsIgnoreCase(typeName);

	}

	@Override
	public boolean isGeometryType (String typeName) {

		return typeName != null && typeName.toLowerCase(Locale.ROOT).contains("geom");

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>PostGIS は16進の EWKB 文字列で返る。</p>
	 */
	@Override
	public String geometryText (ResultSet resultSet, int index) throws Exception {

		return GeometryUtil.parsePostGISGeometry(resultSet.getString(index));

	}

	@Override
	public String tableCommentsSql () {

		return """
			SELECT c.relname AS table_name, obj_description(c.oid, 'pg_class') AS table_comment
			FROM pg_class c
			JOIN pg_namespace n ON n.oid = c.relnamespace
			WHERE c.relkind = 'r' AND n.nspname = current_schema()
			""";

	}

	@Override
	public String setTableCommentSql (String table, String comment) {

		StringBuilder sb = new StringBuilder("COMMENT ON TABLE ");
		identifier(sb, table);
		sb.append(" IS '").append(comment.replace("'", "''")).append('\'');

		return sb.toString();

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * PostgreSQL の {@code lock_timeout} はミリ秒。
	 * </p>
	 *
	 * <p>
	 * <b>{@code SET LOCAL} にする。</b>素の {@code SET} はセッションに残るので、
	 * その接続がプールに戻ったあと<b>ふつうの業務にも上限が効き続ける</b>。
	 * PostgreSQL の {@code lock_timeout} は MySQL の
	 * {@code innodb_lock_wait_timeout} と違って行ロック以外にも効くので、
	 * 残ると影響が広い。呼び出し側（マイグレーション）は
	 * トランザクションの中で呼ぶ。
	 * </p>
	 */
	@Override
	public String setLockTimeoutSql (int seconds) {

		return "SET LOCAL lock_timeout = " + (long) seconds * 1000;

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>PostgreSQL は「どこにも繋がない」ができない。</b>
	 * 必ずある {@code postgres} データベースに差し替える。
	 * </p>
	 */
	@Override
	public String maintenanceUrl (String url) {

		int query = url.indexOf('?');
		String base = query > 0 ? url.substring(0, query) : url;
		String tail = query > 0 ? url.substring(query) : "";

		/*
		 * ホストの前の // を数えないように、そこから先だけを見る。
		 * jdbc:mysql://host:3306 のように<b>データベース名が無い</b>URL では
		 * 落とすものが無いので、そのまま返す。
		 */
		int host = base.indexOf("//");
		int slash = host < 0 ? base.lastIndexOf('/') : base.indexOf('/', host + 2);

		if (slash < 0) {
			return url;
		}

		return base.substring(0, slash) + "/postgres" + tail;

	}

}
