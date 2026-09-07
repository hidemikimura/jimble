package io.jimble.core.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 「全部止める」の1か所（要件 D-77）
 *
 * <p>
 * jimble が立てるもの（サーバー・スケジューラ・DB のプール）は、
 * <b>ここに「止め方」を預ける。</b>
 * </p>
 *
 * <pre>
 * Shutdown.add("サーバー", server::stop);
 * </pre>
 *
 * <h2>何のためにあるか</h2>
 * <p>
 * 普通に動かしているぶんには要らない。<b>プロセスが終われば全部止まる。</b>
 * </p>
 *
 * <p>
 * 要るのは<b>プロセスを終わらせずにアプリを入れ替えるとき</b>である
 * （開発用のホットリロード {@code jimbleRun}）。
 * 止め忘れたものは<b>入れ替えたあとの新しいものと二重に動く。</b>
 * スケジューラや MQ ワーカーだと「同じジョブが2回走る」という形で表に出る。
 * </p>
 *
 * <p>
 * <b>止め方を3か所に散らさない。</b>
 * 散らすと、止める側（開発ツール）が<b>クラス名を1つずつ知っている</b>ことになり、
 * 新しく何かを立てるたびに向こうを直さないと止まらなくなる。
 * </p>
 *
 * <p>
 * アプリが自分で立てたものも、ここに預ければ一緒に止まる。
 * </p>
 */
public final class Shutdown {

	/** システムプロパティ：ホットリロードで動いている（JVM のフックを付けない） */
	public static final String PROPERTY_HOT_RELOAD = "jimble.dev.hot_reload";

	/* 止め方（登録順） */
	private static final List<Hook> HOOKS = new CopyOnWriteArrayList<>();

	/* 止め始めた（ヘルスチェックを落とす） */
	private static final AtomicBoolean STOPPING = new AtomicBoolean(false);

	/* 新しいリクエストを受けない */
	private static final AtomicBoolean DRAINING = new AtomicBoolean(false);

	/* JVM のフックを付けたか */
	private static final AtomicBoolean HOOK_INSTALLED = new AtomicBoolean(false);

	private Shutdown () {}

	/**
	 * 止め方
	 *
	 * @param name	名前（どれが失敗したか分かるように）
	 * @param stop	止める処理
	 */
	public record Hook(String name, Runnable stop) {}

	/**
	 * 止め方を預ける
	 *
	 * <p>
	 * <b>後に預けたものから先に止める</b>（立てた順の逆）。
	 * DB を先に閉じると、止まる途中のスケジューラが DB を使えない。
	 * </p>
	 *
	 * @param name	名前
	 * @param stop	止める処理
	 */
	public static void add (String name, Runnable stop) {

		HOOKS.add(new Hook(name, stop));

	}

	/**
	 * 預かっているもの
	 *
	 * @return	止め方（登録順）
	 */
	public static List<Hook> hooks () {

		return List.copyOf(HOOKS);

	}

	/**
	 * 全部止める
	 *
	 * <p>
	 * <b>1つ失敗しても残りを止める。</b>途中でやめると、
	 * そこから先が全部残る。
	 * </p>
	 *
	 * @return	止められなかったものの名前（全部止まれば空）
	 */
	public static List<String> runAll () {

		markStopping();

		List<Hook> hooks = new ArrayList<>(HOOKS);
		HOOKS.clear();

		List<String> failed = new ArrayList<>();

		// 立てた順の逆
		for (int index = hooks.size() - 1; index >= 0; index--) {

			Hook hook = hooks.get(index);

			try {
				hook.stop().run();
			} catch (Throwable cause) {
				failed.add("%s (%s)".formatted(hook.name(), cause));
			}

		}

		return failed;

	}


	/**
	 * 止め始めたことにする（要件 D-91）
	 *
	 * <p>
	 * <b>ヘルスチェックを先に落とすため</b>にある。
	 * ここから先も<b>普通のリクエストはまだ受ける</b>。
	 * ロードバランサが「この台は外す」と気づくまでに時間がかかるので、
	 * その間に来たものを 503 にすると<b>外から見たらエラー</b>になる。
	 * </p>
	 */
	public static void markStopping () {

		STOPPING.set(true);

	}

	/**
	 * 新しいリクエストを受けないことにする（要件 D-91）
	 *
	 * <p>ここから先に来たものは 503 で断る。</p>
	 */
	public static void markDraining () {

		STOPPING.set(true);
		DRAINING.set(true);

	}

	/**
	 * 止め始めているか
	 *
	 * <p>
	 * <b>ヘルスチェックで見る。</b>
	 * </p>
	 *
	 * <pre>
	 * get("/health_check", context -&gt;
	 *     context.response().send(Shutdown.isStopping() ? 503 : 200));
	 * </pre>
	 *
	 * @return	止め始めていれば true
	 */
	public static boolean isStopping () {

		return STOPPING.get();

	}

	/**
	 * 新しいリクエストを断つ段階か
	 *
	 * @return	断つなら true
	 */
	public static boolean isDraining () {

		return DRAINING.get();

	}

	/**
	 * 止め始めていないことにする（テスト用）
	 */
	public static void reset () {

		STOPPING.set(false);
		DRAINING.set(false);

	}

	/**
	 * SIGTERM で全部止まるようにする（要件 D-91）
	 *
	 * <p>
	 * <b>コンテナは SIGTERM を送って待つ。</b>受け取らずに死ぬと、
	 * 処理中のリクエストが途中で切れ、DB のトランザクションも畳まれない。
	 * </p>
	 *
	 * <p>
	 * <b>1回だけ付ける。</b>ホットリロード（{@code jimbleRun}）のときは付けない。
	 * アプリを入れ替えるたびに Gradle デーモンへフックが溜まり、
	 * 古いクラスローダを掴んだまま残るためである。
	 * </p>
	 */
	public static void installJvmHook () {

		if (Boolean.getBoolean(PROPERTY_HOT_RELOAD)) {
			return;
		}

		if (!HOOK_INSTALLED.compareAndSet(false, true)) {
			return;
		}

		Runtime.getRuntime().addShutdownHook(new Thread(Shutdown::runAll, "jimble-shutdown"));

	}

}
