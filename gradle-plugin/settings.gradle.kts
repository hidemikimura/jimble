/*
 * Gradle プラグインは「含まれるビルド」にしてある。
 *
 * 理由：プラグインを使う側（アプリ）のビルドスクリプトから普通に
 *       plugins { id("io.jimble.db") } と書けるようにするため。
 *       Plugin Portal に publish しなくても composite build で解決できる。
 */
rootProject.name = "jimble-gradle-plugin"

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}

	// 版は本体と同じ表を見る。プラグインだけ別の版になるのを防ぐ
	versionCatalogs {
		create("libs") {
			from(files("../gradle/libs.versions.toml"))
		}
	}
}
