package approval.jobs.mq;

import io.jimble.core.context.Context;
import io.jimble.core.context.MqContext;
import io.jimble.db.DB;
import io.jimble.mq.MqExecutor;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

/**
 * メールを送る（<b>わざと失敗する</b>）
 *
 * <h2>ここで見せたいこと：失敗する道（要件 F-M-04）</h2>
 * <p>
 * {@code examples/blog} の MQ は必ず成功するので、
 * <b>リトライもデッドレターも一度も通っていなかった。</b>
 * ここでは実際に落とす。
 * </p>
 *
 * <p>
 * 積むときに {@code data.fail_until} を入れると、<b>その回数までは失敗する。</b>
 * </p>
 * <pre>
 * // 2回落ちて、3回目に通る
 * new MailExecutor().put(db, new Data()
 *     .putData("to", "hanako@example.co.jp")
 *     .putData("fail_until", 2));
 *
 * // ずっと落ちる → maxRetry() を使い切って dead になる
 * new MailExecutor().put(db, new Data()
 *     .putData("to", "hanako@example.co.jp")
 *     .putData("fail_until", 99));
 * </pre>
 *
 * <p>
 * <b>設定ではなく積んだデータで決めている。</b>
 * 設定にすると<b>アプリ全体が同じ振る舞いになり、成功と失敗を並べて見せられない。</b>
 * 行に入っていれば、あとから {@code select * from mq_notice} を見て
 * 「なぜ落ちたのか」が分かる。
 * </p>
 *
 * <h2>何回目かの知り方</h2>
 * <p>
 * 2つある。どちらも同じ数を指すが、<b>意味が違う。</b>
 * </p>
 * <ul>
 *   <li>{@code row.getInt("retry_count")} — <b>やり直した回数</b>（初回は 0）</li>
 *   <li>{@code MqContext.attempt()} — <b>今回が何回目か</b>（初回は 1）</li>
 * </ul>
 *
 * <h2>例外を投げるのと error を返すのの違い</h2>
 * <p>
 * <b>リトライの扱いは同じである。</b>違うのは
 * {@code log_info} に何が残るかと、ログの出方だけ。
 * </p>
 * <ul>
 *   <li>{@link MqStatus#error} を返す → {@code log_info.last_error} に一言</li>
 *   <li>例外を投げる → {@code log_info} にクラス名・メッセージ・スタックトレース</li>
 * </ul>
 * <p>
 * <b>原因が要るなら例外</b>、<b>想定内の失敗なら {@code error}</b> と使い分ける。
 * ここでは想定内なので {@code error} を返す。
 * </p>
 *
 * <p>
 * もう1つ。<b>{@code null} を返すと {@code error} 扱いになる</b>（{@code running} も同じ）。
 * 「まだ処理中」は結果になっていないためである。
 * </p>
 */
public class MailExecutor extends MqExecutor {

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

		return "mail";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MqExecuteType executeType () {

		return MqExecuteType.short_time;

	}

	/**
	 * やり直す回数
	 *
	 * <p>
	 * 既定は 3。ここでは<b>デッドレターまで短時間で見えるように</b> 2 にしてある。
	 * {@code 0} にすると1回で {@link MqStatus#dead} になる。
	 * </p>
	 *
	 * @return	回数
	 */
	@Override
	public int maxRetry () {

		return 2;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MqStatus execute (DB db, Data row) {

		Data data = row.getDataOptional("data");

		int failUntil = data.getInt("fail_until");
		int attempt = attempt();

		if (attempt <= failUntil) {

			Log.warn("メールを送れませんでした: to=%s / %d 回目（%d 回目までは落ちる指定）"
				.formatted(data.getStringOptional("to"), attempt, failUntil));

			return MqStatus.error;

		}

		Log.info("メールを送りました: to=%s / %d 回目"
			.formatted(data.getStringOptional("to"), attempt));

		return MqStatus.completed;

	}

	/**
	 * 今回が何回目か（1 始まり）
	 *
	 * <p>
	 * {@link MqContext} は<b>メッセージ1件ごとに作られる</b>実行の器である
	 * （要件 F-M-01）。ログにも、この器の識別子がそのまま出る。
	 * </p>
	 *
	 * @return	回数
	 */
	private static int attempt () {

		if (Context.current() instanceof MqContext mqContext) {
			return mqContext.attempt();
		}

		return 1;

	}

}
