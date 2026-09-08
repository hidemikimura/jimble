package io.jimble.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.io.File;
import java.util.List;

/**
 * jimble の Gradle プラグイン
 *
 * <p>
 * <b>ローカルでは {@code migrate} → {@code codegen} → {@code compileJava} を連鎖させる</b>（要件 F-G-07）。
 * ローカル以外のビルドは {@code compileJava} だけを行う。
 * </p>
 *
 * <pre>
 * plugins {
 *     java
 *     id("io.jimble.db")
 * }
 * </pre>
 *
 * <h2>タスク</h2>
 * <table>
 *   <caption>追加されるタスク</caption>
 *   <tr><th>タスク</th><th>内容</th></tr>
 *   <tr><td>{@code migrate}</td><td>未適用のマイグレーションを適用する</td></tr>
 *   <tr><td>{@code codegen}</td><td>テーブル定義のコードを生成する（{@code migrate} のあと）</td></tr>
 *   <tr><td>{@code codegenCheck}</td><td>コミットされている生成物が最新かを確かめる（要件 F-G-14）</td></tr>
 * </table>
 *
 * <h2>中身は薄い</h2>
 * <p>
 * どのタスクも {@code io.jimble.db.cli.JimbleDbCli} を起動するだけである。
 * <b>ロジックを Gradle 側に置かない。</b>CI や本番デプロイからは CLI を直接叩けばよく
 * （要件 F-G-08）、Gradle でしか動かない処理を作らないため。
 * </p>
 */
public class JimbleDbPlugin implements Plugin<Project> {

	/**
	 * コンストラクタ
	 */
	public JimbleDbPlugin () {

	}

	/** CLI のメインクラス */
	public static final String CLI_MAIN_CLASS = "io.jimble.db.cli.JimbleDbCli";

	/** 拡張ブロック名 */
	public static final String EXTENSION_NAME = "jimble";

	/** タスクグループ */
	public static final String GROUP = "jimble";

	/** タスク名：マイグレーション */
	public static final String TASK_MIGRATE = "migrate";

	/** タスク名：コード生成 */
	public static final String TASK_CODEGEN = "codegen";

	/** タスク名：生成物の鮮度確認 */
	public static final String TASK_CODEGEN_CHECK = "codegenCheck";

	/** Gradle プロパティ：自動生成の可否 */
	public static final String PROPERTY_AUTO_GENERATE = "jimble.autoGenerate";

	/** Gradle プロパティ：環境名 */
	public static final String PROPERTY_ENV = "jimble.env";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void apply (Project project) {

		project.getPluginManager().apply(JavaPlugin.class);

		JimbleDbExtension extension = project.getExtensions().create(EXTENSION_NAME, JimbleDbExtension.class);
		extension.getSourceRoot().convention(JimbleDbExtension.DEFAULT_SOURCE_ROOT);
		extension.getEnv().convention(env(project));
		extension.getAutoGenerate().convention(project.provider(() -> autoGenerate(project, extension)));

		TaskProvider<JavaExec> migrate = registerMigrate(project, extension);
		TaskProvider<JavaExec> codegen = registerCodegen(project, extension, migrate);
		registerCodegenCheck(project, extension, migrate);

		// ローカルだけ compileJava の前に流す（要件 F-G-07）
		project.getTasks().named(JavaPlugin.COMPILE_JAVA_TASK_NAME).configure(compileJava ->
			compileJava.dependsOn(project.provider(() ->
				extension.getAutoGenerate().get() ? new Object[]{codegen} : new Object[0]
			))
		);

	}

	// region タスク

