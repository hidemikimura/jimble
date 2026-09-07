package io.jimble.gradle.run;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.util.List;

/**
 * 開発用のホットリロード（要件 F-X-02 / D-47）
 *
 * <pre>
 * plugins {
 *     id("io.jimble.run")
 * }
 *
 * jimbleRun {
 *     mainClass = "blog.BlogApp"
 * }
 * </pre>
 *
 * <pre>
 * ./gradlew :examples:blog:jimbleRun
 * </pre>
 *
 * <p>
 * {@code http://localhost:9000} を開くと、<b>その時点のソースで</b>アプリが動く。
 * ソースを直してリロードすれば、作り直してから応える。
 * </p>
 *
 * <h2>移送元（jooby_run）から変えたところ</h2>
 * <ol>
 *   <li><b>アプリを別プロセスで起動する。</b>移送元は Gradle デーモンと同じ JVM の中に
 *       {@code URLClassLoader} でロードしていた。速いが、
 *       <b>{@code close()} していないので再起動のたびにクラスローダーごと漏れ</b>、
 *       止まりきらなかったスレッド（スケジューラ・MQ ワーカー・コネクションプール）が
 *       <b>次の起動と二重に動く。</b>アプリが {@code System.exit()} を呼べばデーモンごと落ちる。
 *       さらに起動と停止をリフレクション（{@code startApp} / {@code stop} / {@code server} フィールド）で
 *       呼んでいたため、<b>アプリ側が決まったシグネチャを強いられていた</b>（原則1・原則2）。
 *       別プロセスなら、殺せば必ず全部止まる</li>
 *   <li><b>ビルドの成否を終了コードで見る。</b>移送元は<b>標準エラーに1行でも出たら失敗</b>としており、
 *       そのため「無視する文字」を 20 個ほど並べる必要があった
 *       （{@code npm warn} / {@code Xlint} / {@code │} など）。
 *       警告が出ただけで再起動しなくなる</li>
 *   <li><b>プロキシを HTTP のレベルで書き直した。</b>移送元は生の TCP を素通しし、
 *       <b>接続ごとに 100ms ポーリングのスレッドを2本</b>立てて後始末していた</li>
 *   <li><b>ビルドが失敗したらブラウザにそのまま出す。</b>移送元はコンソールにだけ出していたので、
 *       画面が変わらない理由を見に行く必要があった</li>
 *   <li>移送元は除外パスに {@code jooby_base/src/main/resources/builds} を直接書いていた</li>
 * </ol>
 */
public class JimbleRunPlugin implements Plugin<Project> {

	/** 拡張の名前 */
	public static final String EXTENSION_NAME = "jimbleRun";

	/** タスクの名前 */
	public static final String TASK_NAME = "jimbleRun";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void apply (Project project) {

		JimbleRunExtension extension =
			project.getExtensions().create(EXTENSION_NAME, JimbleRunExtension.class);

		extension.getPort().convention(9000);
		extension.getEnv().convention("local");
		extension.getRestartMode().convention(RestartMode.ON_REQUEST.key());
		extension.getQuietMillis().convention(300L);
		extension.getStartTimeoutSeconds().convention(60);
		extension.getBuildTasks().convention(List.of(taskPath(project, "classes")));
		extension.getWatchExtensions().convention(List.of(
			".java", ".jte", ".html", ".js", ".css", ".conf", ".xml", ".properties", ".yml", ".sql"
		));

		// appPort は既定で port + 100
		extension.getAppPort().convention(extension.getPort().map(port -> port + 100));

		project.getTasks().register(TASK_NAME, JimbleRunTask.class, task -> {

			task.setGroup("application");
			task.setDescription("ソースを見張り、変更があったら作り直してアプリを入れ替える（要件 F-X-02）");

			task.getMainClass().set(extension.getMainClass());
			task.getPort().set(extension.getPort());
			task.getAppPort().set(extension.getAppPort());
			task.getEnv().set(extension.getEnv());
			task.getBuildTasks().set(extension.getBuildTasks());
			task.getWatchDirs().set(extension.getWatchDirs());
			task.getExcludeDirs().set(extension.getExcludeDirs());
			task.getWatchExtensions().set(extension.getWatchExtensions());
			task.getJvmArgs().set(extension.getJvmArgs());
			task.getAppArgs().set(extension.getArgs());
			task.getRestartMode().set(extension.getRestartMode());
			task.getQuietMillis().set(extension.getQuietMillis());
			task.getStartTimeoutSeconds().set(extension.getStartTimeoutSeconds());

			task.getProjectDir().set(project.getProjectDir());
			task.getRootDir().set(project.getRootDir());

			/*
			 * クラスパスは設定の時点で決めておく。
			 * タスクの実行中に Project を触ると configuration cache が使えない。
			 */
			task.getRuntimeClasspath().from(runtimeClasspath(project));

			/*
			 * アプリはプロジェクトのツールチェーンで動かす。
			 * Gradle デーモンの JVM とは別物である。
			 */
			task.getJavaLauncher().convention(
				project.getExtensions().getByType(JavaToolchainService.class)
					.launcherFor(project.getExtensions()
						.getByType(JavaPluginExtension.class).getToolchain()));

			/*
			 * 見張り続けるタスクなので、出力を持たない。
			 * up-to-date で飛ばされると何も起きないまま終わる。
			 */
			task.getOutputs().upToDateWhen(t -> false);

		});

	}

	/**
	 * 実行時クラスパス
	 *
	 * @param project	プロジェクト
	 * @return	クラスパス
	 */
	private static Object runtimeClasspath (Project project) {

		SourceSetContainer sourceSets = project.getExtensions()
			.getByType(JavaPluginExtension.class)
			.getSourceSets();

		return sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath();

	}

	/**
	 * タスクのパス
	 *
	 * @param project	プロジェクト
	 * @param name		タスク名
	 * @return	パス（例 {@code :examples:blog:classes}）
	 */
	private static String taskPath (Project project, String name) {

		String path = project.getPath();

		return ":".equals(path) ? ":" + name : path + ":" + name;

	}

}
