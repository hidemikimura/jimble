package io.jimble.load;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.jimble.core.lifecycle.CancelOrderNotify;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.Tx;
import io.jimble.mq.MqExecutor;
import io.jimble.mq.MqQueue;
import io.jimble.mq.MqRegistry;
import io.jimble.mq.status.MqExecuteType;
import io.jimble.mq.status.MqStatus;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.thread.VirtualThreadManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MQ が秒あたり何件さばけるかを測る
 *
 * <pre>
 * jimble-load mq &lt;mysql|postgresql&gt; &lt;JDBC の URL&gt; &lt;スレッド数&gt; &lt;件数&gt; &lt;1件の処理ミリ秒&gt;
 * </pre>
 *
 * <p>
 * 先に<b>決まった件数を積んでから</b>ワーカーを回し、全部さばけるまでの時間を測る。
 * 積むのと処理するのを同時にすると、積む側の速さを測ることになる。
 * </p>
 *
 * <p>
 * <b>最初の 1 割は数えない</b>（JIT が温まるまでと、ワーカーが立ち上がるまで）。
 * 残りの 9 割を、1 割目を処理し終えた時刻から最後の 1 件を処理し終えた時刻までで割る。
 * </p>
 *
 * <p>
 * 1件の処理ミリ秒を 0 にすると、MQ そのものの上乗せ（取り出し・渡し・消す）だけが残る。
 * 数ミリ秒にすると、「短い処理を少ないスレッドで回す」ときに、ワーカーが待たされていないかが見える。
 * </p>
 */
final class MqLoad {

	/** キューの名前 */
	static final String QUEUE = "mq_load";

	/** 数えずに捨てる割合 */
	static final double WARMUP_RATIO = 0.1;

	/** 積むときに1回のトランザクションに入れる件数 */
	static final int PUT_BATCH = 1000;

	/** 全部さばけるまで待つ上限（秒） */
	static final int TIMEOUT_SECONDS = 600;

	/* 処理した件数 */
	private static final AtomicLong DONE = new AtomicLong();

	/* 数え始めた時刻（ナノ秒） */
	private static final AtomicLong STARTED = new AtomicLong();

	/* 最後の1件を処理し終えた時刻（ナノ秒） */
	private static final AtomicLong FINISHED = new AtomicLong();

	/* 1件の処理ミリ秒 */
	private static volatile long workMillis;

	/* 数え始める件数 */
	private static volatile long warmupCount;

	/* 全部の件数 */
	private static volatile long totalCount;

	private MqLoad () {
	}

	/**
	 * 何もしない（か、決まった時間だけ眠る）処理
	 */
	public static final class LoadExecutor extends MqExecutor {

		@Override public String queueName () { return QUEUE; }
		@Override public String key () { return "load"; }
		@Override public MqExecuteType executeType () { return MqExecuteType.short_time; }

		@Override
		public MqStatus execute (DB db, Data row) {

			if (workMillis > 0) {
				try {
					Thread.sleep(workMillis);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}

			long done = DONE.incrementAndGet();

			if (done == warmupCount) {
				STARTED.set(System.nanoTime());
			}
			if (done == totalCount) {
				FINISHED.set(System.nanoTime());
			}

			return MqStatus.completed;

		}

	}

	/**
	 * 止められる通知
	 */
	private static final class Cancel implements CancelOrderNotify {

		private volatile boolean cancelled = false;

		@Override public boolean isCancelOrder () { return cancelled; }
		@Override public void doCancel () { cancelled = true; }

	}

	/**
	 * 測る
	 *
	 * @param args	引数（先頭は "mq"）
	 * @return	全部さばけた場合 = true
	 * @throws Exception	失敗した場合
	 */
	static boolean run (String[] args) throws Exception {

		String product = args[1];
		String url = args[2];
		int threads = Integer.parseInt(args[3]);
		long messages = Long.parseLong(args[4]);
		workMillis = Long.parseLong(args[5]);

		totalCount = messages;
		warmupCount = Math.max(1, (long) (messages * WARMUP_RATIO));

		Conf.replace(config(product, url, threads).withFallback(Conf.conf().config()));
		DBUtil.load(Conf.conf().config(), MqLoad.class);

		try {
			return measure(product, threads, messages);
		} finally {
			DBUtil.stop();
		}

	}

	/**
	 * 設定を組み立てる
	 *
	 * <p>
	 * <b>プールはスレッド数より少し多くする。</b>ワーカーは処理し終えた行を消すのに1本ずつ借り、
	 * 取り出し役も1本借りる。足りないと、MQ ではなくプールの取り合いを測ることになる。
	 * </p>
	 *
	 * @param product	製品
	 * @param url		JDBC の URL
	 * @param threads	スレッド数
	 * @return	設定
	 */
	private static Config config (String product, String url, int threads) {

		boolean postgres = "postgresql".equals(product);
		int pool = threads + 4;

		return ConfigFactory.parseString("""
			db.mq_load {
				main = true
				product = "%s"
				driver = "%s"
				url = "%s"
				username = "jimble"
				password = "jimble"
				maximum_pool_size = %d
				minimum_idle = %d
				connection_timeout = 10s
				connection_pool_type = "hikari"
			}
			mq.thread_count.short_time = %d
			""".formatted(
				postgres ? "postgresql" : "mysql"
				, postgres ? "org.postgresql.Driver" : "org.mariadb.jdbc.Driver"
				, url
				, pool
				, pool
				, threads));

	}

	/**
	 * 積んでから回して、秒あたりの件数を出す
	 *
	 * @param product	製品
	 * @param threads	スレッド数
	 * @param messages	件数
	 * @return	全部さばけた場合 = true
	 * @throws Exception	失敗した場合
	 */
	private static boolean measure (String product, int threads, long messages) throws Exception {

		DB db = DBUtil.getMainDB();
		MqQueue queue = new MqQueue(QUEUE);

		queue.install();
		db.execute("TRUNCATE TABLE %s".formatted(db.dialect().identifier(QUEUE)));

		MqRegistry.clear();
		MqRegistry.add(LoadExecutor::new);

		put(db, messages);

		Cancel cancel = new Cancel();
		VirtualThreadManager manager = queue.startNoWait(cancel);

		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

		while (FINISHED.get() == 0 && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}

		cancel.doCancel();
		manager.awaitTermination(30, TimeUnit.SECONDS);

		long done = DONE.get();
		long left = queue.pendingCount();

		if (FINISHED.get() == 0) {
			System.out.printf("%s\tthreads=%d\twork=%dms\t時間内に終わりませんでした（%d / %d 件）%n"
				, product, threads, workMillis, done, messages);
			return false;
		}

		double seconds = (FINISHED.get() - STARTED.get()) / 1e9;
		double rate = (messages - warmupCount) / seconds;

		System.out.printf("%s\tthreads=%d\twork=%dms\tmessages=%d\t%.0f 件/秒\t残り %d 件%n"
			, product, threads, workMillis, messages, rate, left);

		return left == 0;

	}

	/**
	 * 積む（まとめてコミットする。1件ずつだと積むだけで時間がかかる）
	 *
	 * @param db		DB
	 * @param messages	件数
	 */
	private static void put (DB db, long messages) {

		LoadExecutor executor = new LoadExecutor();

		for (long i = 0; i < messages; i += PUT_BATCH) {

			try (Tx tx = db.begin()) {

				for (long n = i; n < Math.min(messages, i + PUT_BATCH); n++) {
					executor.put(db, new Data().putData("n", n));
				}

				tx.commit();

			}

		}

	}

}
