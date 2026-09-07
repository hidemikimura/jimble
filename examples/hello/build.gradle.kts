plugins {
	application
}

description = "最小のサンプルアプリケーション"

dependencies {
	implementation(project(":jimble-web"))
}

application {
	mainClass = "hello.HelloApp"
	// 日本語ログの文字化け対策（要件 F-U-12）
	applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
