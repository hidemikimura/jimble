# __NAME__

[jimble](https://jimble.io) で作った Web アプリケーション。

## 要るもの

- **Java 25**
- **Gradle 9 以上**（`gradle wrapper` で用意すれば以降は `./gradlew` でよい）

## 動かす

```bash
./gradlew run
```

```bash
curl http://localhost:9000/hello
open http://localhost:9000/
```

## 直しながら動かす

```bash
./gradlew jimbleRun
```

`http://localhost:9000` を開く。ソースやテンプレートを直してリロードすれば、
作り直してから応える。ビルドに失敗したらブラウザにその内容が出る。

## どこに何があるか

```
src/main/java/__PACKAGE_PATH__/App.java   ルート定義とエントリポイント
src/main/jte/__PACKAGE_PATH__/            テンプレート（jte）
src/main/resources/application.conf       設定
```

**ルートは `App.java` の初期化ブロックに上から書く。**
アノテーションもDIも使わない。上から読めば何が動くか分かる状態を保つこと。

## DB を使う

1. `application.conf` の `db { ... }` のコメントを外す
2. `build.gradle.kts` の `io.jimble.db` プラグインと JDBC ドライバのコメントを外す
3. `src/main/resources/migration/<DB名>/001_xxx.sql` を書く
4. `App.main` の `Migration.install()` と `DBUtil.load(...)` のコメントを外す

```bash
./gradlew migrate    # 未適用のマイグレーションを適用する
./gradlew codegen    # テーブル定義のコードを生成する
```

ローカルでは `build` が `migrate` → `codegen` → `compileJava` を繋ぐ。
**生成したコードはコミットする。**ローカル以外は DB に繋がずコンパイルできること。

## 覚えておくこと

- **SELECT の結果はテーブル名でネストする。**`row.getData("user").getString("name")`、
  または `row.getString(User.name)`
- **DB のエラーは例外ではなく戻り値で返る。**select 系は `null`、更新系は `-1`。
  `db.isError()` で確かめる
- **セッションは明示的に `save()` する。**自動保存しない
