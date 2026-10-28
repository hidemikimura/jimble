---
name: jimble-config
description: jimble の設定（application.conf / HOCON）を書く・読むときに使う。Conf.conf() で読む、秘密を環境変数から読む2行の書き方（${?X} の落とし穴）、環境別のファイルと include、時間と大きさの単位、環境の切り替え（-Djimble.env）と読むファイルの差し替え（-Djimble.conf）、ローカル用の application.local.conf を含む。
---

# jimble の設定

全体の決まりと、ほかの skill への案内は `jimble`。DB と Redis の設定のキーは `jimble-db-config`。

`application.properties` ではなく **`application.conf`（HOCON）**。`@Value("${x}")` は無い。
読むのは `Conf.conf().getString("x", "既定")`（`io.jimble.util.conf.Conf`）。

## 秘密の設定は2行で書く

既定の行を先に、`${?環境変数}` の行をあとに置く。

```conf
password = ""
password = ${?DB_PASSWORD}      # 環境変数があれば上書き、無ければ前の行のまま
```

- **`${?X}` の1行だけ**だと、環境変数が無いときに**キーごと消える**。
  `Conf.conf().getString("x")`（既定なし）は呼んだところで落ち、既定つきなら黙って既定になる
- **`?` を書き忘れる**（`${DB_PASSWORD}`）と、環境変数が無い環境（手元・テスト）で**起動時に落ちる**
- **順番を逆にする**と、**あとの `""` が必ず勝って環境変数が効かない**（エラーは出ない）
- `application.prod.conf` などの環境別ファイルで `include "application.conf"` のあとに**同じキーを値つきで書き直すと、
  そちらが勝って環境変数が効かない**。書き直すなら2行組ごと書く
- OIDC の `client_secret`・Redis の `redis.password`・`auth.mfa.secret_key` などもこの形で環境変数から読む

## 単位を書く（要件 D-159）

**時間と大きさの設定は単位を値に書く**（`session.timeout = 30m` / `200ms` / `upload.max_file_size = 10MiB`）。
**素の数値は起動時に落ちる**（`sql_cache.ttl` も 2.5.2 からこの決まりに入った。0 も `0s` と書く）。
`MB` は 1000 の3乗、`MiB` は 1024 の3乗。

`db.<名前>` は **snake_case**（`maximum_pool_size` / `idle_timeout` / `schema`）。
**表に無いキーを書くと起動時に落ちる**（camelCase の古い綴りは警告つきで読む。`jimble-db-config`）。

## 環境とファイル

- **環境は `-Djimble.env` で決める。**バッチの引数の `env=` では変わらない（違えば `IllegalArgumentException`。`jimble-batch-run`）
- 環境は変えずに、**読む設定ファイルだけ替えるなら `-Djimble.conf=application.batch`**
  （クラスパスの `application.batch.conf` だけを読む。見つからなければ起動時に落ちる。2.5.3〜）
- **ローカルだけの値は `application.local.conf`（1行目に `include "application.conf"`）にだけ書く。**
  `application.conf` に書くと本番も引き継ぐ。`cookie { secure = false }`（`jimble-web-session`）、
  `mail.transport = "log"`（`jimble-mq`）、`assets.immutable_max_age = 0s`（`jimble-web-response`）など

詳しくは <https://jimble.io/ja/config.md>（設定ファイルの全項目・環境・`-Djimble.conf`）。
