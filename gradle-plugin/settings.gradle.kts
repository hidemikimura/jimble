/*
 * Gradle プラグインは「含まれるビルド」にしてある。
 *
 * 理由：プラグインを使う側（アプリ）のビルドスクリプトから普通に
 *       plugins { id("io.jimble.db") } と書けるようにするため。
 *       Plugin Portal に publish しなくても composite build で解決できる。
 */
pluginManagement {

	repositories {
		mavenCentral()
	}

	/*
	 * ビルドの規約（設計書 D-13）。
	 *
	 * <b>本体と同じものを取り込む。</b>POM の必須項目（licenses / developers / scm）は
	 * 以前ここに同じ 25 行を書き写していて、「直すときは両方直すこと」と
	 * 注意書きを付けていた。build-logic を別ビルドにしたので共有できる。
	 *
	 * ../build-logic は本体の settings.gradle.kts も取り込んでいるが、
	 * 同じ場所を指していれば Gradle が1つにまとめる。
	 * これで <b>./gradlew -p gradle-plugin build（CI の plugin ジョブ）</b> のように
	 * このビルドだけを動かしたときにも規約が効く。
	 */
	includeBuild("../build-logic")

}

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
