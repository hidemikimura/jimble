---
title: Gradle プラグイン
summary: io.jimble.db / jte / run のタスクと設定
section: 開発
order: 2
---

# Gradle プラグイン

3つあります。**必要なものだけ入れてください。**

| ID | 何をするか | 主なタスク |
| --- | --- | --- |
| `io.jimble.db` | マイグレーションとコード生成を `compileJava` の前に流す | `migrate` / `codegen` / `codegenCheck` |
| `io.jimble.jte` | `src/main/jte` を `compileJava` の前に Java に変換する | `generateJte` |
| `io.jimble.run` | 開発用のホットリロード | `jimbleRun` |

```kotlin
plugins {
	application
	id("io.jimble.jte") version "2.1.4"
	id("io.jimble.run") version "2.1.4"
	id("io.jimble.db")  version "2.1.4"
}
```

**Plugin Portal には出していません。**Maven Central から取るので、
`settings.gradle.kts` に置き場所を書いてください（`jimble new` の雛形には入っています）。

```kotlin
pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}
```

> [!NOTE]
> プラグインは **Gradle デーモンの中**で動くので、バイトコードは Java 17 で出しています。
> アプリ側（Java 25）とは別です。**Gradle は 9 以上**が要ります。

## まっさらから書く

`jimble new` を使わないなら、この2つを置けば動きます。
**下は実際に動かして確かめたもの**です（`gradle build` で
`migrate` → `codegen` → `compileJava` が繋がり、`gradle run` で起動します）。

```kotlin
// settings.gradle.kts
pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}

rootProject.name = "memo"
```

```kotlin
// build.gradle.kts
plugins {
	application
	id("io.jimble.jte") version "2.1.4"   // src/main/jte を使うなら
	id("io.jimble.run") version "2.1.4"   // ホットリロードを使うなら
	id("io.jimble.db")  version "2.1.4"   // DB を使うなら
}

repositories {
	mavenCentral()
}

// jimble は Java 25 で作ってあります。これが無いと依存が解決できません
java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

dependencies {
	implementation("io.jimble:jimble-web:2.1.4")

	testImplementation(platform("org.junit:junit-bom:5.11.4"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
}

// conf/ をリソースに足す。設定もマイグレーションもここから読みます（[設定](./config)）
sourceSets {
	main {
		resources {
			srcDir("conf")
		}
	}
}

application {
	mainClass = "memo.App"
}

jimbleRun {
	mainClass = "memo.App"
}
```

> [!TRAP]
> **ツールチェーンの指定は要ります。**書かないと、依存の解決の時点で止まります。
>
> ```
> Dependency resolution is looking for a library compatible with JVM runtime version 21,
> but 'io.jimble:jimble-web:2.1.4' is only compatible with JVM runtime version 25 or newer
> ```

## どれを依存に足すか

**まとめて入るものは書かなくてよい**です（`jimble-web` を足せば `jimble-db` も `jimble-util` も付いてきます）。

| 足すもの | 何が入るか | いっしょに付いてくるもの |
| --- | --- | --- |
| `io.jimble:jimble-web` | Web（Router / Context / Session / テンプレート） | `jimble-db` → `jimble-util` → `jimble-core` |
| `io.jimble:jimble-db` | DB だけ（バッチや CLI から使うとき） | `jimble-util` → `jimble-core` |
| `io.jimble:jimble-mq` | MQ | `jimble-db` |
| `io.jimble:jimble-batch` | バッチ・DB スケジューラ | `jimble-db` / `jimble-mq` |
| `io.jimble:jimble-batch-manager` | バッチ管理画面 | `jimble-web` / `jimble-batch` |
| `io.jimble:jimble-mcp` | MCP サーバー | `jimble-web` |
| `io.jimble:jimble-otel` | OpenTelemetry へトレースを出す（**足したときだけ 0.9MB 増えます**） | `jimble-core` |

**DB のドライバは要りません**（MySQL / MariaDB と PostgreSQL は `jimble-db` が持っています）。

## io.jimble.db

