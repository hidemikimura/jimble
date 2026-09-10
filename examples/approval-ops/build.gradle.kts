plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")

	application
	id("io.jimble.run")
}

description = "サンプル：動いている中を見る（ヘルスチェック・メトリクス・トレース・流量制限）"

dependencies {
	implementation(project(":jimble-web"))

	/*
	 * トレースの実体（要件 NF-O-05 / D-126）。
	 *
	 * <b>この1行を消しても動く。</b>消すと口だけになり、
	 * {@code Tracing.start(...)} は 1回あたり 0 byte の空実装になる
	 * （`TracingTest` の「登録していなければ、1回あたり 0 byte」がそれを見張っている）。
	 *
	 * <b>入れただけでは何も起きない。</b>アプリが JimbleOtel.install(...) を呼んで初めて出る。
	 */
	implementation(project(":jimble-otel"))
}

/*
 * <b>DB を使わない。</b>io.jimble.db プラグインも JDBC ドライバも要らない
 * （examples/hello と同じ形）。
 *
 * このサンプルの結合テストは @Tag("db") を付けないので、
 * <b>DB の無い CI ジョブ（build）で走る</b>。
 */
sourceSets {
	main {
		resources {
			srcDir("conf")
		}
	}
}

application {
	mainClass = "approval.ops.OpsApp"
	applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

jimbleRun {
	mainClass = "approval.ops.OpsApp"
}
