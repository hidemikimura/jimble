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
 *   ./gradlew centralRelease                             公開する（取り消せない）。続けてタグを打って push する
 *   ./gradlew centralTag                                 タグだけ打ち直す（push に失敗したときなど）
 *   ./gradlew centralDrop                                やめる
 *
 * 手順の全体は docs/publishing.md にある。
 */

/** Portal の API の入口 */
val CENTRAL_API = "https://central.sonatype.com/api/v1/publisher"

/** アップロードした deployment の id を控えておく先 */
val centralDeploymentIdFile = layout.buildDirectory.file("central-deployment-id.txt")

/**
 * 送ったものの控え（要件 D-185）
 *
 * <p>
 * <b>centralUpload が「どの版を・どのコミットから」送ったかを書き、centralRelease がそれを見てタグを打つ。</b>
 * centralRelease は -Pjimble.version を渡さずに叩くので、版もここから読む。
 * タグを打つ先は<b>送ったときのコミット</b>であって、公開するときの HEAD ではない
 * （送ってから公開するまでのあいだにコミットが進んでいても、公開したのは送った木である）。
 * </p>
 */
val centralReleaseFile = layout.buildDirectory.file("central-release.properties")

/** git を叩いた結果 */
data class GitResult (val code: Int, val out: String)

/**
 * git を叩く（ルートで）
 *
 * @return	終了コードと出力（標準エラーも混ぜる）。git が無ければ null
 */
fun git (vararg args: String): GitResult? {

	return try {
		val process = ProcessBuilder(listOf("git") + args)
			.directory(rootDir)
			.redirectErrorStream(true)
			.start()
		val out = process.inputStream.bufferedReader().readText().trimEnd()
		GitResult(process.waitFor(), out)
	} catch (ex: java.io.IOException) {
		null
	}

}

/** git を叩く。失敗したら止める */
fun gitOrFail (vararg args: String): String {

	val result = git(*args)
		?: throw GradleException("git がありません。公開した木にタグを打つために使います（D-185）")

	if (result.code != 0) {
		throw GradleException("git ${args.joinToString(" ")} が失敗しました:\n${result.out}")
	}

	return result.out.trim()

}

/** タグが指しているコミット。タグが無ければ null */
fun tagCommit (tag: String): String? {

	val result = git("rev-parse", "-q", "--verify", "refs/tags/$tag^{commit}") ?: return null

	return if (result.code == 0) result.out.trim() else null

}

/**
 * 送る前に、木が公開してよい形かを見る（要件 D-142 / D-185）
 *
 * <p>
 * <b>コミットしていない変更がある・HEAD が push されていない、のどちらかなら送らない。</b>
 * 0.3.0 では版上げのコミットより先に公開した（D-142）。
 * <b>手順に書いてあっても抜けた</b>ので、抜けられない形にする。
 * 見るのは centralUpload だけで、centralBundle は見ない
 * （作業中の木から中身を確かめるためにバンドルを作ることはある）。
 * </p>
 *
 * @param version	送る版
 * @return	送る木のコミットと、それを持っているリモート
 */
fun centralCheckTree (version: String): Pair<String, String> {

	val inside = git("rev-parse", "--is-inside-work-tree")

	if (inside == null || inside.code != 0 || inside.out.trim() != "true") {
		throw GradleException("git の作業ツリーの中で動かしてください。"
			+ "公開した木にタグを打つために、どのコミットから送ったかを控えます（D-185）")
	}

	val dirty = gitOrFail("status", "--porcelain")

	if (dirty.isNotEmpty()) {
		throw GradleException("コミットしていない変更があります。公開はコミットして push した木から作ります（D-142）。\n"
			+ dirty.lines().take(20).joinToString("\n") { "  $it" })
	}

	val commit = gitOrFail("rev-parse", "HEAD")

	// 「origin/main」のような行が返る。1行も無ければ、どのリモートにも無い
	val remoteBranch = gitOrFail("branch", "-r", "--contains", commit)
		.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.contains(" -> ") }
		?: throw GradleException("HEAD（${commit.take(7)}）がまだ push されていません。"
			+ "push してから送ってください（D-142）\n  git push")

	val tag = "v$version"
	val tagged = tagCommit(tag)

	if (tagged != null && tagged != commit) {
		throw GradleException("$tag はすでに別のコミット（${tagged.take(7)}）に打ってあります。"
			+ "その版は公開済みではありませんか。版番号を確かめてください")
	}

	return commit to remoteBranch.substringBefore('/')

}

/**
 * 公開した木にタグを打って push する（要件 D-185）
 *
 * <p>
 * <b>タグは3度抜けた</b>（0.6.0・1.2.0・1.3.0）。centralRelease のあとは「終わった」感が強く、
 * 手順の最後の2行が目に入らない。サイトはタグで出るので、抜けると<b>サイトが古い版のまま残る</b>。
 * </p>
 *
 * <p>
 * <b>ここで失敗しても、公開は取り消せないので済んでいる。</b>
 * そのうえでビルドを落とす——落とさないと、流れていくログの中で失敗を見落とす。
 * 直すコマンドを必ず添える。
 * </p>
 *
 * @param version	版
 * @param commit	タグを打つコミット
 * @param remote	push する先
 */
