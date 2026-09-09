description = "jimble の DB 層。SQL ビルダー / DB / トランザクション / キャッシュ / 分散ロック"

/*
 * jooby_base から移送したコード。移送時点の警告は段階的に潰す（jimble-util と同じ扱い）。
 */
tasks.withType<JavaCompile>().configureEach {
	options.compilerArgs.addAll(
		listOf(
			"-Xlint:-unchecked",
			"-Xlint:-rawtypes",
			"-Xlint:-fallthrough",
			"-Xlint:-deprecation",
			"-Xlint:-dangling-doc-comments",
			"-Xlint:-this-escape",
			"-Xlint:-cast",
			"-Xlint:-overloads",
		)
	)
}

dependencies {
	api(project(":jimble-util"))

	// コネクションプール（設定で切り替える）
	api(libs.hikaricp)
	api(libs.agroal.pool)

	/*
	 * JDBC ドライバ（要件 F-D-30）
	 *
	 * MySQL / MariaDB と PostgreSQL の両方を入れておく。
	 * conf の db.xxx.product を書き換えるだけで動くようにするためである。
	 * 使わないほうを外したいときは
	 *
	 *   implementation("io.jimble:jimble-db:x.y.z") {
	 *       exclude(group = "org.postgresql", module = "postgresql")
	 *   }
	 *
	 * のように除く（実行時にしか要らないので、外してもコンパイルは通る）。
	 */
	runtimeOnly(libs.mariadb.client)
	runtimeOnly(libs.postgresql.client)

	/*
	 * Redis（キャッシュ / 分散ロック）
	 *
	 * <b>使わない入口の依存を切っている。</b>redisson 本体は 2.8MB だが、
	 * そのままだと 17MB が付いてきて<b>アプリの実行時クラスパスの半分</b>を占める。
	 * jimble が使うのは RLock / RMap / RSet / RBucket / RKeys / RBatch / RScript の
	 * <b>同期 API だけ</b>で、下の4つは別の入口からしか読まれない
	 * （jar のクラスを1つずつ数えて確かめた。要件 D-121）：
	 *
	 *   byte-buddy    8.79MB  liveobject の 6 クラスだけが参照
	 *   rxjava        2.54MB  RedissonRx… の 120 クラスだけが参照
	 *   reactor-core  1.85MB  RedissonReactive… の 113 クラスだけが参照
	 *   jodd-util     0.32MB  liveobject の 2 クラスだけが参照
	 *
	 * <b>この4つを外すと、次の3つが使えなくなる。</b>
	 * 必要なら利用者が自分で依存に足すこと（jimble はどれも呼んでいない）：
	 *
	 *   redissonClient.reactive()          → reactor-core
	 *   redissonClient.rxJava()            → rxjava
	 *   redissonClient.getLiveObjectService() → byte-buddy / jodd-util
	 */
	api(libs.redisson) {
		exclude(group = "net.bytebuddy", module = "byte-buddy")
		exclude(group = "io.reactivex.rxjava3", module = "rxjava")
		exclude(group = "io.projectreactor", module = "reactor-core")
		exclude(group = "org.jodd", module = "jodd-util")
	}

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
}
