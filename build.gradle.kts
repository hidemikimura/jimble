/*
 * 全モジュール共通のビルド設定。
 *
 * NOTE: モジュールが増えたら buildSrc の規約プラグインに移す（設計書 D-13）。
 *       今は Gradle Plugin Portal への依存を持ち込まないためにこの形にしている。
 */

/*
 * 実 DB に繋ぐテスト（dbTest）は同時に走らせない。
 *
 * 開発用 DB は1つしかない（要件 D-16）。org.gradle.parallel=true なので、
 * モジュールが増えると複数の dbTest が同じスキーマを同時に触る。
 * 実際、あるモジュールのテストが TRUNCATE した裏で
 * 別のモジュールのバッチが走っていて、拾えるはずの行が消えていた。
 */
abstract class SharedDatabase : BuildService<BuildServiceParameters.None>

val sharedDatabase = gradle.sharedServices.registerIfAbsent("sharedDatabase", SharedDatabase::class) {
	maxParallelUsages = 1
}

/*
 * 版は -Pjimble.version で上書きできる。
 *
 * Maven Central は -SNAPSHOT を受け付けない（スナップショットは別のリポジトリ）。
 * リリースのときだけ
 *
 *   ./gradlew centralBundle -Pjimble.version=0.1.0
 *
 * のように渡す。既定を素の 0.1.0 にしないのは、
 * うっかり publish したものが「リリース版」として残るのを避けるためである。
 */
val jimbleVersion = providers.gradleProperty("jimble.version").getOrElse("0.1.1-SNAPSHOT")

/* doclint を切るモジュール（要件 D-15。潰したらここから外す） */
val DOCLINT_OFF = setOf("jimble-util")

/*
 * Maven Central へ出さないモジュール。
 *
 * jimble-docs は jimble.io のサイトを作るためのもので、
 * アプリが依存するものではない（このリポジトリの中でしか使い道がない）。
 * examples も同じ理由で出さない（こちらはパスで弾いている）。
 */
val NOT_PUBLISHED = setOf("jimble-docs")

/*
 * Maven Central へ出すときの置き場（要件 NF-L-04）。
 *
 * ここへ Maven のレイアウトのまま publish し、centralBundle が zip にする。
 * gradle-plugin は別ビルド（settings.gradle.kts の includeBuild）なので、
 * 向こうからも同じ場所を指している。
 */
val centralRepoDir = layout.buildDirectory.dir("central")

