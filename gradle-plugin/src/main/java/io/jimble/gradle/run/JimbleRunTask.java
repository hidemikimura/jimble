package io.jimble.gradle.run;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@code jimbleRun}（要件 F-X-02）
 *
 * <p>
 * <b>終わらないタスクである。</b>Ctrl-C で止める。
 * </p>
 *
 * <pre>
 * 1. 作り直す
 * 2. アプリを別プロセスで起動する（appPort）
 * 3. プロキシを立てる（port）
 * 4. ソースを見張る
 *    → 変わったら印を付けるだけ
 *    → 次のリクエストが来たら 作り直す → 入れ替える → そのリクエストを転送する
 * </pre>
 */
public abstract class JimbleRunTask extends DefaultTask {

	/* 作り直しが要るか */
	private final AtomicBoolean dirty = new AtomicBoolean(false);

	/* 入れ替えの最中に重ならないようにする */
	private final ReentrantLock swapLock = new ReentrantLock();

	/* 直近の作り直しの結果 */
	private volatile BuildOutcome lastOutcome = BuildOutcome.OK;

	/* ログ */
	private final RunLog log = new RunLog();

	/* いま動いているアプリ */
	private AppRunner app;

	/* プロキシ */
	private DevProxy proxy;

	/* 見張り */
	private SourceWatcher watcher;

	/*
	 * Gradle の Property / FileCollection は「タスクを動かしているスレッド」でしか引けない。
	 * 入れ替えはプロキシのスレッドから走るので、
	 * 起動に要るものは実行のはじめに素の値へ写しておく。
	 *
	 * ここを怠ると、最初の起動だけ成功して
	 * 2回目以降の入れ替えが「例外も出さずに接続が切れる」形で失敗する。
	 */

	/* アプリの起動の指定 */
	private AppSpec appSpec;

	/* 作り直し */
	private GradleBuild gradleBuild;

	/* アプリが起きるのを待つ上限 */
	private Duration startTimeout;

	// region 設定

	/**
	 * 起動クラス
	 *
	 * @return	起動クラス
	 */
	@Input
	public abstract Property<String> getMainClass ();

	/**
	 * 受けるポート
	 *
	 * @return	ポート
	 */
	@Input
	public abstract Property<Integer> getPort ();

	/**
	 * アプリのポート
	 *
	 * @return	ポート
	 */
	@Input
	public abstract Property<Integer> getAppPort ();

	/**
	 * 環境名
	 *
	 * @return	環境名
	 */
	@Input
	public abstract Property<String> getEnv ();

	/**
	 * 作り直しに流すタスク
	 *
	 * @return	タスク
	 */
	@Input
	public abstract ListProperty<String> getBuildTasks ();

	/**
	 * 追加で見張るディレクトリ
	 *
	 * @return	ディレクトリ
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getWatchDirs ();

	/**
	 * 見張らないディレクトリ
	 *
	 * @return	ディレクトリ
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getExcludeDirs ();

	/**
	 * 見張る拡張子
	 *
	 * @return	拡張子
	 */
	@Input
	public abstract ListProperty<String> getWatchExtensions ();

	/**
	 * コマンドライン引数
	 *
	 * @return	引数
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getAppArgs ();

	/**
	 * 再起動のきっかけ
	 *
	 * @return	きっかけ
	 */
	@Input
	public abstract Property<String> getRestartMode ();

	/**
	 * 変更が落ち着いたと見なすまでの時間
	 *
	 * @return	ミリ秒
	 */
	@Input
	public abstract Property<Long> getQuietMillis ();

	/**
	 * アプリが起きるのを待つ上限
	 *
	 * @return	秒
	 */
	@Input
	public abstract Property<Integer> getStartTimeoutSeconds ();

	/**
	 * 実行時クラスパス
	 *
	 * @return	クラスパス
	 */
	@InputFiles
	public abstract ConfigurableFileCollection getRuntimeClasspath ();

