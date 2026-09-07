plugins {
	application
}

description = "プロジェクト雛形の生成と、マイグレーション・コード生成の実行（要件 F-X-01 / F-G-08）"

dependencies {
	/*
	 * migrate / codegen は jimble-db の CLI に委ねる。
	 * ここが持つのは入口と雛形だけである。
	 */
	api(project(":jimble-db"))

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
}

/*
 * 版は雛形が参照する依存の版になる（Version クラスが読む）。
 * 手で書かず、ビルドの版をそのまま入れる。
 */
tasks.jar {
	manifest {
		attributes(
			"Implementation-Title" to "jimble",
			"Implementation-Version" to project.version,
		)
	}
}

application {
	mainClass = "io.jimble.cli.JimbleCli"
	applicationName = "jimble"
	applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
