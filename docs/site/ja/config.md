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

## 設定キー

**ここに出ているのが、jimble が読むキーの全部です。**
出ていないキーは読みません（打ち間違いは黙って無視されます）。
値は**既定値**なので、変えないものは書かなくて構いません。

> [!NOTE]
> **時間と大きさは、単位を値に書きます**（`30m` / `200ms` / `10MiB`）。
> **素の数値は起動時に落ちます**——`assets.max_age = 3600000` のような取り違えを、
> 黙って通さないためです。使えるのは
> `ns` / `us` / `ms` / `s` / `m` / `h` / `d` と、`B` / `KiB` / `MiB` / `GiB` です。

> [!TRAP]
> **`MB` は 1000 の3乗、`MiB` は 1024 の3乗です**（HOCON の決まり）。
> `10MB` は 10,000,000 バイトで、`10MiB` は 10,485,760 バイトです。
> **書いたとおりに解釈します**——どちらでも構いませんが、
> 既定値と比べるときは違うものだと思ってください。

```conf
server {
	host                 = ""       # 待ち受けるアドレス。空なら全部
	port                 = 9000
	max_request_size     = 10MiB    # リクエスト本文の上限
	max_header_size      = 16KiB    # ヘッダ全体の上限
	idle_timeout         = 60s      # 何もしない接続を閉じるまで
	compression          = true     # 応答を gzip で返すか
	trust_proxy          = false    # ロードバランサの後ろに置くまで false（下を参照）
	access_log           = true     # 切ると速くなるが、何が起きたか残らない
	bot_access_log       = true     # ボットのアクセスログを分けるか
	strict_routes        = false    # 一生呼ばれないルートを例外にする（CI では true に）
	shutdown_grace       = 0s       # 止め始めてから新規を断つまで
	shutdown_timeout     = 15s      # 処理中を待つ上限
	backlog              = 1024     # 受け付け待ちの接続を OS に持たせる数
	write_queue_length   = 0        # 応答を書き出す列の長さ。0/1 は「列を作らない」
	smart_async_writes   = false    # 列があるとき、空いていればその場で書く
}

metrics {
	enabled = true                  # メトリクスを数えるか
}

router {
	ignore_case           = false   # パスの大文字小文字を区別しないか
	redirect_to_canonical = false   # 正規の URL へ 301 で寄せるか
}

cookie {
	secure           = true                      # HTTPS のみ。ローカルでは false（下を参照）
	http_only        = true                      # JavaScript から読めない
	same_site        = "lax"                     # none | strict | lax
	domain           = ""                        # 空なら発行元ドメイン
	max_age          = 365d                      # 0 以下でセッション Cookie
	secret           = ${?COOKIE_SECRET}         # 署名鍵。空なら署名しない
	previous_secrets = [${?COOKIE_SECRET_OLD}]   # 鍵の入れ替え中だけ
	accept_unsigned  = false                     # 署名を入れる移行期間だけ true にする
}

csrf {
	max_age = 1d    # トークンの寿命。cookie.max_age とは別
}

session {
	store            = "none"                     # none | db | redis | cookie
	timeout          = 30m
	cookie_name      = "sid"
	table            = "session"                  # store = db のとき
	secret           = ${?SESSION_SECRET}         # store = cookie のとき必須
	previous_secrets = [${?SESSION_SECRET_OLD}]   # 鍵の入れ替え中だけ
}

upload {
	max_file_size  = 10MiB   # 1ファイル
	max_total_size = 10MiB   # 1リクエスト合計（server.max_request_size と揃える）
	max_files      = 20      # 1リクエストのファイル数
	temp_dir       = ""      # 空なら java.io.tmpdir
}

assets {
	max_age           = 0s      # Cache-Control の max-age
	immutable_max_age = 365d    # 不変扱い（js / css）のファイル
	if_modified_since = true    # If-Modified-Since を見るか
	etag              = true    # ETag / If-None-Match を使うか
}

template {
	package      = "gg.jte.generated.precompiled"   # 事前コンパイルの出力先
	content_type = "text/html; charset=utf-8"
}

paging {
	name_page = "page"   # ページ番号のリクエストパラメータ名（件数ではない）
	name_per  = "per"    # 取得件数のリクエストパラメータ名
	max_per   = 200      # 1ページの上限。per=all にも効く（0 で上限なし）
}

auth {
	lockout {
		enabled       = true    # DB が無ければ何もしない
		free_attempts = 3       # ここまでは待たされない（打ち間違い）
		base          = 1s      # 4回目から 1 → 2 → 4 …
		max           = 5m      # 待ち時間の上限
		forget        = 24h     # これだけ間が空いたら数え直す
	}

	remember {
		enabled     = true         # DB が無ければ何もしない
		cookie_name = "remember"
		sliding     = 30d          # 最後に使ってからこれだけ
		absolute    = 90d          # 発行してからこれを超えたら、使っていても切れる
		grace       = 60s          # 回した直後、古いほうも通す時間
	}

	mfa {
		enabled        = true      # DB と secret_key が要る（下を参照）
		issuer         = ""        # 認証アプリの一覧に出る名前
		digits         = 6         # 6 から変えない（多くのアプリが6桁しか出せない）
		period         = 30        # 秒。RFC 6238 のパラメータなので、ここだけ数値
		window         = 1         # 前後いくつの窓まで許すか。広げるほど当たりも増える
		recovery_codes = 10        # 登録のときに出す数
		pending        = 5m        # パスワードが通ってからコードを入れるまでの猶予

		# 秘密鍵を暗号化する鍵。無ければ Mfa.enroll が断る。
		# cipher.key を流用しないこと（下を参照）
		secret_key     = ${?MFA_SECRET_KEY}
	}

	# プロバイダごとに1ブロック。名前（google）は Oidc.callback に渡す名前
	oidc {
		google {
			issuer        = "https://accounts.google.com"
			client_id     = ${?GOOGLE_CLIENT_ID}      # 環境変数から
			client_secret = ${?GOOGLE_CLIENT_SECRET}  # 設定ファイルに書かない
			redirect_uri  = "https://example.com/auth/google/callback"

			# 省略できる。issuer から discovery で引く
			# authorization_endpoint = "..."
			# token_endpoint         = "..."
			# jwks_uri               = "..."

			scopes        = "openid email profile"
			clock_skew    = 60      # 秒。時計のずれをどこまで許すか
			discovery_ttl = 3600    # 秒。discovery と JWKS を持つ時間
		}
	}
}

cipher {
	# 移送してきたアプリの、暗号化済みパスワードハッシュを読むための鍵。
	# 二要素認証には使わない（下を参照）
	key = ${?CIPHER_KEY}   # 16 / 24 / 32 バイト
	iv  = ${?CIPHER_IV}    # 16 バイト
}

hash {
	password {
		# cipher.* を書くなら、これも必ず書く（書かないと起動時に落ちる）
		encrypt = false
		pepper  = ${?PASSWORD_PEPPER}   # ハッシュに混ぜる秘密。入れ替えられない
	}
}

rate_limit {
	enabled = true
	store   = "db"     # db | memory | redis
}

cache {
	type          = "db"    # db | memory | redis
	temp_dir      = ""      # ファイルを置くところ
	memory.expire = 0s      # memory のときだけ。0 で無期限
}

sql_cache {
	enabled = false
	store   = "memory"   # memory | redis | db
	ttl     = 5m
	max     = 10000      # 件数の上限（memory のみ）
}

redis {
	host = ""        # 空なら Redis 無し
	port = 6379
	ssl  = false

	settings {
		connection_timeout      = 10s
		timeout                 = 3s      # コマンドの応答を待つ上限
		connection_minimum_idle = 24
		connection_pool_size    = 64
		retry_attempts          = 3
		retry_minimum_interval  = 500ms
		retry_maximum_interval  = 2s
		idle_connection_timeout = 10s

		subscription_connection_minimum_idle_size = 1
		subscription_connection_pool_size         = 50
	}
}

db {
	# 全部 null のテーブルを結果から落とすか。
	# データソースの名前ではないので、この位置に書く
	remove_all_null_table_data = false

	# データソースごとに1ブロック。名前（blog）が DB クラスの名前になる
	blog {
		main     = true                    # 主データソースにするか
		driver   = "com.mysql.cj.jdbc.Driver"
		url      = "jdbc:mysql://127.0.0.1:3306/blog"
		username = ${?DB_USER}
		password = ${?DB_PASSWORD}
		product  = "mysql"                 # mysql | mariadb | postgresql | postgres | pgsql
		schema   = ""                      # 空ならブロックの名前。以前は scheme という綴りだった

		maximum_pool_size     = 10         # プールの最大
		minimum_idle          = 1          # 常に開けておく数
		fetch_size            = 100        # 一度に取り出す行数
		idle_timeout          = 10m        # 使っていない接続を閉じるまで
		max_lifetime          = 30m        # 1本の接続の寿命
		connection_timeout    = 30s        # 接続を待つ上限
		keepalive_time        = 30s        # 生存確認の間隔
		connection_init_sql   = ""         # 接続直後に流す SQL
		connection_test_query = ""         # 生存確認の SQL
		connection_pool_type  = "hikari"   # hikari | agroal
		transaction_isolation = ""         # 分離レベル
		create_database_sql   = ""         # 無ければ作るときの SQL
		long_connection_log   = false      # 長く握った接続をログに出すか
		long_connection_time  = 0s         # それを「長い」とみなす時間

		# 読み取り専用の接続。書かなければ上と同じところを読む
		read { url = "jdbc:mysql://127.0.0.1:3307/blog" }

		# シャード。中身は上と同じキー
		subs {
			shard1 { url = "jdbc:mysql://127.0.0.1:3308/blog" }
		}
	}
}

db_sticky {
	use = false   # 書いたあと、同じリクエストの読みを書き側に寄せるか
}

log {
	db = false    # SQL をログに残すか
}

async {
	prefetch {
		on_response = false   # レスポンスを送る前に自動で先読みするか
		max_depth   = 5       # 先読みの周回の上限
	}
}

migration {
	on_startup   = "auto"        # auto | true | false。auto はローカル以外で当てる
	down         = false         # down を実行するか
	lock_timeout = 60s           # ロック待ちの上限
	resource_dir = "migration"   # SQL ファイルのリソースディレクトリ
}

codegen {
	package        = "db"   # 生成先パッケージ
	exclude_tables = []     # 生成対象から外すテーブル
}

mq {
	poll_min          = 10ms   # キューが空でないときの待ち
	poll_max          = 1s     # キューが空のときの待ち（だんだん伸びる）
	retry_backoff     = 10s    # リトライの間隔（回を追うごとに倍）
	retry_backoff_max = 10m
	stale             = 10m    # これだけ running のままなら落ちたとみなす

	# 実行種別ごとのスレッド数。書かなければ種別ごとの既定
	thread_count {
		short_time = 2
		long_time  = 8
	}
}

scheduler {
	reload_interval = 10s             # batch_master を読み直す間隔
	tick_interval   = 1s              # cron を確かめる間隔
	exit_check      = 3s              # 止められていないか確かめる間隔
	execute_threads = 10              # バッチを走らせるスレッド数（0 で無制限）
	queue_name      = "mq_scheduler"
}

batch {
	scheduler_id = ""     # このインスタンスの識別子。空ならホスト名
	heartbeat    = 3s     # 実行中であることを知らせる間隔
	alive        = 10s    # これだけ更新が無ければ「実行中ではない」とみなす
	cancel_check = 3s     # 中断指示を見にいく間隔
	progress     = 5s     # チャンクバッチが進み具合を履歴に書く間隔
	all_stop     = 1h     # 全停止フラグが効く時間
}

batch_manager {
	enabled  = false                    # 利用者名かパスワードが空だと画面が生えない
	path     = "/batch-manager"
	realm    = "jimble batch manager"
	username = ${?BATCH_MANAGER_USER}
	password = ${?BATCH_MANAGER_PASSWORD}
}

proxy {
	connect_timeout = 5s     # 転送先に繋ぐまでの上限
	request_timeout = 30s    # 応答を待つ上限
}

sse {
	max_duration = 5m    # 1本を張っていられる上限（0 以下で無制限）
	max_events   = 0     # 送れる件数の上限（0 以下で無制限）
	retry        = 3s    # 切れたクライアントがどれだけ後で繋ぎ直すか
}

mcp {
	path            = "/mcp"
	name            = "jimble"    # サーバーの名前
	version         = "0.1.0"     # サーバーの版（MCP の仕様の版ではない）
	instructions    = ""          # モデルへの手引き（server/discover で返す）
	page_size       = 100         # 一覧の1ページの件数
	allowed_origins = []          # 許すオリジン
}

jimble {
	io.buffer_size       = 256KiB   # ファイルを送るときの読み書き単位
	read_only_container  = false    # 書き込めないコンテナで動かすか

	# server.port は -Djimble.server.port=8080 でも指定できる（システムプロパティが優先）
}
```

