plugins {
	`java-library`
}

description = "MCP（Model Context Protocol）サーバー。Streamable HTTP / 仕様 2026-07-28"

dependencies {
	api(project(":jimble-web"))

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
}
