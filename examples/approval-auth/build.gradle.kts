plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")

	application
	id("io.jimble.db")
	id("io.jimble.jte")
	id("io.jimble.run")
}

description = "サンプル：誰が入れるか（セッション・認可・CSRF・パスワード）"

dependencies {
	implementation(project(":jimble-web"))

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
 * <b>dbTest（MySQL）では動かさない。</b>
 *
 * このサンプルは PostgreSQL 単独である（N-3 の決定3）。
 * dbTest は application.pgtest.conf の有無を見ないので、
 * <b>放っておくと MySQL しか無い CI で PostgreSQL に繋ぎに行って落ちる</b>。
 * pgTest のほうは conf の有無で自分から止まるので、この指定は要らない。
 */
tasks.named("dbTest") {
	enabled = false
}

application {
	mainClass = "approval.auth.AuthApp"
	applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

jimbleRun {
	mainClass = "approval.auth.AuthApp"
}