	/**
	 * アプリを動かす JVM
	 *
	 * <p>
	 * <b>Gradle デーモンの JVM ではなく、プロジェクトのツールチェーン</b>を使う。
	 * デーモンが Java 21 で本体が Java 25 という組み合わせは普通にあり、
	 * デーモンの {@code java.home} で起動すると
	 * {@code UnsupportedClassVersionError} でアプリだけが立ち上がらない。
	 * </p>
	 *
	 * @return	JVM
	 */
	@Nested
	public abstract Property<JavaLauncher> getJavaLauncher ();

	/**
	 * このプロジェクトのディレクトリ
	 *
	 * @return	ディレクトリ
	 */
	@Internal
	public abstract DirectoryProperty getProjectDir ();

	/**
	 * ルートプロジェクトのディレクトリ
	 *
	 * @return	ディレクトリ
	 */
	@Internal
	public abstract DirectoryProperty getRootDir ();

	// endregion

	/**
	 * 実行する
	 */
	@TaskAction
	public void run () {

		if (!getMainClass().isPresent() || getMainClass().get().isEmpty()) {
			throw new GradleException("""
				jimbleRun { mainClass = "..." } が指定されていません。
				アプリの main を持つクラスを指定してください（例 "blog.BlogApp"）。""");
		}

		RestartMode mode = RestartMode.of(getRestartMode().get());

		resolveOnce();

		Runtime.getRuntime().addShutdownHook(new Thread(this::stopAll, "jimble-run-shutdown"));

		try {

			// 1. まず作り直す
			lastOutcome = build();
			if (!lastOutcome.success()) {
				/*
				 * 最初のビルドが失敗しても止めない。
				 * プロキシは立てて、ブラウザにエラーを出す。
				 * 直して保存してリロードすれば、そのまま続きができる。
				 */
				log.error("作り直しに失敗しました。直して保存し、ブラウザをリロードしてください");
			} else {
				startApp();
			}

			// 2. プロキシ
			startProxy();

			// 3. 見張り
			startWatch(mode);

			log.info("""

				  jimbleRun がブラウザを待っています
				    http://localhost:%d/
				  （Ctrl-C で終了）
				""".formatted(getPort().get()));

			// 見張りは別のスレッドで動く。ここは Ctrl-C まで寝る
			await();

		} finally {

			stopAll();

		}

	}

	// region 組み立て

	/**
	 * Gradle 側の値を素の値へ写す
	 *
	 * <p>
	 * <b>タスクを動かしているスレッドでだけ呼ぶ。</b>
	 * </p>
	 */
	private void resolveOnce () {

		checkDaemonJavaVersion();

		this.appSpec = new AppSpec(
			getMainClass().get()
			, classpath()
			, getEnv().get()
			, getAppPort().get()
			, List.copyOf(getAppArgs().getOrElse(List.of()))
			, getProjectDir().get().getAsFile()
		);

		this.gradleBuild = new GradleBuild(
			getRootDir().get().getAsFile()
			, List.copyOf(getBuildTasks().get())
			, log
		);

		this.startTimeout = Duration.ofSeconds(getStartTimeoutSeconds().get());

	}

	/**
	 * Gradle デーモンの JVM がアプリを動かせるか確かめる（要件 D-77）
	 *
	 * <p>
	 * <b>アプリは Gradle デーモンの JVM で動く。</b>ツールチェーンではない。
	 * デーモンのほうが古いと {@code UnsupportedClassVersionError} になるが、
	 * <b>その文言からは「IDE の Gradle JVM を変えればよい」に辿り着けない。</b>
	 * ここで見て、何を直せばよいかを言って落とす。
	 * </p>
	 */
	private void checkDaemonJavaVersion () {

		int daemon = Runtime.version().feature();
		int toolchain = getJavaLauncher().get().getMetadata().getLanguageVersion().asInt();

		if (daemon >= toolchain) {
			return;
		}

		throw new GradleException("""
			Gradle デーモンの JVM が Java %d です。このプロジェクトは Java %d です。
			  jimbleRun はアプリを同じ JVM の中で動かすので、
			  デーモンのほうが古いと起動できません（UnsupportedClassVersionError）。
			  次のどちらかで Java %d にしてください。
			    - IntelliJ: 設定 > ビルド、実行、デプロイ > ビルドツール > Gradle > Gradle JVM
			    - gradle.properties: org.gradle.java.home=<Java %d のパス>"""
			.formatted(daemon, toolchain, toolchain, toolchain));

	}

