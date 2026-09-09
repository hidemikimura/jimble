plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")
	id("jimble.publish-conventions")
}

description = "jimble のバッチ管理画面。バッチ一覧・履歴・実行状況（要件 F-B-11）"

dependencies {
	// manager → web / batch → ... （どちらも db より上。一方向は保たれる）
	api(project(":jimble-web"))
	api(project(":jimble-batch"))

	// 実 DB に繋ぐテスト（dbTest）用
	testRuntimeOnly(libs.mariadb.client)
}
