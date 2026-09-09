/*
 * ルートのビルド。
 *
 * <b>モジュール共通の設定はここには無い。</b>build-logic の規約プラグインにある（設計書 D-13）。
 *
 *   jimble.java-conventions     Java の共通設定（ツールチェーン / -Werror / doclint）
 *   jimble.test-conventions     test / dbTest / pgTest / bench と junit
 *   jimble.publish-conventions  Maven Central へ出すモジュールの設定（POM / 置き場 / 署名）
 *
 * どのモジュールが何を使っているかは、そのモジュールの build.gradle.kts の
 * plugins {} を見れば分かる（原則1）。
 *
 * <b>ここに残してあるのは「リリースの手順」だけである。</b>
 * 年に数回しか動かさないうえ、HTTP を直に叩いていて読む機会がまとまっているので、
 * 規約プラグインへ散らさずに1ファイルに置いてある。
 */

/*
 * 版（-Pjimble.version で上書きできる）。
 *
 * 既定は gradle/libs.versions.toml の jimble にある。
 * <b>ここに書き写さないこと。</b>規約プラグイン（JimbleBuild.version）と
 * gradle-plugin も同じ表を見ている。
 */
val jimbleVersion = providers.gradleProperty("jimble.version")
	.getOrElse(libs.versions.jimble.get())

/*
 * Maven Central へ出すときの置き場（要件 NF-L-04）。
 *
 * ここへ Maven のレイアウトのまま publish し、centralBundle が zip にする。
 * jimble.publish-conventions と gradle-plugin（別ビルド）も同じ場所を指している。
 */
val centralRepoDir = layout.buildDirectory.dir("central")

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
 *   ./gradlew centralBundle  -Pjimble.version=0.2.0     材料を作って zip にする
 *   ./gradlew centralUpload  -Pjimble.version=0.2.0     Portal へ送る（公開はまだ）
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

/**
 * publish するモジュール
 *
 * <p>
 * <b>「jimble.publish-conventions を適用したか」で決める。</b>
 * 以前は名前とパスで振り分けていたので、<b>publish するかどうかの判断が
 * モジュールの外に1か所だけ別にあった</b>（増やすときに2か所直すことになる）。
 * </p>
 *
 * <p>
 * 全モジュールの設定が終わってから読む必要があるので provider に包んである
 * （タスクの依存は設定がすべて終わったあとに組み立てられる）。
 * </p>
 */
val publishedProjects = provider {
	subprojects.filter { it.pluginManager.hasPlugin("jimble.publish-conventions") }
}

/**
 * バンドルの材料を build/central に出す。
 *
 * 別ビルドの gradle-plugin も同じ場所へ出す（向こうの build.gradle.kts 参照）。
 */
val centralPublishLocal = tasks.register("centralPublishLocal") {

	group = "publishing"
	description = "Maven Central へ送る材料を build/central に出す"

	dependsOn(publishedProjects.map { list ->
		list.map { "${it.path}:publishMavenPublicationToCentralRepository" }
	})
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
			logger.warn("  ./gradlew centralBundle -Pjimble.version=0.2.0")
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
			throw GradleException("Maven Central は -SNAPSHOT を受け付けません。-Pjimble.version=0.2.0 のように渡してください")
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