	/**
	 * アプリを起動する
	 */
	private void startApp () {

		if (AppRunner.isPortTaken(appSpec.port())) {

			lastOutcome = new BuildOutcome(false, List.of(
				"%d 番はすでに誰かが待ち受けています。".formatted(appSpec.port())
				, ""
				, "前に起動したアプリが残っている可能性があります。"
				, "止めるか、jimbleRun { appPort = ... } で別の番号にしてください。"));

			log.error("%d 番がふさがっています".formatted(appSpec.port()));
			return;

		}

		try {

			app = AppRunner.start(appSpec, log);

			Duration timeout = startTimeout;

			if (app.awaitReady(timeout)) {
				log.debug("アプリが起きました");
				return;
			}

			if (!app.isAlive()) {
				lastOutcome = new BuildOutcome(false, List.of(
					"アプリが起動の途中で落ちました。"
					, "上のログに例外が出ているはずです。"));
				log.error("アプリが起動の途中で落ちました");
				return;
			}

			lastOutcome = new BuildOutcome(false, List.of(
				"アプリが %d 秒たっても %d 番を待ち受けませんでした。"
					.formatted(timeout.toSeconds(), appSpec.port())
				, "ポートが他に使われていないか確認してください。"));
			log.error("アプリが待ち受けを始めません");

		} catch (IOException ex) {

			lastOutcome = new BuildOutcome(false, List.of("アプリを起動できませんでした: " + ex));
			log.error("アプリを起動できませんでした: " + ex.getMessage());

		}

	}

	/**
	 * プロキシを立てる
	 */
	private void startProxy () {

		try {

			proxy = new DevProxy(getPort().get(), getAppPort().get(), this::beforeRequest, log);
			proxy.start();

		} catch (IOException ex) {

			throw new GradleException("""
				%d 番で待ち受けられませんでした: %s
				jimbleRun { port = ... } で変えるか、使っているものを止めてください。"""
				.formatted(getPort().get(), ex.getMessage()), ex);

		}

	}

	/**
	 * 見張りを始める
	 *
	 * @param mode	再起動のきっかけ
	 */
	private void startWatch (RestartMode mode) {

		List<Path> roots = watchRoots();

		if (roots.isEmpty()) {
			log.error("見張るディレクトリがありません");
			return;
		}

		Set<String> extensions = new HashSet<>(getWatchExtensions().get());

		try {

			watcher = new SourceWatcher(roots, excludeRoots(), extensions, path -> onChange(path, mode));
			watcher.start();

			StringJoiner joiner = new StringJoiner(", ");
			for (Path root : roots) {
				joiner.add(getRootDir().get().getAsFile().toPath().relativize(root).toString());
			}
			log.debug("見張ります: " + joiner);

		} catch (IOException ex) {

			log.error("見張りを始められませんでした: " + ex.getMessage());

		}

	}

	// endregion

	// region 入れ替え

	/**
	 * 変わった
	 *
	 * @param path	ファイル
	 * @param mode	再起動のきっかけ
	 */
	private void onChange (Path path, RestartMode mode) {

		if (dirty.getAndSet(true)) {
			// もう印が付いている。連続保存でここへ何度も来る
			return;
		}

		log.info("変わりました: " + relativize(path));

		if (mode == RestartMode.ON_REQUEST) {
			log.info("次のリクエストで入れ替えます");
			return;
		}

		// immediate。落ち着くまで待ってから入れ替える
		Thread thread = new Thread(() -> {

			sleep(getQuietMillis().get());
			swap();

		}, "jimble-run-restart");

		thread.setDaemon(true);
		thread.start();

	}

