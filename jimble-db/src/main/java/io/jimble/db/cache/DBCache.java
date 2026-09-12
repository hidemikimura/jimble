package io.jimble.db.cache;

import io.jimble.db.FrameworkTables;
import io.jimble.util.string.StringUtil;
import io.jimble.db.cache.AbstractCache;
import io.jimble.db.cache.Cache;
import io.jimble.db.cache.CacheData;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.db.dialect.Sqls;
import io.jimble.db.internal.version.DBVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * DBキャッシュ
 */
public class DBCache extends AbstractCache {

	/* DB */
	private final DB db;

	/**
	 * コンストラクタ
	 *
	 * @param db    DB
	 */
	public DBCache (DB db) {

		this.db = db;

	}

	/**
	 * DB
	 *
	 * @return  DB
	 */
	private DB db () {

		return db.newDB();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getString(String key) {

		Data data = db().select("""
			SELECT
				content
			FROM
				db_cache
			WHERE
				cache_key = ?
		""", key);

		if (data == null) {
			return null;
		}

		return data.getString("content");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getString(String key, String group) {

		return getString(key);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getStringGroup(String group) {

		List<Data> list = db().selectList("""
			SELECT
				content
			FROM
				db_cache
			WHERE
				group_key = ?
		""", group);
		if (list == null) {
			return null;
		}

		List<String> res = new ArrayList<>();
		for (Data data : list) {
			res.add(data.getString("content"));
		}

		return res;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CacheData get(String key) {

		return get(key, null);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CacheData get(String key, String group) {

		Data data = db().select("""
			SELECT
				created_at
				, content_type
				, content_length
				, created_at
			FROM
				db_cache
			WHERE
				cache_key = ?
		""", key);

		if (data == null) {
			return new CacheData(key, group);
		}

		String contentType = data.getString("content_type");
		long contentLength = data.getLong("content_length");
		Date createdAt = data.getDate("created_at");

		String fileName = key + "_" + data.getDateTime("created_at");

		File file = new File(Cache.getTempDirPath(), fileName);
		if (file.exists() && Cache.isFileResponse(contentLength)) {
			return new CacheData(key, group, file, contentType, createdAt);
		}

		data = db().select("""
			SELECT
				content
			FROM
				db_cache
			WHERE
				cache_key = ?
		""", key);
		if (data == null) {
			return new CacheData(key, group);
		}

		return writeFileCache(key, group, contentType, contentLength, data.getString("content"), file, createdAt);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<CacheData> getGroup(String group) {

		List<Data> list = db().selectList("""
			SELECT
				cache_key
				, created_at
				, content_type
				, content_length
				, created_at
			FROM
				db_cache
			WHERE
				group_key = ?
		""", group);
		if (list == null) {
			return null;
		}

		List<CacheData> res = new ArrayList<>();
		for (Data data : list) {

			String key = data.getString("cache_key");
			String contentType = data.getString("content_type");
			long contentLength = data.getLong("content_length");
			Date createdAt = data.getDate("created_at");

			String fileName = key + "_" + data.getDateTime("created_at");

			File file = new File(Cache.getTempDirPath(), fileName);
			if (file.exists() && Cache.isFileResponse(contentLength)) {
				res.add(new CacheData(key, group, file, contentType, createdAt));
			} else {
				Data row = db().select("""
					SELECT
						content
					FROM
						db_cache
					WHERE
						cache_key = ?
				""", key);
				if (row == null) {
					res.add(new CacheData(key, group));
				} else {
					res.add(writeFileCache(key, group, contentType, contentLength, row.getString("content"), file, createdAt));
				}
			}

		}

		return res;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean set(String key, String value, String contentType) {

		long res = db().insert(
			"""
				INSERT INTO db_cache (
					cache_key
					, content
					, group_key
					, content_type
					, content_length
					, created_at
				) VALUES (
					?
					, ?
					, ?
					, ?
					, ?
					, NOW()
				)
			""" + Sqls.upsert(db().dialect(), List.of("cache_key")
				, "content", "group_key", "content_type", "content_length", "created_at")
			, key
			, value
			, null
			, contentType
			, StringUtil.utf8Length(value)
		);

		return res >= 0;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean set(String key, String value, String contentType, String group) {

		long res = db().insert(
			"""
				INSERT INTO db_cache (
					cache_key
					, content
					, group_key
					, content_type
					, content_length
					, created_at
				) VALUES (
					?
					, ?
					, ?
					, ?
					, ?
					, NOW()
				)
			""" + Sqls.upsert(db().dialect(), List.of("cache_key")
				, "content", "group_key", "content_type", "content_length", "created_at")
			, key
			, value
			, group
			, contentType
			, StringUtil.utf8Length(value)
		);

		return res >= 0;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove(String key) {

		db().delete("""
			DELETE FROM db_cache
			WHERE
				cache_key = ?
		""", key);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeGroup(String group) {

		db().delete("""
			DELETE FROM db_cache
			WHERE
				group_key = ?
		""", group);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean has(String key, String group) {

		Data data = db().select("""
			SELECT
				cache_key
			FROM
				db_cache
			WHERE
				cache_key = ?
		""", key);

		return data != null;

	}

	/**
	 * 初期処理
	 *
	 * @param db    DB
	 */
	public static void init (DB db) {

		DBVersion dbVersion = new DBVersion(FrameworkTables.DB_CACHE, "汎用キャッシュ情報");
		dbVersion.add(1)
			.mysql("""
				create table db_cache
				 (
				     cache_key    varchar(250) not null,
				     content      longtext     null,
				     group_key    varchar(250) null,
				     content_type varchar(250) null,
				     content_length bigint unsigned default 0 not null,
				     created_at   datetime     not null,
				     constraint db_cache_pk primary key (cache_key)
				 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
			""".formatted(dbVersion.placeholder())
			, "create index db_cache__index_1 on db_cache (group_key)")
			.postgresql("""
				create table db_cache
				 (
				     cache_key    varchar(250) not null,
				     content      text         null,
				     group_key    varchar(250) null,
				     content_type varchar(250) null,
				     content_length bigint default 0 not null,
				     created_at   timestamp    not null,
				     constraint db_cache_pk primary key (cache_key)
				 )
			"""
			, "create index db_cache__index_1 on db_cache (group_key)");
		dbVersion.apply(db);

	}

}
