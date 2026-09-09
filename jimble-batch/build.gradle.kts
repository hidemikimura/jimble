plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")
	id("jimble.publish-conventions")
}

description = "jimble のバッチ実行基盤。バッチ / スケジューラ"

dependencies {
	// batch → mq → db → util → core（D-38）
	api(project(":jimble-db"))
	// スケジューラが「今すぐ実行」の指示を受けるのに MQ を使う
	api(project(":jimble-mq"))

	// cron のパース（要件 F-B-04）
	implementation(libs.cron.utils)

	// 実 DB に繋ぐテスト（dbTest）用
	testRuntimeOnly(libs.mariadb.client)
}
