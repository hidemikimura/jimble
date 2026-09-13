---
title: 本番で動かす
summary: jar の作り方と、起動するコマンド
section: 開発
order: 8
---

# 本番で動かす

## jar を作る

fat jar（依存も全部入り）にするのがいちばん簡単です。

```kotlin
plugins {
	application
	id("com.gradleup.shadow") version "<版>"
}

application {
	mainClass = "my_blog.App"
}

tasks.shadowJar {
	archiveFileName = "app.jar"
	mergeServiceFiles()   // JDBC ドライバなどの META-INF/services が消えないように
}
```

```bash
./gradlew shadowJar     # build/libs/app.jar
```

## Web を起動する

```bash
java \
  -Djimble.env=prod \
  -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  -jar app.jar
```

| 渡すもの | なぜ |
| --- | --- |
| `-Djimble.env=prod` | `application.prod.conf` を読み、`isProduction()` を true にします |
| `-Dstdout.encoding` / `-Dstderr.encoding` | 付けないと `LANG` 未設定のコンテナで**日本語のログが化けます** |

**`-Djimble.env` は JVM の引数です。**プログラム引数（`java -jar app.jar env=prod`）
では効きません。

### 環境の名前

**判定は略記でも通ります。**書いたとおりの名前は**ファイル名にだけ**使われます。

| 書いてよい名前 | 判定 | 読むファイル |
| --- | --- | --- |
| `local` / `dev` / `development` | `isLocal()` | `application.<書いたとおり>.conf` |
| `staging` / `stg` / `stage` | `isStaging()` | 同上 |
| `production` / `prod` / `prd` | `isProduction()` | 同上 |

大文字小文字は区別しません。

> [!TRAP]
> **表に無い名前は、どの判定にも当たりません。**
> `producton` のように打ち間違えると、`isLocal()` も `isProduction()` も false になります。
> **起動時に1度だけ警告が出ます**——黙って `local` に倒すと、
> **本番機が「ローカルです」と名乗ったまま動いてしまう**からです。
>
> なお、**`-Djimble.env=prod` と書いて `isProduction()` が false だった時期があります**（1.0 で直しました）。
> 判定が `production` としか一致していませんでした。

## 設定はどこから読まれるか

**jar の中だけです。**（`conf/` をリソースに足してあるので、jar に入っています。
[設定](./config) を参照）

```
1. application.<env>.conf     ← あればこれだけ
2. 無ければ application.conf
```

**読むのは1ファイルだけです。**共通を読み込むのは環境別ファイルの
1行目に書く `include "application.conf"` です。
書き忘れると共通の設定が落ちるので、**起動時に名指しで言います。**

**jar の外の設定は読みません。**設定を変えたらビルドし直します。
そのぶん、動いている jar と設定が1対1になり、
「直したのに効かない」が起きません。
環境ごとに変わる値（DB のパスワードなど）は次の節のとおり環境変数で渡します。

**どれを読んだかは起動ログに出ます**（[設定](./config)）。

## 秘密は環境変数で

設定ファイルには `${?ENV_NAME}` だけ書き、値は環境変数で渡します。

```bash
export DB_PASSWORD='...'
export COOKIE_SECRET='...'
export CIPHER_KEY='...'
java -Djimble.env=prod -jar app.jar
```

## バッチを動かす

fat jar から起動クラスを指定して呼びます。

```bash
java \
  -Djimble.env=prod \
  -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  -cp app.jar my_blog.batch.Batch \
  class=my_blog.batch.PostCleanupBatch
```

`class=` がバッチのクラス名です。ほかの `key=value` はバッチの引数になります。

**`env=prod` を引数で渡しても環境は変わりません。**
設定はバッチの入口に来る前に読み終わっているためです。
食い違っていたら、その場で止めて言います。

```
env=prod と指定されていますが、いま動いているのは env=local です。
  env= では環境は変わりません（設定はここに来る前に読み終わっています）。
  JVM の引数で渡してください。
    java -Djimble.env=prod -cp app.jar <起動クラス> ...
```

## スケジューラを動かす

cron でバッチを回すなら、スケジューラを常駐させます。

```bash
java -Djimble.env=prod -cp app.jar my_blog.batch.Batch \
  class=my_blog.batch.SchedulerBatch
```

止めるまで動き続けます。`systemd` などに任せてください。
**Web と同じプロセスで動かすこともできます**が、複数台に並べるときは
`batch.scheduler_id` の扱いを決めてから にしてください。

## 終了

`SIGTERM` で終了処理が走ります（サーバー・スケジューラ・DB のプール）。
`kill -9` はトランザクションが中途半端に残るので避けてください。

## 起動したら確認すること

起動ログの1行目あたりに構成が出ます。**思っていたものと合っているか**を見てください。

```
jimble 構成: env=prod / session=db / cache=db / redis=あり / db=[main] / パスワード暗号化=あり
設定: jar:file:/opt/app/app.jar!/application.prod.conf, jar:file:/opt/app/app.jar!/application.conf
```

`env=local` になっていたら、`-Djimble.env` が渡っていません。