```bash
./gradlew migrate       # 未適用の SQL を当てる
./gradlew codegen       # テーブル定義のクラスを作る（migrate のあと）
./gradlew codegenCheck  # コミットされている生成物がスキーマと合うか見る
```

中身は [マイグレーションとコード生成](./codegen) を見てください。

```kotlin
jimble {
	sourceRoot   = "src/main/java"   // 生成先
	env          = "local"           // CLI に -Denv=... で渡る
	autoGenerate = true              // compileJava の前に繋ぐか
}
```

| プロパティ | 既定 |
| --- | --- |
| `sourceRoot` | `src/main/java` |
| `env` | `-Pjimble.env` → 環境変数 `ENV` → `local` |
| `autoGenerate` | `-Pjimble.autoGenerate` → **`env == "local"` なら true** |

つまり**ローカルでだけ** `migrate → codegen → compileJava` が繋がります。
CI では繋がらないので、コミットされた生成物でそのままコンパイルできます。

```bash
./gradlew build -Pjimble.autoGenerate=true    # CI でも繋ぎたいとき
```

> [!NOTE]
> `migrate` と `codegen` は**毎回走ります**（up-to-date になりません）。
> DB の状態は Gradle からは見えないためです。

> [!TIP]
> どちらも実体は CLI（`io.jimble.db.cli.JimbleDbCli`）を叩いているだけです。
> **ロジックを Gradle 側に置いていない**ので、CI や本番では同じことを CLI で直接できます。

> [!WARN]
> クラスパスに**このプロジェクトのクラスは入りません**（リソースと依存だけ）。
> 入れると `compileJava → codegen → compileJava` で循環します。
> SQL も設定もリソース側にあるので、これで足ります。

`codegenCheck` がずれを見つけると、こう言って落ちます。

```
コミットされている生成物がスキーマと一致しません（2 件）。codegen を実行してコミットしてください。
  生成物が古いです: db/blog_example/table/post/Post.java
  生成物が足りません: db/blog_example/table/tag/Tag.java
```

## io.jimble.jte

`src/main/jte` の `.jte` を **Java に変換**します。コンパイルは `compileJava` がやるので、
**テンプレートの型の間違いはビルドで落ちます**（[テンプレート](./view)）。

| タスク | 入力 | 出力 |
| --- | --- | --- |
| `generateJte` | `src/main/jte` | `build/generated/sources/jte/main/java` |
| `generateTestJte` | `src/test/jte`（**変えられません**） | `build/generated/sources/jte/test/java` |

```kotlin
jte {
	sourceDirectory       = "src/main/jte"
	packageName           = "gg.jte.generated.precompiled"
	contentType           = "Html"    // Html | Plain
	trimControlStructures = true
	htmlCommentsPreserved = false
}
```

> [!WARN]
> `packageName` は**変えないでください。**jte の既定と揃えてあります。
> ずらすと実行時にテンプレートが見つかりません。

> [!NOTE]
> 生成先は**毎回まるごと消してから**作ります。
> 消したテンプレートの生成物が残ると、**消したはずのものがコンパイルを通って jar に入ります。**

コンパイラ（`gg.jte:jte`）を持っているのはプラグインだけです。
アプリの実行時クラスパスには `jte-runtime` しか入りません（要件 D-27）。

## io.jimble.run

```bash
./gradlew jimbleRun
```

仕組みと注意は [ホットリロード](./hot-reload) にあります。ここは設定の一覧です。

```kotlin
jimbleRun {
	mainClass = "my_blog.App"        // 必須
}
```

| プロパティ | 既定 | 何を決めるか |
| --- | --- | --- |
| `mainClass` | **なし（必須）** | `main` を持つクラス |
| `port` | `9000` | ブラウザが見るプロキシのポート |
| `appPort` | `port + 100` | アプリが待ち受けるポート |
| `env` | `"local"` | `jimble.env` として渡る |
| `buildTasks` | `[":classes"]` | 変更時に流すタスク |
| `watchDirs` | なし | 追加で見張る（ルートからの相対パス） |
| `excludeDirs` | なし | 見張らない |
| `watchExtensions` | `.java .jte .html .js .css .conf .xml .properties .yml .sql` | 見張る拡張子 |
| `restartMode` | `"on_request"` | `on_request`（次のリクエストで） / `immediate`（保存したら） |
| `quietMillis` | `300` | 変更が落ち着いたと見なす待ち |
| `startTimeoutSeconds` | `60` | 起動を待つ上限 |
| `args` | なし | `main` に渡す引数 |

