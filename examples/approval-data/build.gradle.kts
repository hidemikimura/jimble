plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")

	application
	id("io.jimble.db")
	id("io.jimble.run")
}

description = "サンプル：DB の込み入った話（トランザクション・サブ DB・一括更新・キャッシュ・ロック）"

dependencies {
	implementation(project(":jimble-web"))

	/*
	 * 足す依存は無い。
	 *
	 * Cache / DBLock / RedisLock / DBValue / CodeMigration はどれも jimble-db にあり、
	 * jimble-web から推移的に来る。Redisson も jimble-db の依存である。
	 */

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
 */
tasks.named("dbTest") {
	enabled = false
}

application {
	mainClass = "approval.data.DataApp"
	applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

jimbleRun {
	mainClass = "approval.data.DataApp"
}
