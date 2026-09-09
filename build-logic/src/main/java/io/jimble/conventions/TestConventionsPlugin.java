package io.jimble.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.logging.TestExceptionFormat;

import java.io.File;

/**
 * テストの共通設定（設計書 D-13）
 *
 * <p>
 * 走らせ方が4つに分かれている。
 * </p>
 *
 * <ul>
 *   <li>{@code test} — DB もベンチマークも使わない。ふつうのビルドで回る</li>
 *   <li>{@code dbTest} — 実 DB に繋ぐもの（{@code @Tag("db")}。要件 D-16）</li>
 *   <li>{@code pgTest} — 同じものを PostgreSQL に対して流す（要件 F-D-30）</li>
 *   <li>{@code bench} — 割り当て量を測るもの（{@code @Tag("bench")}。要件 NF-P-06）</li>
 * </ul>
 */
public class TestConventionsPlugin implements Plugin<Project> {

	@Override
	public void apply (Project project) {

		/*
		 * 開発用 DB は1つしかないので、dbTest / pgTest を同時に走らせない。
		 * 名前で1つに寄るので、どのモジュールから呼んでも同じものが返る。
		 */
		Provider<SharedDatabase> sharedDatabase = project.getGradle().getSharedServices()
			.registerIfAbsent("sharedDatabase", SharedDatabase.class
				, spec -> spec.getMaxParallelUsages().set(1));

		junit(project);
		test(project);

		SourceSet testSourceSet = project.getExtensions()
			.getByType(SourceSetContainer.class).getByName("test");

		dbTest(project, sharedDatabase, testSourceSet);
		pgTest(project, sharedDatabase, testSourceSet);
		bench(project, testSourceSet);

	}

	/**
	 * junit を付ける
	 *
	 * <p>
	 * <b>ここで付ける。</b>12 モジュールが同じ3行を書き写していて、
	 * 版を上げるときに<b>1か所だけ古いまま</b>になりうる形だった。
	 * </p>
	 *
	 * @param project プロジェクト
	 */
	private static void junit (Project project) {

		VersionCatalog libs = project.getExtensions()
			.getByType(VersionCatalogsExtension.class).named("libs");

		DependencyHandler dependencies = project.getDependencies();

		dependencies.add("testImplementation", dependencies.platform(library(libs, "junit-bom")));
		dependencies.add("testImplementation", library(libs, "junit-jupiter"));
		dependencies.add("testRuntimeOnly", library(libs, "junit-platform-launcher"));

	}

	/**
	 * 版の表から1つ引く
	 *
	 * @param libs	版の表
	 * @param alias	名前
	 * @return 依存
	 */
	private static Object library (VersionCatalog libs, String alias) {

		return libs.findLibrary(alias).orElseThrow(() -> new IllegalStateException(
			"gradle/libs.versions.toml に " + alias + " がありません")).get();

	}

	/**
	 * すべての Test タスクに共通のもの
	 *
	 * <p>
	 * 通常の {@code test} は DB もベンチマークも走らせない。
	 * <b>ベンチマークを外すのは、時間がかかるからだけではない。</b>
	 * 数十万回まわして割り当てを測るので、<b>ほかのテストと同じ JVM で走らせると
	 * 測り終わったころには JIT の状態が変わっている</b>（測った順で答えが変わる）。
	 * </p>
	 *
	 * @param project プロジェクト
	 */
	private static void test (Project project) {

		project.getTasks().withType(Test.class).configureEach(task -> {

			boolean isDbTest = task.getName().equals("dbTest") || task.getName().equals("pgTest");
			boolean isBench = task.getName().equals("bench");

			task.useJUnitPlatform(options -> {
				if (isBench) {
					options.includeTags("bench");
				} else if (isDbTest) {
					options.includeTags("db");
				} else {
					options.excludeTags("db", "bench");
				}
			});

			// 標準出力の文字化け対策（要件 F-U-12）
			task.jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");

			task.getTestLogging().events("failed");
			task.getTestLogging().setExceptionFormat(TestExceptionFormat.FULL);
			task.getTestLogging().setShowStackTraces(true);

		});

	}

	/**
	 * 実 DB に接続するテスト
	 *
	 * <pre>
	 * ./gradlew :jimble-db:dbTest
	 * </pre>
	 *
	 * <p>
	 * 接続先は {@code JIMBLE_TEST_DB_URL} / {@code JIMBLE_TEST_DB_USER} /
	 * {@code JIMBLE_TEST_DB_PASSWORD} で上書きできる。
	 * </p>
	 *
	 * @param project			プロジェクト
	 * @param sharedDatabase	開発用 DB の印
	 * @param testSourceSet		テストのソースセット
	 */
	private static void dbTest (Project project, Provider<SharedDatabase> sharedDatabase, SourceSet testSourceSet) {

		project.getTasks().register("dbTest", Test.class, task -> {

			task.setGroup("verification");
			task.setDescription("実 DB に接続するテストを実行する（開発用 DB が必要）");

			// 開発用 DB は1つ。同時に走らせない
			task.usesService(sharedDatabase);

			task.setTestClassesDirs(testSourceSet.getOutput().getClassesDirs());
			task.setClasspath(testSourceSet.getRuntimeClasspath());

			task.systemProperty("env", "dbtest");

			// 常に実行する（結果をキャッシュしない）
			task.getOutputs().upToDateWhen(ignore -> false);

			task.getTestLogging().events("passed", "failed");

		});

	}

