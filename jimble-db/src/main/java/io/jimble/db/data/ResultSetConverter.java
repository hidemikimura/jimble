package io.jimble.db.data;

import io.jimble.db.dialect.Dialect;
import io.jimble.util.conf.Conf;
import io.jimble.util.json.Dson;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * ResultSetからDataへ変換する.
 */
public class ResultSetConverter {

	/** JSON を表す内部の型（JDBC の型番号とぶつからない値） */
	public static final int TYPE_JSON = -999;

	/** 地理空間を表す内部の型 */
	public static final int TYPE_GEOMETRY = -998;

	/** 時差を持たない日時（MySQL の DATETIME・PostgreSQL の timestamp）を表す内部の型 */
	public static final int TYPE_LOCAL_DATETIME = -997;

	/* 全ての列がNullのテーブルデータを削除する */
	/** 設定キー：全部 null のテーブルを結果から落とすか */
	public static final String KEY_REMOVE_ALL_NULL_TABLE_DATA = "db.remove_all_null_table_data";

	/**
	 * ResultSetからオブジェクトへ変換する.
	 *
	 * @param resultSet          SQL結果セット
	 * @param keyInfoList        キー情報一覧
	 * @param columnTypeList     列種別一覧
	 * @return オブジェクト
	 */
	public static Data convert (ResultSet resultSet, List<ResultSetIterator.KeyInfo> keyInfoList, List<Integer> columnTypeList, Dialect dialect) {

		return convert(resultSet, keyInfoList, columnTypeList, dialect, removesAllNullTableData());

	}

	/**
	 * ResultSetからオブジェクトへ変換する（設定を読まない。結果を読み始めたときに1回だけ読んだ値を渡す）
	 *
	 * @param resultSet					SQL結果セット
	 * @param keyInfoList				キー情報一覧
	 * @param columnTypeList			列種別一覧
	 * @param dialect					方言
	 * @param removeAllNullTableData	全部 null のテーブルを落とすか（{@link #removesAllNullTableData()}）
	 * @return オブジェクト
	 */
	static Data convert (ResultSet resultSet, List<ResultSetIterator.KeyInfo> keyInfoList, List<Integer> columnTypeList, Dialect dialect, boolean removeAllNullTableData) {

		return convertToData(resultSet, keyInfoList, columnTypeList, dialect, removeAllNullTableData);

	}

	/**
	 * 全部 null のテーブルを結果から落とすか（{@value #KEY_REMOVE_ALL_NULL_TABLE_DATA}）
	 *
	 * <p>
	 * <b>結果ごとに1回だけ読む</b>（{@code ResultSetIterator}）。行ごとに読むと、
	 * 設定のキーの読み解きが行の多い結果で CPU の 1 割ほどを使っていた。
	 * </p>
	 *
	 * @return	落とすなら true
	 */
	static boolean removesAllNullTableData () {

		/*
		 * <b>クラス初期化のときに読んでいたのをやめた</b>（要件 D-159）。
		 * {@code static final} で読むと、<b>設定を読み直しても反映されず</b>、
		 * Conf が立ち上がる前にこのクラスに触れると落ちる
		 * （{@code CipherUtil} が鍵を static で読んでいたのと同じ形）。
		 */
		return Conf.conf().getBoolean(KEY_REMOVE_ALL_NULL_TABLE_DATA, false);

	}

