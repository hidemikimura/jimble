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
	id("io.jimble.jte") version "1.0.0"
	id("io.jimble.run") version "1.0.0"
	id("io.jimble.db")  version "1.0.0"
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
	id("io.jimble.jte") version "1.0.0"   // src/main/jte を使うなら
	id("io.jimble.run") version "1.0.0"   // ホットリロードを使うなら
	id("io.jimble.db")  version "1.0.0"   // DB を使うなら
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
	implementation("io.jimble:jimble-web:1.0.0")

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
> but 'io.jimble:jimble-web:1.0.0' is only compatible with JVM runtime version 25 or newer
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
