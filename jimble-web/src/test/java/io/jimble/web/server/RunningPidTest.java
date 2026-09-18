package io.jimble.web.server;

import io.jimble.core.lifecycle.Shutdown;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 動いている印（RUNNING_PID_{ポート}）
 */
class RunningPidTest {

	@BeforeEach
	void setUp () {

		Conf.reload();
		Shutdown.reset();

	}

	@AfterEach
	void tearDown () {

		Shutdown.reset();
		Conf.reload();

	}

	@Test
	@DisplayName("名前は RUNNING_PID_ にポート番号")
	void fileName () {

		assertEquals("RUNNING_PID_8080", RunningPid.fileName(8080));

	}

	@Test
	@DisplayName("jar のクラスなら、その jar のあるディレクトリ")
	void jarDirectoryOfJarClass () {

		// JUnit は jar から読み込まれている
		Path directory = RunningPid.jarDirectory(Test.class);

		assertNotNull(directory);
		assertTrue(Files.isDirectory(directory));
		assertTrue(Files.exists(directory.resolve(Path.of(Test.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath()).getFileName())));

	}

	@Test
	@DisplayName("クラスのディレクトリから動いているなら null（開発中・テスト）")
	void jarDirectoryOfClassesDirectory () {

		assertNull(RunningPid.jarDirectory(RunningPidTest.class));

	}

	@Test
	@DisplayName("置くと中身はプロセス ID、消すと無くなる")
	void createAndDelete (@TempDir Path directory) throws Exception {

		Path file = RunningPid.create(directory, 8080);

		assertNotNull(file);
		assertEquals(directory.resolve("RUNNING_PID_8080"), file);
		assertEquals(Long.toString(ProcessHandle.current().pid()), Files.readString(file, StandardCharsets.UTF_8));

		RunningPid.delete(file);

		assertFalse(Files.exists(file));

	}

	@Test
	@DisplayName("前回の印が残っていても上書きする")
	void overwritesStaleFile (@TempDir Path directory) throws Exception {

		Path stale = directory.resolve("RUNNING_PID_8080");
		Files.writeString(stale, "99999", StandardCharsets.UTF_8);

		Path file = RunningPid.create(directory, 8080);

		assertEquals(stale, file);
		assertEquals(Long.toString(ProcessHandle.current().pid()), Files.readString(file, StandardCharsets.UTF_8));

	}

	@Test
	@DisplayName("null を消しても何も起きない")
	void deleteNull () {

		RunningPid.delete(null);

	}

	@Test
	@DisplayName("jar から動いていないアプリでは置かない（起動は止めない）")
	void serverWithoutJar () {

		JimbleApp app = new JimbleApp() {
			{
				get("/", context -> context.response().send("ok"));
			}
		};

		JimbleServer server = JimbleServer.start(app, 0);

		try {
			assertNull(server.runningPid());
		} finally {
			server.stop();
		}

	}

}
