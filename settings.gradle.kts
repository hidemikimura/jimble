/*
 * Gradle プラグインは別ビルドにしてここから取り込む。
 * これで plugins { id("io.jimble.db") } が publish なしで解決できる。
 */
pluginManagement {
	/*
	 * 取り込んだプラグインの依存（jte のコンパイラ）はこのビルドの
	 * classpath として解決される。既定は Plugin Portal だけなので、
	 * Maven Central を足す。Plugin Portal は使わない（D-22）。
	 */
	repositories {
		mavenCentral()
	}

	/*
	 * ビルドの規約（設計書 D-13）。
	 * jimble.java-conventions / jimble.test-conventions /
	 * jimble.publish-conventions / jimble.central-publish がここから来る。
	 * gradle-plugin も同じものを取り込んでいる（POM の必須項目を共有するため）。
	 */
	includeBuild("build-logic")

	includeBuild("gradle-plugin")
}

rootProject.name = "jimble"

dependencyResolutionManagement {
	repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
	repositories {
		mavenCentral()
	}
}

// M1
include("jimble-core")
include("jimble-util")
include("jimble-db")
include("jimble-web")

// M7
include("jimble-batch")
include("jimble-mq")
include("jimble-batch-manager")
// M8
include("jimble-cli")

// M9
include("jimble-mcp")
include("jimble-docs")

/*
 * OpenTelemetry（要件 NF-O-05）。
 *
 * <b>本体からは参照しない。</b>jimble-core が持っているのは口（Tracer）だけで、
 * 実装をここに分けてあるので、使わないアプリの実行時クラスパスは1 byte も増えない。
 */
include("jimble-otel")

include("examples:hello")
include("examples:blog")

// 以降のマイルストーンで追加する
// include("jimble-migration")       M3（jimble-db に同居させた）
// include("jimble-codegen")         M3（jimble-db に同居させた）