	/**
	 * {@code migrate} を登録する
	 *
	 * @param project	プロジェクト
	 * @param extension	設定
	 * @return	タスク
	 */
	private TaskProvider<JavaExec> registerMigrate (Project project, JimbleDbExtension extension) {

		return project.getTasks().register(TASK_MIGRATE, JavaExec.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("未適用のマイグレーションを適用する");
			configureCli(project, extension, task);
			task.args("migrate");
		});

	}

	/**
	 * {@code codegen} を登録する
	 *
	 * @param project	プロジェクト
	 * @param extension	設定
	 * @param migrate	マイグレーションタスク
	 * @return	タスク
	 */
	private TaskProvider<JavaExec> registerCodegen (Project project, JimbleDbExtension extension, TaskProvider<JavaExec> migrate) {

		return project.getTasks().register(TASK_CODEGEN, JavaExec.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("テーブル定義のコードを生成する");
			// スキーマを最新にしてから生成する
			task.dependsOn(migrate);
			configureCli(project, extension, task);
			task.getArgumentProviders().add(() -> List.of(
				"codegen"
				, project.file(extension.getSourceRoot().get()).getAbsolutePath()
			));
		});

	}

	/**
	 * {@code codegenCheck} を登録する
	 *
	 * <p>
	 * 生成物はリポジトリにコミットする決まりなので（要件 F-G-13）、
	 * <b>コミットされているものがスキーマと合っているかを CI で確かめる</b>（要件 F-G-14）。
	 * </p>
	 *
	 * @param project	プロジェクト
	 * @param extension	設定
	 * @param migrate	マイグレーションタスク
	 */
	private void registerCodegenCheck (Project project, JimbleDbExtension extension, TaskProvider<JavaExec> migrate) {

		File temporaryRoot = new File(project.getLayout().getBuildDirectory().get().getAsFile(), "codegen-check");

		TaskProvider<JavaExec> generateToTemporary = project.getTasks().register("codegenToTemporary", JavaExec.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("生成物の比較用に一時ディレクトリへ生成する");
			task.dependsOn(migrate);
			configureCli(project, extension, task);
			task.doFirst(t -> project.delete(temporaryRoot));
			task.getArgumentProviders().add(() -> List.of("codegen", temporaryRoot.getAbsolutePath()));
		});

		project.getTasks().register(TASK_CODEGEN_CHECK, CodegenCheckTask.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("コミットされている生成物がスキーマと一致するかを確かめる");
			task.dependsOn(generateToTemporary);
			task.getGenerated().set(temporaryRoot);
			task.getCommitted().set(project.getLayout().dir(
				project.provider(() -> project.file(extension.getSourceRoot().get()))
			));
		});

	}

	// endregion

	// region 共通設定

	/**
	 * CLI を起動する設定を入れる
	 *
	 * <p>
	 * クラスパスは<b>依存関係とリソースだけ</b>で、このプロジェクトのクラスは含めない。
	 * 含めると {@code compileJava} が要り、{@code compileJava → codegen → compileJava} で循環する。
	 * マイグレーション SQL と設定ファイルはリソース側にあるので、これで足りる。
	 * </p>
	 *
	 * @param project	プロジェクト
	 * @param extension	設定
	 * @param task		タスク
	 */
	private void configureCli (Project project, JimbleDbExtension extension, JavaExec task) {

		task.getMainClass().set(CLI_MAIN_CLASS);
		task.setClasspath(toolClasspath(project));

		/*
		 * プロジェクトの toolchain で起動する。
		 * 既定では Gradle デーモンの Java が使われるが、デーモンはビルドする側の環境で決まるため、
		 * アプリより古い Java だと UnsupportedClassVersionError になる。
		 */
		JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
		JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
		task.getJavaLauncher().set(toolchains.launcherFor(javaExtension.getToolchain()));
		task.systemProperty("stdout.encoding", "UTF-8");
		task.systemProperty("stderr.encoding", "UTF-8");
		task.getJvmArgumentProviders().add(() -> List.of("-Denv=" + extension.getEnv().get()));

		// 常に実行する。DB の状態は Gradle からは見えない
		task.getOutputs().upToDateWhen(t -> false);

	}

	/**
	 * CLI のクラスパス
	 *
	 * @param project	プロジェクト
	 * @return	クラスパス
	 */
	private FileCollection toolClasspath (Project project) {

		SourceSet main = project.getExtensions()
			.getByType(SourceSetContainer.class)
			.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

		// processResources は compileJava に依存しないので循環しない
		TaskProvider<ProcessResources> processResources =
			project.getTasks().named(main.getProcessResourcesTaskName(), ProcessResources.class);

		return project.files(processResources)
			.plus(project.getConfigurations().getByName(main.getRuntimeClasspathConfigurationName()));

	}

	/**
	 * 自動生成するかを決める
	 *
	 * <p>
	 * Gradle プロパティ {@code jimble.autoGenerate} が指定されていればそれに従う
	 * （環境判定に依存しない指定。要件 F-G-17）。無ければローカルのときだけ。
	 * </p>
	 *
	 * @param project	プロジェクト
	 * @param extension	設定
	 * @return	自動生成する場合 = true
	 */
	private boolean autoGenerate (Project project, JimbleDbExtension extension) {

		Object property = project.findProperty(PROPERTY_AUTO_GENERATE);
		if (property != null && !property.toString().isEmpty()) {
			return Boolean.parseBoolean(property.toString());
		}

		return JimbleDbExtension.DEFAULT_ENV.equals(extension.getEnv().get());

	}

	/**
	 * 環境名を決める
	 *
	 * <p>
	 * Gradle プロパティ {@code jimble.env} → 環境変数 {@code ENV} → {@code local} の順に見る。
	 * </p>
	 *
	 * @param project	プロジェクト
	 * @return	環境名
	 */
	private String env (Project project) {

		Object property = project.findProperty(PROPERTY_ENV);
		if (property != null && !property.toString().isEmpty()) {
			return property.toString();
		}

		String environment = System.getenv("ENV");
		if (environment != null && !environment.isEmpty()) {
			return environment;
		}

		return JimbleDbExtension.DEFAULT_ENV;

	}

	// endregion

}
