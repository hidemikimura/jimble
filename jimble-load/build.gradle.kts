plugins {
	id("jimble.java-conventions")

	/*
	 * テストは1つだけある（行に付ける印の線引き）。
	 * 印が出る条件を人の目だけで守ると、いつのまにか「いつも出る」か「出ない」になる
	 */
	id("jimble.test-conventions")

	application
}

description = "負荷をかけて秒あたりの本数とレイテンシを測る（要件 NF-P-08）。公開しない"

dependencies {
	implementation(project(":jimble-web"))

	/*
	 * <b>helidon を直に足す。</b>jimble-web では implementation なので
	 * ここまで降りてこない。比較の相手（素の helidon）を立てるのに要る。
	 */
	implementation(libs.helidon.webserver)
	implementation(libs.helidon.logging.jul)
}

application {
	mainClass = "io.jimble.load.LoadMain"
	applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
