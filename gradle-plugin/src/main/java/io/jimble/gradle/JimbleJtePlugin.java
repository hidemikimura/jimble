package io.jimble.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;

/**
 * jte テンプレートの Gradle プラグイン（要件 F-W-10 / O-13）
 *
 * <pre>
 * plugins {
 *     java
 *     id("io.jimble.jte")
 * }
 * </pre>
 *
 * <p>
 * {@code src/main/jte} を Java に変換して {@code compileJava} の前に挟む。
 * テストは {@code src/test/jte} を見る。
 * </p>
 *
 * <h2>タスク</h2>
 * <table>
 *   <caption>追加されるタスク</caption>
 *   <tr><th>タスク</th><th>内容</th></tr>
 *   <tr><td>{@code generateJte}</td><td>{@code src/main/jte} を変換する</td></tr>
 *   <tr><td>{@code generateTestJte}</td><td>{@code src/test/jte} を変換する</td></tr>
 * </table>
 *
 * <h2>なぜ jte の公式プラグインを使わないか</h2>
 * <p>
 * 公式プラグインは Plugin Portal から取る。D-22 で「Plugin Portal を経由しない」と決めており、
 * <b>取り込みビルド（composite build）で完結させたい。</b>
 * やっていることは jte のコード生成 API を1回呼ぶだけなので、自分で持っても薄い。
 * </p>
 */
public class JimbleJtePlugin implements Plugin<Project> {

	/** 拡張ブロック名 */
	public static final String EXTENSION_NAME = "jte";

	/** タスクグループ */
	public static final String GROUP = "jimble";

	/** 生成するクラスの既定パッケージ（jte の既定と揃える。ずらすと実行時に見つからない） */
	public static final String DEFAULT_PACKAGE_NAME = "gg.jte.generated.precompiled";

	/** 既定の種別 */
	public static final String DEFAULT_CONTENT_TYPE = "Html";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void apply (Project project) {

		project.getPluginManager().apply(JavaPlugin.class);

		JimbleJteExtension extension = project.getExtensions()
			.create(EXTENSION_NAME, JimbleJteExtension.class);

		extension.getPackageName().convention(DEFAULT_PACKAGE_NAME);
		extension.getContentType().convention(DEFAULT_CONTENT_TYPE);
		extension.getTrimControlStructures().convention(true);
		extension.getHtmlCommentsPreserved().convention(false);

		SourceSetContainer sourceSets = project.getExtensions()
			.getByType(JavaPluginExtension.class).getSourceSets();

		register(project, extension, sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME), "generateJte");
		register(project, extension, sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME), "generateTestJte");

	}

	/**
	 * ソースセット1つ分を登録する
	 *
	 * @param project		プロジェクト
	 * @param extension		設定
	 * @param sourceSet		ソースセット
	 * @param taskName		タスク名
	 */
	private void register (Project project, JimbleJteExtension extension, SourceSet sourceSet, String taskName) {

		String setName = sourceSet.getName();

		File sourceDirectory = SourceSet.MAIN_SOURCE_SET_NAME.equals(setName)
			// main だけは設定で置き場所を変えられる
			? project.file(extension.getSourceDirectory().getOrElse("src/main/jte"))
			: project.file("src/" + setName + "/jte");

		File targetDirectory = new File(project.getLayout().getBuildDirectory().get().getAsFile()
			, "generated/sources/jte/" + setName + "/java");

		TaskProvider<GenerateJteTask> task = project.getTasks()
			.register(taskName, GenerateJteTask.class, it -> {

				it.setGroup(GROUP);
				it.setDescription("jte テンプレート（%s）を Java に変換する".formatted(sourceDirectory));

				it.getSourceDirectory().set(sourceDirectory);
				it.getTemplates().setFrom(project.fileTree(sourceDirectory, tree -> tree.include("**/*.jte")));
				it.getTargetDirectory().set(targetDirectory);
				it.getPackageName().set(extension.getPackageName());
				it.getContentType().set(extension.getContentType());
				it.getTrimControlStructures().set(extension.getTrimControlStructures());
				it.getHtmlCommentsPreserved().set(extension.getHtmlCommentsPreserved());

			});

		// 生成物を普通のソースとして扱う。ここが要点
		sourceSet.getJava().srcDir(task.map(GenerateJteTask::getTargetDirectory));

		project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class,
			it -> it.dependsOn(task));

	}

}
