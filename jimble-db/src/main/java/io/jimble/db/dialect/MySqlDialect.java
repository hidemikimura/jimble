package io.jimble.db.dialect;

import io.jimble.util.geometry.GeometryUtil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Locale;

/**
 * MySQL / MariaDB（要件 F-D-30）
 *
 * <p>
 * <b>これまでの出力と1文字も変えない。</b>
 * 方言を足したことで既存のアプリの SQL が変わってはいけない。
 * </p>
 */
public final class MySqlDialect implements Dialect {

	/** 製品名 */
	public static final String NAME = "mysql";

	/** インスタンス */
	public static final MySqlDialect INSTANCE = new MySqlDialect();

	/**
	 * コンストラクタ
	 */
	private MySqlDialect () {

	}

	@Override
	public String name () {

		return NAME;

	}

	@Override
	public void identifier (StringBuilder sb, String name) {

		// 名前に ` が入っていたら二重にする（設定から来るテーブル名など）
		sb.append('`').append(name.replace("`", "``")).append('`');

	}

	@Override
	public String function (SqlFunction function) {

		return switch (function) {
			case NOW -> "NOW";
			case RAND -> "RAND";
			case COUNT -> "COUNT";
			case SUM -> "SUM";
			case MIN -> "MIN";
			case MAX -> "MAX";
			case AVG -> "AVG";
			case CEILING -> "CEILING";
			case FLOOR -> "FLOOR";
			case ROUND -> "ROUND";
			case TRUNCATE -> "TRUNCATE";
			case CONCAT -> "CONCAT";
			case IFNULL -> "IFNULL";
			case DATE_FORMAT -> "DATE_FORMAT";
			case ST_GEOM_FROM_TEXT -> "ST_GeomFromText";
			case ST_DISTANCE_SPHERE -> "ST_Distance_Sphere";
			case ST_WITHIN -> "ST_Within";
			case LOWER -> "LOWER";
			case UPPER -> "UPPER";
			case TRIM -> "TRIM";
			case LTRIM -> "LTRIM";
			case RTRIM -> "RTRIM";
			// MySQL の LENGTH はバイト数。文字数は CHAR_LENGTH
			case CHAR_LENGTH -> "CHAR_LENGTH";
			case BYTE_LENGTH -> "LENGTH";
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

	@Override
	public void concat (StringBuilder sb, List<Runnable> values) {

		sb.append("CONCAT(");

		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(",");
			}
			values.get(i).run();
		}

		sb.append(")");

	}

	@Override
	public void dateFormat (StringBuilder sb, Runnable value, String format) {

		sb.append("DATE_FORMAT(");
		value.run();
		sb.append(", '").append(format.replace("'", "''")).append("')");

	}

	@Override
	public void datePart (StringBuilder sb, DatePart part, Runnable value) {

		sb.append(switch (part) {
			case YEAR -> "YEAR";
			case MONTH -> "MONTH";
			case DAY -> "DAYOFMONTH";
			case HOUR -> "HOUR";
			case MINUTE -> "MINUTE";
			case SECOND -> "SECOND";
			case QUARTER -> "QUARTER";
			case DAY_OF_WEEK -> "DAYOFWEEK";
			case DAY_OF_YEAR -> "DAYOFYEAR";
			// WEEK は既定が ISO ではない。WEEKOFYEAR が ISO
			case WEEK -> "WEEKOFYEAR";
		});

		sb.append('(');
		value.run();
		sb.append(')');

	}

	@Override
	public void dateAdd (StringBuilder sb, Runnable value, DateUnit unit, boolean subtract) {

		sb.append(subtract ? "DATE_SUB(" : "DATE_ADD(");
		value.run();
		sb.append(", INTERVAL ? ").append(unit.name()).append(')');

	}

	@Override
	public void dateDiffDays (StringBuilder sb, Runnable from, Runnable to) {

		sb.append("DATEDIFF(");
		from.run();
		sb.append(", ");
		to.run();
		sb.append(')');

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@code TIMESTAMPDIFF(SECOND, to, from)} は<b>引数の並びが逆</b>になり、
	 * 書き出す順とバインドする順がずれる（両方が {@code ?} のとき
	 * 値が入れ替わり、<b>符号が反転した秒数</b>が黙って返る）。
	 * 引き算で書けば並びが変わらない。
	 * </p>
	 */
	@Override
	public void dateDiffSeconds (StringBuilder sb, Runnable from, Runnable to) {

		sb.append("(UNIX_TIMESTAMP(");
		from.run();
		sb.append(") - UNIX_TIMESTAMP(");
		to.run();
		sb.append("))");

	}

	@Override
	public void toDate (StringBuilder sb, Runnable value) {

		sb.append("DATE(");
		value.run();
		sb.append(')');

	}

	@Override
	public String currentDate () {

		return "CURDATE()";

	}

	@Override
	public String currentTime () {

		return "CURTIME()";

	}

	@Override
	public void unixTimestamp (StringBuilder sb, Runnable value) {

		sb.append("UNIX_TIMESTAMP(");
		value.run();
		sb.append(')');

	}

	@Override
	public void fromUnixTime (StringBuilder sb, Runnable value) {

		sb.append("FROM_UNIXTIME(");
		value.run();
		sb.append(')');

	}

	@Override
	public void locate (StringBuilder sb, Runnable needle, Runnable haystack) {

		sb.append("LOCATE(");
		needle.run();
		sb.append(", ");
		haystack.run();
		sb.append(')');

	}

	@Override
	public void ifThenElse (StringBuilder sb, Runnable condition, Runnable whenTrue, Runnable whenFalse) {

		sb.append("IF(");
		condition.run();
		sb.append(", ");
		whenTrue.run();
		sb.append(", ");
		whenFalse.run();
		sb.append(')');

	}

