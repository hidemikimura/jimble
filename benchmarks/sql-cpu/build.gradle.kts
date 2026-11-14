/*
 * SQL の組み立て・SQL 結果キャッシュに当たる SELECT・Cookie を付けた応答の CPU を JMH で測る。
 *
 * 既定では Maven Central に公開した版（基準）を使う。-Pjimble.local=true でこのリポジトリのソースを使う。
 * 接続先は bench 用の MySQL（127.0.0.1:13306）だけ。src/main/resources/application.conf を参照。
 */
plugins {
	java
	application
}

repositories {
	// 2.7.11 からの公開版は jimble の置き場にある（基準の版として測るため。jimble のものだけ取る）
	maven("https://repo.jimble.io") {
		content { includeGroupByRegex("io\\.jimble(\\..*)?") }
	}
	mavenCentral()
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

val jimbleVersion = providers.gradleProperty("jimble.version").getOrElse("2.7.4")

dependencies {
	implementation("io.jimble:jimble-db:$jimbleVersion")
	implementation("io.jimble:jimble-web:$jimbleVersion")
	implementation("org.mariadb.jdbc:mariadb-java-client:4.0.0")

	// SQL 結果キャッシュのキーのハッシュを比べるため（ベンチだけ。jimble の依存ではない）
	implementation("com.dynatrace.hash4j:hash4j:0.31.0")

	implementation("org.openjdk.jmh:jmh-core:1.37")
	annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

application {
	mainClass = "org.openjdk.jmh.Main"
}

/*
 * 古い公開版ではコンパイルできないベンチは、このリポジトリのソースで測るときだけ入れる
 * （template は 3.0.0 から、HashBench の Hash.sipHashOf は 2.7.5 から）
 */
if (providers.gradleProperty("jimble.local").orNull != "true") {
	sourceSets.main {
		java.exclude("bench/Template*.java", "bench/HashBench.java")
	}
}
