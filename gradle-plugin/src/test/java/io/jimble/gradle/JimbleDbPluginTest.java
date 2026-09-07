package io.jimble.gradle;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JimbleDbPlugin} のテスト
 *
 * <p>
 * タスクの配線を確かめる。実際に DB へ繋ぐところは
 * {@code jimble-db} の結合テストと {@code examples/blog} で見ている。
 * </p>
 */
class JimbleDbPluginTest {

	// region タスクの登録

	@Test
	@DisplayName("タスクが登録される")
	void registersTasks () {

		Project project = project();

		assertNotNull(project.getTasks().findByName(JimbleDbPlugin.TASK_MIGRATE));
		assertNotNull(project.getTasks().findByName(JimbleDbPlugin.TASK_CODEGEN));
		assertNotNull(project.getTasks().findByName(JimbleDbPlugin.TASK_CODEGEN_CHECK));

	}

	@Test
	@DisplayName("java プラグインが無くても適用できる（自分で入れる）")
	void appliesJavaPlugin () {

		Project project = ProjectBuilder.builder().build();
		project.getPluginManager().apply(JimbleDbPlugin.class);

		assertTrue(project.getPlugins().hasPlugin(JavaPlugin.class));

	}

	// endregion

	// region 連鎖

	@Test
	@DisplayName("codegen は migrate のあとに走る")
	void codegenDependsOnMigrate () {

		Project project = project();

		assertTrue(
			dependencyNames(project.getTasks().getByName(JimbleDbPlugin.TASK_CODEGEN))
				.contains(JimbleDbPlugin.TASK_MIGRATE)
		);

	}

	@Test
	@DisplayName("ローカルなら compileJava の前に codegen が走る（F-G-07）")
	void compileJavaDependsOnCodegenOnLocal () {

		Project project = project();
		extension(project).getEnv().set("local");

		assertTrue(compileJavaDependsOnCodegen(project));

	}

	@Test
	@DisplayName("ローカル以外なら compileJava だけを行う（F-G-07）")
	void compileJavaStandsAloneOnOtherEnv () {

		Project project = project();
		extension(project).getEnv().set("production");

		assertFalse(compileJavaDependsOnCodegen(project));

	}

	@Test
	@DisplayName("autoGenerate を直接指定すれば環境判定より優先される（F-G-17）")
	void autoGenerateOverridesEnv () {

		Project project = project();
		extension(project).getEnv().set("production");
		extension(project).getAutoGenerate().set(true);

		assertTrue(compileJavaDependsOnCodegen(project));

	}

	// endregion

	// region 既定値

	@Test
	@DisplayName("生成先の既定は src/main/java")
	void defaultSourceRoot () {

		assertEquals(JimbleDbExtension.DEFAULT_SOURCE_ROOT, extension(project()).getSourceRoot().get());

	}

	@Test
	@DisplayName("環境の既定は local")
	void defaultEnv () {

		assertEquals(JimbleDbExtension.DEFAULT_ENV, extension(project()).getEnv().get());

	}

	// endregion

	// region ヘルパー

	/**
	 * プラグインを適用したプロジェクト
	 *
	 * @return	プロジェクト
	 */
	private Project project () {

		Project project = ProjectBuilder.builder().build();
		project.getPluginManager().apply(JavaPlugin.class);
		project.getPluginManager().apply(JimbleDbPlugin.class);

		return project;

	}

	/**
	 * 設定
	 *
	 * @param project	プロジェクト
	 * @return	設定
	 */
	private JimbleDbExtension extension (Project project) {

		return project.getExtensions().getByType(JimbleDbExtension.class);

	}

	/**
	 * compileJava が codegen に依存しているか
	 *
	 * @param project	プロジェクト
	 * @return	依存している場合 = true
	 */
	private boolean compileJavaDependsOnCodegen (Project project) {

		return dependencyNames(project.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME))
			.contains(JimbleDbPlugin.TASK_CODEGEN);

	}

	/**
	 * 依存しているタスク名
	 *
	 * @param task	タスク
	 * @return	タスク名
	 */
	private List<String> dependencyNames (Task task) {

		return task.getTaskDependencies().getDependencies(task).stream().map(Task::getName).toList();

	}

	// endregion

}
