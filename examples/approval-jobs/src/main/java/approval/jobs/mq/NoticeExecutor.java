package approval.jobs.mq;

import io.jimble.db.DB;
import io.jimble.mq.MqExecutor;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

/**
 * 通知を1件作る（正常系）
 *
 * <h2>ここで見せたいこと：2回実行されても壊れないこと（要件 F-M-05）</h2>
 * <p>
 * <b>MQ は「1回だけ実行する」ことを約束しない。</b>
 * ワーカーが行を掴んだあとにプロセスごと落ちれば、その行は
 * {@code stale_seconds} を過ぎて {@code waiting} に戻り、<b>もう一度実行される。</b>
 * 「たぶん落ちない」で書くと、落ちた日に通知が二重に飛ぶ。
 * </p>
 *
 * <p>
 * ここでは<b>2つ重ねて</b>ある。
 * </p>
 * <ol>
 *   <li><b>先に見る。</b>すでに作ってあれば、何もせず {@link MqStatus#completed} を返す</li>
 *   <li><b>DB にも言わせる。</b>{@code notice (request_id, kind)} に一意キーを張ってある。
 *       1 と 2 のあいだに別のワーカーが入っても、<b>2つ目の INSERT は必ず失敗する</b></li>
 * </ol>
 *
 * <p>
 * <b>1 だけでは足りない。</b>「見る」と「作る」のあいだに隙間があるからである。
 * <b>2 だけでも足りない。</b>失敗が例外にならず {@code db.isError()} に入るだけなので、
 * 見ないと「入っていないのに completed」になる。
 * </p>
 */
public class NoticeExecutor extends MqExecutor {

	/** 通知の種類：締切が近い */
	public static final String KIND_DUE_SOON = "due_soon";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String queueName () {

		return JobsQueue.NOTICE;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String key () {

		return "notice";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MqExecuteType executeType () {

		return MqExecuteType.short_time;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MqStatus execute (DB db, Data row) {

		Data data = row.getDataOptional("data");

		long requestId = data.getLong("request_id");
		long toStaffId = data.getLong("to_staff_id");
		String kind = data.getString("kind");

		// 1. すでに作ってあれば、何もしない
		Data exists = db.select("SELECT id FROM notice WHERE request_id = ? AND kind = ?"
			, requestId, kind);

		if (exists != null) {
			Log.info("通知はもう作ってあります: request_id=%d / kind=%s".formatted(requestId, kind));
			return MqStatus.completed;
		}

		db.insert("""
				INSERT INTO notice (request_id, to_staff_id, kind, sent_at, created_at)
				VALUES (?, ?, ?, NOW(), NOW())
			"""
			, requestId, toStaffId, kind);

		/*
		 * 2. 一意キーに弾かれていないか見る。
		 *
		 * db.insert() は失敗しても例外を投げない。
		 * 見ないと「入っていないのに completed」になり、
		 * 行が消えて誰も気づけなくなる。
		 */
		if (db.isError()) {

			// 隙間に別のワーカーが入って、先に作った
			if (db.select("SELECT id FROM notice WHERE request_id = ? AND kind = ?"
				, requestId, kind) != null) {

				Log.info("通知は別のワーカーが作りました: request_id=%d".formatted(requestId));
				return MqStatus.completed;

			}

			Log.error("通知を作れませんでした: request_id=%d / %s"
				.formatted(requestId, String.valueOf(db.getError())));

			return MqStatus.error;

		}

		Log.info("通知を作りました: request_id=%d / to=%d / kind=%s"
			.formatted(requestId, toStaffId, kind));

		return MqStatus.completed;

	}

}
