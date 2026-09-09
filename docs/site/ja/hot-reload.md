---
title: ホットリロード
summary: ./gradlew jimbleRun。直したら次のリクエストで反映される
section: 開発
order: 3
---

# ホットリロード

```bash
./gradlew jimbleRun
```

## 何が起きているか

```
ブラウザ ──▶ プロキシ（9000） ──▶ アプリ（9100・Gradle と同じ JVM）
```

1. `jimbleRun` が 9000 番でプロキシを立てる
2. その裏でアプリを 9100 番（`port + 100`）で起動する
3. ソースが変わったのを見つけたら印を付ける
4. **次のリクエストが来たとき**にビルドして、アプリを入れ替える
5. 入れ替わってからリクエストを流す

**保存したそばからビルドはしません。** リクエストが来るまで待ちます。
連続して保存しているあいだ、何度もビルドが走るのを避けるためです。

## Gradle と同じ JVM で動きます

**アプリは Gradle と同じプロセスの中で動きます。**
IDE で `jimbleRun` を「デバッグ実行」すれば、**そのままアプリの
ブレークポイントに止まります。** リモートデバッガを別に繋ぐ必要はありません。
メソッド本体だけの変更なら、IntelliJ の HotSwap でそのまま差し替わります。

入れ替えるときは、**新しいクラスローダを作って `main` を呼び直します。**
古いほうは、止めてから捨てます。

### Gradle の JVM が Java 25 である必要があります

同じ JVM で動かすので、**ツールチェーンではなく Gradle デーモンの JVM**
がアプリを動かします。古いと起動できません。起動時に見て、
古ければ何を直せばよいかを言って止まります。

- IntelliJ: 設定 &gt; ビルド、実行、デプロイ &gt; ビルドツール &gt; Gradle &gt; **Gradle JVM**
- `gradle.properties`: `org.gradle.java.home=<Java 25 のパス>`

### 止め方を預けてください

プロセスを殺さないので、**アプリが立てたものは止めないと残ります。**
残ったスケジューラや MQ ワーカーは、入れ替えたあとの新しいものと
**二重に動きます**（同じジョブが2回走る）。

jimble が立てるもの（サーバー・スケジューラ・DB のプール）は
すでに預けてあります。自分で立てるものは書いてください。

```java
Shutdown.add("わたしのワーカー", worker::stop);
```

止まらなかったスレッドがあれば、名前を出して言います
（同じ名前は1回だけ）。

```
[jimbleRun] 止まらなかったスレッドがあります: my-worker
  同じ JVM で動かしているので、止めないものは残ります。
  ...
```

### `System.exit()` を呼ばないでください

アプリの `main` が `System.exit()` を呼ぶと、**Gradle ごと落ちます。**
同じプロセスなので防ぎようがありません（Java 24 以降は `SecurityManager` がありません）。
バッチの入口のように `System.exit()` を使うクラスは、`jimbleRun` の
`mainClass` にしないでください。

### 本番と同じではありません

同じ JVM で入れ替えるので、`static` の状態が残ったり残らなかったりします。
**「リロードしたときだけ落ちる」「リロードしたときだけ通る」が起こりえます。**
おかしいと思ったら、`jimbleRun` を止めて起動し直してください。

### 直列化フィルタは切っています

helidon は起動のときに、**JVM 全体の Java 直列化フィルタ**を張ります
（許可リストに無いクラスは読み戻さない、という守り）。
同じ JVM で動かすので、これは **Gradle デーモンに張られます。**
一度張ると外せないので、**アプリを止めてもデーモンに残ります。**

残ったままだと、次のビルドがこれで落ちます。

```
Couldn't populate class org.gradle.api.services.BuildServiceParameters$None
> filter status: REJECTED
```

**「1回目は動く。止めてもう1回動かすと起動しない」**という出かたをします。
デーモンを作り直す（IDE の Gradle 同期、`gradle --stop`）と直るので、
何が原因か分かりにくいところです。

`jimbleRun` は、これを避けるために
`helidon.serialFilter.missing.action` を `IGNORE` にしてからアプリを起動します。
**切るのは `jimbleRun` のあいだだけです。** 本番はアプリが自分の JVM で
動くので、helidon はいつもどおりフィルタを張ります。

自分で `-Dhelidon.serialFilter.missing.action=...` を指定しているときは、
そちらを尊重して何もしません。

## ビルドが失敗したら

エラーがそのままブラウザに出ます。ターミナルを見に行かなくて済みます。
直して再読み込みすれば、またビルドが走ります。

**アプリが落ちたままでも、プロキシは生きています。** ポートは変わりません。

## 設定

```kotlin
jimbleRun {
	// 要るのはこれだけ。ほかは既定がある
	mainClass = "my_blog.App"

	port    = 9000              // プロキシ（ブラウザが見るほう）
	appPort = 9100              // アプリ。既定は port + 100
	env     = "local"

	buildTasks      = listOf(":my-blog:classes")
	watchExtensions = listOf(".java", ".jte", ".conf")
	excludeDirs     = listOf("build")

	restartMode         = "on_request"   // on_request | immediate
	quietMillis         = 300
	startTimeoutSeconds = 60

	args = listOf()
}
```

`restartMode = "immediate"` にすると、保存した時点でビルドと入れ替えを始めます。
待ち時間は減りますが、保存のたびに走ります。

見張るディレクトリを足すなら `watchDirs`（ルートからの相対パス）です。
プロパティの一覧は [Gradle プラグイン](./gradle) にまとめてあります。

## 気をつけること

- **`jimbleRun` は開発用です。** 本番では `./gradlew run` か、作った jar を直接動かします
- **Gradle デーモンの JVM で動きます。** ヒープを変えたいときは
  `gradle.properties` の `org.gradle.jvmargs` です（`jimbleRun { jvmArgs }` はありません）
- 入れ替えを重ねると、止まらなかったスレッドのぶんだけメモリが増えます。
  1日中つけっぱなしにして重くなったら、起動し直してください
- `gradlew` があっても `gradle/wrapper/gradle-wrapper.properties` が無ければ、
  ラッパーは壊れていると判断してシステムの `gradle` を使います
