plugins {
	/*
	 * Gradle 同梱のコアプラグイン。<b>Plugin Portal を見に行かない</b>（D-22 / D-13）。
	 *
	 * <b>Kotlin の規約スクリプト（kotlin-dsl）は使えない。</b>
	 * kotlin-dsl プラグインの実体（org.gradle.kotlin:gradle-kotlin-dsl-plugins）は
	 * Gradle の配布物に入っておらず、<b>Plugin Portal からしか取れない</b>
	 * （Maven Central には org.gradle.kotlin というグループ自体が無い）。
	 * D-22 が「Plugin Portal への依存を持ち込まない」と決めていて、
	 * その理由としてここ（D-13）を名指ししているので、Java で書く。
	 * gradle-plugin と同じ形なので、読み方も同じである。
	 */
	`java-gradle-plugin`
}

/*
 * <b>ここは Gradle デーモンの中で動くコードである。</b>
 * デーモンの Java はビルドする側の環境で決まり、本体（Java 25）とは別物になる。
 * ツールチェーンで 25 を要求すると<b>デーモンが 21 の環境ではビルドが始まる前に落ちる</b>ので、
 * gradle-plugin と同じく release だけ Gradle 9 の下限に合わせる。
 */
tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release = 17
	options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
}

gradlePlugin {
	plugins {
		create("javaConventions") {
			id = "jimble.java-conventions"
			implementationClass = "io.jimble.build.JavaConventionsPlugin"
			displayName = "jimble java conventions"
			description = "Java の共通設定（ツールチェーン / -Werror / doclint）"
		}
		create("testConventions") {
			id = "jimble.test-conventions"
			implementationClass = "io.jimble.build.TestConventionsPlugin"
			displayName = "jimble test conventions"
			description = "test / dbTest / pgTest / bench"
		}
		create("publishConventions") {
			id = "jimble.publish-conventions"
			implementationClass = "io.jimble.build.PublishConventionsPlugin"
			displayName = "jimble publish conventions"
			description = "Maven Central へ出すモジュールの設定"
		}
		create("pluginPublishConventions") {
			id = "jimble.plugin-publish-conventions"
			implementationClass = "io.jimble.build.PluginPublishConventionsPlugin"
			displayName = "jimble gradle-plugin publish conventions"
			description = "gradle-plugin（別ビルド）を Maven Central へ出す設定"
		}
	}
}
