---
title: jimble コマンド
summary: 入れ方と、new / migrate / codegen
section: 開発
order: 1
---

# jimble コマンド

```
使い方: jimble <コマンド>

  new <name>              プロジェクトの雛形を作る
  migrate                 未適用のマイグレーションを適用する
  codegen <ソースルート>   テーブル定義のコードを生成する（例: src/main/java）
  version                 版を表示する
```

## 入れる

**jimble のリポジトリからビルドして、PATH に通します。**

```bash
git clone https://github.com/hidemikimura/jimble.git
cd jimble
./gradlew :jimble-cli:installDist
export PATH="$PWD/jimble-cli/build/install/jimble/bin:$PATH"
```

> [!NOTE]
> **CLI は Maven Central にありますが、そのままでは起動できません**
> （`io.jimble:jimble-cli` の jar に `Main-Class` を入れていないので、
> `java -jar` では動きません）。リポジトリからビルドしてください。
>
> **CLI が無くても始められます。**`jimble new` が作るものは
> [Gradle プラグイン](./gradle) の「まっさらから書く」に全文があります。
> `migrate` と `codegen` は Gradle のタスク（`./gradlew migrate` / `./gradlew codegen`）でも走ります。

```bash
$ jimble version
jimble 0.5.0-SNAPSHOT
```

毎回書きたくなければ、`~/.zshrc` に上の `export` を足すか、リンクを張ってください。

```bash
ln -sf "$PWD/jimble-cli/build/install/jimble/bin/jimble" /usr/local/bin/jimble
```

> [!WARN]
> **`java` として Java 25 が引ける必要があります。**
> 起動スクリプトは `JAVA_HOME` か PATH の `java` を使うので、
> 古い Java だと `UnsupportedClassVersionError` になります。
> 生成したプロジェクトをビルドするのに **Gradle 9 以上**も要ります。

> [!TRAP]
> **jimble を直したら `installDist` をやり直してください。**
> 動くのは `build/install/` にコピーされたほうなので、
> **直しただけでは反映されません。**

### 手元でビルドした jimble を使うとき

`jimble new` が作る `build.gradle.kts` は、**CLI をビルドした版**を参照します。
公開していない版（`0.5.0-SNAPSHOT` など）なら、**先にローカルへ publish**してください。

```bash
./gradlew publishToMavenLocal
./gradlew -p gradle-plugin publishToMavenLocal
```

雛形の `settings.gradle.kts` は `mavenLocal()` を先に見るようにしてあります。
公開済みの版だけを使うなら、これは要りません。

## jimble new

```bash
jimble new my-blog
```

作られるもの:

```
my-blog/
	settings.gradle.kts
	build.gradle.kts
	gradle.properties
	.gitignore
	README.md
	src/main/java/myblog/App.java
	src/main/jte/myblog/index.jte
	conf/application.conf
	conf/logback.xml
	conf/migration/my_blog/001_create_note.sql.example
```

**空のディレクトリは作りません。** 使う段になってから作ってください。
`.example` が付いているものは、名前を変えれば有効になります。

名前の決まり:

- 使えるのは英小文字・数字・`-`・`_` です
- 先頭は英字です
- **パッケージ名は英数字だけ残したもの**（`my-blog` → `myblog`）
- **DB 名は英数字以外を `_` にしたもの**（`my-blog` → `my_blog`）
- Java の予約語や、既にあるディレクトリ名は断られます

理由はエラーメッセージに書いてあります。

> [!NOTE]
> **DB も Redis も要らないものが出ます。**手を入れずにビルドして起動できます。
> 使うようになったら、`build.gradle.kts` と `application.conf` の
> コメントを外していく形にしてあります。

## 次にすること

```bash
cd my-blog
gradle wrapper --gradle-version 9.7.1   # Gradle ラッパーを用意する（1回だけ）
./gradlew run           # 起動する
./gradlew jimbleRun     # ホットリロードで起動する
```

```
$ curl http://localhost:9000/hello
{"message":"hello, jimble"}
```

## migrate / codegen

**プロジェクトの中では Gradle から呼びます。**

```bash
./gradlew migrate
./gradlew codegen
```

`io.jimble.db` プラグインを入れておくと、`compileJava` の前に
`migrate → codegen` が自動で繋がります。

中身は [マイグレーションとコード生成](./codegen)、タスクと設定は
[Gradle プラグイン](./gradle) を見てください。

> [!TRAP]
> **`jimble migrate` / `jimble codegen` を素で叩いても動きません。**
> どちらも設定（`application.conf`）とマイグレーションの SQL を
> **クラスパスから探す**ので、アプリのリソースが見えている必要があります。
> CI や本番で Gradle が使えないところでは、jar をクラスパスに足して呼んでください。
>
> ```bash
> java -cp app.jar io.jimble.db.cli.JimbleDbCli migrate
> ```

## 版を確認する

```bash
jimble version
```

jar の `Implementation-Version` を読んでいます。
**雛形が参照する jimble の版もこれ**です（手で書いていません）。