見張るのは `src` と `conf`（あれば）＋ `watchDirs` です。

> [!TRAP]
> **`restartMode` に書けない値を書くと落ちます。**
> 前は黙って `on_request` に倒していたので、書き間違えても
> 「設定したのに効かない」だけが残っていました。

> [!NOTE]
> `debug` や `jvmArgs` のプロパティは**ありません。**
> アプリは Gradle と**同じ JVM**で動くので、IDE のデバッガがそのまま効きます。
> ヒープを変えたいときは `gradle.properties` の `org.gradle.jvmargs` です。

### ビルドに失敗したら

止まりません。**ブラウザにそのまま出ます。**

| 何が起きたか | ステータス |
| --- | --- |
| 作り直しに失敗した | **503**（Gradle の出力をそのまま） |
| アプリに繋がらない | **502** |
| `jimbleRun` の中で例外 | **500** |

直して保存し、リロードすれば続きができます。

> [!NOTE]
> ビルドは `gradlew` を**子プロセスで**叩きます（動いているビルドの中から
> 同じプロジェクトのタスクは呼べないため）。
> `gradlew` が壊れていたら、理由を出して PATH の `gradle` に逃がします。

## AI 向けのタスク

**どの jimble のプラグイン（`io.jimble.jte` / `io.jimble.run` / `io.jimble.db`）を当てても付きます。**

| タスク | すること |
| --- | --- |
| `./gradlew jimbleSkills` | `.claude/skills/` を、**使っている jimble の版の skill** に揃える。`AGENTS.md` / `CLAUDE.md` が無ければ置く |
| `./gradlew jimbleCheck` | jimble の**既知の落とし穴**をソースと設定から見つける。見つけたものには直し方と引き先を付ける |

### jimbleSkills

`jimble new` は skill を**写して**置くので、jimble の版を上げても写しは古いままです。
アプリの AI は古い jimble の説明を読み続けます。**版を上げたら流してください。**

```bash
./gradlew jimbleSkills
./gradlew jimbleSkills --overwrite   # 手で直したもの・控えの無いものも入れ替える
```

- **手で直した skill は上書きしません。**置いたときの中身のハッシュを `.claude/jimble-skills.properties` に控えていて、
  控えと同じ（jimble が置いたまま）なら入れ替え、違えば触りません
- **控えの無い skill**（控えを書くようになる前の `jimble new` で置いたもの）も、直したかどうか分からないので触りません。
  直していなければ `--overwrite` で入れ替えてください
- **アプリ固有の決まりは skill ではなく `AGENTS.md` に書きます。**skill を直すと、次の版に揃えられなくなります
- `AGENTS.md` / `CLAUDE.md` は**無ければ置き、あれば触りません**（`CLAUDE.md` は `@AGENTS.md` と書いて読み込むだけ）

### jimbleCheck

**動かしても黙って間違えるもの**だけを見ます（起動時に落ちて直し方を言うものは見ません）。
`ERROR` が1つでもあれば落ち、`WARN` だけなら落ちません。

```
[J903 WARN] src/main/java/demo/PostController.java:83  isError() は 2.0 で無くなった（DB の失敗は SqlExecuteException）
    直し方: db.transaction(...) の中なら確定しないことで守られる。分岐したいのは一意制約くらいなので DuplicateKeyException で受ける
    詳しく: https://jimble.io/ja/migrate-2.md
```

