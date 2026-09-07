description = "jimble の DB 層。SQL ビルダー / DB / トランザクション / キャッシュ / 分散ロック"

/*
 * jooby_base から移送したコード。移送時点の警告は段階的に潰す（jimble-util と同じ扱い）。
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
	api(project(":jimble-util"))

	// コネクションプール（設定で切り替える）
	api(libs.hikaricp)
	api(libs.agroal.pool)

	// JDBC ドライバ
	runtimeOnly(libs.mariadb.client)

	// Redis（キャッシュ / 分散ロック）
	api(libs.redisson)

	implementation(libs.caffeine)
	implementation(libs.guava)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
}
