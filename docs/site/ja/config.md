---
title: 設定
summary: application.conf。環境別の上書きと、秘密の渡し方
section: 開発
order: 5
---

# 設定

`conf/application.conf` に書きます。書式は HOCON です。

## 置き場所

**設定は `conf/` に置きます。**

```
conf/application.conf          設定
conf/application.local.conf    環境別
conf/migration/<スキーマ名>/   マイグレーション
```

`build.gradle.kts` で `conf` をリソースに足します。

```kotlin
sourceSets {
	main {
		resources {
			srcDir("conf")
		}
	}
}
```

こうすると **`conf/` の中身はそのまま jar に入ります。**
`codegen` / `migrate` / `jimbleRun` / テストも、同じファイルをクラスパスから見ます。

**jimble が読むのはクラスパスだけです。** jar の外は見ません。
設定を変えたらビルドし直します。**動いている jar と設定が1対1になります。**

**読むのは1ファイルだけです。**

1. `application.<env>.conf` があれば**それ**
2. 無ければ `application.conf`

**どのファイルを読んだかは起動ログに出ます。**

```
設定: jar:file:/opt/app/app.jar!/application.prod.conf
```

## 環境で切り替える

共通を読み込むのは**環境別ファイルの `include`** です。

```conf
# application.prod.conf
include "application.conf"

db {
	main {
		url = ${?DB_URL}
	}
}
```

```
application.conf          共通
application.prod.conf     本番。1行目で共通を読み、続きで上書きする
```

環境は `-Djimble.env=prod` で決まります。既定は `local` です。

**jimble は裏で共通を足しません。** 足すと、`include` が効いているのか
フレームワークが足しているのかが**ファイルを見ても分からなくなる**からです。
読むファイルは1つ、続きは書いてあるとおりです。

### include を書き忘れたら

共通の設定が丸ごと落ちます。落ちたら起動時に名指しで言います。

```
設定: application.conf にしかないキーが読まれていません: cipher, session, server
 / application.prod.conf の先頭に include "application.conf" を書いてください
```

## 秘密はファイルに書かない

```conf
db {
	main_db {
		url      = "jdbc:mariadb://127.0.0.1:3306/app"
		url      = ${?DB_URL}
		password = ""
		password = ${?DB_PASSWORD}
	}
}
```

`${?ENV_NAME}` は「環境変数があれば上書き、無ければ前の行のまま」です。
同じキーを2回書くのが正しい書き方です。

## コメントは # か //

**HOCON に `/* */` はありません。** 書くとこうなります。

```
Key '/' may not be followed by token: '*'
```

## 主なキー

```conf
server {
	host        = ""         # 待ち受けるアドレス。空なら全部
	port        = 9000
	trust_proxy = false      # ロードバランサの後ろに置くまで false
	max_request_size     = 10485760
	max_header_size      = 16384
	idle_timeout_seconds = 60
	compression          = true
	bot_access_log       = true

	shutdown_grace_seconds   = 0    # 止め始めてから新規を断つまで
	shutdown_timeout_seconds = 15   # 処理中を待つ上限
}

cookie {
	secure = true            # ローカルでは false（下を参照）
	secret = ${?COOKIE_SECRET}
}

session {
	store       = "none"     # none | db | redis | cookie
	cookie_name = "sid"
}

upload {
	max_file_size  = 10485760   # 1ファイル（10MB）
	max_total_size = 52428800   # 1リクエスト合計（50MB）
	max_files      = 20
	temp_dir       = ""         # 空なら java.io.tmpdir
}

rate_limit {
	store   = "memory"       # memory | redis | db
	enabled = true
}

cache {
	type          = "db"     # db | memory | redis
	temp_dir      = ""
	memory.expire = 0        # memory のときだけ。秒
}

redis {
	host = ""                # 空なら Redis 無し
	port = 6379
	ssl  = false
}

sse {
	max_duration_seconds = 300
	max_events           = 0
	retry_millis         = 3000
}

mcp {
	path            = "/mcp"
	allowed_origins = []
}

migration {
	# auto | true | false。auto はローカル以外で当てる
	on_startup   = "auto"
	resource_dir = "migration"
}

codegen {
	package = "db"
}
```

## trust_proxy は既定で false

`true` にすると `X-Forwarded-For` を信じます。
**ロードバランサの後ろに置くまでは `false` のままにしてください。**
直接叩ける状態で `true` にすると、送信元 IP を誰でも偽れます。

## cookie.secure は既定で true

安全側が既定ですが、ローカルは http なのでブラウザが Cookie を返しません。
セッションも CSRF も Flash も、エラーを出さずに効かなくなります。

`env=local` かつ `secure = true` のときは、起動時に WARN が出ます。
ローカル用の `application.conf` には `cookie { secure = false }` を入れて、
**本番へ出すときに消してください**。

## 起動ログで確認する

```
jimble 構成: env=local / session=none / cache=db / redis=なし / db=[blog_example]
```

読み込まれた設定が起動の1行目に出ます。
「設定したつもりが効いていない」はここで気づけます。
