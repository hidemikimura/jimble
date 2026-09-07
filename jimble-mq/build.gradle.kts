description = "jimble の MQ 実行基盤。DB をキューとして使う"

dependencies {
	// mq → db → util → core
	api(project(":jimble-db"))

	// 実 DB に繋ぐテスト（dbTest）用
	testRuntimeOnly(libs.mariadb.client)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
}
