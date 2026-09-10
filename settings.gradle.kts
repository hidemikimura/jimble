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

/*
 * 負荷試験（要件 NF-P-08）。
 *
 * <b>公開しない。</b>秒あたり何本さばけるかを手元で測るためだけのもので、
 * 使う人のクラスパスには関係しない（publish-conventions を付けていない）。
 */
include("jimble-load")

include("examples:hello")
include("examples:blog")

/*
 * 機能ごとのサンプル（残件 N-3。docs/design-n3.md）。
 *
 * <b>1本ずつ単体で読めるようにしてある。</b>共通モジュールを持たないので、
 * テーブルもマイグレーションも設定もそれぞれが自分で持つ。
 * <b>PostgreSQL 単独</b>である（方言の両対応は examples/blog が担保する）。
 */
include("examples:approval-auth")
include("examples:approval-forms")
include("examples:approval-list")

// 以降のマイルストーンで追加する
// include("jimble-migration")       M3（jimble-db に同居させた）
// include("jimble-codegen")         M3（jimble-db に同居させた）
