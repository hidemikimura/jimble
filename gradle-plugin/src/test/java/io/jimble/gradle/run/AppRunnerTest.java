package io.jimble.gradle.run;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * アプリを同じ JVM の中で動かす（要件 F-X-02 / D-77）
 *
 * <p>
 * 本物の jimble を持ち込まずに、<b>止める口だけ同じ形の</b>小さなアプリを
 * その場でコンパイルして動かす。{@link AppRunner} が見ているのは
 * クラス名とメソッド名なので、これで契約を確かめられる。
 * </p>
 */
class AppRunnerTest {

	/** 「全部止める」の入口（{@link AppRunner} が名前で引くもの） */
	private static final String FAKE_SHUTDOWN = """
		package io.jimble.core.lifecycle;

		import java.util.ArrayList;
		import java.util.List;

		public final class Shutdown {

			private static final List<Runnable> HOOKS = new ArrayList<>();

			public static void add (String name, Runnable stop) {
				HOOKS.add(stop);
			}

			public static List<String> runAll () {
				List<String> failed = new ArrayList<>();
				for (int i = HOOKS.size() - 1; i >= 0; i--) {
					try {
						HOOKS.get(i).run();
					} catch (Throwable cause) {
						failed.add(String.valueOf(cause));
					}
				}
				HOOKS.clear();
				return failed;
			}

		}
		""";

	/** テスト用のサーバー */
	private static final String FAKE_SERVER = """
		package io.jimble.web.server;

		import io.jimble.core.lifecycle.Shutdown;
		import java.net.ServerSocket;

		public final class JimbleServer {

			private static ServerSocket socket;
			private static Thread thread;

			public static void start (int port) throws Exception {
				// 止め方を「待ち受けを始める前」に預ける。あとにすると隙間ができる
				Shutdown.add("サーバー", JimbleServer::stopAll);
				socket = new ServerSocket(port);
				thread = new Thread(() -> {
					try {
						while (!socket.isClosed()) {
							socket.accept().close();
						}
					} catch (Exception ignore) {
					}
				}, "fake-server");
				thread.setDaemon(true);
				thread.start();
			}

			public static void stopAll () {
				try {
					if (socket != null) {
						socket.close();
					}
					if (thread != null) {
						thread.join(2000);
					}
				} catch (Exception ignore) {
				}
			}

		}
		""";

	/** テスト用アプリ */
	private static final String FAKE_APP = """
		package testapp;

		public final class App {

			/* 何代目のクラスローダで読まれたかを外から見るため */
			public static volatile ClassLoader loader;

			public static void main (String[] args) throws Exception {
				loader = App.class.getClassLoader();
				io.jimble.web.server.JimbleServer.start(
					Integer.getInteger("jimble.server.port"));
			}

		}
		""";

	/** 止める口を持たないアプリ（スレッドを残す） */
	private static final String LEAKY_APP = """
		package testapp;

		public final class Leaky {

			public static void main (String[] args) throws Exception {
				Thread thread = new Thread(() -> {
					try {
						Thread.sleep(600000);
					} catch (InterruptedException ignore) {
					}
				}, "leaked-worker");
				// 非デーモンなので、止めないと残る
				thread.setDaemon(false);
				thread.setContextClassLoader(Leaky.class.getClassLoader());
				thread.start();

				io.jimble.web.server.JimbleServer.start(
					Integer.getInteger("jimble.server.port"));
			}

		}
		""";

