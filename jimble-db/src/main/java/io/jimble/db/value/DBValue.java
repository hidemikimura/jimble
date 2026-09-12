package io.jimble.db.value;

import io.jimble.db.FrameworkTables;
import java.util.List;
import io.jimble.db.dialect.Sqls;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.db.internal.version.DBVersion;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * value
 */
public class DBValue {

	// region 設定する

	/**
	 * 設定する
	 *
	 * @param db    DB
	 * @param key   キー
	 * @param value 値
	 * @return レコード
	 */
	public static Data set (DB db, String key, Object value) {

		return set(db, key, value, -1, "");

	}

	/**
	 * 設定する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param value         値
	 * @param cacheSecond   キャッシュ秒（-1=キャッシュしない、0=無制限）
	 * @param description   説明
	 * @return レコード
	 */
	public static Data set (DB db, String key, Object value, long cacheSecond, String description) {

		db.insert(
			"""
				INSERT INTO db_value (
					value_key
					, value
					, cache_second
					, description
				) VALUES (
					?
					, ?
					, ?
					, ?
				)
			""" + Sqls.upsert(db.dialect(), List.of("value_key"), "value", "cache_second", "description")
			, key
			, PropertyUtil.toString(value)
			, cacheSecond
			, description
		);

		Data row = new Data()
			.putData("value_key", key)
			.putData("value", PropertyUtil.toString(value))
			.putData("cache_second", cacheSecond)
			.putData("description", description)
			.putData("get_at", new Date()
		);

		cache.put(key, row);

		return row;

	}

	// endregion


	// region Data

