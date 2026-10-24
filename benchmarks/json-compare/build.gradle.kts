/*
 * JSON ライブラリとの性能比較。
 *
 * 既定では、利用者と同じ条件にするため jimble 本体のソースではなく Maven Central に公開した版を使う
 * （-Pjimble.local=true でソースを使う。settings.gradle.kts を参照）。
 * 比べる相手の版は固定する。変えたら README.md の表も直すこと。
 */
plugins {
	java
	application
}

repositories {
	mavenCentral()
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

dependencies {
	// 測られる側（JSON と型変換は jimble-util にある）
	implementation("io.jimble:jimble-util:2.5.12")

	// 比べる相手（基準）
	implementation("com.alibaba.fastjson2:fastjson2:2.0.66")
	implementation("io.github.wycst:wast:0.0.29.1")
	implementation("org.apache.fory:fory-json:1.7.7")

	// 比べる相手（参考）。書く側の共通の入力を作るのと、結果が正しいかを見るのにも使う
	implementation("tools.jackson.core:jackson-databind:3.2.4")
}

application {
	mainClass = "bench.JsonCompare"
}
