package approval.jobs.mq;

/**
 * このサンプルが使うキューの名前
 *
 * <p>
 * <b>キューのテーブル名はアプリが決める。</b>
 * 決めるということは、<b>codegen の除外一覧に自分で足す</b>ということでもある
 * （{@code conf/application.conf} の {@code codegen.exclude_tables}）。
 * 足さないと、キューのテーブル定義クラスがアプリのコードとして生成される。
 * </p>
 *
 * <p>
 * スケジューラは自分のキュー（{@code mq_scheduler}）を自分で作るので、
 * アプリが名前を書くのはこちらだけである。ただし
 * <b>除外一覧には {@code mq_scheduler} も要る</b>（同じ DB にできるため）。
 * </p>
 */
public final class JobsQueue {

	/** 通知のキュー（3つの Executor が同じキューに乗る） */
	public static final String NOTICE = "mq_notice";

	private JobsQueue () {
	}

}
