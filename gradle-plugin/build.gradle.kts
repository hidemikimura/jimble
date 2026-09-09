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

	/*
	 * Maven Central へ出す設定（置き場・POM・署名）。
	 * 本体と同じ build-logic から来る（設計書 D-13）。
	 */
	id("jimble.plugin-publish-conventions")
}

description = "jimble の Gradle プラグイン（migrate / codegen / jte 変換 / ホットリロード）"

group = "io.jimble"

/*
 * 版は本体と揃える（-Pjimble.version）。
 * 外側のビルドに渡したプロパティは、取り込まれたこのビルドにも届く。
 */
version = io.jimble.conventions.JimbleBuild.version(project)

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
	/*
	 * 警告はエラーにする（要件 D-15。本体と同じ扱い）。
	 * this-escape は jimble の「コンストラクタで登録する」形に由来するので落とす。
	 */
	options.compilerArgs.addAll(listOf(
		"-Xlint:all", "-Xlint:-serial", "-Xlint:-this-escape", "-Werror", "-parameters"))
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

	testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
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

/*
 * プラグインの作法を<b>手元でも Gradle 9 と同じ厳しさで</b>見る。
 *
 * Gradle 8 は「キャッシュの可否を書いていない」「正規化を書いていない」を
 * <b>警告で流す</b>が、Gradle 9 は<b>エラーで落とす</b>。
 * 手元が 8 のままだと、<b>CI で初めて分かる</b>ことになる（実際そうなった）。
 */
tasks.withType<org.gradle.plugin.devel.tasks.ValidatePlugins>().configureEach {
	enableStricterValidation = true
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
