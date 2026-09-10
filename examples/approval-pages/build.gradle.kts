plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")

	application
	id("io.jimble.jte")
	id("io.jimble.run")
}

description = "サンプル：画面を返す（jte のレイアウト・静的配信・SPA・MPA・プロキシ）"

dependencies {
	implementation(project(":jimble-web"))
}

/*
 * DB を使わない。io.jimble.db プラグインも JDBC ドライバも要らない
 * （examples/hello / examples/approval-ops と同じ形）。
 *
 * このサンプルの結合テストは @Tag("db") を付けないので、
 * DB の無い CI ジョブ（build）で走る。
 */
sourceSets {
	main {
		resources {
			srcDir("conf")
		}
	}
}

application {
	mainClass = "approval.pages.PagesApp"
	applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

jimbleRun {
	mainClass = "approval.pages.PagesApp"
}
