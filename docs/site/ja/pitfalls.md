---
title: 落とし穴
summary: 黙って効かなくなるところ。先に読んでおくと1日得します
section: そのほか
order: 1
---

# 落とし穴

落ちるバグは自分で見つかります。ここに集めたのは、**落ちないバグ**です。

## ローカルで Cookie が効かない

**症状**: セッションもログインも CSRF も Flash も、エラーなしで効かない。

**原因**: `cookie.secure` の既定は `true` です。ローカルは http なので、
ブラウザが Cookie を送り返しません。

**対処**: ローカルの `application.conf` に入れます。本番では消します。

```conf
cookie {
	secure = false
}
```

起動時に WARN が出るので、ログを見てください。

## ルートが登録されていない

**症状**: 404 になる。コードには書いてある。

**原因**: `install(SomeController::new)` を書き忘れています。
jimble はクラスパスを走査しないので、書かないと登録されません。

**対処**: 起動ログのルート一覧を見てください。無ければ登録されていません。

## before が効いていない（同じパスなのに）

`before` は**書いたブロックの中だけ**に効きます。パスには付きません。

```java
path("/admin", () -> {
	before(requireAuth);
	get("/users", ...);
});

install(() -> new SpaController("/admin", ...));   // ← 認証されない
```

同じ `/admin` でも、別の場所で登録したルートは別のブロックです。
まとめたいなら、同じブロックに入れてください。

逆の症状（**効いてほしくないのに効く**）は起きません。これが起きていたのが
移行前の形で、`/admin` の SPA ログイン画面まで認証に捕まっていました。

なお、**ルートが確定した後に `before` を足すと落ちます。**
「足したのに効かない」を黙って通さないためです。
ルート定義はコントローラの初期化ブロックの中で完結させてください。

## request() から直接読むと、黙って空になる

**症状**: フォームも JSON も送っているのに、値が取れない。例外は出ない。

**原因**: `Request` も `Data` なので、こう書けてしまいます。

```java
context.request().getString("title")   // コンパイルは通る。null が返る
```

`Request` そのものには本文もクエリも入っていません。

**対処**: `bodyAll()`（またはどこから来た値か決め打ちで `bodyJson()` など）を通します。

```java
Data input = context.request().bodyAll();
String title = input.getString("title");
```

無いキーの `getString` は `null` です。**そのまま NOT NULL の列に入れると
`Column 'title' cannot be null` になります。**

## セッションが保存されていない

**症状**: `put()` したのに次のリクエストで消えている。

**原因**: `save()` を呼んでいません。自動保存はしません。

```java snippet=session-save
```

## select の結果が取れない

**症状**: `row.getString("title")` が空。

**原因**: 結果はテーブル名でネストしています。

```java snippet=select-nesting
```

`Column` で引けば間違えません。

## DB のエラーに気づかない

**症状**: 0件のはずがないのに0件。あるいは NPE。

**原因**: DB のエラーは例外ではなく戻り値です。`select` は `null`、更新系は `-1`。

```java snippet=db-error
```

## トランザクションが続いていない

**症状**: `commit()` のあとの更新が、ロールバックしても戻らない。

**原因**: これは移送元の話です。移送元の `commit()` はトランザクションを
終わらせていたので、そこから先が自動コミットになっていました。
jimble では `commit()` は終わらせません。終わらせるのは `commitEndTransaction()` です。

**移送してきたコードを見るときは、`commit()` の後ろを確認してください。**

## 「コミットもロールバックもされていない」が出る

```
ERROR コミットもロールバックもされていないトランザクションが残っていました。ロールバックします
```

**原因**: try-with-resources で囲まずに `beginTransaction()` して、途中で `return` しています。

**対処**: 囲んでください。jimble は実行の終わりに拾ってロールバックしますが、
それは事故の後始末であって、正しい書き方ではありません。

## 入口によって用意されるテーブルが違う

```
{"error":"トランザクションのコミットに失敗しました。"}
```

**原因**: `MqQueue#install()` や `BatchTables.install()` を、**一部の入口でしか呼んでいない**。

Web・バッチ・スケジューラで入口が分かれていると、それぞれが自分の分だけ用意しがちです。
たとえば MQ のテーブルをバッチの入口でしか作っていないのに、
**キューに積むのは Web** ということが起こります。

やっかいなのは、**いちどでもバッチを動かしたマシンでは動いてしまう**ことです。
手元では気づけず、まっさらな DB（CI や新しい環境）で初めて出ます。

**対処**: **起動時に用意するものは1か所にまとめ、どの入口もそれを呼んでください。**

```java
public final class Bootstrap {

	public static void load () {

		Migration.install();                                  // DBUtil.load より前
		DBUtil.load(Conf.conf().config(), Bootstrap.class);
		BatchTables.install(DBUtil.getMainDB());
		new MqQueue(NoticeExecutor.QUEUE_NAME).install();

	}

}
```

