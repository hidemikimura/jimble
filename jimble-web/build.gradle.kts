plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")
	id("jimble.publish-conventions")

	// テンプレートのテストのために src/test/jte を Java へ変換する
	id("io.jimble.jte")
}

description = "jimble の Web 層。Router / Dispatcher / Request / Response"

dependencies {
	// 依存は web → db → util → core（D-14）
	api(project(":jimble-db"))

	// HTTP サーバー。io.jimble.web.server 配下からしか触らない
	implementation(libs.helidon.webserver)
	// multipart（ファイルアップロード。要件 F-W-06）
	implementation(libs.helidon.media.multipart)
	// 応答の gzip 圧縮（要件 F-H-03）。server.compression = false で切れる
	/*
	 * 応答の gzip 圧縮（要件 F-H-03）。
	 *
	 * <b>import が1つも無いが消してはいけない。</b>
	 * META-INF/services の ContentEncodingProvider として helidon が拾うので、
	 * 依存を外すと<b>コンパイルは通ったまま gzip が黙って止まる</b>。
	 */
	implementation(libs.helidon.encoding.gzip)
	/*
	 * WebSocket（要件 F-W-22）。+3 jar / 0.1MB。
	 * helidon はアップグレードを HTTP のルーティングより前で横取りするので、
	 * jimble の Router は WS のパスを持てない。
	 * ルート表は jimble 側で持ち、起動時に helidon の WsRouting へ流し込む。
	 */
	implementation(libs.helidon.websocket)

	/*
	 * テンプレート（要件 F-W-08）。
	 * jte-runtime にはコンパイラが入っていない。
	 * 事前コンパイル済みのテンプレートしか描画できない（要件 F-W-10 / O-13）。
	 */
	api(libs.jte.runtime)
	runtimeOnly(libs.helidon.logging.jul)

	// 実 DB に繋ぐテスト（dbTest）用
	testRuntimeOnly(libs.mariadb.client)
}