| 規則 | 重さ | 見るもの |
| --- | --- | --- |
| J101 | ERROR | `context.request().getString(...)` など（`Request` は `Data` ではない。`bodyAll()` を通す） |
| J201 | WARN | 設定の `${ENV}` に `?` が無い（環境変数が無い環境で起動時に落ちる） |
| J202 | ERROR | `${?ENV}` の行のあとで同じキーを書き直している（環境変数が効かない） |
| J301 | ERROR | `Migration.install()` が `DBUtil.load(...)` のあと（マイグレーションが流れない） |
| J302 | ERROR | `BatchRegistry.sync` はあるのに `BatchTables.install` がどこにも無い |
| J303 | ERROR | `BatchRegistry.sync` が `BatchRegistry.add` より前（全部のバッチが `nothing` になる） |
| J304 | WARN | codegen を使っているのに、MQ の表（`mq_scheduler` など）を `codegen.exclude_tables` に書いていない |
| J401 | ERROR | 外側の `before(Auth::guard)` と、`Auth.REALM` のブロックの中の `Remember.restore`（覚えていても毎回 401） |
| J402 | WARN | `Remember.forgetAll(...)` があって、同じファイルに `Auth.revoke` / `revokeOthers` が無い（ほかの端末のセッションが残る） |
| J501 | WARN | skill が使っている jimble の版のものではない（`jimbleSkills` で揃える） |
| J701 | WARN | トランザクション（`db.begin()` / `db.transaction(...)` / `TransactionException`）を使うファイルの空の `catch`（確定していないのに成功を返す） |

**1.x の書き方で、2.0 で消えたもの・型や意味が変わったもの**も出します（どれも WARN。書き換え先は [2.0 への移行](./migrate-2)）。

| 規則 | 見るもの |
| --- | --- |
| J801 | `new DBTransaction(...)` / `DBTransaction.transaction(...)`（2.0 で消えた） |
| J802 | DB の `beginTransaction()` / `commitEndTransaction()` / `rollbackEndTransaction()` / `endTransaction()`（2.0 で消えた） |
| J803 | `Router x = router.path("/x")`（2.0 で消えた。ブロックの `path("/x", r -> { ... })` にする） |
| J804 | 列の `subtract(...)`（2.0 で消えた。割り算を出していた） |
| J805 | `Dsl.or(...)` / `Dsl.and(...)`（2.0 で消えた。`Dsl.anyOf` / `Dsl.allOf` にする） |
| J806 | `cookies().put(cookie)`（2.0 で消えた。署名しなかった） |
| J807 | `eq(null)` / `not(null)`（2.0 で例外。`is_null()` / `is_not_null()` にする） |
| J808 | 文字列 `"now()"`（2.0 ではただの文字列。`Dsl.now()` にする） |
| J810 | `long id = db.insert(...)`（2.0 の `insert` は値を返さない。`insertKey` にする） |
| J901 | `Data row = db.select(...)` / `selectCached(...)`（2.0 は `Optional<Data>`。`.orElse(...)` などで受けているものは出さない） |
| J902 | `if (!db.execute(...))` / `boolean x = db.execute(...)`（2.0 は件数を返し、失敗は例外） |
| J903 | `isError()`（2.0 で無くなった） |
| J904 | `DBUtil.load` / `DBLock.lock` / `DBLock.create` の戻り値を `if (!...)` などで見ている（2.0 は値を返さず、失敗は例外） |
| J905 | `RedisLock.tryLock(...)`（2.0 は `Optional<RedisLockResult>`。`Optional` で受けているものは出さない） |
| J906 | `selectOrThrow` / `selectListOrThrow` / `insertNoReturnKey`（2.0 で非推奨） |
| J907 | `session().data().put(...)`（と `putData` / `remove` / `clear` / `putAll`。2.0 で例外） |

J8xx と J9xx は**いつも出します**。`--target=2.0` は 1.5 のころのスクリプトがそのまま動くように受け付けますが、何もしません。

> [!NOTE]
> Java は構文木にせず、コメントと文字列を除いてから読んでいます。**誤検知は、その行か前の行に
> `// jimble-check:ignore J101` と書けば出なくなります**（設定ファイルは `# jimble-check:ignore J201`）。