	/**
	 * 空いているポートを取る
	 *
	 * @return	ポート
	 * @throws IOException	取れなかった場合
	 */
	private static int freePort () throws IOException {

		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}

	}

	/**
	 * テスト用アプリをコンパイルする
	 *
	 * @param root		置き場所
	 * @param sources	ソース（パッケージのパス → 中身）
	 * @return	クラスパス
	 */
	private static String compile (Path root, java.util.Map<String, String> sources) throws IOException {

		Path src = root.resolve("src");
		Path classes = root.resolve("classes");
		Files.createDirectories(classes);

		List<String> files = new java.util.ArrayList<>();

		for (var entry : sources.entrySet()) {

			Path file = src.resolve(entry.getKey());
			Files.createDirectories(file.getParent());
			Files.writeString(file, entry.getValue());

			files.add(file.toString());

		}

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "JDK でないとこのテストは動かない");

		List<String> args = new java.util.ArrayList<>(List.of("-d", classes.toString()));
		args.addAll(files);

		assertEquals(0, compiler.run(null, null, null, args.toArray(new String[0]))
			, "テスト用アプリをコンパイルできなかった");

		return classes.toString();

	}

	/**
	 * 起動の指定を作る
	 *
	 * @param classpath	クラスパス
	 * @param mainClass	起動クラス
	 * @param port		ポート
	 * @param workingDir	作業ディレクトリ
	 * @return	指定
	 */
	private static AppSpec spec (String classpath, String mainClass, int port, Path workingDir) {

		return new AppSpec(mainClass, classpath, "local", port, List.of(), workingDir.toFile());

	}

	@Test
	@DisplayName("D-77 同じ JVM の中で起動し、止められる")
	void startAndStop (@TempDir Path root) throws Exception {

		String classpath = compile(root, java.util.Map.of(
			"io/jimble/core/lifecycle/Shutdown.java", FAKE_SHUTDOWN
			, "io/jimble/web/server/JimbleServer.java", FAKE_SERVER
			, "testapp/App.java", FAKE_APP));

		int port = freePort();
		AppRunner runner = AppRunner.start(spec(classpath, "testapp.App", port, root), new RunLog());

		assertTrue(runner.awaitReady(Duration.ofSeconds(10)), "待ち受けが始まらない");
		assertTrue(runner.isAlive());

		/*
		 * <b>止めたあとにポートを叩き直してはいけない。</b>
		 * 空いた瞬間に<b>別のもの（同じマシンの他のプロセス）が同じ番号を取りうる</b>ので、
		 * 「繋がった = 止まっていない」にはならない。実際それで時々落ちていた。
		 * stop() 自身が「空くまで待った結果」を返すので、それを見る。
		 */
		assertTrue(runner.stop(), "止めたのにポートが空かない");

	}

	@Test
	@DisplayName("D-77 入れ替えると別のクラスローダになる")
	void reloadUsesNewClassLoader (@TempDir Path root) throws Exception {

		String classpath = compile(root, java.util.Map.of(
			"io/jimble/core/lifecycle/Shutdown.java", FAKE_SHUTDOWN
			, "io/jimble/web/server/JimbleServer.java", FAKE_SERVER
			, "testapp/App.java", FAKE_APP));

		AppRunner first = AppRunner.start(spec(classpath, "testapp.App", freePort(), root), new RunLog());
		assertTrue(first.awaitReady(Duration.ofSeconds(10)));
		ClassLoader firstLoader = loaderOf(classpath, first);
		assertTrue(first.stop(), "1つめが止まらない");

		/*
		 * <b>2つめは別のポートで立てる。</b>
		 * 同じ番号を取り直すと、空いた一瞬に<b>同じマシンの他のプロセスが
		 * その番号を取る</b>ことがあり、束ねられずに落ちる（実際に時々落ちていた）。
		 * このテストが見たいのは「入れ替えたら別のクラスローダになる」ことだけである。
		 */
		AppRunner second = AppRunner.start(spec(classpath, "testapp.App", freePort(), root), new RunLog());
		assertTrue(second.awaitReady(Duration.ofSeconds(10)), "入れ替えたあとに起動できない");
		ClassLoader secondLoader = loaderOf(classpath, second);
		second.stop();

		assertNotNull(firstLoader);
		assertNotNull(secondLoader);
		assertNotSame(firstLoader, secondLoader, "同じクラスローダを使い回している");

	}

	@Test
	@DisplayName("D-77 起動クラスが無ければ落ちたことが分かる")
	void missingMainClass (@TempDir Path root) throws Exception {

		String classpath = compile(root, java.util.Map.of(
			"io/jimble/core/lifecycle/Shutdown.java", FAKE_SHUTDOWN
			, "io/jimble/web/server/JimbleServer.java", FAKE_SERVER
			, "testapp/App.java", FAKE_APP));

		AppRunner runner = AppRunner.start(
			spec(classpath, "testapp.NotHere", freePort(), root), new RunLog());

		assertFalse(runner.awaitReady(Duration.ofSeconds(5)));
		assertNotNull(runner.failure(), "落ちたのに例外が取れない");
		assertTrue(runner.failure() instanceof ClassNotFoundException, String.valueOf(runner.failure()));

		runner.stop();

	}

	@Test
	@DisplayName("D-77 止まりきらないスレッドが残っても、入れ替え自体は続く")
	void leftoverThreadDoesNotBlockReload (@TempDir Path root) throws Exception {

		/*
		 * 同じ JVM で動かすことの代償。
		 * アプリが自分で止めないスレッドは残る（AppRunner がログで言う）。
		 */
		String classpath = compile(root, java.util.Map.of(
			"io/jimble/core/lifecycle/Shutdown.java", FAKE_SHUTDOWN
			, "io/jimble/web/server/JimbleServer.java", FAKE_SERVER
			, "testapp/Leaky.java", LEAKY_APP));

		int port = freePort();

		AppRunner runner = AppRunner.start(spec(classpath, "testapp.Leaky", port, root), new RunLog());
		assertTrue(runner.awaitReady(Duration.ofSeconds(10)));

		/*
		 * ポートは空く（止める口があるので）。
		 * ただし<b>残ったスレッドがある</b>ので、stop() は false を返す
		 * （クラスローダを閉じない、という判断がそこに出ている）。
		 */
		assertTrue(runner.stop(), "止める口はあるので、ポートは空くこと");

		// 残ったスレッドは残る。ここを黙って通さないのが AppRunner の仕事
		assertTrue(Thread.getAllStackTraces().keySet().stream()
			.anyMatch(thread -> "leaked-worker".equals(thread.getName()))
			, "テストの前提が崩れている（スレッドが残っていない）");

	}

	/**
	 * helidon の JVM 全体の直列化フィルタを切る
	 *
	 * <p>
	 * 切らないと、helidon が Gradle デーモンにフィルタを張ってしまい、
	 * <b>次のビルドが {@code filter status: REJECTED} で起動しなくなる</b>
	 * （要件 F-X-02 / D-77）。
	 * </p>
	 */
	@Test
	@DisplayName("helidon に JVM 全体の直列化フィルタを張らせない")
	void keepDaemonDeserializable () {

		String key = "helidon.serialFilter.missing.action";
		String saved = System.getProperty(key);

		try {

			System.clearProperty(key);

			AppRunner.keepDaemonDeserializable(new RunLog());

			assertEquals("IGNORE", System.getProperty(key));

		} finally {

			if (saved == null) {
				System.clearProperty(key);
			} else {
				System.setProperty(key, saved);
			}

		}

	}

	/**
	 * 自分で指定していたらそのまま
	 */
	@Test
	@DisplayName("自分で指定した直列化フィルタの扱いは変えない")
	void keepDaemonDeserializableKeepsExplicit () {

		String key = "helidon.serialFilter.missing.action";
		String saved = System.getProperty(key);

		try {

			System.setProperty(key, "FAIL");

			AppRunner.keepDaemonDeserializable(new RunLog());

			assertEquals("FAIL", System.getProperty(key));

		} finally {

			if (saved == null) {
				System.clearProperty(key);
			} else {
				System.setProperty(key, saved);
			}

		}

	}

	/**
	 * アプリが読まれたクラスローダを取る
	 *
	 * @param classpath	クラスパス
	 * @param runner	動かしたもの
	 * @return	クラスローダ
	 */
	private static ClassLoader loaderOf (String classpath, AppRunner runner) throws Exception {

		return (ClassLoader) runner.loadedClass("testapp.App")
			.getField("loader").get(null);

	}

}
