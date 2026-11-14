package io.jimble.db.data;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 結果の 1 行を Data にする段取り（結果ごとに、最初の行で 1 回だけ作る。2.8.0）
 *
 * <p>
 * <b>列ごとの読み方を、最初に 1 回だけ決める</b>（jOOQ が型ごとの読み方を型に 1 回だけ結びつけるのと同じ考え）。
 * 2.7.19 までは値ごとに型を指定しない {@code getObject(i)} を呼んでいて、
 * ドライバ（MariaDB Connector/J）が<b>値ごとに型に合う変換器を表から探していた</b>。20 行の一覧を引く CPU の 1 割ほどがそこだった。
 * </p>
 *
 * <ul>
 *   <li>ドライバが {@code getObject(i)} で返すクラス（{@code ResultSetMetaData#getColumnClassName}）と
 *       JDBC の型が<b>両方とも</b>合う列だけ、型を指定した getter で読む：
 *       {@code Long}（{@code BIGINT}・{@code INTEGER}）は {@code getLong}、{@code Integer}（{@code INTEGER}・{@code SMALLINT}・{@code TINYINT}）は {@code getInt}（どちらも {@code wasNull} と）、
 *       {@code String}（{@code CHAR}・{@code VARCHAR} などの文字の型）は {@code getString}、
 *       {@code BigDecimal}（{@code DECIMAL}・{@code NUMERIC}。MySQL だけ）は {@code getBigDecimal}。
 *       <b>返る値の型も値も {@code getObject(i)} と同じ</b></li>
 *   <li>クラス名だけで決めない。PostgreSQL のドライバは、Java のクラスを持たない型（{@code inet}・{@code citext}・範囲型・複合型など）を
 *       {@code String} と答えるが、{@code getObject} は {@code PGobject} を返す。
 *       PostgreSQL の {@code numeric} は、{@code getObject} だけが {@code NaN} と負の桁数を扱う（{@code getBigDecimal} は例外か別の値）ので、任せる</li>
 *   <li>それ以外の列（JSON・geometry・日時・{@code Boolean}・{@code BigInteger}・バイナリなど）は、
 *       これまでどおり {@link ResultSetConverter#value} で読む</li>
 *   <li>表名と列名の分け（{@code 表__列}）と、行・表ごとの Data に入る数も 1 回だけ求め、
 *       Data を入れても伸び直さない大きさで作る（2.7.19 までは既定の大きさで、13 列を超える表では行ごとに伸び直していた）</li>
 * </ul>
 *
 * <p>
 * 行の Data のキーの並び・入れ子の形・全部 null の表を落とす扱いは、{@link ResultSetConverter} と同じ。
 * 同じ名前の列が 2 つある・列の名前と表の名前がぶつかる、という結果は段取りを作らず、
 * {@link ResultSetConverter} にそのまま任せる（後ろの列が勝つ・表の Data に入る、という今の動きを変えない）。
 * </p>
 */
final class RowPlan {

	/* 読み方（型を指定した getter で読めない列は DELEGATE） */
	private static final int DELEGATE = 0;
	private static final int LONG = 1;
	private static final int INTEGER = 2;
	private static final int STRING = 3;
	private static final int BIG_DECIMAL = 4;

	/* 列ごとの読み方 */
	private final int[] kinds;

	/* 列ごとの型の分け（DELEGATE の列を ResultSetConverter に渡すとき） */
	private final int[] columnTypes;

	/* 列ごとの置き場（-1 なら行の直下、ほかは parents の添字） */
	private final int[] parentIndex;

	/* 列ごとのキー（直下ならそのまま、表の中なら列名） */
	private final String[] keys;

	/* 表の名前（現れた順） */
	private final String[] parents;

	/* 表ごとの列の数 */
	private final int[] parentSizes;

	/* 行の直下に入る数（直下の列 + 表） */
	private final int topSize;

	private RowPlan (int[] kinds, int[] columnTypes, int[] parentIndex, String[] keys, String[] parents, int[] parentSizes, int topSize) {

		this.kinds = kinds;
		this.columnTypes = columnTypes;
		this.parentIndex = parentIndex;
		this.keys = keys;
		this.parents = parents;
		this.parentSizes = parentSizes;
		this.topSize = topSize;

	}

	/**
	 * 段取りを作る
	 *
	 * @param metaData					結果の列
	 * @param keyInfoList				列のキー（{@link ResultSetIterator} が分けたもの）
	 * @param columnTypeList			列の型の分け
	 * @return	段取り（同じ名前の列があるなど、今の読み方に任せる結果なら null）
	 * @throws SQLException	列の情報を読めなかったとき
	 */
	static RowPlan of (ResultSetMetaData metaData, Dialect dialect, List<ResultSetIterator.KeyInfo> keyInfoList, List<Integer> columnTypeList) throws SQLException {

		// DECIMAL を getBigDecimal で読んでよいか（MySQL の DECIMAL は getObject も同じ読み方。PostgreSQL の numeric は違う）
		boolean plainDecimal = dialect instanceof MySqlDialect;

		int count = keyInfoList.size();
		int[] kinds = new int[count];
		int[] columnTypes = new int[count];
		int[] parentIndex = new int[count];
		String[] keys = new String[count];

		Map<String, Integer> parentNumbers = new HashMap<>();
		List<String> parents = new ArrayList<>();
		List<Set<String>> parentKeys = new ArrayList<>();
		Set<String> topKeys = new HashSet<>();

		for (int i = 0; i < count; i++) {

			ResultSetIterator.KeyInfo keyInfo = keyInfoList.get(i);

			columnTypes[i] = columnTypeList.get(i);
			kinds[i] = kind(columnTypes[i], className(metaData, i + 1), plainDecimal);

			if (!keyInfo.isNest) {
				parentIndex[i] = -1;
				keys[i] = keyInfo.key;
				if (!topKeys.add(keyInfo.key)) {
					return null;
				}
				continue;
			}

			Integer number = parentNumbers.get(keyInfo.parentKey);
			if (number == null) {
				number = parents.size();
				parentNumbers.put(keyInfo.parentKey, number);
				parents.add(keyInfo.parentKey);
				parentKeys.add(new HashSet<>());
			}

			if (!parentKeys.get(number).add(keyInfo.childKey)) {
				return null;
			}

			parentIndex[i] = number;
			keys[i] = keyInfo.childKey;

		}

		for (String parent : parents) {
			if (topKeys.contains(parent)) {
				return null;
			}
		}

		int[] parentSizes = new int[parents.size()];
		for (int p = 0; p < parentSizes.length; p++) {
			parentSizes[p] = parentKeys.get(p).size();
		}

		return new RowPlan(kinds, columnTypes, parentIndex, keys, parents.toArray(new String[0]), parentSizes
			, topKeys.size() + parents.size());

	}

	/**
	 * ドライバが getObject で返すクラスの名前（読めなければ null。その列は今の読み方に任せる）
	 */
	private static String className (ResultSetMetaData metaData, int index) {

		try {
			return metaData.getColumnClassName(index);
		} catch (SQLException | RuntimeException ex) {
			return null;
		}

	}

	/**
	 * 列の読み方を決める（クラスの名前と JDBC の型が両方とも合うときだけ、型を指定した getter で読む）
	 *
	 * @param columnType	列の型の分け（{@link java.sql.Types} の値か、ResultSetConverter の TYPE_*）
	 * @param className		ドライバが getObject で返すクラスの名前
	 * @param plainDecimal	DECIMAL を getBigDecimal で読んでよいか
	 * @return	読み方
	 */
	static int kind (int columnType, String className, boolean plainDecimal) {

		if (className == null) {
			return DELEGATE;
		}

		return switch (className) {
			case "java.lang.Long" -> columnType == Types.BIGINT || columnType == Types.INTEGER ? LONG : DELEGATE;
			case "java.lang.Integer" -> columnType == Types.INTEGER || columnType == Types.SMALLINT || columnType == Types.TINYINT ? INTEGER : DELEGATE;
			case "java.lang.String" -> switch (columnType) {
				case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> STRING;
				default -> DELEGATE;
			};
			case "java.math.BigDecimal" -> plainDecimal && (columnType == Types.DECIMAL || columnType == Types.NUMERIC) ? BIG_DECIMAL : DELEGATE;
			default -> DELEGATE;
		};

	}

	/**
	 * 今の行を読む
	 *
	 * @param resultSet				結果
	 * @param dialect				方言
	 * @param removeAllNullTableData	全部 null の表を落とすか（段取りは使い回すので、設定は読むたびに受け取る）
	 * @return	行（読めなければ null。{@link ResultSetConverter} と同じ）
	 */
	Data read (ResultSet resultSet, Dialect dialect, boolean removeAllNullTableData) {

		try {

			Data row = new Data(topSize);
			Data[] children = parents.length == 0 ? null : new Data[parents.length];
			boolean[] kept = removeAllNullTableData && parents.length > 0 ? new boolean[parents.length] : null;

			for (int i = 0; i < kinds.length; i++) {

				Object value = value(resultSet, i, dialect);

				int parent = parentIndex[i];
				if (parent < 0) {
					row.put(keys[i], value);
					continue;
				}

				Data child = children[parent];
				if (child == null) {
					// 表の Data は、その表の最初の列のところで行に入れる（キーの並びを ResultSetConverter と同じにする）
					child = new Data(parentSizes[parent]);
					children[parent] = child;
					row.put(parents[parent], child);
				}
				child.put(keys[i], value);

				if (kept != null && value != null) {
					kept[parent] = true;
				}

			}

			if (kept != null) {
				for (int p = 0; p < parents.length; p++) {
					if (!kept[p]) {
						row.remove(parents[p]);
					}
				}
			}

			return row;

		} catch (Exception ex) {

			Log.error(ex);
			return null;

		}

	}

	/**
	 * 1 つの値を読む
	 *
	 * @param resultSet	結果
	 * @param i			列の添字（0 から）
	 * @param dialect	方言
	 * @return	値（{@code getObject(i)} と同じ型）
	 * @throws Exception	読めなかったとき
	 */
	private Object value (ResultSet resultSet, int i, Dialect dialect) throws Exception {

		int index = i + 1;

		switch (kinds[i]) {

			case LONG: {
				long value = resultSet.getLong(index);
				return resultSet.wasNull() ? null : value;
			}

			case INTEGER: {
				int value = resultSet.getInt(index);
				return resultSet.wasNull() ? null : value;
			}

			case STRING:
				return resultSet.getString(index);

			case BIG_DECIMAL:
				return resultSet.getBigDecimal(index);

			default:
				return ResultSetConverter.value(resultSet, index, columnTypes[i], dialect);

		}

	}

}
