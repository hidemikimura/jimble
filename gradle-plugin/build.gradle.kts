plugins {
	// どれも Gradle 同梱のコアプラグイン。Plugin Portal を見に行かない
	`java-gradle-plugin`
	/*
	 * jimble new で作ったプロジェクトは includeBuild ではなく
	 * 普通の plugins { id("io.jimble.jte") version "..." } でプラグインを取る。
	 * そのためにローカルの Maven リポジトリへ publish できるようにする。
	 *   ./gradlew -p gradle-plugin publishToMavenLocal
	 * java-gradle-plugin がプラグインマーカーも一緒に出す。
	 */
	`maven-publish`
}

description = "jimble の Gradle プラグイン（migrate / codegen / jte 変換 / ホットリロード）"

group = "io.jimble"

/*
 * 版は本体と揃える（-Pjimble.version）。
 * 外側のビルドに渡したプロパティは、取り込まれたこのビルドにも届く。
 */
version = providers.gradleProperty("jimble.version").getOrElse("0.1.0-SNAPSHOT")

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
	// Maven Central は jar ごとに -sources.jar と -javadoc.jar を要求する
	withSourcesJar()
	withJavadocJar()
}

tasks.withType<Javadoc>().configureEach {
	options.encoding = "UTF-8"
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	/*
	 * プラグインは Gradle デーモンの中で動く。デーモンの Java は
	 * ビルドする側の環境で決まり、本体（Java 25）とは別物である。
	 * Gradle 9 の下限に合わせて 17 で出す。
	 */
	options.release = 17
	options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-parameters"))
}

dependencies {
	/*
	 * jte のコンパイラ本体。ここ（Gradle デーモンの中）でだけ使う。
	 * アプリの実行時クラスパスには入らない（要件 F-W-10）。
	 */
	implementation(libs.jte.compiler)

	/*
	 * ホットリロードのファイル監視（要件 F-X-02）。
	 * これも Gradle デーモンの中だけ。
	 */
	implementation(libs.directory.watcher)

	testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
	testLogging {
		events("failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}

gradlePlugin {
	plugins {
		create("jimbleDb") {
			id = "io.jimble.db"
			implementationClass = "io.jimble.gradle.JimbleDbPlugin"
			displayName = "jimble db"
			description = "マイグレーションとコード生成を compileJava の前に流す"
		}
		create("jimbleJte") {
			id = "io.jimble.jte"
			implementationClass = "io.jimble.gradle.JimbleJtePlugin"
			displayName = "jimble jte"
			description = "jte テンプレートを compileJava の前に Java へ変換する"
		}
		create("jimbleRun") {
			id = "io.jimble.run"
			implementationClass = "io.jimble.gradle.run.JimbleRunPlugin"
			displayName = "jimble run"
			description = "開発用のホットリロード。ソースを見張り、リクエストが来たら作り直してアプリを入れ替える"
		}
	}
}

/*
 * Maven Central へ出す（要件 NF-L-04 / D-22）。
 *
 * Gradle Plugin Portal には出さない。かわりに、jimble new が作る
 * settings.gradle.kts の pluginManagement で mavenCentral() を見に行かせる。
 * java-gradle-plugin が
 *
 *   io.jimble.jte:io.jimble.jte.gradle.plugin
 *
 * という形のプラグインマーカーも一緒に publish するので、
 * plugins { id("io.jimble.jte") version "..." } がそれで解決できる。
 *
 * 置き場はリポジトリの build/central。本体と同じ場所に出して、
 * 外側の centralBundle が丸ごと zip にする。
 */
publishing {
	repositories {
		maven {
			name = "central"
			// rootDir はこの別ビルド（<repo>/gradle-plugin）。その隣の build/central を指す
			url = uri(rootDir.resolveSibling("build").resolve("central"))
		}
	}
}

/*
 * POM の必須項目。本体（<repo>/build.gradle.kts の jimblePom）と同じ内容である。
 * 別ビルドなので共有できない。直すときは両方直すこと。
 *
 * プラグインマーカーの publication は java-gradle-plugin があとから足すので、
 * withType + configureEach で「これから増えるもの」にも掛ける。
 */
publishing.publications.withType<MavenPublication>().configureEach {

	pom {

		// マーカーは name も description も空のまま出るので、ここで入れる
		name = artifactId
		description = provider { project.description ?: "jimble Gradle plugin" }
		url = "https://jimble.io"

		licenses {
			license {
				name = "The Apache License, Version 2.0"
				url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
			}
		}

		developers {
			developer {
				id = "hidemikimura"
				name = "Hidemi Kimura"
				email = "hidemikimura@gmail.com"
				organization = "ecx Inc."
				organizationUrl = "https://www.ecx.co.jp/"
			}
		}

		scm {
			url = "https://github.com/hidemikimura/jimble"
			connection = "scm:git:https://github.com/hidemikimura/jimble.git"
			// 書き込む側は SSH
			developerConnection = "scm:git:ssh://git@github.com/hidemikimura/jimble.git"
		}

	}

}

/*
 * 署名。鍵は環境変数から（本体と同じ）。
 * 鍵が無い環境では署名を飛ばす。
 */
val signingKey = providers.environmentVariable("JIMBLE_SIGNING_KEY")

if (signingKey.isPresent) {

	apply(plugin = "signing")

	extensions.configure<SigningExtension> {
		useInMemoryPgpKeys(
			signingKey.get()
			, providers.environmentVariable("JIMBLE_SIGNING_PASSWORD").getOrElse(""))
		sign(publishing.publications)
	}

}
