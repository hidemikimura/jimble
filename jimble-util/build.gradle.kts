description = "jimble の共通ユーティリティ。Data / 型変換 / JSON / 文字列 / 日時 / IO / HTTP クライアントなど"

/*
 * jooby_base から移送したコード（168ファイル / 約 23,700 行）。
 * 移送時点の警告が多いため、うるさい種別だけ落としている。
 * 段階的に潰して、最終的にはこのブロックを消す。
 */
tasks.withType<JavaCompile>().configureEach {
	options.compilerArgs.addAll(
		listOf(
			"-Xlint:-unchecked",
			"-Xlint:-rawtypes",
			"-Xlint:-fallthrough",
			"-Xlint:-deprecation",
			"-Xlint:-dangling-doc-comments",
			"-Xlint:-this-escape",
			"-Xlint:-cast",
			"-Xlint:-overloads",
		)
	)
}

dependencies {
	api(project(":jimble-core"))

	// Data を API に露出する型
	api(libs.jspecify)

	// 設定（HOCON）とログ
	api(libs.typesafe.config)
	api(libs.slf4j.api)
	// エンコーダの実装で logback の型を使う
	compileOnly(libs.logback.classic)
	runtimeOnly(libs.logback.classic)
	// エンコーダのテストで logback のイベントを組み立てる
	testImplementation(libs.logback.classic)

	implementation(libs.guava)
	implementation(libs.icu4j)
	implementation(libs.tika.core)
	implementation(libs.tika.parser.text)
	implementation(libs.fastcsv)
	implementation(libs.jts.core)
	implementation(libs.caffeine)
	implementation(libs.commons.text)
	implementation(libs.commons.validator)
	implementation(libs.jbcrypt)
	implementation(libs.juniversalchardet)
	implementation(libs.brotli.dec)
	implementation(libs.aircompressor)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
}