	@Override
	public void cast (StringBuilder sb, Runnable value, CastType type, int precision, int scale) {

		sb.append("CAST(");
		value.run();
		sb.append(" AS ");

		sb.append(switch (type) {
			case STRING -> "CHAR";
			// MySQL に boolean 型は無い。tinyint と同じ 0/1 になる
			case INT, BIGINT, BOOLEAN -> "SIGNED";
			case DECIMAL -> "DECIMAL(%d,%d)".formatted(precision, scale);
			case DATE -> "DATE";
			case DATETIME -> "DATETIME";
			case JSON -> "JSON";
		});

		sb.append(')');

	}

	@Override
	public void groupConcat (StringBuilder sb, Runnable value, String separator, boolean distinct) {

		sb.append("GROUP_CONCAT(");

		if (distinct) {
			sb.append("DISTINCT ");
		}

		value.run();
		sb.append(" SEPARATOR '").append(separator.replace("'", "''")).append("')");

	}

	@Override
	public void regexp (StringBuilder sb, Runnable value, boolean ignoreCase) {

		/*
		 * MySQL の REGEXP は<b>照合順序に従う</b>。
		 * jimble の既定は utf8mb4_bin（大文字小文字を区別する）なので、
		 * 無視したいときは明示的に畳む。
		 */
		if (ignoreCase) {
			sb.append("LOWER(");
			value.run();
			sb.append(") REGEXP LOWER(?)");
			return;
		}

		value.run();
		sb.append(" REGEXP ?");

	}

	@Override
	public void round (StringBuilder sb, Runnable value, int digits) {

		sb.append("ROUND(");
		value.run();
		sb.append(", ").append(digits).append(')');

	}

	@Override
	public void truncate (StringBuilder sb, Runnable value, int digits) {

		sb.append("TRUNCATE(");
		value.run();
		sb.append(", ").append(digits).append(')');

	}

	@Override
	public void jsonExtract (StringBuilder sb, Runnable value, String path, boolean unquote) {

		if (unquote) {
			sb.append("JSON_UNQUOTE(");
		}

		sb.append("JSON_EXTRACT(");
		value.run();
		sb.append(", '").append(path).append("')");

		if (unquote) {
			sb.append(')');
		}

	}

	@Override
	public void intervalFromNow (StringBuilder sb, String unit, boolean ago) {

		sb.append("CURRENT_TIMESTAMP + INTERVAL ").append(ago ? "- " : "+ ").append("? ").append(unit);

	}

	@Override
	public void fullTextMatch (StringBuilder sb, Runnable columns, String modifier) {

		sb.append("MATCH(");
		columns.run();
		sb.append(") AGAINST(?");

		if (modifier != null && !modifier.isEmpty()) {
			sb.append(' ').append(modifier);
		}

		sb.append(')');

	}

	@Override
	public String insertIgnorePrefix () {

		return "IGNORE ";

	}

	@Override
	public String insertIgnoreSuffix () {

		return "";

	}

	@Override
	public String onDuplicateKeyUpdate (List<String> keyColumns) {

		return " ON DUPLICATE KEY UPDATE ";

	}

	@Override
	public void insertedValue (StringBuilder sb, String table, String column) {

		sb.append("VALUES(");

		if (table != null && !table.isEmpty()) {
			identifier(sb, table);
			sb.append('.');
		}

		identifier(sb, column);
		sb.append(')');

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>{@code tinyint(1)} なので 1 / 0 を渡す。</p>
	 */
	@Override
	public void bindBoolean (PreparedStatement statement, int index, boolean value) throws Exception {

		statement.setObject(index, value ? 1 : 0);

	}

	@Override
	public void bindJson (PreparedStatement statement, int index, String json) throws Exception {

		statement.setObject(index, json);

	}

	@Override
	public long generatedKey (ResultSet resultSet) throws Exception {

		return resultSet.getLong(1);

	}

	@Override
	public String versionSql () {

		return "SELECT VERSION() AS version";

	}

	@Override
	public boolean isUnknownDatabase (String message) {

		return message != null && message.contains("Unknown database");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SqlSyntax sqlSyntax () {

		return SqlSyntax.MYSQL;

	}

	@Override
	public boolean isJsonType (String typeName) {

		return "JSON".equalsIgnoreCase(typeName);

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * MySQL の geometry は VARBINARY として返る。
	 * 型名で見分けないと、<b>ただの BLOB を座標として読もうとする</b>。
	 * </p>
	 */
	@Override
	public boolean isGeometryType (String typeName) {

		return typeName != null && typeName.toUpperCase(Locale.ROOT).contains("GEOM");

	}

	@Override
	public String geometryText (ResultSet resultSet, int index) throws Exception {

		return GeometryUtil.parseMySQLGeometry(resultSet.getBytes(index));

	}

	@Override
	public String tableCommentsSql () {

		return "SELECT TABLE_NAME AS table_name, TABLE_COMMENT AS table_comment"
			+ " FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()";

	}

	@Override
	public String setTableCommentSql (String table, String comment) {

		StringBuilder sb = new StringBuilder("ALTER TABLE ");
		identifier(sb, table);
		sb.append(" COMMENT '").append(comment.replace("'", "''")).append('\'');

		return sb.toString();

	}

	@Override
	public String setLockTimeoutSql (int seconds) {

		return "SET SESSION innodb_lock_wait_timeout = " + seconds;

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>スキーマ名を落とす（{@code ?} 以降のパラメータは残す）。</p>
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

		return base.substring(0, slash) + "" + tail;

	}

}
