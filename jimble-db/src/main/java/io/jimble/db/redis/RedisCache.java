package io.jimble.db.redis;

import io.jimble.util.string.StringUtil;
import io.jimble.db.cache.AbstractCache;
import io.jimble.db.cache.Cache;
import io.jimble.db.cache.CacheData;
import io.jimble.util.parse.Parse;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * redisキャッシュ
 */
public class RedisCache extends AbstractCache {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getString(String key) {

		return getString(key, null);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getString(String key, String group) {

		try {

			RedissonClient client = RedisClient.client();

			if (group == null || group.isEmpty()) {
				RBucket<String> bucket = client.getBucket(key);
				return bucket.get();
			} else {
				RMap<String, String> map = client.getMap(group);
				return map.get(key);
			}

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getStringGroup(String group) {

		try {

			RedissonClient client = RedisClient.client();

			RMap<String, String> map = client.getMap(group);

			List<String> res = new ArrayList<>();
			for (String value : map.values()) {
				res.add(value);
			}
			return res;

		} catch (Exception ex) {

			return null;

		}

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

		try {

			RedissonClient client = RedisClient.client();

			String createdTimestamp;
			{
				RBucket<String> bucket = client.getBucket(key + "__created_at");
				createdTimestamp = bucket.get();
			}
			String contentType;
			{
				RBucket<String> bucket = client.getBucket(key + "__content_type");
				contentType = bucket.get();
			}
			long contentLength;
			{
				RBucket<String> bucket = client.getBucket(key + "__content_length");
				contentLength = Parse.parseLong(bucket.get());
			}
			Date createdAt = new Date(Long.parseLong(createdTimestamp));

			String fileName = key + "_" + createdTimestamp;

			File file = new File(Cache.getTempDirPath(), fileName);
			if (file.exists() && Cache.isFileResponse(contentLength)) {
				return new CacheData(key, group, file, contentType, createdAt);
			}

			String content = null;
			if (group == null || group.isEmpty()) {
				RBucket<String> bucket = client.getBucket(key);
				content = bucket.get();
			} else {
				RMap<String, String> map = client.getMap(group);
				content = map.get(key);
			}

			return writeFileCache(key, group, contentType, contentLength, content, file, createdAt);

		} catch (Exception ex) {

			return new CacheData(key, group);

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<CacheData> getGroup(String group) {

		try {

			RedissonClient client = RedisClient.client();

			List<CacheData> res = new ArrayList<>();
			RMap<String, String> map = client.getMap(group);
			for (Map.Entry<String, String> entry : map.entrySet()) {
				String key = entry.getKey();
				String createdTimestamp;
				{
					RBucket<String> bucket = client.getBucket(key + "__created_at");
					createdTimestamp = bucket.get();
				}
				String contentType;
				{
					RBucket<String> bucket = client.getBucket(key + "__content_type");
					contentType = bucket.get();
				}
				long contentLength;
				{
					RBucket<String> bucket = client.getBucket(key + "__content_length");
					contentLength = Parse.parseLong(bucket.get());
				}
				Date createdAt = new Date(Long.parseLong(createdTimestamp));

				String fileName = key + "_" + createdTimestamp;

				File file = new File(Cache.getTempDirPath(), fileName);
				if (file.exists() && Cache.isFileResponse(contentLength)) {
					res.add(new CacheData(key, group, file, contentType, createdAt));
				} else {
					String content = null;
					if (group == null || group.isEmpty()) {
						RBucket<String> bucket = client.getBucket(key);
						content = bucket.get();
					} else {
						content = entry.getValue();
					}
					res.add(writeFileCache(key, group, contentType, contentLength, content, file, createdAt));
				}
			}

			return res;

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean set(String key, String value, String contentType) {

		return set(key, value, contentType, null);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean set(String key, String value, String contentType, String group) {

		try {

			RedissonClient client = RedisClient.client();

			if (group == null || group.isEmpty()) {
				RBucket<String> bucket = client.getBucket(key);
				bucket.set(value);
			} else {
				RMap<String, String> map = client.getMap(group);
				map.put(key, value);
			}

			{
				RBucket<String> bucket = client.getBucket(key + "__created_at");
				bucket.set(String.valueOf(System.currentTimeMillis()));
			}
			{
				RBucket<String> bucket = client.getBucket(key + "__content_type");
				bucket.set(contentType == null ? "" : contentType);
			}
			{
				RBucket<String> bucket = client.getBucket(key + "__content_length");
				bucket.set(String.valueOf(StringUtil.utf8Length(value)));
			}

			return true;

		} catch (Exception ex) {

			return false;

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove(String key) {

		try {

			RedissonClient client = RedisClient.client();

			RKeys keys = client.getKeys();
			keys.delete(key);

		} catch (Exception ex) {}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeGroup(String group) {

		remove(group);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean has(String key, String group) {

		return getString(key, group) != null;

	}

}
