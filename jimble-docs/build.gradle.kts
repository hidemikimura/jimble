plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")

	application
	// サイトのテンプレートは jte。ドキュメントサイト自身が jimble の実例になる
	id("io.jimble.jte")
}

description = "ドキュメントサイトの生成（要件 NF-D-01〜06）"

dependencies {
	/*
	 * jte のランタイムだけ。サーバーは要らない（静的 HTML を吐くだけ）。
	 */
	implementation(project(":jimble-util"))
	implementation(libs.jte.runtime)

	/*
	 * Markdown。ビルド時にだけ使うので、アプリの実行時クラスパスには入らない
	 * （jte のコンパイラや directory-watcher と同じ扱い）。
	 */
	implementation(libs.commonmark)
	implementation(libs.commonmark.tables)
}

application {
	mainClass = "io.jimble.docs.DocsBuilder"
	applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

/*
 * ./gradlew :jimble-docs:site
 *
 * docs/site/<言語> の .md を読み、docs/site/build/ に静的 HTML を出す。
 */
tasks.register<JavaExec>("site") {
	group = "documentation"
	description = "ドキュメントサイトを生成する（要件 NF-D-01）"

	dependsOn(tasks.named("classes"))

	mainClass = "io.jimble.docs.DocsBuilder"
	classpath = sourceSets["main"].runtimeClasspath

	// Gradle デーモンの JVM ではなく、プロジェクトのツールチェーンで動かす
	javaLauncher = javaToolchains.launcherFor(java.toolchain)

	// 印の付いたコードを抜く元と、出す先
	args(rootDir.absolutePath, "${rootDir}/docs/site/build")

	jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")

	outputs.upToDateWhen { false }
}
