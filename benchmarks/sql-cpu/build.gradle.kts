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

	implementation("org.openjdk.jmh:jmh-core:1.37")
	annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

application {
	mainClass = "org.openjdk.jmh.Main"
}
