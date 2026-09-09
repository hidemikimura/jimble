---
title: 5分で最初のエンドポイント
summary: jimble new から起動、ホットリロードまで
section: はじめに
order: 2
---

# 5分で最初のエンドポイント

## 用意するもの

- **JDK 25**（LTS）。jimble は仮想スレッドと `ScopedValue` を使います
- **Gradle 9 以上**。Gradle 8 は Java 25 の上では動きません
  （ツールチェーンとして Java 25 を使うのは 8 でもできますが、Gradle 自身が動きません）

DB はまだ要りません。あとから足せます。

`jimble` コマンドの入れ方は [jimble コマンド](./cli) にあります
（[リポジトリ](https://github.com/hidemikimura/jimble)で `./gradlew :jimble-cli:installDist` して PATH に通すだけです）。

> [!TIP]
> **コマンドを入れなくても始められます。**`jimble new` が作るビルドファイルは
> [Gradle プラグイン](./gradle) の「まっさらから書く」に全文があります。
> 2つのファイルを貼れば、ここから先は同じです。

## 1. 雛形を作る

```bash
jimble new my-blog
cd my-blog
gradle wrapper --gradle-version 9.7.1
```

`gradle wrapper` に版を付けるのは、**手元の Gradle が 8 だと 8 のラッパーができてしまう**ためです。

`jimble new` が作るのは、ビルドファイル・`application.conf`・`App.java`・
`index.jte`・`.gitignore` だけです。空のディレクトリは作りません。

## 2. 起動する

```bash
./gradlew run
```

```
jimble 構成: env=local / session=none / cache=db / redis=なし / db=[]
route: GET     /
jimble を起動しました: http://localhost:9000
```

起動ログに **ルート一覧が全部出ます**。
「登録したつもりのルートが無い」を起動の時点で見つけるためです。

```bash
curl http://localhost:9000/
```

## 3. エンドポイントを足す

`App.java` の初期化ブロックに1行足します。

```java snippet=hello-route
```

`{ }` の中はコンストラクタの前に走る初期化ブロックです。
`JimbleApp` を継承したクラスの初期化ブロックが、そのままルート定義になります。

JSON を返すなら:

```java snippet=json-route
```

`json()` は `Data`（`LinkedHashMap<String,Object>` の派生）を組み立てて、
`send()` の時点で JSON にします。

上のコード片はサンプルアプリ（`examples/blog`）から抜いたものなので、
`listPosts()` のような**そのアプリのメソッド**が出てきます。
自分のアプリでは、そこを自分の処理に置き換えてください。

## 4. 直したら反映されるようにする

```bash
./gradlew jimbleRun
```

`jimbleRun` は 9000 番でプロキシを立て、その裏で**同じ JVM の中に**アプリを起動します。
ソースを直すと次のリクエストのときにビルドして入れ替えます。
ビルドが失敗したら、そのエラーがブラウザに出ます。

詳しくは [ホットリロード](./hot-reload) を見てください。

## 5. エラーの出し方を決める

```java snippet=error-handler
```

`error()` に渡したものが、投げられた例外にも、どのルートにも当たらなかった 404 にも使われます。
何も登録しないと jimble の既定の形で返ります。

## クラスの置き場所

コード片には `import` を書いていません。よく使うものはここにあります。

| クラス | パッケージ |
| --- | --- |
| `JimbleApp` / `JimbleServer` | `io.jimble.web.server` |
| `Context` / `WebContext` | `io.jimble.core.context` / `io.jimble.web.context` |
| `Data` | `io.jimble.util.data` |
| `Conf` | `io.jimble.util.conf` |
| `Log` | `io.jimble.util.log` |
| `DB` / `DBUtil` | `io.jimble.db` |
| `SQL` / `Dsl` | `io.jimble.db.sql` / `io.jimble.db.sql.query.dsl` |
| `Migration` | `io.jimble.db.migration` |
| 生成されたテーブル定義 | `<codegen.package>.<スキーマ名>.table.<テーブル名>` |

## 次に読むもの

- [ルーティング](./routing) — パスパラメータ、フィルタ、まとめ方
- [DB を使う](./db) — DB を足す
- [落とし穴](./pitfalls) — 先に読んでおくと1日得します
