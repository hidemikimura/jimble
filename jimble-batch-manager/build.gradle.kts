description = "jimble のバッチ管理画面。バッチ一覧・履歴・実行状況（要件 F-B-11）"

dependencies {
	// manager → web / batch → ... （どちらも db より上。一方向は保たれる）
	api(project(":jimble-web"))
	api(project(":jimble-batch"))

	// 実 DB に繋ぐテスト（dbTest）用
	testRuntimeOnly(libs.mariadb.client)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
}
