package io.jimble.gradle.run;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * アプリを<b>同じ JVM の中で</b>動かす（要件 F-X-02 / D-77）
 *
 * <p>
 * 作り直すたびに<b>新しいクラスローダ</b>を作り、そこで {@code main} を呼ぶ。
 * 古いほうは止めてから捨てる。
 * </p>
 *
 * <h2>なぜ同じ JVM なのか</h2>
 * <p>
 * <b>IDE のデバッガがそのまま効く</b>ためである。
 * 別プロセスにしていたときは、IDE で「Gradle タスクをデバッグ実行」しても
 * 止まるのは Gradle のほうで、アプリのブレークポイントには止まらなかった。
 * リモートデバッガを別に繋ぐ必要があり、<b>作り直すたびに繋ぎ直し</b>だった。
 * </p>
 *
 * <h2>そのかわり、止めるのは自分の仕事になる</h2>
 * <p>
 * プロセスを殺せないので、<b>アプリが立てたものは自分で止めないと残る。</b>
 * 残ったスケジューラや MQ ワーカーは<b>入れ替えたあとの新しいものと二重に動く</b>
 * （同じジョブが2回走る）。だから {@link #shutdown} で
 * jimble が立てるもの（サーバー・スケジューラ・DB）を順に止め、
 * <b>それでも残ったスレッドがあれば名前を出して言う</b>。
 * </p>
 *
 * <h2>クラスローダの親</h2>
 * <p>
 * 親を<b>プラットフォームのクラスローダ</b>にする。Gradle のクラスパス
 * （Groovy / Kotlin / Gradle 本体）をアプリに見せないためである。
 * 見せると、アプリが使っているライブラリと版が食い違ったときに
 * <b>Gradle 側の版が勝つ</b>、という追いにくい壊れ方をする。
 * </p>
 */
final class AppRunner {

	/** 「全部止める」の入口（jimble 側。要件 D-77） */
	private static final String SHUTDOWN_CLASS = "io.jimble.core.lifecycle.Shutdown";

	/**
	 * helidon に「全体のフィルタが無かったときどうするか」を伝えるもの
	 *
	 * <p>{@code IGNORE} にすると helidon はフィルタを張らない。</p>
	 */
	private static final String HELIDON_MISSING_ACTION = "helidon.serialFilter.missing.action";

	/** 止まるのを待つ上限 */
	private static final Duration STOP_TIMEOUT = Duration.ofSeconds(20);

	/**
	 * もう言ったスレッド
	 *
	 * <p>
	 * <b>入れ替えのたびに同じことを言わない。</b>
	 * helidon のタイマーのように、こちらでは止めようのないものが必ず残る。
	 * 毎回出すと<b>読まれなくなり、本当に困るもの（アプリのスケジューラなど）を
	 * 見落とす。</b>名前ごとに1回だけ言う。
	 * </p>
	 */
	private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

	/* クラスローダ */
	private final URLClassLoader loader;

	/* main を走らせるスレッド */
	private final Thread thread;

	/* 待ち受けポート */
	private final int port;

	/* ログ */
	private final RunLog log;

	/* main が投げた例外 */
	private volatile Throwable failure;

	/* main を抜けたか */
	private volatile boolean finished;

	/**
	 * コンストラクタ
	 *
	 * @param spec	起動の指定
	 * @param log	ログ
	 * @throws IOException	クラスパスを組み立てられなかった場合
	 */
	private AppRunner (AppSpec spec, RunLog log) throws IOException {

		this.port = spec.port();
		this.log = log;
		this.loader = new URLClassLoader("jimble-app", urls(spec.classpath())
			, ClassLoader.getPlatformClassLoader());

		this.thread = new Thread(() -> invokeMain(spec), "jimble-app-main");
		this.thread.setContextClassLoader(loader);
		this.thread.setDaemon(true);

	}

	/**
	 * 起動する
	 *
	 * @param spec	起動の指定
	 * @param log	ログ
	 * @return	起動したもの
	 * @throws IOException	クラスパスを組み立てられなかった場合
	 */
	static AppRunner start (AppSpec spec, RunLog log) throws IOException {

		log.debug("アプリを起動します: " + spec.mainClass() + " (port=" + spec.port() + ")");

		/*
		 * 別プロセスのときは -D で渡していたもの。
		 *
		 * 同じ JVM なので<b>システムプロパティは Gradle デーモンのもの</b>である。
		 * jimbleRun のあいだだけ入れっぱなしにする（入れ替えても値は同じ）。
		 */
		System.setProperty("jimble.env", spec.env());
		System.setProperty("jimble.server.port", String.valueOf(spec.port()));

		keepDaemonDeserializable(log);

		AppRunner runner = new AppRunner(spec, log);
		runner.thread.start();

		return runner;

	}

	/**
	 * helidon に JVM 全体の直列化フィルタを張らせない
	 *
	 * <p>
	 * helidon は {@code WebServer} を立てるときに
	 * {@code SerializationConfig.configureRuntime()} を呼び、
	 * <b>JVM 全体の直列化フィルタ</b>
	 * （{@code ObjectInputFilter.Config.setSerialFilter}）を
	 * 「許可リスト + {@code !*}」で張る。
	 * 攻撃で任意のクラスを読み戻されないためのもので、
	 * <b>アプリが自分のプロセスで動いているうちは正しい</b>。
	 * </p>
	 *
	 * <h2>ここでは正しくない</h2>
	 * <p>
	 * jimbleRun は<b>アプリを Gradle デーモンの中で動かす</b>（要件 F-X-02 / D-77）。
	 * つまりこのフィルタは<b>デーモンに張られる</b>。
	 * フィルタは一度きりで、外せない。アプリを止めても<b>デーモンに残る</b>。
	 * </p>
	 * <p>
	 * Gradle は自分のビルドサービスのパラメータを Java 直列化で読み戻す。
	 * その {@code ObjectInputStream} にもこのフィルタが載るので、
	 * <b>次のビルドが始まる前に落ちる。</b>
	 * </p>
	 * <pre>
	 * Couldn't populate class org.gradle.api.services.BuildServiceParameters$None
	 * &gt; filter status: REJECTED
	 * </pre>
	 * <p>
	 * <b>「1回目は動く。止めてもう1回動かすと起動しない」</b>という出かたをする。
	 * デーモンを作り直す（IDE の同期、{@code gradle --stop}）と直るので、
	 * 原因がここだと分かりにくい。
	 * </p>
	 *
	 * <h2>本番は変わらない</h2>
	 * <p>
	 * 切るのは<b>jimbleRun のあいだだけ</b>である。
	 * 本番はアプリが自分の JVM で動くので、helidon はいつもどおりフィルタを張る。
	 * 自分で {@code -Dhelidon.serialFilter.missing.action=...} を
	 * 指定している場合は、そちらを尊重して何もしない。
	 * </p>
	 *
	 * @param log	ログ
	 */
	static void keepDaemonDeserializable (RunLog log) {

		if (System.getProperty(HELIDON_MISSING_ACTION) != null) {
			return;
		}

		System.setProperty(HELIDON_MISSING_ACTION, "IGNORE");

		log.debug("helidon の JVM 全体の直列化フィルタを切りました"
			+ "（Gradle デーモンの中で動かすため。本番では切りません）");

	}

	/**
	 * すでに誰かが待ち受けていないか
	 *
	 * @param port	ポート
	 * @return	誰かが待ち受けている場合 = true
	 */
	static boolean isPortTaken (int port) {

		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
			return true;
		} catch (IOException ex) {
			return false;
		}

	}

	/**
	 * 待ち受けが始まるまで待つ
	 *
	 * <p>
	 * <b>ポートに繋がるかどうかで見る。</b>ログの文字列を待つと、
	 * アプリがログの出し方を変えただけで止まらなくなる。
	 * </p>
	 *
	 * @param timeout	上限
	 * @return	待ち受けを始めた場合 = true
	 */
	boolean awaitReady (Duration timeout) {

		long deadline = System.nanoTime() + timeout.toNanos();

		while (System.nanoTime() < deadline) {

			if (failure != null) {
				return false;
			}

			if (isPortTaken(port)) {
				return true;
			}

			/*
			 * main を抜けても待ち受けが始まっていないなら、それは失敗である。
			 * JimbleServer.start() はブロックしないので、
			 * <b>main を抜けること自体は普通</b>。ポートで見る。
			 */
			if (finished && !isPortTaken(port)) {
				// 抜けた直後はまだ開いていないことがあるので、少しだけ待つ
				sleep(200);
				return isPortTaken(port);
			}

			sleep(50);

		}

		return false;

	}

	/**
	 * 生きているか
	 *
	 * @return	生きている場合 = true
	 */
	boolean isAlive () {

		return failure == null && isPortTaken(port);

	}

	/**
	 * main が投げた例外
	 *
	 * @return	例外（無ければ null）
	 */
	Throwable failure () {

		return failure;

	}

	/**
	 * このクラスローダで読んだクラス（テスト用）
	 *
	 * @param className	クラス名
	 * @return	クラス
	 * @throws ClassNotFoundException	無い場合
	 */
	Class<?> loadedClass (String className) throws ClassNotFoundException {

		return Class.forName(className, false, loader);

	}

	/**
	 * 止める
	 *
	 * <p>
	 * jimble が立てるものを順に止めてから、クラスローダを捨てる。
	 * </p>
	 *
	 * <p>
	 * <b>止め方は「待ち受けを始める前」に預けること。</b>
	 * ポートを開いてから {@code Shutdown.add(...)} を呼ぶまでのあいだに
	 * ここが止めに来ると、<b>預かっているものがまだ無いので何も止まらない。</b>
	 * 古いアプリがポートを握ったまま残り、入れ替えたほうが立ち上がれない。
	 * </p>
	 *
	 * @return	<b>ポートが空いた場合 = true</b>（残ったスレッドがあっても、ポートが空けば true）
	 */
	boolean stop () {

		shutdown();

		try {
			thread.join(5000);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

		boolean released = awaitPortReleased();
		List<String> leftover = leftoverThreads();

		if (!released) {
			log.error("""
				%d 番がまだ空きません。
				  古いアプリが残ったままなので、入れ替えても<b>古いコードが応え続けます。</b>
				  止め方を Shutdown.add(...) に預けているか確かめてください。"""
				.formatted(port).replace("<b>", "").replace("</b>", ""));
		}

		List<String> unreported = leftover.stream().filter(REPORTED::add).toList();

		if (!unreported.isEmpty()) {
			log.error("""
				止まらなかったスレッドがあります: %s
				  同じ JVM で動かしているので、止めないものは残ります。
				  入れ替えたあとの新しいものと二重に動きます（同じジョブが2回走る）。
				  自分で立てたものなら Shutdown.add("名前", 止める処理) を書いてください。
				  （同じ名前はこのあと出しません）"""
				.formatted(String.join(", ", unreported)));
		}

		/*
		 * <b>残っているなら閉じない。</b>
		 * 閉じるとクラスパスの jar が読めなくなるので、
		 * 残ったスレッドが NoClassDefFoundError を撒き散らす。
		 * 「止まらなかった」より分かりにくい壊れ方になる。
		 */
		if (!released || !leftover.isEmpty()) {
			log.debug("古いクラスローダは閉じません（まだ使われています）");
			return released;
		}

		try {
			loader.close();
		} catch (IOException ex) {
			log.debug("クラスローダを閉じられませんでした: " + ex.getMessage());
		}

		return true;

	}

	// region 中身

	/**
	 * 止める
	 *
	 * <p>
	 * jimble の {@code Shutdown.runAll()} を1つ呼ぶだけである。
	 * <b>プラグインが「何を止めるか」を知らない</b>ようにしてある。
	 * 知っていると、jimble が新しく何かを立てるたびにここを直す羽目になり、
	 * <b>直し忘れると黙って止まらなくなる。</b>
	 * </p>
	 */
	private void shutdown () {

		try {

			Class<?> type = Class.forName(SHUTDOWN_CLASS, true, loader);

			@SuppressWarnings("unchecked")
			List<String> failed = (List<String>) type.getMethod("runAll").invoke(null);

			if (failed != null && !failed.isEmpty()) {
				log.error("止められなかったものがあります: " + String.join(", ", failed));
			}

		} catch (ClassNotFoundException ex) {

			log.error("""
				%s が見つかりません。
				  jimbleRun はアプリを同じ JVM で動かすので、止める口が要ります。
				  jimble を新しくしてください。"""
				.formatted(SHUTDOWN_CLASS));

		} catch (Exception ex) {

			log.error("止める途中で失敗しました: " + ex);

		}

	}

	/**
	 * ポートが空くのを待つ
	 *
	 * <p>
	 * <b>止めろと言った直後はまだ空いていない。</b>
	 * 空く前に次を起動すると、運が悪いと<b>古いほうが応え続ける。</b>
	 * </p>
	 *
	 * @return	空いた場合 = true
	 */
	private boolean awaitPortReleased () {

		long deadline = System.nanoTime() + STOP_TIMEOUT.toNanos();

		while (System.nanoTime() < deadline) {

			if (!isPortTaken(port)) {
				return true;
			}

			sleep(50);

		}

		return !isPortTaken(port);

	}

	/**
	 * 止まりきらなかったスレッド
	 *
	 * <p>
	 * <b>デーモンスレッドも仮想スレッドも数える。</b>
	 * 最初はデーモンを外していたが、いまどきのアプリは
	 * {@code Thread.ofVirtual()} で立てる（仮想スレッドは常にデーモンである）。
	 * 外すと<b>いちばん見つけたいものが見つからない。</b>
	 * </p>
	 *
	 * @return	名前
	 */
	private List<String> leftoverThreads () {

		List<String> names = new ArrayList<>();

		for (Thread other : Thread.getAllStackTraces().keySet()) {

			if (other == thread || !other.isAlive()) {
				continue;
			}

			if (other.getContextClassLoader() == loader) {
				names.add(other.getName());
			}

		}

		return names;

	}

	/**
	 * {@code main} を呼ぶ
	 *
	 * @param spec	起動の指定
	 */
	private void invokeMain (AppSpec spec) {

		try {

			Class<?> mainClass = Class.forName(spec.mainClass(), true, loader);
			Method main = mainClass.getMethod("main", String[].class);

			main.invoke(null, (Object) spec.args().toArray(new String[0]));

		} catch (Throwable cause) {

			Throwable actual = cause instanceof java.lang.reflect.InvocationTargetException invocation
				&& invocation.getCause() != null ? invocation.getCause() : cause;

			this.failure = actual;

			log.error("アプリが落ちました: " + actual);

			for (StackTraceElement element : actual.getStackTrace()) {
				log.app("\tat " + element);
			}

		} finally {

			this.finished = true;

		}

	}

	/**
	 * クラスパスを URL にする
	 *
	 * @param classpath	クラスパス
	 * @return	URL
	 * @throws MalformedURLException	組み立てられなかった場合
	 */
	private static URL[] urls (String classpath) throws MalformedURLException {

		List<URL> urls = new ArrayList<>();

		for (String entry : classpath.split(File.pathSeparator)) {

			if (entry.isEmpty()) {
				continue;
			}

			urls.add(new File(entry).toURI().toURL());

		}

		return urls.toArray(new URL[0]);

	}

	/**
	 * 待つ
	 *
	 * @param millis	ミリ秒
	 */
	private static void sleep (long millis) {

		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

	}

	// endregion

}