subprojects {

	apply(plugin = "java-library")

	group = "io.jimble"
	version = jimbleVersion

	extensions.configure<JavaPluginExtension> {
		// Java 25（要件定義 5. 技術前提）
		toolchain {
			languageVersion = JavaLanguageVersion.of(25)
		}
		/*
		 * Maven Central は jar ごとに -sources.jar と -javadoc.jar を要求する。
		 * examples も付いてくるが、publish しないので出力されるだけである。
		 */
		withSourcesJar()
		withJavadocJar()
	}

	tasks.withType<JavaCompile>().configureEach {
		options.encoding = "UTF-8"
		options.compilerArgs.addAll(
			listOf(
				"-Xlint:all",
				"-Xlint:-serial",
				// リフレクションに頼らずパラメータ名を残す
				"-parameters",
			)
		)
	}

	tasks.withType<Javadoc>().configureEach {

		options.encoding = "UTF-8"

		/*
		 * 移送してきたコードは javadoc の作法が揃っていない（要件 D-15）。
		 * ここに挙げたモジュールだけ doclint を切る。
		 * 全体で切ると、これから書くコードの間違いに気づけなくなる。
		 * 潰したら、この表から外す。
		 */
		// ここでの name は Javadoc タスクの名前。見たいのはモジュール名である
		if (this@subprojects.name in DOCLINT_OFF) {
			(options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
		}

	}

	/*
	 * ローカルの Maven リポジトリへ publish できるようにする。
	 *
	 *   ./gradlew publishToMavenLocal
	 *
	 * これがないと「jimble new で作ったプロジェクトが本当にビルドできるか」を
	 * 確かめられない（要件 F-X-01 / NF-T-06）。
	 * Maven Central への公開は Phase 2（NF-L-04）。ここはその下ごしらえである。
	 *
	 * examples は publish しない。ライブラリではない。
	 */
	if (name !in NOT_PUBLISHED && !path.startsWith(":examples")) {

		apply(plugin = "maven-publish")

		extensions.configure<PublishingExtension> {

			publications {
				create<MavenPublication>("maven") {
					from(components["java"])
					pom { jimblePom(this@subprojects.name, provider { this@subprojects.description ?: this@subprojects.name }) }
				}
			}

			/*
			 * central: Maven Central へ出すためのバンドルの材料。
			 *          ここへ出したものを centralBundle が zip にまとめる。
			 */
			repositories {
				maven {
					name = "central"
					url = uri(centralRepoDir)
				}
			}

		}

		/*
		 * 署名（要件 NF-L-04）。Maven Central は全ファイルに .asc を要求する。
		 *
		 * 鍵はファイルに置かず、環境変数から渡す。
		 *   JIMBLE_SIGNING_KEY       gpg --armor --export-secret-keys の中身
		 *   JIMBLE_SIGNING_PASSWORD  その鍵のパスフレーズ
		 *
		 * 鍵が無い環境では署名を飛ばす。CI でも手元でも、
		 * 鍵を持っていない人のビルドが落ちないようにするためである。
		 */
		val signingKey = providers.environmentVariable("JIMBLE_SIGNING_KEY")

		if (signingKey.isPresent) {

			apply(plugin = "signing")

			extensions.configure<SigningExtension> {
				useInMemoryPgpKeys(
					signingKey.get()
					, providers.environmentVariable("JIMBLE_SIGNING_PASSWORD").getOrElse(""))
				sign(extensions.getByType<PublishingExtension>().publications)
			}

		}

	}

	/*
	 * 通常の test は DB を要求しない。
	 * 実 DB が要るテストは @Tag("db") を付け、dbTest タスクで実行する（要件 D-16）。
	 */
	tasks.withType<Test>().configureEach {
		val isDbTest = name == "dbTest"
		useJUnitPlatform {
			if (isDbTest) includeTags("db") else excludeTags("db")
		}
		// 標準出力の文字化け対策（要件 F-U-12）
		jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
		testLogging {
			events("failed")
			exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
			showStackTraces = true
		}
	}

	/*
	 * 実 DB に接続するテスト。開発用 DB が要る。
	 *   ./gradlew :jimble-db:dbTest
	 * 接続先は JIMBLE_TEST_DB_URL / JIMBLE_TEST_DB_USER / JIMBLE_TEST_DB_PASSWORD で上書きできる。
	 */
	val testSourceSet = extensions.getByType<SourceSetContainer>()["test"]

	tasks.register<Test>("dbTest") {
		group = "verification"
		description = "実 DB に接続するテストを実行する（開発用 DB が必要）"

		// 開発用 DB は1つ。同時に走らせない
		usesService(sharedDatabase)

		testClassesDirs = testSourceSet.output.classesDirs
		classpath = testSourceSet.runtimeClasspath

		systemProperty("env", "dbtest")
		jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")

		// 常に実行する（結果をキャッシュしない）
		outputs.upToDateWhen { false }

		testLogging {
			events("passed", "failed")
			exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
		}
	}

}

/*
 * Maven Central が要求する POM の項目（要件 NF-L-04）。
 *
 *   name / description / url / licenses / developers / scm
 *
 * どれか1つでも欠けると、アップロードは通ってから検証で落ちる。
 * gradle-plugin は別ビルドなので、向こうにも同じものがある。
 * 直すときは両方直すこと（共有する仕組みを1つ増やすより、
 * 25 行を2箇所に置くほうが辿りやすい）。
 */
fun MavenPom.jimblePom (moduleName: String, moduleDescription: Provider<String>) {

	name = moduleName
	description = moduleDescription
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

// region Maven Central（要件 NF-L-04）

/*
 * Sonatype には公式の Gradle プラグインが無い（2026-09 時点。ドキュメントに
 * 「no official Gradle plugin」と書いてある）。あるのはコミュニティ製か、
 * Portal の REST API を直に叩くかの二択である。
 *
 * jimble は直に叩く。やることは
 *
 *   1. Maven のレイアウトで build/central に出す（maven-publish。Gradle 同梱）
 *   2. 全部に署名を付ける（signing。Gradle 同梱）
 *   3. zip にまとめる
 *   4. multipart で POST する
 *
 * の4つだけで、3 と 4 のために外部プラグインを1つ増やすと
 * 「中で何をしているか」が辿れなくなる（原則1）。
 * HTTP は JDK の HttpClient を使う（D-24 と同じ）。
 *
 *   ./gradlew centralBundle  -Pjimble.version=0.1.0     材料を作って zip にする
 *   ./gradlew centralUpload  -Pjimble.version=0.1.0     Portal へ送る（公開はまだ）
 *   ./gradlew centralStatus                              検証の結果を見る
 *   ./gradlew centralRelease                             公開する（取り消せない）
 *   ./gradlew centralDrop                                やめる
 *
 * 手順の全体は docs/publishing.md にある。
 */

/** Portal の API の入口 */
val CENTRAL_API = "https://central.sonatype.com/api/v1/publisher"

/** アップロードした deployment の id を控えておく先 */
val centralDeploymentIdFile = layout.buildDirectory.file("central-deployment-id.txt")

/** publish する（= examples ではない）モジュール */
val publishedProjects = subprojects.filterNot { it.name in NOT_PUBLISHED || it.path.startsWith(":examples") }

/**
 * バンドルの材料を build/central に出す。
 *
 * 別ビルドの gradle-plugin も同じ場所へ出す（向こうの build.gradle.kts 参照）。
 */
val centralPublishLocal = tasks.register("centralPublishLocal") {

	group = "publishing"
	description = "Maven Central へ送る材料を build/central に出す"

	dependsOn(publishedProjects.map { "${it.path}:publishMavenPublicationToCentralRepository" })
	dependsOn(gradle.includedBuild("gradle-plugin").task(":publishAllPublicationsToCentralRepository"))

}

/**
 * バンドルを zip にする。
 *
 * 古い版のファイルが build/central に残っていても混ざらないよう、
 * <b>いま作っている版のディレクトリだけ</b>を入れる。
 * maven-metadata.xml は Central が自分で作るので入れない。
 */
val centralBundle = tasks.register<Zip>("centralBundle") {

	group = "publishing"
	description = "Maven Central へ送る zip を作る（要件 NF-L-04）"

	dependsOn(centralPublishLocal)

	archiveFileName = "jimble-$jimbleVersion-central.zip"
	destinationDirectory = layout.buildDirectory

	from(centralRepoDir) {
		include("**/$jimbleVersion/**")
		exclude("**/maven-metadata*")
	}

	doLast {

		val zip = archiveFile.get().asFile

		if (!jimbleVersion.endsWith("-SNAPSHOT")) {
			logger.lifecycle("バンドル: ${zip.absolutePath}")
		} else {
			logger.warn("版が $jimbleVersion です。Maven Central は -SNAPSHOT を受け付けません。")
			logger.warn("  ./gradlew centralBundle -Pjimble.version=0.1.0")
		}

	}

}

/**
 * Portal のトークン（アカウント画面で発行する User Token）
 *
 * <p>
 * <b>前後の空白と改行を落とす。</b>
 * {@code export X=$(pbpaste)} や、コピーの取りこぼしで末尾に改行が1つ入るだけで
 * Base64 の中身が変わり、Portal は 401 を返す。
 * 値そのものは何があってもログに出さない。
 * </p>
 */
fun centralAuthorization (): String {

	val rawUser = providers.environmentVariable("JIMBLE_CENTRAL_USERNAME").orNull
	val rawPassword = providers.environmentVariable("JIMBLE_CENTRAL_PASSWORD").orNull

	if (rawUser.isNullOrEmpty() || rawPassword.isNullOrEmpty()) {
		throw GradleException(
			"""
			Portal のトークンがありません。
			  JIMBLE_CENTRAL_USERNAME / JIMBLE_CENTRAL_PASSWORD を環境変数で渡してください。
			  https://central.sonatype.com/account の Generate User Token で発行します。
			""".trimIndent())
	}

	val user = rawUser.trim()
	val password = rawPassword.trim()

	if (user != rawUser || password != rawPassword) {
		logger.warn("トークンの前後に空白か改行が入っていたので落としました（値は出しません）")
	}

	val encoded = java.util.Base64.getEncoder()
		.encodeToString("$user:$password".toByteArray(Charsets.UTF_8))

	return "Bearer $encoded"

}

/**
 * 401 のときに、値を出さずに切り分けの手がかりを出す
 */
fun centralUnauthorizedHint (): String {

	val user = providers.environmentVariable("JIMBLE_CENTRAL_USERNAME").orNull.orEmpty()
	val password = providers.environmentVariable("JIMBLE_CENTRAL_PASSWORD").orNull.orEmpty()

	fun shape (value: String): String {

		val trimmed = value.trim()

		val inner = if (trimmed.any { it.isWhitespace() }) " / 途中に空白あり" else ""

		return "${value.length}文字（前後の空白を落とすと ${trimmed.length}文字）$inner"

	}

	return """

		Portal がトークンを受け付けませんでした（401）。値は出しませんが、渡ったものの形は次のとおりです。

		  JIMBLE_CENTRAL_USERNAME : ${shape(user)}
		  JIMBLE_CENTRAL_PASSWORD : ${shape(password)}

		よくある原因は次の4つです。

		  1. トークンを作り直した。前のものは無効になります
		  2. ユーザー名とパスワードが逆
		  3. ログインのパスワードを入れている（User Token は別物です）
		  4. 別のシェルで export した（Gradle を動かしているシェルに渡っていない）

		手元で切り分けるには：

		  curl -s -o /dev/null -w '%{http_code}\n' -X POST \
		    -H "Authorization: Bearer ${'$'}(printf '%s:%s' "${'$'}JIMBLE_CENTRAL_USERNAME" "${'$'}JIMBLE_CENTRAL_PASSWORD" | base64)" \
		    'https://central.sonatype.com/api/v1/publisher/status?id=00000000-0000-0000-0000-000000000000'

		  404 が返ればトークンは通っています（その id の deployment が無いだけ）。
		  401 ならトークンそのものです。https://central.sonatype.com/account で作り直してください。
	""".trimIndent()

}

/** 返ってきたものをそのままログに出す */
fun centralCall (request: java.net.http.HttpRequest): String {

	val response = java.net.http.HttpClient.newHttpClient()
		.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())

	logger.lifecycle("HTTP ${response.statusCode()}")

	if (response.body().isNotEmpty()) {
		logger.lifecycle(response.body())
	}

	if (response.statusCode() == 401) {
		throw GradleException(centralUnauthorizedHint())
	}

	if (response.statusCode() >= 400) {
		throw GradleException("Portal が ${response.statusCode()} を返しました")
	}

	return response.body()

}

/** 控えてある deployment の id（-Pid= で上書きできる） */
fun centralDeploymentId (): String {

	providers.gradleProperty("id").orNull?.let { return it }

	val file = centralDeploymentIdFile.get().asFile

	if (!file.exists()) {
		throw GradleException("deployment の id がありません。先に centralUpload するか -Pid=<uuid> を渡してください")
	}

	return file.readText().trim()

}

tasks.register("centralUpload") {

	group = "publishing"
	description = "バンドルを Central Portal へ送る（公開はまだしない）"

	dependsOn(centralBundle)

	doLast {

		if (jimbleVersion.endsWith("-SNAPSHOT")) {
			throw GradleException("Maven Central は -SNAPSHOT を受け付けません。-Pjimble.version=0.1.0 のように渡してください")
		}

		val zip = centralBundle.get().archiveFile.get().asFile

		/*
		 * multipart/form-data を手で組む。
		 * 部品は bundle 1つだけなので、境界文字列と見出しを1組書けば済む。
		 */
		val boundary = "jimble-" + java.util.UUID.randomUUID()

		val head = ("--$boundary\r\n"
			+ "Content-Disposition: form-data; name=\"bundle\"; filename=\"${zip.name}\"\r\n"
			+ "Content-Type: application/octet-stream\r\n\r\n").toByteArray(Charsets.UTF_8)

		val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

		val body = head + zip.readBytes() + tail

		val request = java.net.http.HttpRequest.newBuilder()
			.uri(java.net.URI.create("$CENTRAL_API/upload?name=jimble-$jimbleVersion&publishingType=USER_MANAGED"))
			.header("Authorization", centralAuthorization())
			.header("Content-Type", "multipart/form-data; boundary=$boundary")
			.POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body))
			.build()

		val deploymentId = centralCall(request).trim()

		centralDeploymentIdFile.get().asFile.writeText(deploymentId)

		logger.lifecycle("")
		logger.lifecycle("送りました: $deploymentId")
		logger.lifecycle("  ./gradlew centralStatus     検証の結果を見る")
		logger.lifecycle("  ./gradlew centralRelease    公開する（取り消せない）")
		logger.lifecycle("  https://central.sonatype.com/publishing/deployments でも見られます")

	}

}

