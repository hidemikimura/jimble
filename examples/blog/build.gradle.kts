plugins {
	application
	id("io.jimble.db")
	// src/main/jte を compileJava の前に Java へ変換する（要件 F-W-10 / O-13）
	id("io.jimble.jte")
	// 開発用のホットリロード（要件 F-X-02）
	id("io.jimble.run")
}

description = "マイグレーションとコード生成つきのサンプルアプリケーション"

dependencies {
	implementation(project(":jimble-web"))
	implementation(project(":jimble-batch"))
	implementation(project(":jimble-mq"))
	implementation(project(":jimble-batch-manager"))
	implementation(project(":jimble-mcp"))
	runtimeOnly(libs.mariadb.client)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
	testRuntimeOnly(libs.mariadb.client)
	// pgTest 用（要件 F-D-30）。application.pgtest.conf が product = postgresql を持つ
	testRuntimeOnly(libs.postgresql.client)
}

jimble {
	/*
	 * アプリ側のプロジェクトでは、この行を書かなければローカルで自動的に流れる（要件 F-G-07）。
	 *
	 * ここで既定を false にしているのは、jimble 自身の build を DB なしで通すため
	 * （要件 D-16 と同じ方針）。連鎖させるときは次のようにする。
	 *   ./gradlew :examples:blog:build -Pjimble.autoGenerate=true
	 */
	autoGenerate = providers.gradleProperty("jimble.autoGenerate").map(String::toBoolean).orElse(false)
}

/*
 * 設定とマイグレーションは conf/ に置く（移送元と同じ）。
 *
 * リソースとして足すので、conf/ の中身は<b>そのまま jar に入る</b>。
 * jimble は<b>クラスパスの設定しか読まない</b>（jimble D-80）ので、
 * これを外すと設定が見つからない。
 * codegen / migrate / jimbleRun / テストも同じファイルを見る。
 */
sourceSets {
	main {
		resources {
			srcDir("conf")
		}
	}
}

application {
	mainClass = "blog.BlogApp"
	applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

/*
 * ./gradlew :examples:blog:jimbleRun
 *
 * http://localhost:9000 を開くと、その時点のソースでアプリが動く。
 * ソースやテンプレートを直してリロードすれば、作り直してから応える。
 */
jimbleRun {
	mainClass = "blog.BlogApp"
}