## cipher.key と auth.mfa.secret_key は別のもの

**二要素認証に `cipher.key` を流用しないでください。**

**`cipher.*` を書いたら、`hash.password.encrypt` も必ず書いてください。**
書いていないと**起動時に落ちます**。

以前は「`cipher.key` と `cipher.iv` の両方が揃っていれば true」という既定でした。
つまり **`cipher.key` だけ足したアプリは false のまま**で、
**あとから `cipher.iv` を足した瞬間に反転しました**——
保存済みの BCrypt が「暗号化済み」として読まれ、**全員入れなくなります**。
出るのは「IDかパスワードが違います」だけなので、設定を足したことと結び付きません。

**離れたキーで決まる既定は置きません。**暗号化を使うなら、そう書いてもらいます。

| | |
| --- | --- |
| `cipher.key` / `cipher.iv` | **移送してきたアプリの暗号化済みパスワードハッシュを読む**ためのもの。新しく暗号化するものには使いません（固定 IV で、改ざん検知もありません） |
| `auth.mfa.secret_key` | **TOTP の秘密鍵を暗号化する**ためのもの。AES-256-GCM です |

`cipher.*` をまったく書いていないアプリは、これまでどおり何も書かなくて構いません
（暗号化なし、BCrypt だけ）。

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