	/**
	 * 転送の前に呼ばれる
	 *
	 * @return	作り直しの結果
	 */
	private BuildOutcome beforeRequest () {

		if (dirty.get() || app == null || !app.isAlive()) {
			swap();
		}

		return lastOutcome;

	}

	/**
	 * 作り直して入れ替える
	 */
	private void swap () {

		swapLock.lock();

		try {

			if (!dirty.getAndSet(false) && app != null && app.isAlive()) {
				// 待っているあいだに他のリクエストが済ませていた
				return;
			}

			log.info("入れ替えます");

			stopApp();

			lastOutcome = build();

			if (!lastOutcome.success()) {
				return;
			}

			startApp();

		} catch (RuntimeException | Error ex) {

			/*
			 * 入れ替えはプロキシのスレッドからも走る。
			 * ここで投げるとブラウザには「接続が切れた」としか見えず、
			 * 何が起きたのか分からない。
			 */
			lastOutcome = new BuildOutcome(false, List.of("入れ替えに失敗しました: " + ex));
			log.error("入れ替えに失敗しました: " + ex);

		} finally {

			swapLock.unlock();

		}

	}

	/**
	 * 作り直す
	 *
	 * @return	結果
	 */
	private BuildOutcome build () {

		return gradleBuild.run();

	}

	// endregion

	// region 後始末

	/**
	 * すべて止める
	 */
	private void stopAll () {

		if (watcher != null) {
			watcher.close();
			watcher = null;
		}

		if (proxy != null) {
			proxy.stop();
			proxy = null;
		}

		stopApp();

	}

	/**
	 * アプリを止める
	 */
	private void stopApp () {

		if (app == null) {
			return;
		}

		app.stop();
		app = null;

	}

	/**
	 * Ctrl-C まで寝る
	 */
	private void await () {

		try {
			// 見張りもプロキシも別のスレッドで動く。ここはただ待つ
			new java.util.concurrent.CountDownLatch(1).await();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

	}

	// endregion

	// region 小物

	/**
	 * クラスパス
	 *
	 * @return	クラスパス
	 */
	private String classpath () {

		StringJoiner joiner = new StringJoiner(File.pathSeparator);

		for (File file : getRuntimeClasspath().getFiles()) {
			joiner.add(file.getAbsolutePath());
		}

		return joiner.toString();

	}

	/**
	 * 見張るディレクトリ
	 *
	 * @return	ディレクトリ
	 */
	private List<Path> watchRoots () {

		List<Path> roots = new ArrayList<>();

		Path projectDir = getProjectDir().get().getAsFile().toPath();
		addIfExists(roots, projectDir.resolve("src"));
		addIfExists(roots, projectDir.resolve("conf"));

		Path rootDir = getRootDir().get().getAsFile().toPath();
		for (String dir : getWatchDirs().getOrElse(List.of())) {
			addIfExists(roots, rootDir.resolve(dir));
		}

		return roots;

	}

	/**
	 * 見張らないディレクトリ
	 *
	 * @return	ディレクトリ
	 */
	private List<Path> excludeRoots () {

		List<Path> excludes = new ArrayList<>();

		Path rootDir = getRootDir().get().getAsFile().toPath();
		for (String dir : getExcludeDirs().getOrElse(List.of())) {
			excludes.add(rootDir.resolve(dir));
		}

		return excludes;

	}

	/**
	 * あれば足す
	 *
	 * @param paths	足し先
	 * @param path	ディレクトリ
	 */
	private static void addIfExists (List<Path> paths, Path path) {

		if (Files.isDirectory(path)) {
			paths.add(path);
		}

	}

	/**
	 * ルートからの相対パスにする
	 *
	 * @param path	パス
	 * @return	相対パス
	 */
	private String relativize (Path path) {

		try {
			return getRootDir().get().getAsFile().toPath().relativize(path).toString();
		} catch (IllegalArgumentException ex) {
			return path.toString();
		}

	}

	/**
	 * 寝る
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
