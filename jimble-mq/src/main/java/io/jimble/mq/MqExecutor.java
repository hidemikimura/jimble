package io.jimble.mq;

import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.DB;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.core.trace.Span;
import io.jimble.core.trace.SpanKind;
import io.jimble.core.trace.Tracing;
import io.jimble.util.data.Data;

import java.util.Date;

/**
 * MQ の処理1種類（要件 F-M-02）
 *
 * <pre>
 * public class SendMailExecutor extends MqExecutor {
 *
 *     &#64;Override public String queueName ()        { return "mq_main"; }
 *     &#64;Override public String key ()              { return "send_mail"; }
 *     &#64;Override public MqExecuteType executeType(){ return MqExecuteType.short_time; }
 *
 *     &#64;Override
 *     public MqStatus execute (DB db, Data row) {
 *         Data data = row.getDataOptional("data");
 *         mailer.send(data.getString("to"));
 *         return MqStatus.completed;
 *     }
 * }
 * </pre>
 *
 * <p>登録は明示的に行う（要件 F-M-07）。{@link MqRegistry} 参照。</p>
 *
 * <h2>積むとき（要件 F-M-03）</h2>
 *
 * <pre>
 * try (DBTransaction transaction = new DBTransaction(db)) {
 *     transaction.beginTransaction();
 *     db.insert(...);                                  // 業務のデータ
 *     new SendMailExecutor().put(db, new Data()...);   // キュー
 *     transaction.commitEndTransaction();
 * }
 * </pre>
 *
 * <p>
 * <b>同じトランザクションで積むので、ロールバックすればキューも消える。</b>
 * 「登録はできたがメールのキューだけ残った」が起きない。
 * </p>
 *
 * <h2>二重に処理されうる（要件 F-M-05）</h2>
 * <p>
 * <b>{@link #execute(DB, Data)} は同じメッセージに対して2回以上呼ばれうる。</b>
 * 処理の途中でプロセスが落ちれば、その行は
 * {@code mq.stale_seconds} のあとに {@code waiting} へ戻されて拾い直される。
 * リトライ（要件 F-M-04）でも同じことが起きる。
 * </p>
 * <p>
 * したがって<b>冪等に書く。</b>「送った」「作った」を記録して、
 * すでにあれば {@link MqStatus#completed} を返す形にする。
 * </p>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>Executor のインスタンスが全ワーカーで共有されていた。</b>
 *       しかもメッセージごとに {@code setCancelOrderNotify()} で
 *       <b>その共有インスタンスのフィールドを書き換えていた。</b>
 *       Executor にフィールドを持たせると<b>別のメッセージの処理と混ざる。</b>
 *       jimble は<b>メッセージごとに1つ作る</b>（原則3。ルートの {@code UseCase::new} と同じ）</li>
 * </ol>
 */
public abstract class MqExecutor {

	/* 中断通知（メッセージごとのインスタンスなので共有されない） */
	private CancelOrderNotify cancelOrderNotify;

	/* いま処理している行 */
	private Data currentRow;

	// region 実装するもの

	/**
	 * キューのテーブル名
	 *
	 * @return	テーブル名
	 */
	public abstract String queueName ();

	/**
	 * 処理の種類を表すキー
	 *
	 * @return	キー
	 */
	public abstract String key ();

	/**
	 * 実行種別（要件 F-M-08）
	 *
	 * @return	種別
	 */
	public abstract MqExecuteType executeType ();

	/**
	 * 処理する
	 *
	 * @param db	DB
	 * @param row	キューの行（{@code data} に積んだ内容が入っている）
	 * @return	結果
	 */
	public abstract MqStatus execute (DB db, Data row);

	/**
	 * リトライの上限（要件 F-M-04）
	 *
	 * <p>0 にするとリトライしない（1回で {@code dead} になる）。</p>
	 *
	 * @return	上限
	 */
	public int maxRetry () {

		return 3;

	}

	// endregion

	// region 積む（要件 F-M-03）

	/**
	 * キューに積む
	 *
	 * @param db	DB（トランザクションの中で呼べば、ロールバックでキューも消える）
	 * @param data	内容
	 * @return	キューID（失敗したら -1）
	 */
	public long put (DB db, Data data) {

		return put(db, data, null);

	}

	/**
	 * キューに積む（時刻を指定する）
	 *
	 * @param db			DB
	 * @param data			内容
	 * @param scheduledAt	処理してよい時刻（null なら即時）
	 * @return	キューID（失敗したら -1）
	 */
	public long put (DB db, Data data, Date scheduledAt) {

		/*
		 * 積んだところを1区間として残す（要件 NF-O-05）。
		 *
		 * <b>この区間の traceparent を行に書く。</b>あとで処理する側がそれを親にするので、
		 * 積んだところと処理したところが1本のトレースで繋がる
		 * （処理は別のスレッド、たいていは別のプロセスで、何分もあとに起きる）。
		 */
		try (Span span = Tracing.enabled()
				? Tracing.start("mq.put %s".formatted(queueName()), SpanKind.producer)
				: Span.NOOP) {

			span.attribute("messaging.destination.name", queueName());
			span.attribute("messaging.operation.name", key());

			long id = db.insert("""
					INSERT INTO %s (
						execute_type, mq_key, status, scheduled_at, retry_count, data, traceparent, created_at, updated_at
					) VALUES (
						?, ?, ?, ?, 0, ?, ?, NOW(), NOW()
					)
				""".formatted(db.dialect().identifier(queueName()))
				, executeType().name()
				, key()
				, MqStatus.waiting.name()
				, scheduledAt
				, data == null ? new Data() : data
				, span.traceparent());

			if (db.isError()) {
				return -1;
			}

			span.attribute("messaging.message.id", id);

			return id;

		}

	}

	// endregion

	// region 処理の中から使うもの

	/**
	 * 中断が指示されているか
	 *
	 * @return	指示されている場合 = true
	 */
	protected boolean isCancelOrder () {

		return cancelOrderNotify != null && cancelOrderNotify.isCancelOrder();

	}

	/**
	 * ワーカーごと止める
	 */
	protected void cancelWorker () {

		if (cancelOrderNotify != null) {
			cancelOrderNotify.doCancel();
		}

	}

	/**
	 * いま処理している行
	 *
	 * @return	行
	 */
	protected Data currentRow () {

		return currentRow;

	}

	/**
	 * 内容を書き換える
	 *
	 * @param db	DB
	 * @param row	行
	 * @param data	内容
	 * @return	書き換えられた場合 = true
	 */
	protected boolean updateData (DB db, Data row, Data data) {

		return db.update("UPDATE %s SET data = ?, updated_at = NOW() WHERE id = ?".formatted(db.dialect().identifier(queueName()))
			, data, row.getLong("id")) >= 0;

	}

	/**
	 * ログ情報を書き換える
	 *
	 * @param db		DB
	 * @param row		行
	 * @param logInfo	ログ情報
	 * @return	書き換えられた場合 = true
	 */
	protected boolean updateLogInfo (DB db, Data row, Data logInfo) {

		return db.update("UPDATE %s SET log_info = ?, updated_at = NOW() WHERE id = ?".formatted(db.dialect().identifier(queueName()))
			, logInfo, row.getLong("id")) >= 0;

	}

	// endregion

	/**
	 * ワーカーから渡される
	 *
	 * @param cancelOrderNotify	中断通知
	 * @param row				いま処理している行
	 */
	void bind (CancelOrderNotify cancelOrderNotify, Data row) {

		this.cancelOrderNotify = cancelOrderNotify;
		this.currentRow = row;

	}

}
