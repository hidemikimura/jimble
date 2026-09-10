plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")

	application
	id("io.jimble.db")
	id("io.jimble.run")
}

description = "サンプル：時間のかかる仕事（バッチ・スケジューラ・MQ の失敗とリトライ）"

dependencies {
	implementation(project(":jimble-web"))

	// バッチとスケジューラ（要件 F-B-*）
	implementation(project(":jimble-batch"))

	// キュー（要件 F-M-*）。jimble-db は推移的に来る
	implementation(project(":jimble-mq"))

	// バッチ管理画面（要件 F-B-11）
	implementation(project(":jimble-batch-manager"))

	runtimeOnly(libs.postgresql.client)
	testRuntimeOnly(libs.postgresql.client)
}

jimble {
	// 既定は false。連鎖させるときは -Pjimble.autoGenerate=true（examples/blog と同じ）
	autoGenerate = providers.gradleProperty("jimble.autoGenerate").map(String::toBoolean).orElse(false)
}

sourceSets {
	main {
		resources {
			srcDir("conf")
		}
	}
}

/*
 * dbTest（MySQL）では動かさない。
 *
 * このサンプルは PostgreSQL 単独である（N-3 の決定3）。
 * dbTest は application.pgtest.conf の有無を見ないので、
 * 放っておくと MySQL しか無い CI で PostgreSQL に繋ぎに行って落ちる。
 * pgTest のほうは conf の有無で自分から止まるので、この指定は要らない。
 */
tasks.named("dbTest") {
	enabled = false
}

application {
	mainClass = "approval.jobs.JobsApp"
	applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

jimbleRun {
	mainClass = "approval.jobs.JobsApp"
}
