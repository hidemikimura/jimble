package io.jimble.db.data;

import io.jimble.db.dialect.Dialect;
import io.jimble.util.conf.Conf;
import io.jimble.util.json.Dson;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.sql.ResultSet;
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

	/* 全ての列がNullのテーブルデータを削除する */
	private static final boolean removeAllNullTableData = Conf.conf().getBoolean("db.removeAllNullTableData", false);

	/**
	 * ResultSetからオブジェクトへ変換する.
	 *
	 * @param resultSet          SQL結果セット
	 * @param keyInfoList        キー情報一覧
	 * @param columnTypeList     列種別一覧
	 * @return オブジェクト
	 */
	public static Data convert (ResultSet resultSet, List<ResultSetIterator.KeyInfo> keyInfoList, List<Integer> columnTypeList, Dialect dialect) {

		return convertToData(resultSet, keyInfoList, columnTypeList, dialect);

	}

	/**
	 * SQL結果セットからMapに変換する.
	 *
	 * @param resultSet         SQL結果セット
	 * @param keyInfoList       キー情報一覧
	 * @param columnTypeList    列種別一覧
	 * @return Map
	 */
	private static Data convertToData (ResultSet resultSet, List<ResultSetIterator.KeyInfo> keyInfoList, List<Integer> columnTypeList, Dialect dialect) {

		try {

			Data data = new Data();

			HashMap<String, Boolean> keepTableHash = new HashMap<>();

			int i = 0;
			for (ResultSetIterator.KeyInfo keyInfo : keyInfoList) {

				Object value = null;
				if (TYPE_JSON == columnTypeList.get(i)) {
					// JSON
					String jsonString = resultSet.getString(i + 1);
					if (jsonString != null) {
						if (jsonString.startsWith("[")) {
							value = Dson.decodes(jsonString, List.class);
						} else {
							value = Dson.decodes(jsonString, Data.class);
						}
					}
				} else if (TYPE_GEOMETRY == columnTypeList.get(i)) {
					// Geometry（返ってくる形が製品で違う。要件 F-D-30）
					value = dialect.geometryText(resultSet, i + 1);
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
					Data child = data.getDataOptional(keyInfo.parentKey);
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

}