	/**
	 * 同じテストを PostgreSQL に対して実行する（要件 F-D-30）
	 *
	 * <pre>
	 * ./gradlew :jimble-db:pgTest
	 * </pre>
	 *
	 * <p>
	 * {@code dbTest} との違いは {@code env} だけ。{@code application.pgtest.conf} が
	 * {@code db.jimble_test.product = postgresql} を持っているので、
	 * <b>テストのコードは1行も変わらない</b>。
	 * </p>
	 *
	 * @param project			プロジェクト
	 * @param sharedDatabase	開発用 DB の印
	 * @param testSourceSet		テストのソースセット
	 */
	private static void pgTest (Project project, Provider<SharedDatabase> sharedDatabase, SourceSet testSourceSet) {

		/*
		 * application.pgtest.conf を持っているか。
		 *
		 * <b>無いモジュールで pgTest を走らせてはいけない。</b>
		 * Conf は環境別ファイルが無ければ application.conf に落ちるので、
		 * <b>PostgreSQL のつもりで MySQL に繋ぎに行く</b>ことになる。
		 * 手元では MySQL も立っているので気づけず、
		 * CI の pg ジョブ（PostgreSQL しか無い）で初めて落ちた（examples/blog がそれ）。
		 *
		 * dbTest のほうは落ちた先が MySQL なので、
		 * <b>application.conf に落ちるのが意図どおり</b>である（examples/blog はそれで動く）。
		 * 同じ判定を dbTest には付けない。
		 */
		Provider<Boolean> hasPgTestConf = project.provider(() -> {

			SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

			return hasFile(sourceSets.getByName("main"), "application.pgtest.conf")
				|| hasFile(sourceSets.getByName("test"), "application.pgtest.conf");

		});

		project.getTasks().register("pgTest", Test.class, task -> {

			task.setGroup("verification");
			task.setDescription("実 PostgreSQL に接続するテストを実行する（開発用 DB が必要）");

			task.onlyIf("application.pgtest.conf が無いモジュール", ignore -> hasPgTestConf.get());

			// 開発用 DB は1つ。同時に走らせない
			task.usesService(sharedDatabase);

			task.setTestClassesDirs(testSourceSet.getOutput().getClassesDirs());
			task.setClasspath(testSourceSet.getRuntimeClasspath());

			task.systemProperty("env", "pgtest");

			// 常に実行する（結果をキャッシュしない）
			task.getOutputs().upToDateWhen(ignore -> false);

			task.getTestLogging().events("passed", "failed");

		});

	}

	/**
	 * ベンチマーク（要件 NF-P-06）
	 *
	 * <pre>
	 * ./gradlew :jimble-web:bench
	 * </pre>
	 *
	 * <p>
	 * <b>落とすのは「1回あたりに割り当てた byte 数」だけで、時間では落とさない。</b>
	 * CI の共用ランナーは走るたびに 20〜30% ぶれるので、
	 * 「ベースライン比 -10%」を時間でやると<b>直していないのに赤くなる日</b>ができる。
	 * 赤が信用されなくなると、本物の退行も見過ごされる（詳しくは {@code Bench} の javadoc）。
	 * </p>
	 *
	 * <p>
	 * 測った値は {@code build/bench/bench.txt} に出る。CI はこれを成果物として持ち帰るだけで、
	 * <b>中身を見て落とすことはしない</b>（人が前後を見比べるためのもの）。
	 * </p>
	 *
	 * @param project		プロジェクト
	 * @param testSourceSet	テストのソースセット
	 */
	private static void bench (Project project, SourceSet testSourceSet) {

		/*
		 * ベンチマークを持っているか。
		 *
		 * <b>持っていないモジュールで走らせると「テストが1つも見つからない」で落ちる。</b>
		 * pgTest と同じで、飛ばしたことは SKIPPED としてログに出る。
		 */
		Provider<Boolean> hasBench = project.provider(
			() -> hasDirectory(testSourceSet.getJava(), "bench"));

		project.getTasks().register("bench", Test.class, task -> {

			task.setGroup("verification");
			task.setDescription("ベンチマークを実行する（要件 NF-P-06）");

			task.onlyIf("ベンチマークが無いモジュール", ignore -> hasBench.get());

			task.setTestClassesDirs(testSourceSet.getOutput().getClassesDirs());
			task.setClasspath(testSourceSet.getRuntimeClasspath());

			// 常に実行する（結果をキャッシュしない）
			task.getOutputs().upToDateWhen(ignore -> false);

			// 測った表を流す
			task.getTestLogging().events("passed", "failed");
			task.getTestLogging().setShowStandardStreams(true);

		});

	}

	/**
	 * リソースの置き場にそのファイルがあるか
	 *
	 * @param sourceSet	ソースセット
	 * @param name		ファイル名
	 * @return あれば true
	 */
	private static boolean hasFile (SourceSet sourceSet, String name) {

		for (File dir : sourceSet.getResources().getSrcDirs()) {
			if (new File(dir, name).exists()) {
				return true;
			}
		}

		return false;

	}

	/**
	 * ソースの置き場の下に、その名前のディレクトリがあるか
	 *
	 * @param source	ソース
	 * @param name		ディレクトリ名
	 * @return あれば true
	 */
	private static boolean hasDirectory (SourceDirectorySet source, String name) {

		for (File root : source.getSrcDirs()) {

			if (!root.isDirectory()) {
				continue;
			}

			if (find(root, name)) {
				return true;
			}

		}

		return false;

	}

	/**
	 * 下まで探す
	 *
	 * @param dir	探す先
	 * @param name	ディレクトリ名
	 * @return あれば true
	 */
	private static boolean find (File dir, String name) {

		File[] children = dir.listFiles(File::isDirectory);

		if (children == null) {
			return false;
		}

		for (File child : children) {
			if (child.getName().equals(name) || find(child, name)) {
				return true;
			}
		}

		return false;

	}

}
