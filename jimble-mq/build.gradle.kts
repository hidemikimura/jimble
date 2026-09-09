plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")
	id("jimble.publish-conventions")
}

description = "jimble の MQ 実行基盤。DB をキューとして使う"

dependencies {
	// mq → db → util → core
	api(project(":jimble-db"))

	// 実 DB に繋ぐテスト（dbTest）用
	testRuntimeOnly(libs.mariadb.client)
}
