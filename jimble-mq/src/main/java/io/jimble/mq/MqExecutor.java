package io.jimble.mq;

import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.DB;
import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.SqlExecuteException;
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
 * db.transaction(tx -&gt; {
 *     db.insert(...);                                  // 業務のデータ
 *     new SendMailExecutor().put(db, new Data()...);   // キュー
 * });
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
 * {@code mq.stale} のあとに {@code waiting} へ戻されて拾い直される。
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

	/**
	 * 主のデータソースだけに置くキューか
	 *
	 * <p>
	 * true にすると、{@link #put} は主の DB にしか積めない（スケジューラのキューのため。{@link MqQueue#mainOnly(String)} と組で使う）。
	 * </p>
	 *
	 * @return	主だけの場合 = true（既定 false）
	 * @since 2.7.8
	 */
	public boolean mainOnly () {

		return false;

	}

	// region 積む（要件 F-M-03）

	/**
	 * キューに積む
	 *
	 * @param db	DB（トランザクションの中で呼べば、ロールバックでキューも消える）
	 * @param data	内容
	 * @return	キューID
	 * @throws MqException	積めなかった場合
	 */
	public long put (DB db, Data data) {

		return put(db, data, (Date) null);

	}

	/**
	 * キューに積む（時刻を指定する）
	 *
	 * <p>
	 * <b>{@code Instant} で受ける版（D-173）。</b>jimble の公開 API は
	 * {@code Instant} / {@code Duration} / {@code ZonedDateTime} で揃えてあり、
	 * <b>{@code java.util.Date} が出てくるのはここだけ</b>だった。
	 * {@code Date} 版は残してあるが、新しく書くならこちらを使うこと。
	 * </p>
	 *
	 * @param db			DB
	 * @param data			内容
	 * @param scheduledAt	処理してよい時刻（null なら即時）
	 * @return	キューID
	 * @throws MqException	積めなかった場合
	 */
	public long put (DB db, Data data, java.time.Instant scheduledAt) {

		return put(db, data, scheduledAt == null ? null : Date.from(scheduledAt));

	}

	/**
	 * キューに積む（時刻を指定する）
	 *
	 * @param db			DB
	 * @param data			内容
	 * @param scheduledAt	処理してよい時刻（null なら即時）
	 * @return	キューID
	 * @throws MqException	積めなかった場合
	 */
	public long put (DB db, Data data, Date scheduledAt) {

		DBSource source = requireTarget(db);

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

			long id;
			try {
				id = db.insertKey("""
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
			} catch (SqlExecuteException ex) {
				/*
				 * <b>積めなかったら投げる（D-173）。</b>
				 * 「注文は入ったが、メールのキューだけ無い」を黙って起こさない。
				 * トランザクションの中なら、投げればそのまま巻き戻る。
				 */
				throw new MqException("キューに積めませんでした: %s（%s）".formatted(queueName(), ex.getMessage()), ex);
			}

			span.attribute("messaging.message.id", id);

			// 仕事のある印（mq.hint）。積んだ側のトランザクションが確定する前に見に来ても、取り出し役はしばらく見続ける
			MqHints.markAt(queueName(), source == null ? db.getDBName() : source.name()
				, scheduledAt == null ? System.currentTimeMillis() : scheduledAt.getTime());

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

	/**
	 * このキューを置いていないデータソースの DB に積もうとしたら止める
	 *
	 * <p>
	 * 積んでも、取り出し役はそのデータソースを見に行かないので、<b>誰にも処理されないまま残る</b>
	 * （表が無ければ、表が無いという SQL のエラーになる）。どの設定で外れたかが分かるように言う。
	 * </p>
	 *
	 * @param db	積む DB
	 * @return	データソース（分からなければ null）
	 * @throws MqException	置いていないデータソースの場合
	 */
	private DBSource requireTarget (DB db) {

		DBSource source = dataSource(db);

		if (source == null) {
			// DBUtil の外で作った DB（テストなど）。確かめようがないので、これまでどおり積む
			return null;
		}

		if (mainOnly()) {
			if (source != DBUtil.getMainDataSource()) {
				throw new MqException("キュー %s は主のデータソースにしか積めません（積もうとした先: %s）".formatted(queueName(), source.name()));
			}
			return source;
		}

		if (!MqQueue.isTarget(queueName(), source)) {
			throw new MqException(("データソース %s にはキュー %s を置いていません（db.%s.mq と mq.queues.%s.data_sources を見てください）")
				.formatted(source.name(), queueName(), source.name(), queueName()));
		}

		return source;

	}

	private static DBSource dataSource (DB db) {

		for (DBSource source : DBUtil.getDataSourceList()) {
			if (source.name().equals(db.getDBName())) {
				return source;
			}
		}

		return null;

	}

}