	/**
	 * 取得する
	 *
	 * @param db    DB
	 * @param key   キー
	 * @return  値
	 */
	public static Data getData (DB db, String key) {

		return getData(db, key, null);

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @return  値
	 */
	public static Data getData (DB db, String key, Data defaultValue) {

		return getData(db, key, defaultValue, -1, "");

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @param cacheSecond   キャッシュ秒（-1=キャッシュしない、0=無制限）
	 * @param description   説明
	 * @return  値
	 */
	public static Data getData (DB db, String key, Data defaultValue, long cacheSecond, String description) {

		String defaultValueString = null;
		if (defaultValue != null) {
			defaultValueString = defaultValue.getJsonString();
		}
		Data data = get(db, key, defaultValueString, cacheSecond, description);
		if (data == null) {
			return defaultValue;
		}

		String resultString = data.getString("value");
		if (resultString == null || resultString.isEmpty()) {
			return null;
		}

		return Data.fromJsonString(resultString);

	}

	// endregion

	// region String

	/**
	 * 取得する
	 *
	 * @param db    DB
	 * @param key   キー
	 * @return  値
	 */
	public static String getString (DB db, String key) {

		return getString(db, key, null);

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @return  値
	 */
	public static String getString (DB db, String key, String defaultValue) {

		return getString(db, key, defaultValue, -1, "");

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @param cacheSecond   キャッシュ秒（-1=キャッシュしない、0=無制限）
	 * @param description   説明
	 * @return  値
	 */
	public static String getString (DB db, String key, String defaultValue, long cacheSecond, String description) {

		Data data = get(db, key, defaultValue, cacheSecond, description);
		if (data == null) {
			return defaultValue;
		}

		return data.getString("value");

	}

	// endregion

	// region int

	/**
	 * 取得する
	 *
	 * @param db    DB
	 * @param key   キー
	 * @return  値
	 */
	public static int getInt (DB db, String key) {

		return getInt(db, key, 0);

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @return  値
	 */
	public static int getInt (DB db, String key, int defaultValue) {

		return getInt(db, key, defaultValue, -1, "");

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @param cacheSecond   キャッシュ秒（-1=キャッシュしない、0=無制限）
	 * @param description   説明
	 * @return  値
	 */
	public static int getInt (DB db, String key, int defaultValue, long cacheSecond, String description) {

		Data data = get(db, key, String.valueOf(defaultValue), cacheSecond, description);
		if (data == null) {
			return defaultValue;
		}

		return data.getInt("value");

	}

	// endregion

	// region long

	/**
	 * 取得する
	 *
	 * @param db    DB
	 * @param key   キー
	 * @return  値
	 */
	public static long getLong (DB db, String key) {

		return getLong(db, key, 0);

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @return  値
	 */
	public static long getLong (DB db, String key, long defaultValue) {

		return getLong(db, key, defaultValue, -1, "");

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @param cacheSecond   キャッシュ秒（-1=キャッシュしない、0=無制限）
	 * @param description   説明
	 * @return  値
	 */
	public static long getLong (DB db, String key, long defaultValue, long cacheSecond, String description) {

		Data data = get(db, key, String.valueOf(defaultValue), cacheSecond, description);
		if (data == null) {
			return defaultValue;
		}

		return data.getLong("value");

	}

	// endregion

	// region float

	/**
	 * 取得する
	 *
	 * @param db    DB
	 * @param key   キー
	 * @return  値
	 */
	public static float getFloat (DB db, String key) {

		return getFloat(db, key, 0);

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @return  値
	 */
	public static float getFloat (DB db, String key, float defaultValue) {

		return getFloat(db, key, defaultValue, -1, "");

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @param cacheSecond   キャッシュ秒（0=無制限）
	 * @param description   説明
	 * @return  値
	 */
	public static float getFloat (DB db, String key, float defaultValue, long cacheSecond, String description) {

		Data data = get(db, key, String.valueOf(defaultValue), cacheSecond, description);
		if (data == null) {
			return defaultValue;
		}

		return data.getFloat("value");

	}

	// endregion

	// region double

	/**
	 * 取得する
	 *
	 * @param db    DB
	 * @param key   キー
	 * @return  値
	 */
	public static double getDouble (DB db, String key) {

		return getDouble(db, key, 0);

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @return  値
	 */
	public static double getDouble (DB db, String key, double defaultValue) {

		return getDouble(db, key, defaultValue, -1, "");

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @param cacheSecond   キャッシュ秒（0=無制限）
	 * @param description   説明
	 * @return  値
	 */
	public static double getDouble (DB db, String key, double defaultValue, long cacheSecond, String description) {

		Data data = get(db, key, String.valueOf(defaultValue), cacheSecond, description);
		if (data == null) {
			return defaultValue;
		}

		return data.getDouble("value");

	}

	// endregion

	// region date

	/**
	 * 取得する
	 *
	 * @param db    DB
	 * @param key   キー
	 * @return  値
	 */
	public static Date getDate (DB db, String key) {

		return getDate(db, key, null);

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @return  値
	 */
	public static Date getDate (DB db, String key, Date defaultValue) {

		return getDate(db, key, defaultValue, -1, "");

	}

	/**
	 * 取得する
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @param cacheSecond   キャッシュ秒（-1=キャッシュしない、0=無制限）
	 * @param description   説明
	 * @return  値
	 */
	public static Date getDate (DB db, String key, Date defaultValue, long cacheSecond, String description) {

		String defaultValueString = null;
		if (defaultValue != null) {
			defaultValueString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(defaultValue);
		}

		Data data = get(db, key, defaultValueString, cacheSecond, description);
		if (data == null) {
			return defaultValue;
		}

		return data.getDate("value");

	}

	// endregion


	/* キャッシュ */
	private final static Map<String, Data> cache = new HashMap<>();

	/**
	 * 取得
	 *
	 * @param db            DB
	 * @param key           キー
	 * @param defaultValue  デフォルト値
	 * @param cacheSecond   キャッシュ秒（0=無制限）
	 * @param description   説明
	 * @return  結果
	 */
	private static Data get (DB db, String key, String defaultValue, long cacheSecond, String description) {

		Data cacheData = cache.get(key);
		if (cacheData != null) {
			long _cacheSecond = cacheData.getLong("cache_second");
			if (_cacheSecond == 0) {
				return cacheData;
			}
			if (System.currentTimeMillis() <= cacheData.getDateTime("get_at") + _cacheSecond * 1000) {
				return cacheData;
			}
		}

		Data result = db.select(
			"""
				SELECT
					*
				FROM
					db_value
				WHERE
					value_key = ?
			"""
			, key
		);
		if (result == null) {
			result = set(db, key, defaultValue, cacheSecond, description);
		} else {
			result.put("get_at", new Date());
			if (result.getLong("cache_second") >= 0) {
				cache.put(key, result);
			}
		}

		return result;

	}

	/**
	 * 初期化
	 *
	 * @param db DB
	 */
	public static void init (DB db) {

		DBVersion dbVersion = new DBVersion(FrameworkTables.DB_VALUE, "汎用値情報");
		dbVersion.add(1)
			.mysql("""
				create table db_value
				(
					value_key varchar(250) not null primary key,
					value     varchar(250) not null
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
			""".formatted(dbVersion.placeholder()))
			.postgresql("""
				create table db_value
				(
					value_key varchar(250) not null primary key,
					value     varchar(250) not null
				)
			""");
		dbVersion.add(2)
			.mysql("""
				alter table db_value add cache_second bigint default -1 not null comment 'キャッシュ秒（0=無制限）'
			"""
			, """
				alter table db_value add description text null comment '説明'
			""")
			.postgresql("""
				alter table db_value add column cache_second bigint default -1 not null
			"""
			, """
				alter table db_value add column description text null
			""");
		dbVersion.apply(db);

	}

}