fun centralTagAndPush (version: String, commit: String, remote: String) {

	val tag = "v$version"
	val fix = "  git tag -a $tag $commit -m \"$version\" && git push $remote $tag"

	val tagged = tagCommit(tag)

	if (tagged == null) {

		val made = git("tag", "-a", tag, commit, "-m", version)
			?: throw GradleException("公開は済んでいます。git が無いのでタグを打てませんでした。\n$fix")

		if (made.code != 0) {
			throw GradleException("公開は済んでいます。タグを打てませんでした:\n${made.out}\n\n$fix")
		}

	} else if (tagged != commit) {

		// 動かさない。公開したものを指している古いタグかもしれない
		throw GradleException("公開は済んでいます。ただし $tag はすでに別のコミット（${tagged.take(7)}）に打ってあるので、"
			+ "動かしていません。公開したのは ${commit.take(7)} です。確かめてから直してください")

	}

	val pushed = git("push", remote, "refs/tags/$tag")
		?: throw GradleException("公開は済んでいます。git が無いので push できませんでした。\n  git push $remote $tag")

	if (pushed.code != 0) {
		throw GradleException("公開は済んでいます。タグ $tag は手元に打ちましたが、push に失敗しました:\n${pushed.out}\n\n"
			+ "  git push $remote $tag\n\nあるいは ./gradlew centralTag")
	}

	logger.lifecycle("")
	logger.lifecycle("タグ $tag を ${commit.take(7)} に打って $remote へ push しました（サイトはこのタグで出ます）")

}

/** 控えを読む */
fun centralReleaseRecord (): java.util.Properties {

	val properties = java.util.Properties()
	val file = centralReleaseFile.get().asFile

	if (file.exists()) {
		file.reader(Charsets.UTF_8).use { properties.load(it) }
	}

	return properties

}

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

		// 送る前に見る。送ったあとで気づいても、公開するかやめるかしか無い
		val (commit, remote) = centralCheckTree(jimbleVersion)

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

		val record = java.util.Properties()
		record.setProperty("id", deploymentId)
		record.setProperty("version", jimbleVersion)
		record.setProperty("commit", commit)
		record.setProperty("remote", remote)
		centralReleaseFile.get().asFile.writer(Charsets.UTF_8).use { record.store(it, "written by centralUpload (D-185)") }

		logger.lifecycle("")
		logger.lifecycle("送りました: $deploymentId（$jimbleVersion / ${commit.take(7)}）")
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
	description = "検証を通ったバンドルを公開し、送った木にタグを打って push する（取り消せない）"

	doLast {

		val deploymentId = centralDeploymentId()
		val record = centralReleaseRecord()

		val request = java.net.http.HttpRequest.newBuilder()
			.uri(java.net.URI.create("$CENTRAL_API/deployment/$deploymentId"))
			.header("Authorization", centralAuthorization())
			.POST(java.net.http.HttpRequest.BodyPublishers.noBody())
			.build()

		centralCall(request)

		logger.lifecycle("公開しました。Maven Central に出るまで数分〜数十分かかります")

		/*
		 * <b>控えが「いま公開したもの」のときだけ打つ。</b>
		 * -Pid= で別の deployment を公開した場合、控えの版とコミットはそれのものではない。
		 * 違うものにタグを打つくらいなら、打たずに言う
		 */
		if (record.getProperty("id") != deploymentId) {
			throw GradleException("公開は済んでいます。ただし、どのコミットから送ったかの控えがこの deployment（$deploymentId）の"
				+ "ものではないので、タグは打っていません。\n"
				+ "  ./gradlew centralTag -Pjimble.version=<版> -Pcommit=<送ったコミット>")
		}

		centralTagAndPush(record.getProperty("version"), record.getProperty("commit"), record.getProperty("remote"))

	}

}

tasks.register("centralTag") {

	group = "publishing"
	description = "公開した木にタグを打って push する（centralRelease が続けてやる。やり直し用）"

	doLast {

		val record = centralReleaseRecord()

		val version = providers.gradleProperty("jimble.version").orNull ?: record.getProperty("version")
			?: throw GradleException("版が分かりません。-Pjimble.version=<版> を渡してください")
		val commit = providers.gradleProperty("commit").orNull ?: record.getProperty("commit")
			?: throw GradleException("コミットが分かりません。-Pcommit=<送ったコミット> を渡してください")
		val remote = record.getProperty("remote") ?: "origin"

		if (version.endsWith("-SNAPSHOT")) {
			throw GradleException("$version にはタグを打ちません。-Pjimble.version=<公開した版> を渡してください")
		}

		centralTagAndPush(version, gitOrFail("rev-parse", "--verify", "$commit^{commit}"), remote)

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
		centralReleaseFile.get().asFile.delete()

	}

}
