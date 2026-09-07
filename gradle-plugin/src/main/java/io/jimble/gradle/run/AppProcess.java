package io.jimble.gradle.run;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * アプリの子プロセス（要件 F-X-02）
 *
 * <p>
 * {@code java -cp ... <mainClass>} を別プロセスで起動する。
 * </p>
 *
 * <p>
 * <b>移送元は同じ JVM の中で動かしていた。</b>速いが、
 * 止まりきらなかったスレッドが次の起動と二重に動く。
 * スケジューラや MQ ワーカーを持つアプリでは、
 * <b>同じジョブが2回走る</b>という形で表に出る。
 * プロセスを分ければ、殺した時点で必ず全部止まる。
 * </p>
 */
final class AppProcess {

	/** クラスパスが長すぎるときにファイル経由にする閾値（Windows のコマンドライン上限に合わせる） */
	private static final int ARG_FILE_THRESHOLD = 30000;

	/* 起動するプロセス */
	private final Process process;

	/* ログを流すスレッド */
	private final Thread pump;

	/* 待ち受けポート */
	private final int port;

	/**
	 * コンストラクタ
	 *
	 * @param process	プロセス
	 * @param port		待ち受けポート
	 * @param log		ログの出し先
	 */
	private AppProcess (Process process, int port, RunLog log) {

		this.process = process;
		this.port = port;

		this.pump = new Thread(() -> pump(process.getInputStream(), log), "jimble-run-app-log");
		this.pump.setDaemon(true);
		this.pump.start();

	}

	/**
	 * 起動する
	 *
	 * @param spec	起動の指定
	 * @param log	ログの出し先
	 * @return	プロセス
	 * @throws IOException	起動に失敗した場合
	 */
	static AppProcess start (AppSpec spec, RunLog log) throws IOException {

		List<String> command = new ArrayList<>();
		command.add(spec.javaExecutable());
		command.addAll(spec.jvmArgs());

		/*
		 * ログの文字化けを防ぐ（要件 F-U-12）。
		 * 子プロセスはコンソールに繋がっていないので、
		 * 指定しないと環境によっては US-ASCII になる。
		 */
		command.add("-Dfile.encoding=UTF-8");
		command.add("-Dstdout.encoding=UTF-8");
		command.add("-Dstderr.encoding=UTF-8");

		command.add("-Djimble.env=" + spec.env());
		command.add("-Djimble.server.port=" + spec.port());

		command.add("-cp");
		command.add(classpathArgument(spec));

		command.add(spec.mainClass());
		command.addAll(spec.args());

		ProcessBuilder builder = new ProcessBuilder(command)
			.directory(spec.workingDir())
			// 標準エラーも同じ流れに混ぜる。アプリのログは元々1本である
			.redirectErrorStream(true);

		log.debug("アプリを起動します: " + spec.mainClass() + " (port=" + spec.port() + ")");

		return new AppProcess(builder.start(), spec.port(), log);

	}

	/**
	 * すでに誰かが待ち受けていないか
	 *
	 * <p>
	 * ここを見ないと、前のプロセスが生き残っていたときに
	 * <b>「アプリが起きました」と言いながら古いコードが応え続ける</b>。
	 * ポートに繋がるかどうかで見ているので、区別が付かない。
	 * </p>
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

			if (!process.isAlive()) {
				return false;
			}

			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
				return true;
			} catch (IOException ignore) {
				// まだ起きていない
			}

			try {
				Thread.sleep(50);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return false;
			}

		}

		return false;

	}

	/**
	 * 生きているか
	 *
	 * @return	生きている場合 = true
	 */
	boolean isAlive () {

		return process.isAlive();

	}

	/**
	 * 止める
	 *
	 * <p>
	 * まず終了を頼み（{@code SIGTERM}）、待っても死ななければ殺す。
	 * jimble は終了フックで DB 接続とバッチを畳むので、
	 * <b>いきなり殺すと未コミットのトランザクションが残る。</b>
	 * </p>
	 */
	void stop () {

		if (!process.isAlive()) {
			return;
		}

		process.destroy();

		try {
			if (!process.waitFor(10, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				process.waitFor(5, TimeUnit.SECONDS);
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
		}

		try {
			pump.join(1000);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

	}

	/**
	 * ログを流す
	 *
	 * @param stream	入力
	 * @param log		出し先
	 */
	private static void pump (InputStream stream, RunLog log) {

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {

			String line;
			while ((line = reader.readLine()) != null) {
				log.app(line);
			}

		} catch (IOException ignore) {
			// プロセスを止めたときにここへ来る
		}

	}

	/**
	 * {@code -cp} に渡すもの
	 *
	 * <p>
	 * 長すぎるとコマンドラインの上限に当たるので、
	 * その場合は JAR の {@code Class-Path} 経由にする。
	 * </p>
	 *
	 * @param spec	起動の指定
	 * @return	渡すもの
	 * @throws IOException	一時ファイルを作れなかった場合
	 */
	private static String classpathArgument (AppSpec spec) throws IOException {

		String classpath = spec.classpath();

		if (classpath.length() < ARG_FILE_THRESHOLD) {
			return classpath;
		}

		return ClasspathJar.create(classpath, spec.workingDir()).getAbsolutePath();

	}

}
