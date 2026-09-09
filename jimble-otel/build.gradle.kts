plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")
	id("jimble.publish-conventions")
}

description = "OpenTelemetry へトレースを出す（要件 NF-O-05）。使うアプリだけが依存を払う"

dependencies {

	// 口（Tracer / Span）は core にある。web も db も要らない
	api(project(":jimble-core"))

	/*
	 * トレースだけを使う（要件 NF-O-05）。
	 *
	 * <b>opentelemetry-sdk（まとめ役）は使わない。</b>あれは metrics と logs も
	 * 連れてくるが、jimble が出すのはトレースだけである
	 * （メトリクスは Metrics が持っていて、外に送るかどうかはアプリが決める。D-124）。
	 */
	api(libs.opentelemetry.sdk.trace)

	/*
	 * OTLP で送る。
	 *
	 * <b>okhttp を外して JDK の HttpClient に差し替える。</b>
	 * 既定の送信器は okhttp で、okhttp 851KB ＋ okio 374KB ＋
	 * <b>kotlin-stdlib 1.7MB</b> が付いてくる。送っているのは protobuf の POST 1本で、
	 * JDK の HttpClient で足りる（D-24 と同じ判断。依存の縮小は NF-L-01）。
	 *
	 * <b>足すだけでは駄目で、外す必要がある。</b>送信器は ServiceLoader で選ばれるので、
	 * 両方あるとクラスパスに jar が並ぶ順で決まってしまう（D-122 と同じ罠）。
	 */
	api(libs.opentelemetry.exporter.otlp) {
		exclude(group = "io.opentelemetry", module = "opentelemetry-exporter-sender-okhttp")
		exclude(group = "io.opentelemetry", module = "opentelemetry-sdk-metrics")
		exclude(group = "io.opentelemetry", module = "opentelemetry-sdk-logs")
	}

	runtimeOnly(libs.opentelemetry.exporter.sender.jdk)

}

/*
 * このモジュールを足すと何 byte 増えるかを、その場で数えられるようにしておく。
 *   ./gradlew :jimble-otel:deps
 */
tasks.register("deps") {
	group = "help"
	description = "実行時クラスパスの内訳を出す（要件 NF-L-01）"
	val files = configurations.named("runtimeClasspath")
	doLast {
		var total = 0L
		files.get().sortedByDescending { it.length() }.forEach {
			if (!it.name.startsWith("jimble-")) {
				total += it.length()
				println("%-58s %8.1f KB".format(it.name, it.length() / 1024.0))
			}
		}
		println("合計 %.2f MB".format(total / 1024.0 / 1024.0))
	}
}
