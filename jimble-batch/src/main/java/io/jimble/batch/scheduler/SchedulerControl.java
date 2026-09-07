package io.jimble.batch.scheduler;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.cache.Cache;
import io.jimble.db.cache.ICache;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.db.value.DBValue;

import java.util.Date;
import java.util.List;

/**
 * スケジューラの入り切りと稼働状況（要件 F-B-07 / F-B-10）
 *
 * <p>
 * <b>DB だけで完結する</b>（Redis は要らない。要件 F-B-10）。
 * </p>
 *
 * <ul>
 *   <li>入り切り：{@code db_value} の1行（{@link #enable()} / {@link #disable()}）</li>
 *   <li>稼働状況：キャッシュに置くハートビート（{@link #isRunning()}）</li>
 * </ul>
 */
public final class SchedulerControl {

	/** 入り切りを持つキー */
	public static final String KEY_ENABLED = "batch_db_scheduler_enabled";

	/** ハートビートのグループ */
	public static final String HEARTBEAT_GROUP = "batch_db_scheduler_started";

	private SchedulerControl () {}

	/**
	 * 動いてよいか
	 *
	 * @return	動いてよい場合 = true
	 */
	public static boolean isEnabled () {

		try (DB db = DBUtil.getMainDB()) {

			return !"0".equals(DBValue.getString(db, KEY_ENABLED, "1"));

		} catch (Exception ex) {

			Log.error(ex, "スケジューラの状態を読めませんでした");
			return false;

		}

	}

	/**
	 * 動いてよいことにする
	 *
	 * @return	書けた場合 = true
	 */
	public static boolean enable () {

		try (DB db = DBUtil.getMainDB()) {

			DBValue.set(db, KEY_ENABLED, "1");

			return !db.isError();

		} catch (Exception ex) {

			Log.error(ex, "スケジューラを有効にできませんでした");
			return false;

		}

	}

	/**
	 * 止める
	 *
	 * <p>動いているスケジューラは、次の確認のタイミングで自分から降りる。</p>
	 *
	 * @return	書けた場合 = true
	 */
	public static boolean disable () {

		try (DB db = DBUtil.getMainDB()) {

			DBValue.set(db, KEY_ENABLED, "0");

			return !db.isError();

		} catch (Exception ex) {

			Log.error(ex, "スケジューラを止められませんでした");
			return false;

		}

	}

	/**
	 * ハートビートを打つ
	 *
	 * @param schedulerId	スケジューラの識別子
	 */
	static void heartbeat (String schedulerId) {

		try (DB db = DBUtil.getMainDB()) {

			ICache cache = Cache.instance(db);

			cache.set(heartbeatKey(schedulerId)
				, new Data().putData("created_at", new Date()).getJsonString()
				, "application/json"
				, HEARTBEAT_GROUP);

		} catch (Exception ex) {

			Log.error(ex, "スケジューラのハートビートを打てませんでした");

		}

	}

	/**
	 * ハートビートを消す
	 *
	 * @param schedulerId	スケジューラの識別子
	 */
	static void clearHeartbeat (String schedulerId) {

		try (DB db = DBUtil.getMainDB()) {

			Cache.instance(db).remove(heartbeatKey(schedulerId));

		} catch (Exception ex) {

			Log.error(ex, "スケジューラのハートビートを消せませんでした");

		}

	}

	/**
	 * どこかでスケジューラが動いているか
	 *
	 * @return	動いている場合 = true
	 */
	public static boolean isRunning () {

		long limit = SchedulerConf.reloadIntervalMs() + 5000;

		try (DB db = DBUtil.getMainDB()) {

			List<String> values = Cache.instance(db).getStringGroup(HEARTBEAT_GROUP);

			if (values == null) {
				return false;
			}

			for (String json : values) {

				if (json == null || json.isEmpty()) {
					continue;
				}

				Data data = Data.fromJsonString(json);

				if (System.currentTimeMillis() - data.getDateTime("created_at") <= limit) {
					return true;
				}

			}

			return false;

		} catch (Exception ex) {

			Log.error(ex, "スケジューラの稼働状況を読めませんでした");
			return false;

		}

	}

	/**
	 * ハートビートのキー
	 *
	 * @param schedulerId	スケジューラの識別子
	 * @return	キー
	 */
	private static String heartbeatKey (String schedulerId) {

		return HEARTBEAT_GROUP + "-" + schedulerId;

	}

}
