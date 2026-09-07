description = "jimble のコア。HTTP を知らない層（Context / Executor）"

dependencies {
	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
}
