package io.jimble.gradle.run;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JimbleRunPlugin} の配線のテスト
 *
 * <p>
 * 実際に起動して入れ替わるところは {@code examples/blog} で見ている
 * （{@code docs/design-m8.md} 5.7）。
 * </p>
 */
class JimbleRunPluginTest {

	@Test
	@DisplayName("タスクが登録される")
	void registersTask () {

		Project project = project();

		assertNotNull(project.getTasks().findByName(JimbleRunPlugin.TASK_NAME));

	}

	@Test
	@DisplayName("既定値だけで動く形になっている")
	void defaults () {

		Project project = project();
		JimbleRunExtension extension = extension(project);

		assertEquals(9000, extension.getPort().get());
		assertEquals("local", extension.getEnv().get());
		assertEquals(RestartMode.ON_REQUEST.key(), extension.getRestartMode().get());
		assertEquals(300L, extension.getQuietMillis().get());

		assertTrue(extension.getWatchExtensions().get().contains(".java"));
		assertTrue(extension.getWatchExtensions().get().contains(".jte"));

	}

	@Test
	@DisplayName("アプリのポートは受付ポートの +100")
	void appPortFollowsPort () {

		Project project = project();
		JimbleRunExtension extension = extension(project);

		// 既定
		assertEquals(9100, extension.getAppPort().get());

		// port を変えれば付いてくる
		extension.getPort().set(8080);
		assertEquals(8180, extension.getAppPort().get());

		// 明示すればそちらが勝つ
		extension.getAppPort().set(3000);
		assertEquals(3000, extension.getAppPort().get());

	}

	@Test
	@DisplayName("作り直しに流すのは classes まで")
	void buildTasksAreClassesOnly () {

		Project project = project();

		/*
		 * 移送元は build を叩いており、保存のたびにテストまで走っていた。
		 * classes には codegen と generateJte が繋がっているので、
		 * テンプレートと生成コードはここで作り直される。
		 */
		assertEquals(List.of(":classes"), extension(project).getBuildTasks().get());

	}

	@Test
	@DisplayName("mainClass が無ければ、何が足りないか言って止まる")
	void mainClassIsRequired () {

		Project project = project();

		JimbleRunTask task = (JimbleRunTask) project.getTasks().getByName(JimbleRunPlugin.TASK_NAME);

		Exception exception = org.junit.jupiter.api.Assertions.assertThrows(
			Exception.class, task::run);

		assertTrue(String.valueOf(exception.getMessage()).contains("mainClass")
			, exception.getMessage());

	}

	// region 再起動のきっかけ

	@Test
	@DisplayName("再起動のきっかけは名前で引ける")
	void restartModeOf () {

		assertEquals(RestartMode.ON_REQUEST, RestartMode.of("on_request"));
		assertEquals(RestartMode.IMMEDIATE, RestartMode.of("immediate"));
		assertEquals(RestartMode.IMMEDIATE, RestartMode.of("  IMMEDIATE  "));

		// 指定が無ければ安全側（勝手に再起動しない方）
		assertEquals(RestartMode.ON_REQUEST, RestartMode.of(null));
		assertEquals(RestartMode.ON_REQUEST, RestartMode.of("  "));

		/*
		 * D-89 読めない名前は落とす。
		 * 黙って倒すと「設定したのに効かない」だけが残る。
		 */
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class
			, () -> RestartMode.of("on_change"));

		assertTrue(thrown.getMessage().contains("on_request"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("immediate"), thrown.getMessage());

	}

	// endregion

	// region 転送してはいけないヘッダ

	@Test
	@DisplayName("hop-by-hop ヘッダは転送しない")
	void hopByHop () {

		assertTrue(HopByHopHeaders.isForwardable("Accept"));
		assertTrue(HopByHopHeaders.isForwardable("Cookie"));
		assertTrue(HopByHopHeaders.isForwardable("Content-Type"));

		org.junit.jupiter.api.Assertions.assertFalse(HopByHopHeaders.isForwardable("Connection"));
		org.junit.jupiter.api.Assertions.assertFalse(HopByHopHeaders.isForwardable("keep-alive"));
		org.junit.jupiter.api.Assertions.assertFalse(HopByHopHeaders.isForwardable("Transfer-Encoding"));

		/*
		 * Host と Content-Length は HttpClient / HttpServer が付け直す。
		 * 元の値を持っていくと本文の長さが合わなくなる。
		 */
		org.junit.jupiter.api.Assertions.assertFalse(HopByHopHeaders.isForwardable("Host"));
		org.junit.jupiter.api.Assertions.assertFalse(HopByHopHeaders.isForwardable("Content-Length"));

	}

	// endregion

	// region エラー画面

	@Test
	@DisplayName("エラー画面は中身を HTML として無害にする")
	void errorPageEscapes () {

		String html = ErrorPage.html("作り直しに失敗しました", "<script>alert(1)</script> & 'x'");

		assertTrue(html.contains("&lt;script&gt;"), html);
		assertTrue(html.contains("&amp;"), html);
		org.junit.jupiter.api.Assertions.assertFalse(html.contains("<script>"), html);

	}

	// endregion

	/**
	 * テスト用のプロジェクト
	 *
	 * @return	プロジェクト
	 */
	private static Project project () {

		Project project = ProjectBuilder.builder().build();
		project.getPluginManager().apply(JavaPlugin.class);
		project.getPluginManager().apply(JimbleRunPlugin.class);

		return project;

	}

	/**
	 * 設定
	 *
	 * @param project	プロジェクト
	 * @return	設定
	 */
	private static JimbleRunExtension extension (Project project) {

		return project.getExtensions().getByType(JimbleRunExtension.class);

	}

}