**テストからも同じものを呼んでください。**テストだけ別に用意すると、
入口が変わったときに<b>テストだけ通る</b>状態になります。

> [!TIP]
> **まっさらな DB で1回流してみる**のがいちばん早い確認です。
> `examples/blog` はこれで壊れているのが分かりました。

## 製品を変えたら生成物の列順が変わる

**症状**: 何も直していないのに `codegen` の差分が出る。列の並びだけが違う。

**原因**: 生成される列の並びは**物理順**です。
`ALTER TABLE ... ADD COLUMN` の位置が MySQL（`AFTER body` が書ける）と
PostgreSQL（必ず末尾）で違うので、同じスキーマでも並びが変わります。

**対処**: **生成は主にする製品で行ってください。**`codegenCheck` も同じ製品で回します。
型も NULL 可否もコメントも一致するので、並び以外は変わりません。

## SSE が届かない・全部まとめて届く

**原因**: 間にいる nginx が溜め込んでいます。

**対処**: jimble は `X-Accel-Buffering: no` を付けています。
それでも駄目なら nginx 側で `proxy_buffering off;` を設定してください。

## SSE のループが終わらない

**原因**: `sse.isOpen()` を見ていません。

`isOpen()` は**寿命が残っているか**を返します。切断は検出できません
（helidon が教えてくれず、書き込みが永久にブロックすることがあります）。
ループの中で必ず見てください。詳しくは [SSE](./sse) を読んでください。

## WebSocket で Cookie が読めない

**原因**: 昇格したあとに読もうとしています。
HTTP のヘッダが見えるのは `onUpgrade` の時点だけです。

**対処**: 認証は `onUpgrade` でやります。`false` を返せば 403 になります。

## MQ が2回実行された

**原因**: そういうものです。「ちょうど1回」は作れません。

**対処**: 何度やっても同じになるように書いてください。詳しくは [MQ](./mq)。

## HOCON のコメントで落ちる

```
Key '/' may not be followed by token: '*'
```

**原因**: `/* */` を使っています。HOCON のコメントは `#` か `//` です。

## jimbleRun で「メイン・マニフェスト属性がありません」

`gradle-wrapper.jar` に `Main-Class` がありません。**`gradlew` と `gradle-wrapper.jar` が
食い違っています。**

いまの `gradlew` はこう起動します。

```sh
exec java -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ...
```

これには `Main-Class` を持つ JAR が要ります。古い（`-classpath` 前提の）JAR が
残っていると、`gradlew` も JAR も見た目は揃っているのに動きません。

```sh
unzip -p gradle/wrapper/gradle-wrapper.jar META-INF/MANIFEST.MF
# Main-Class: org.gradle.wrapper.GradleWrapperMain  ← これが無ければアウト
```

直すには、そのプロジェクトで一度流してください。

```sh
gradle wrapper --gradle-version 9.7.1
```

`jimbleRun` は作り直しのときに `./gradlew` を子プロセスで叩くので、
**保存のたびにこれが出ます。** 壊れていると分かったときは
理由を出して PATH の `gradle` に逃がすようにしてあるので、
ホットリロード自体は続きます。

## Gradle が起動しない

```
* What went wrong: 25.0.4
```

**原因**: Gradle 8 は Java 25 の上では動きません。
（ツールチェーンとして Java 25 を使うのは 8 でもできますが、Gradle 自身が動きません）

**対処**: Gradle 9 以上を使ってください。

## Kotlin のコメントが入れ子になる

`build.gradle.kts` のブロックコメントに `/*` が現れると、
**そこから入れ子のコメントが始まります**（Kotlin のブロックコメントは入れ子にできます）。
外側が閉じないまま、その下のコードが全部消えます。**エラーは出ません。**

```kotlin
/*
 * docs/site/<言語>/*.md を読む     ← この /* が入れ子を開く
 */
tasks.register("site") { ... }      ← 登録されない。エラーも出ない
```

パスを書くときは `*` を避けるか、`//` を使ってください。

## .gitignore の `docs/` が全階層に効く

**症状**: 公開したリポジトリからテンプレートが消えている。

**原因**: `.gitignore` に `docs/` と書くと、git は**どの階層の `docs/` にも**効かせます。
`jimble-docs/src/main/jte/docs/` まで巻き込みます。

**対処**: 先頭に `/` を付けて `/docs/` と書きます。

## Gradle 9 で落ちるビルドスクリプト

Gradle 8 では通り、9 で落ちるものがあります。

| 書き方 | 何が起きるか |
|---|---|
| `val x by tasks.registering { }` | 9.6 で非推奨。Kotlin DSL では**コンパイルエラー**。`tasks.register("x") { }` にします |
| `"...".formatted(...)` | Java 15 のメソッド。9.7 の Kotlin スクリプトからは**解決できません**。文字列テンプレートにします |

**ビルドスクリプトは、実際に使うバージョンで一度は流してください。**