	/**
	 * SQL結果セットからMapに変換する.
	 *
	 * @param resultSet         SQL結果セット
	 * @param keyInfoList       キー情報一覧
	 * @param columnTypeList    列種別一覧
	 * @return Map
	 */
	private static Data convertToData (ResultSet resultSet, List<ResultSetIterator.KeyInfo> keyInfoList, List<Integer> columnTypeList, Dialect dialect, boolean removeAllNullTableData) {

		try {

			Data data = new Data();

			/*
			 * 直前に入れたテーブルの Data。同じテーブルの列は続いて並ぶので、
			 * 列ごとに getDataOptional で引き直さない
			 */
			String lastParentKey = null;
			Data lastChild = null;

			HashMap<String, Boolean> keepTableHash = new HashMap<>();

			int i = 0;
			for (ResultSetIterator.KeyInfo keyInfo : keyInfoList) {

				Object value = null;
				if (TYPE_JSON == columnTypeList.get(i)) {
					// JSON
					String jsonString = resultSet.getString(i + 1);
					if (jsonString != null) {
						if (jsonString.startsWith("[")) {
							/*
							 * 印を付けて入れる（D-189）。getString すると先頭の要素だけが返るので、
							 * そうしたときに Data が1度だけ言えるようにする
							 */
							List<?> list = Dson.decodes(jsonString, List.class);
							value = list == null ? null : new io.jimble.util.internal.JsonArrayList(list);
						} else {
							value = Dson.decodes(jsonString, Data.class);
						}
					}
				} else if (TYPE_GEOMETRY == columnTypeList.get(i)) {
					// Geometry（返ってくる形が製品で違う。要件 F-D-30）
					value = dialect.geometryText(resultSet, i + 1);
				} else if (TYPE_LOCAL_DATETIME == columnTypeList.get(i)) {
					// 時差を持たない日時（Dialect#isLocalDateTimeType）
					value = localDateTime(resultSet, i + 1);
				} else {
					// その他
					value = resultSet.getObject(i + 1);
					if (value instanceof LocalDateTime) {
						LocalDateTime d = (LocalDateTime) value;
						Calendar calendar = Calendar.getInstance();
						calendar.set(Calendar.YEAR, d.getYear());
						calendar.set(Calendar.MONTH, d.getMonth().ordinal());
						calendar.set(Calendar.DAY_OF_MONTH, d.getDayOfMonth());
						calendar.set(Calendar.HOUR_OF_DAY, d.getHour());
						calendar.set(Calendar.MINUTE, d.getMinute());
						calendar.set(Calendar.SECOND, d.getSecond());
						calendar.set(Calendar.MILLISECOND, 0);
						value = calendar.getTime();
					}
				}

				if (keyInfo.isNest) {
					Data child;
					if (keyInfo.parentKey.equals(lastParentKey)) {
						child = lastChild;
					} else {
						child = data.getDataOptional(keyInfo.parentKey);
						lastParentKey = keyInfo.parentKey;
						lastChild = child;
					}
					child.put(keyInfo.childKey, value);
					if (removeAllNullTableData) {
						Boolean beforeKeep = keepTableHash.get(keyInfo.parentKey);
						Boolean nowKeep = value != null ? Boolean.TRUE : Boolean.FALSE;
						if (beforeKeep == null) {
							keepTableHash.put(keyInfo.parentKey, nowKeep);
						} else {
							if (!beforeKeep && nowKeep) {
								keepTableHash.put(keyInfo.parentKey, Boolean.TRUE);
							}
						}
					}
				} else {
					data.put(keyInfo.key, value);
				}

				i++;
			}

			if (removeAllNullTableData && !keepTableHash.isEmpty()) {
				for (Map.Entry<String, Boolean> entry : keepTableHash.entrySet()) {
					if (!entry.getValue()) {
						data.remove(entry.getKey());
					}
				}
			}

			return data;

		} catch (Exception ex) {

			Log.error(ex);
			return null;

		}

	}

	/**
	 * 時差を持たない日時の列を、{@code getObject} と同じ {@code Timestamp} にする
	 *
	 * <p>
	 * {@code LocalDateTime} で読んでから作る。ドライバの {@code getObject} は値ごとに {@code Calendar} を作るので、
	 * 日時の列の多い結果では、それだけで CPU の 2 割ほどを使っていた。
	 * </p>
	 *
	 * <p>
	 * {@code LocalDateTime} で表せない値は、これまでどおり {@code getObject} で読む：
	 * MySQL の月や日が 0 の日付（{@code 2024-00-05}。ドライバは前の月に繰り下げる）と、
	 * PostgreSQL の {@code infinity} / {@code -infinity}（{@code LocalDateTime.MAX} / {@code MIN} になる）。
	 * </p>
	 *
	 * @param resultSet	SQL結果セット
	 * @param index		列の位置（1 から）
	 * @return	{@code Timestamp}（null なら null）
	 * @throws SQLException	読めなかったとき
	 */
	private static Object localDateTime (ResultSet resultSet, int index) throws SQLException {

		LocalDateTime local;
		try {
			local = resultSet.getObject(index, LocalDateTime.class);
		} catch (SQLException ex) {
			return resultSet.getObject(index);
		}

		if (local == null) {
			return null;
		}
		if (local.equals(LocalDateTime.MAX) || local.equals(LocalDateTime.MIN)) {
			return resultSet.getObject(index);
		}
		return Timestamp.valueOf(local);

	}

}
