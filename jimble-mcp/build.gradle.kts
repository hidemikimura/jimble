plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")
	id("jimble.publish-conventions")
}

description = "MCP（Model Context Protocol）サーバー。Streamable HTTP と stdio / 仕様 2026-07-28"

dependencies {
	api(project(":jimble-web"))
}