tasks.register("centralStatus") {

	group = "publishing"
	description = "送ったバンドルの検証結果を見る"

	doLast {

		val request = java.net.http.HttpRequest.newBuilder()
			.uri(java.net.URI.create("$CENTRAL_API/status?id=${centralDeploymentId()}"))
			.header("Authorization", centralAuthorization())
			.POST(java.net.http.HttpRequest.BodyPublishers.noBody())
			.build()

		centralCall(request)

	}

}

tasks.register("centralRelease") {

	group = "publishing"
	description = "検証を通ったバンドルを公開する（取り消せない）"

	doLast {

		val request = java.net.http.HttpRequest.newBuilder()
			.uri(java.net.URI.create("$CENTRAL_API/deployment/${centralDeploymentId()}"))
			.header("Authorization", centralAuthorization())
			.POST(java.net.http.HttpRequest.BodyPublishers.noBody())
			.build()

		centralCall(request)

		logger.lifecycle("公開しました。Maven Central に出るまで数分〜数十分かかります")

	}

}

tasks.register("centralDrop") {

	group = "publishing"
	description = "送ったバンドルを取り下げる"

	doLast {

		val request = java.net.http.HttpRequest.newBuilder()
			.uri(java.net.URI.create("$CENTRAL_API/deployment/${centralDeploymentId()}"))
			.header("Authorization", centralAuthorization())
			.DELETE()
			.build()

		centralCall(request)

		centralDeploymentIdFile.get().asFile.delete()

	}

}

// endregion
