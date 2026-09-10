# 変更履歴

版の付け方は [Semantic Versioning](https://semver.org/lang/ja/) に従う。

---

## 未リリース

### 変わったこと（挙動）

| | |
|---|---|
| **パスが合っていてメソッドだけ違うと 405 になります**（F-R-25 / D-11） | いままでは 404 でした。`Allow` ヘッダも付きます（`Allow: GET`）。**「404 が返ること」を確かめているテストは落ちます。**404 は「そんなものは無い」、405 は「あるが、その呼び方ではない」で、混ぜると<b>`post` と書くべきところを `get` と書いただけ</b>の間違いが「パスが違う」に見えます。擬似メソッドの `WS` は `Allow` に出しません |
| **`error` を書いていないときのエラー応答に本文が入ります**（F-C-17 / D-11） | いままでは<b>ステータスだけで本文が空</b>（`Accept: application/json` のときだけ `{}`）でした。これからは JSON を名指しされたら `{"error":{"status":404,"message":"Not Found"}}`、そうでなければ `404 Not Found` の短いテキストです。**`error(...)` で本文を組み立てていれば、送信前でもそちらが勝ちます**（挙動は変わりません）。**中身は決め打ちで、例外のメッセージも SQL もスタックトレースも入りません**（NF-S-06）。空の本文を前提にしていたテストは落ちます |
| **`acceptJson()` が q 値つきの Accept を拾うようになりました**（D-11） | `application/json;q=0.9` も、並びの2つ目以降で前に空白があるもの（`text/html, application/json`）も、いままでは **false** でした（`,` で割った断片を丸ごと比べていたため）。**`q=0`（要らない）は false**、**`*/*` は今までどおり false** です（`*/*` を true にすると<b>ビューを返すルートが JSON を返す</b>ようになります）。JSON を求めていたのに画面が返っていた箇所があれば、そこの挙動が変わります |
| **一生呼ばれないルートを起動時に警告します**（F-R-13 / D-10） | `/users/{id}` と `/users/{userId}` を両方 GET で登録するような、<b>どんなリクエストでも他が先に当たる</b>ルートです。**既定は警告だけで、起動は止めません。**`server.strict_routes = true` にすると例外になります（CI ではこちらを推奨）。`/users/me` が `/users/{id}` より先に当たるような<b>正しい動きは警告しません</b> |
| **MQ のテーブルに列が1つ増えます**（NF-O-05 / D-126） | `traceparent varchar(64)`（null 可）。分散トレーシングで、**積んだところと処理したところを1本のトレースで繋ぐ**ために使います。`MqTables.install(...)` が起動時に当てるので手で流す必要はありませんが、**キューのテーブルに `ALTER` が1回走ります**。トレースを使わないアプリでは、この列はずっと null のままです。**アプリの内容（`data`）には触りません** |
| **`in()` / `not_in()` に空の一覧を渡すと例外になる**（F-D-07 / D-102） | いままでは `IN ()` という構文エラーの SQL を組み立てて DB に投げていた。**SQL を組み立てたところで `SqlBuildException`** にする。「空なら条件ごと外す」ことはしない（`in(空)` は「どれにも当たらない」、条件を外すと**全件**。取り違えると静かに全件消したり全件見せたりする）。空になりうるところは `if (ids.isEmpty())` で分けること |
| **ウィンドウ関数を `where` / `having` に書くと例外になる**（D-103） | `Dsl.rowNumber().over(...).eq(1)` は SQL の決まりで書けない。いままでは書けてしまい、**DB に投げるまで気づけなかった** |
| **バッチの登録が0件のまま `BatchRegistry.sync` を呼ぶと、`batch_master` の行が全部 `nothing` になる**（D-104） | いままでは0件のとき何もしなかったため、**最後の1つを消したときだけ行が `enable` のまま残って**いた。「1つも登録されていない = 全部消えた」に揃えた。**`sync` は登録を済ませてから呼ぶこと。**バッチを持たないアプリは呼ばないこと |
| **`FileUtil.FileMetaData` の `delimiter` と `charset` の中身が変わった**（D-116） | `delimiter` は tika の名前（`comma`）ではなく**区切り文字そのもの**（`,` / `\t` / `;` / `|`）を返します。`charset` は `Charset` の正式な名前（`UTF-8`。以前は小文字）です。画像・その他のときは `null` ではなく空文字を返します。`contentType` は**パラメータを落とした小文字**で返します |
| **HTTP クライアントが zstd を扱わなくなった**（D-121） | `Accept-Encoding` から `zstd` を外しました（gzip / deflate / br は変わりません）。展開に 2.9MB のライブラリが要り、**1行の分岐のためだけに抱えるには大きすぎた**ためです。名乗らなくなるので、まともなサーバは zstd で返してきません |
| **redisson の Rx / Reactive / LiveObject が使えなくなった**（D-121） | `redissonClient.reactive()` / `rxJava()` / `getLiveObjectService()` を呼ぶと `NoClassDefFoundError` になります。jimble はどれも使っておらず、**この3つのためだけに 13.5MB**（byte-buddy / rxjava / reactor-core / jodd-util）が付いてきていました。必要な場合は、それぞれのライブラリを自分の依存に足してください |
| **`IcuUtil.convertToHiragana(...)` が「ー」を残すようになった**（D-120） | いままでは `コーヒー` → **`こおひい`**、`サーバー` → `さあばあ` と、直前のかなの母音に開いていました（ICU の決まり）。これからは `こーひー` `さーばー` と<b>そのまま残ります</b>。`convertToKatakana` で往復すると元に戻ります。`ヶ` → `け`、`ヵ` → `か` は変わりません。あわせて、かなの変換のついでに起きていた**無関係な文字の Unicode 正規化**（チベット文字の合成、結合文字の並べ替え）が無くなりました |
| **`FileCharDetecter.detector(...)` の返す名前が変わった**（D-119） | `Charset.name()` の正式な名前を返します（`SHIFT_JIS` → `Shift_JIS`、`WINDOWS-1252` → `windows-1252`）。`Charset.forName(...)` に通す使い方なら影響しません。あわせて**言い切れないときは既定の文字コードを返す**ようになりました（純 ASCII や空のファイルに当てずっぽうを返しません） |
| **`Dialect` に `sqlSyntax()` が増えた**（D-114） | SQL の字面の決まり（文字列・識別子・コメントの見分け方）を返す。`jimble-db` の `MySqlDialect` / `PostgreSqlDialect` は対応済み。**自分で `Dialect` を実装している場合は追加が要る**（`SqlSyntax.MYSQL` / `SqlSyntax.POSTGRESQL` をそのまま返せます）。既定値を持たせなかったのは、黙って MySQL の読み方になるほうが危ないためです |
| **マイグレーションで1文も取り出せなかったら失敗にする**（D-114） | up や down が全部コメントだったとき、いままでは成功として通していた。down でこれが起きると<b>テーブルは残ったまま履歴だけ消える</b> |
| **適用済みのマイグレーション SQL の名前を変えると、起動時に止まる**（F-G-20 / D-113） | 適用済みはファイル名で覚えているので、`001_create_post.sql` を `001_create_post.mysql.sql` に変えると**別のファイルとして同じ SQL がもう一度流れる**（`Table 'post' already exists`）。**製品の接尾辞を落とした名前**でそれを見分けて止め、**履歴を付け替える `UPDATE` 文を出す**（中身も変わっていれば `hash` も）。`examples/blog` を使っている環境では、その `UPDATE` を流すか `blog_example` を作り直すこと |
| **`codegen` が `sql_cache` / `sql_cache_tag` / `rate_limit` を生成しなくなった**（D-68） | jimble が作るテーブルなのに除外一覧から漏れていて、**アプリのテーブル定義クラスとして生成されていた**（`rate_limit` は `io.jimble.web.ratelimit.RateLimit` と単純名がぶつかる）。これらのテーブルがある環境では `codegen` の出力が変わるので、**生成物をコミットしているなら流し直すこと** |

### 直した

| | |
|---|---|
| **DB が読み込めていないと、DB の話をしない例外で落ちていた**（F-X-05 / D-130） | `DBUtil.load` は<b>繋がらなくても例外を投げず、原因をログに出して `false` を返します</b>。その戻り値を見ずに先へ進むと、サーバーは起動してしまい、最初のリクエストで `NullPointerException: Cannot read field "conf" because "dbSource" is null` になっていました（<b>本当の原因は何十行も上のログにあります</b>）。これからは `DBUtil.getMainDB()` / `getDB(名前)` が<b>取り出したその場で</b>、どの DB の話か・何を直せばいいかを書いた `IllegalStateException` で落ちます。`DBUtil.load` そのものの戻り値は変えていません（`db` の設定が無ければ `true`）。**呼ぶ側で戻り値を見てください**——`db.md` / `pitfalls.md` と `examples/blog` を直しました |
| **`cookie.secret` を設定すると Cookie セッションが丸ごと効かなくなっていた**（F-S-08 / D-128） | セッションの Cookie を<b>署名せずに</b>書いていたのに、受け取り側は<b>すべての Cookie の署名を検証して、落ちたものを捨てる</b>。つまり署名鍵を設定した瞬間に `session.store = "cookie"` が動かなくなっていました。<b>例外もログも出ず、「保存したのに次のリクエストで消えている」だけ</b>が残る形です。署名も通すようにしました（大きさの上限も署名を含めて見ます） |
| **Cookie セッションの有効期限を、中身を見ずに延ばしていた**（D-128） | 読めなくなった Cookie も、踏まれるたびに寿命が更新されて<b>永久に残り続けて</b>いました。読めるものだけ延ばします |
| **ドキュメントが署名検証をすり抜けるコードを教えていた**（D-128） | `session-security.md` が「読むときは `unsignCookie("名前")`。改ざんされていたら `null`」と書いていましたが、<b>`unsignCookie()` は署名を検証しません</b>（返るのは受信した生の値で、無いときは `null` ではなく空文字）。正しくは `cookie("名前")` です。日英とも直しました。**コードは変えていません** |
| **`jimbleRun` を止めて動かし直すと、次のビルドが起動しない**（D-101） | helidon が起動のときに JVM 全体の直列化フィルタを張る。`jimbleRun` はアプリを Gradle デーモンの中で動かす（D-77）ので、**フィルタがデーモンに残り**、次のビルドが `Couldn't populate class org.gradle.api.services.BuildServiceParameters$None > filter status: REJECTED` で落ちていた。`jimbleRun` のあいだだけ `helidon.serialFilter.missing.action = IGNORE` にする。**本番の挙動は変わらない** |
| **マイグレーション SQL の切り分けが、製品にかかわらず MySQL の読み方だった**（F-G-05 / D-114） | バックスラッシュを常にエスケープとして読むので、PostgreSQL の `insert into t values ('c:\');` で**文字列が閉じず、後ろの SQL が全部1文にくっついて**いた。文字列・識別子・コメントの見分け方を方言に聞くようにした（`Dialect.sqlSyntax()`）。`#` の行コメント（MySQL だけ）、`--` のあとの空白（MySQL は要る）、ドル引用符 `$tag$ ... $tag$`（PostgreSQL の関数の本体・`DO` ブロック）、入れ子のブロックコメント（PostgreSQL）も読み分ける |
| **行コメントを書くと、そこから先の SQL が全部コメントになっていた**（D-114） | 読んだ直後に改行を空白へ潰していたので、`--` や `#` が**行末で終わらなかった**。潰すのは**ハッシュを取るときだけ**にした（潰し方は変えていないので、適用済みのハッシュは変わりません）。コメントだけになった断片は捨てます（MySQL の `/*! ... */` は実行されるコメントなので捨てません） |
| **SPA を `/` に置くとトップページだけ 404** | `"/*"` はセグメントが0個の `/` に当たらないのに、`/` 自身を登録していなかった。`/any` は 200 で返るので気づきにくかった |
| **`in` の空一覧が JSON の `where` からだと素通りしていた** | `{"where": {"site": {"id|in": []}}}` が `IN (NULL)` になり、**例外もエラーも出ずに 0 件**（`not_in` なら本来の全件が 0 件）。`in(null)` も同じく落とすようにした |
| **エラーハンドラが送信してから落ちると、後続のエラーハンドラが走っていた** | 「送信済みなら以降は実行しない」の判定を `catch` で飛ばしていた |
| **ドキュメントだけでアプリを1本作れなかった**（`docs/docs-only-trial.md`） | ドキュメントと Maven Central の 0.2.0 だけでアプリを作る実地確認をしたところ、**そもそも始められませんでした**（依存の座標も動くビルドファイルもサイトに無い）。日英 35 ページずつに次を入れました：**そのまま動く `settings.gradle.kts` / `build.gradle.kts` の全文**とモジュールの表、リポジトリ URL、**「サイトは開発中の版に追従している」の明示と 0.2.0 に無い節の印**、`getString` / `getStringOptional` の説明の訂正（逆でした）、**`context.request()` から直接は読めない**、`DBUtil.load(...)` を DB のページに、クラスの置き場所の表、`gradle wrapper --gradle-version`。**フレームワークのコードは変えていません** |
| **依存の一覧が GitHub に一度も登録できていなかった**（NF-S-07 / D-118） | `dependency-submission` が9回とも失敗していた。原因はワークフローではなく<b>リポジトリの設定で Dependency graph が無効</b>だったことで、`The Dependency graph is disabled for this repository` は**ログ本文ではなく Annotations にしか出ない**ため、最後まで読んでも成功したようにしか見えなかった。設定を有効にして通した。ワークフローの先頭にこの前提を書いた |
| **GitHub Actions の action を上げた**（D-118） | Node 20 の非推奨警告を消すため、`actions/checkout` を v7、`actions/upload-artifact` を v7、`actions/setup-java` を v5 に。**`gradle/actions` は v5 で止めた**（v6 はキャッシュが MIT ではない非公開コンポーネントになり、使うと Gradle の Terms of Use への同意になる）。Dependabot が毎週 v6 を出してこないよう `ignore` を入れ、上限を 3 から 5 に上げた |
| **commonmark を 0.26.0 → 0.30.0 に上げた**（D-123） | 壊れる変更はありません。病的な入力に対する<b>スタックオーバーフローと二次関数的な遅さの修正</b>が入り、表のセル数（100 万）と入れ子の深さ（100）に既定の上限が付きました。**ドキュメントサイトの全 70 ページを新旧で描き比べて、生成される HTML が 1 byte も違わない**ことを確認しています |
| **依存を棚卸しして 21.5MB 減らした**（NF-L-01 / D-121） | アプリ1本の実行時クラスパスが **39.5MB / 102 jar → 18.0MB / 86 jar** になりました。`guava`・`commons-validator`（＋古い4本）・`commons-text`・`aircompressor` を外し、redisson の使っていない枝（byte-buddy / rxjava / reactor-core / jodd-util）を切りました。**置き換えたものはすべて、置き換える前と突き合わせて答えが変わらないことを確かめてあります**（IP 判定 54 万件、公開サフィックス 33 万件、XML エスケープは全文字、SipHash 9 万件）。`SipHash` の値は DB に入っているので、**1 bit も変えていません** |
| **`UrlUtil.isIPUrl` が IPv6 の URL で true を返すようになった**（D-121） | `http://[::1]/` のホストは `URI.getHost()` が **`[::1]` と角かっこ付きで返す**ため、判定に渡す前に外しておらず、**IPv6 では一度も true になっていませんでした** |
| **`icu4j` を外した**（NF-L-01 / D-120） | `IcuUtil` の4つの変換（ひらがな⇄カタカナ、全角⇄半角）を自前の表にしました。**14.5MB の依存が 12KB のクラス1つになります**（実行時クラスパスの単独最大でした）。ICU が返していたものと **129 万通りを突き合わせて**あり、上の「ー」の件以外は 1 文字も変わりません（`IcuUtilTest`）。半角カナの取り込み、濁点の合成、`㋐` → `ア`、`㌀` → `アパート` もそのままです |
| **`juniversalchardet` を外した**（NF-L-01 / D-119） | 文字コードの判定を tika に寄せました。tika の判定器は CSV のために入れている `tika-parser-text-module` が連れてくるので、**依存は増えません**（Apache-2.0）。**245KB と MPL-1.1 / GPL-3.0 / LGPL-3.0 のトリプルライセンスが1本減ります。**94 ファイルで突き合わせて、**実寸のファイルでは答えが変わらない**ことを確かめてあります（`FileCharDetecterTest`）。**BOM は自前で読みます**（UTF-8 / UTF-16 / UTF-32）。tika の判定器は<b>クラスパスに jar が並ぶ順しだいで BOM を無視する</b>ためです（D-122）。ICU4J も測りましたが、短い日本語に弱いので採っていません |
| **ドキュメントの食い違い 3 件** | `execution.md`「送ったら止まる」で `after` も止まると書いていた（`after` と `onComplete` は `finally` にあるので必ず通る）／`config.md` の `migration.on_startup` の既定を `false` と書いていた（実際は `"auto"`）／`deploy.md` の起動ログ例が jar の外の conf を「クラスパスより優先」と書いていた（jimble はクラスパスしか見ない） |

### 足した

| | |
|---|---|
| **アクセスログを切れるようになった**（NF-O-02 / D-130） | `server.access_log = false` で、1行も出さなくなります（**既定は `true` のままです**）。**1リクエストの割り当ての約3割がここ**（約 8,100 byte のうち約 2,300 byte。測るたびに数十 byte 動く）で、切ると秒あたりの本数が変わります（10 コアの Mac・8接続で 52,537 → 57,030 rps。<b>約 8%</b>）。**台によりますので、切る前に自分の台で測ってください**（`jimble-load/load.sh` が出す版と出さない版を並べて測ります）。行を組み立てる仕事も、ボット判定（User-Agent の照合）も止まります。**メトリクスとトレースは残ります。**<b>切ると、後から「あのとき何が起きたか」を調べる手段が無くなります</b>——500 が出ていたことも、誰がどのパスを叩いたかも残りません。前段（ロードバランサや nginx）が同じ内容を残していて、かつ実測して足りないと分かったときだけにしてください |
| **署名・暗号の鍵を、止めずに入れ替えられるようになった**（NF-S-09 / D-128） | 新しい鍵を `secret` に、いままでの鍵を `previous_secrets` に書きます。**書くのは常に `secret`、読むときだけ `previous_secrets` も試します。**<b>誰もログアウトしません。</b>古い鍵で読めたら<b>その場で新しい鍵で書き直す</b>ので、アクセスしてきた人から順に入れ替わります（`sid` と `csrf_token` は枠組みが書き直し、アプリが書いた Cookie は `cookies().isStale(名前)` で見て自分で書き直します——有効期限は書いた側にしか分からないためです）。**いつ古い鍵を捨ててよいかはメトリクスで分かります**（`cookie.stale_secret` / `session.stale_secret`。これが増えなくなったら捨てられます）。起動ログにも本数が出ます。<b>`cipher.key` と `hash.password.pepper` は対象外です</b>——作った値が DB に残り、書き直せるのが<b>ログイン成功時だけ</b>なので、休眠ユーザーがいる限り古い鍵を捨てられません（アプリ側の手順をドキュメントに載せました） |
| **鍵が無いことを起動時に言うようになった**（F-S-08 / D-128） | `cookie.secret` が空だと<b>署名が丸ごと効かない</b>のに、例外も出ず Cookie は普通に読み書きできるので気づけませんでした。`session.store = "cookie"` なのに `session.secret` が空のときも言います。**止めはしません**（手元で動かすだけのときに鍵を強制すると「とりあえず動かす」ができなくなるため）。`examples/blog` にも署名鍵を入れました |
| **MCP を標準入出力でも動かせるようになった**（F-MCP-12 / D-127） | `McpStdio.run(app, app.mcp().registry())` の1行です。HTTP を立てず、クライアントにプロセスを起こしてもらう形で会話します。**ポートは開きませんがルート表は組む**ので、すでにある API をそのまま出す `RouteTool` も `before` の認証も、HTTP のときとまったく同じに効きます。仕様は「**標準出力に MCP のメッセージ以外を書いてはならない**」と決めていて、ログが1行混ざるだけでクライアントは接続を切ります。そこで**本物の標準出力は `McpStdio` だけが持ち、`System.out` は標準エラーに差し替えます**——気をつけて避けるのではなく、書けなくします。出力は端末の文字コードに関係なく **UTF-8** です（`LANG` が決まっていないコンテナで、日本語が全部 `?` になって出るのを防ぐため） |
| **変わったことをクライアントに知らせられるようになった**（F-MCP-13 / D-127） | クライアントが `subscriptions/listen` で購読し、アプリは変わったところで `McpNotify.resourceUpdated("blog://posts/1")` と1行呼びます。**頼まれた URI にしか届きません。**HTTP なら SSE、stdio なら同じ標準出力に流れます。**流せるのはリソースの2つだけ**です（ツールとプロンプトは起動時に明示登録するので、動いているあいだに増えも減りもしません。「対応している」と答えて一生届かないほうが困るので、`toolsListChanged` は `acknowledged` に入れません） |
| **一覧が多いときにページに分かれるようになった**（F-MCP-14 / D-127） | `params.cursor` で受け、`result.nextCursor` で返します。**既定は 100 件**なので、それ以下しか登録していないアプリの応答は今までと変わりません（`mcp.page_size` で変えられます）。**読めないカーソルは `-32602` で断ります**——黙って先頭に戻すと、クライアントは同じページを永遠に読み続け、しかもエラーが1つも出ません |
| **`server/discover` に応えるようになった**（F-MCP-16 / D-127） | 仕様が「サーバーは実装しなければならない」と定めているのに**入っていませんでした**。話せる版と、そのサーバーが何を持っているかを返します。**この呼び出しにだけは版のヘッダが要りません**（版を知るための呼び出しに版を要求すると、初めて繋ぐクライアントは何もできません） |
| **分散トレーシングが使えるようになった**（NF-O-05 / D-126） | `jimble-otel` を依存に足して、起動時に `JimbleOtel.install("my-app", "http://localhost:4318")` と1行書くだけです。**HTTP リクエスト・SQL 1文・MQ の積みと処理・バッチ1回**に自動で区間が付き、入ってきた `traceparent` は引き継ぎ、jimble の HTTP クライアントから出るときも自動で付きます。**MQ も繋がります**——積んだリクエストと、何分もあとに別のプロセスで動いた処理が1本のトレースになります。ログには `trace_id` / `span_id` が入ります（**実行 ID の形は変えていません**）。送り先は OTLP（Collector / Jaeger / Grafana Tempo など） |
| **使わないアプリの容量は1 byte も増えません**（NF-L-01 / D-126） | `jimble-core` が持っているのは口（`Tracer` / `Span` / `Tracing`）だけで、OpenTelemetry の実体は `jimble-otel` にあります。登録していないあいだ、`Tracing.start(...)` の費用は**静的な変数を1つ読んで分岐するだけ**で、**1 回あたり 0 byte**です（ベンチマークと同じ測り方で確かめています）。1リクエストあたりの割り当ては 7,966 → 7,993 byte（+27 byte）に収まりました |
| **`jimble-otel` は 0.88MB**（NF-L-01 / D-126） | OpenTelemetry をそのまま入れると 4.2MB になります（既定の送信器 okhttp が okhttp 851KB ＋ okio 374KB ＋ **kotlin-stdlib 1.7MB** を連れてくるため）。**JDK の `HttpClient` を使う送信器（20KB）に差し替え**、トレースしか出さないので `sdk-metrics` と `sdk-logs` も外しました。削りすぎていないことは、**OTLP を受ける口を立てて実際に送り、届いた中身を見るテスト**で確かめています（コンパイルだけでは分かりません） |
| **メトリクスが取れるようになった**（NF-O-04 / D-124） | `Metrics.snapshot()` が、リクエスト数・レイテンシの分布・接続プールの使用数・MQ の件数をまとめて返します。**`/metrics` のルートは生やしません。**外に晒すかどうかは認証も公開範囲もアプリの都合なので、`get("/metrics", c -> c.response().json(Metrics.snapshot()))` と1行書いてください（ヘルスチェックを持たないのと同じ理由です）。**分布は固定バケット**なので、何件入れてもメモリは増えません（そのぶんパーセンタイルはバケットの境目までしか言えません）。**レイテンシの名前は `GET /posts/{id}` のようなルートの型**で、生のパスは使いません（`/aaa` `/aab` … と叩かれるだけで名前が無限に増えるため。名前は 1000 種類が上限で、超えたら1回だけ警告して捨てます）。**MQ の滞留数だけは自動で登録しません**（1回 SQL が要るので、`Metrics.snapshot()` が DB を触ることになる。**DB が詰まっているときに限ってメトリクスも取れなくなる**のを避けるため）。要るときは `Metrics.gauge("mq.notice.pending", () -> queue.pendingCount())` と書いてください |
| **ベンチマークを入れた**（NF-P-06 / D-125） | `./gradlew bench` で走ります。CI では毎回流れます。**落とすのは「1回あたりに割り当てた byte 数」だけで、時間では落としません。**共用ランナーの時間は走るたびに 20〜30% ぶれるので、要件にあった「ベースライン比 -10%」をそのまま実装すると**直していないのに赤くなる日**ができるためです（赤が信用されなくなるほうが害が大きい）。割り当て量は同じコードなら機械が変わっても同じ値になります。いまの実測は **1リクエスト 7,969 byte**（うちアクセスログが 2,264 byte）、**ルーティング1回 544 byte / 約 170ns**。時間も `build/bench/bench.txt` に記録します（CI の成果物として持ち帰るだけで、見て落とすことはしません） |
| **マッチ結果はキャッシュしないと決めた**（D-6） | 積み残していた宿題です。測ったところ、**ルートが 1000 本あっても 10 本と 1 byte も変わりません**でした（木を降りているので、ルート数に依らない）。1リクエスト全体の 7% で、DB を触るアプリでは誤差に沈みます。いっぽうキャッシュを持つと**鍵は利用者が送ってくる生のパス**になり、無限に増える表を抱えることになります。得るものが誤差で失うものがヒープなので、入れません |
| **マイグレーション SQL を製品ごとに分けられる**（F-G-20 / D-113） | ファイル名の接尾辞（`001_create_post.mysql.sql` / `001_create_post.postgresql.sql`）。**接尾辞の無いファイルはどちらでも流れる**ので、両製品で通る SQL は分けなくてよい。接尾辞は `db.<名前>.product` に書ける名前と同じ表で見る（`mariadb` は `mysql`）。飛ばしたファイルは起動ログに出る。`examples/blog` の DDL を両製品で書き、**`pgTest` に載せた**（D-112 の残り） |
| **ドキュメントサイトの反映を自動化した**（NF-D-01 / D-117） | **タグを打つと** GitHub Actions がサイトを作り、配る先（`jimble-document`）の `dist/` を置き換えて push します。Cloudflare はそこを見ているので、そのままデプロイされます。**push のたびには反映しません**（サイトだけが公開版より先に進むのを止めるため）。ドキュメントだけ直したときは Actions の画面から手で流せます。あわせて**サイトの原稿をリポジトリに入れました**（`.gitignore`）。それまで原稿が入っていなかったので、CI の「ドキュメントサイトの生成」は**0 ページのサイトを作って成功していました**（`site` タスクは 0 ページでも成功します）。ページ数の確認を CI と反映の両方に入れてあります |
| **CI を入れた**（D-108） | GitHub Actions。`build`（DB なし）／`db`（MariaDB + Redis）／`pg`（PostgreSQL + Redis）の3ジョブ。`dbTest` / `pgTest` / `codegenCheck`（F-G-14）/ `migrate → codegen → compileJava` の連鎖（NF-T-07）/ サンプルアプリの疎通（NF-T-06）/ ドキュメントサイトの生成（NF-D-03）が毎回回る。**標準ランナーだけ**で、larger runner は使わない |
| **待ち受けを始めてから止め方を預けていた**（D-110） | `JimbleServer` は `WebServer` を start したあとに `Shutdown.add(...)` していたので、**そのあいだに `jimbleRun` が止めに来ると何も止まらなかった**（古いアプリがポートを握ったまま残る）。start の前に預けるようにした。あわせて `stop()` は待ち受け前なら即座に抜ける（猶予のぶん黙って寝ないように） |
| **`pgTest` が PostgreSQL のつもりで MySQL に繋ぎに行っていた**（D-112） | `application.pgtest.conf` が無いモジュール（`examples/blog`）では `application.conf` に落ちるため。**手元は MySQL も立っているので通ってしまい**、CI の PostgreSQL だけのジョブで初めて落ちた。conf が無いモジュールは `pgTest` を飛ばす（`SKIPPED` としてログに出る） |
| **Gradle 9 でプラグインの検証が落ちていた**（D-111） | キャッシュの可否と入力の正規化の注釈が無かった。Gradle 8 は警告で流すが 9 はエラー。`enableStricterValidation` を入れて**手元でも 9 と同じ厳しさ**で見るようにし、あわせて javadoc の警告 14 件も潰した |
| **サンプルアプリが、まっさらな DB では動かなかった**（D-109） | `examples/blog` は MQ のテーブルをバッチの入口でしか作っていないのに、**キューに積むのは Web** だった。まっさらな DB で記事を登録すると「トランザクションのコミットに失敗しました」で 500。**いちどでもバッチを動かしたマシンでは動く**ので手元では気づけず、**CI を入れた初回に出た**。起動時に用意するものを `Bootstrap` 1か所にまとめ、3つの入口とテストがそれを呼ぶようにした。落とし穴のページにも足した |
| **Apache Tika を 3.3.2 → 4.0.0 に上げた**（D-115） | `Parser.parse` / `Detector.detect` が `TikaInputStream` を取るようになり、`Detector.detect` に `ParseContext` が増え、`Metadata` が `HttpHeaders` / `TIFF` を実装しなくなった（`Metadata.CONTENT_TYPE` → `HttpHeaders.CONTENT_TYPE`、`Metadata.IMAGE_WIDTH` → `TIFF.IMAGE_WIDTH`）。**種別判定の答えは1つも変わっていません**（同じテストを両方の版で流して確認。そのために `FileUtil` に初めてテストを付けました）。**依存が増えます**：`juniversalchardet`（MPL-1.1）が消え、`tika-encoding-detector-mojibuster` / `tika-ml-core` / `tika-parser-datauri-commons` と **commonmark**（tika-core 4 の既定出力が Markdown になったため）が入ります。実行時クラスパスで約 +0.5MB |
| **画像の幅と高さが取れるようになった**（D-116） | いままで**常に 0** でした（`AutoDetectParser` に画像を読む parser が無いため）。JDK の `ImageIO` でヘッダだけを読みます（PNG / JPEG / GIF / BMP / TIFF）。**WebP は `ImageIO` に無いのでコンテナを自前で読みます**（`VP8 ` / `VP8L` / `VP8X`）。画素は展開しないので大きな画像でもヒープを食いません。**HEIC / AVIF / SVG と動画は 0 のまま**です。`tika-parser-image-module` は +9 jar / 約 5.4MB（pdfbox 一式）になるので入れていません |
| **CSV の文字コードと区切り文字が取れるようになった**（D-116） | 種別を名前から決め直すときに**文字コードごと差し替えていました**。あわせて、`text/csv; charset=UTF-8` のようなパラメータ付きの種別が CSV として扱われなかったのと、拡張子の無い一時ファイル（アップロードの置き場所）が CSV として扱われなかったのも直しました |
| **依存の脆弱性を見る口を作った**（NF-S-07） | Gradle の依存グラフを GitHub に登録して Dependabot alerts に当てる。あわせて Dependabot で週1の更新 PR |
| **`UrlUtil.normalizeUrl`**（D-107） | **未エンコードの URL でも、エンコード済みの URL でも、同じ答えになる**（2回通しても変わらない）。ホストは punycode にする。移送してきた `fullUrlEncode` は<b>エンコード済みを二重にし、ホストをパーセントエンコードしていた</b>（DNS が引けない）。`urlToEncodeUrl` は<b>パスの空白を `+` にしていた</b>（`+` が空白なのはフォームの書式だけ）。どちらも呼ばれていないので消さず、javadoc から新しいほうへ案内している |
| **静的配信のルートを外から触れる**（D-70） | `AssetController` / `SpaController` / `MpaController` に `routes()`。`install` した子のルートに `AttributeKey`（認証の除外・流量制限）を付けられる |
| **`NOT IN` の条件を読めるようにした**（D-105） | `NotIn` に `operator()` / `conditionValue()`。`In` と対称になった。**`IN` とは別の演算子（`WhereTerm.NOT_IN`）**にしてある（`NOT IN (1,3)` は「それ以外の全部」なので、1 と 3 の行タグだけ消すと**古い値が返り続ける**）。SQL 結果キャッシュは従来どおりテーブルごと消す安全側のまま |
| **`codegen` が外したテーブルの名前を出す**（D-68） | `session` のように、jimble の管理テーブルとアプリの業務テーブルで名前がぶつかりうる。黙って外すと**自分のテーブルのクラスが生成されないことに気づけない** |

### 中の整理

| | |
|---|---|
| **負荷試験を置いた**（NF-P-08 / D-129） | `./jimble-load/load.sh` で、秒あたりの本数とレイテンシ（p50 / p90 / p99）を測れます。**素の helidon と並べて出す**ので、差がそのまま jimble の上乗せ分です。**依存は1つも増えていません**（Java で書いてあるので Mac でも Linux でも同じものが走ります）。`jimble-load` は<b>公開しません</b>。**CI では回しません**（D-125。共用ランナーの時間は 20〜30% ぶれるため） |
| **負荷試験を置いた**（NF-P-08 / D-129） | `./jimble-load/load.sh` で、秒あたりの本数とレイテンシ（p50 / p90 / p99）を測れます。**素の helidon と、アクセスログを切った jimble と並べて出す**ので、<b>helidon との差が jimble の上乗せ分、切った版との差がアクセスログ1行の代金</b>です（D-130）。**依存は1つも増えていません**（Java で書いてあるので Mac でも Linux でも同じものが走ります）。`jimble-load` は<b>公開しません</b>。**CI では回しません**（D-125。共用ランナーの時間は 20〜30% ぶれるため） |
| **ビルドの規約を `build-logic` へ移した**（D-13。**完了**） | root の `build.gradle.kts` が 709 行になり、うち 293 行が全モジュール共通の設定でした。`jimble.java-conventions` / `jimble.test-conventions` / `jimble.publish-conventions` の3つに分け、各モジュールの `plugins {}` に書くようにしています。**どのモジュールが何を使っているかが、そのモジュールを見れば分かります。**あわせて **junit の3行が 12 か所から消え**、`examples` には `-sources.jar` / `-javadoc.jar` が作られなくなりました（publish しないので要りません）。root は 369 行、`gradle-plugin` は 199 → 117 行になりました。**フレームワークのコードは1行も変えていません**（`build/central` に出るファイル 330 個と POM 13 個が移行前と 1 byte も変わらないことを確認しています） |
| **POM の必須項目を1か所にした**（NF-L-04 / D-13） | `licenses` / `developers` / `scm` の 25 行を本体と `gradle-plugin` に書き写していて、「直すときは両方直すこと」と注意書きを付けていました。`build-logic` を**別ビルド**（buildSrc ではなく）にしたので、別ビルドである `gradle-plugin` からも取り込めます。あわせて**版の既定値**も `gradle/libs.versions.toml` の `jimble` 1か所に寄せました（以前は3か所に同じ文字列がありました）。**developer から `organization` / `organizationUrl` を外しました**：Maven Central が求めているのは「連絡が付くこと」で所属ではありません（Central にある caffeine / jspecify / fastcsv も `id` / `name` / `email` だけです）。**すでに公開した 0.2.0 の POM は変わりません**（公開済みのものは書き換えられないので、次の版から） |
| **規約プラグインは Kotlin ではなく Java で書いた**（D-13 / D-22） | `plugins { kotlin-dsl }` の実体（`org.gradle.kotlin:gradle-kotlin-dsl-plugins`）は **Gradle の配布物に入っておらず、Plugin Portal からしか取れません**（Maven Central には `org.gradle.kotlin` というグループ自体がありません）。D-22 が「Plugin Portal への依存を持ち込まない」と決めていて、しかもその理由として D-13 を名指ししているので、`java-gradle-plugin`（Gradle 同梱）で書いています。`gradle-plugin` と同じ形なので、読み方も同じです |
| **移送してきたコードの警告を全部潰し、`-Werror` にした**（D-15。**完了**） | `jimble-util` は移送時点の警告を種別ごと（`unchecked` / `rawtypes` / `fallthrough` / `deprecation` / `dangling-doc-comments` / `this-escape` / `cast` / `overloads`）落としていて、**javadoc の doclint も切っていた**。**コンパイル警告 81 件と doclint 45 件を潰して、抑止をやめた。**以降は警告が出たらビルドが落ちる。消せないものは、消せない理由を書いた `@SuppressWarnings` をその場所に付けてある |
| **ヘルスチェックの要件を実態に合わせた**（NF-O-03 / D-106） | 要件は「標準で提供する」と書いてあったが、実装は無く、ドキュメントは「アプリが書いてください」だった。**グレースフルシャットダウンで「先にヘルスチェックだけ落とす」順番をアプリが決めるため**という理由を要件側に書いた |
| **jimble が作るテーブルの名前を1か所に**（D-68） | `io.jimble.db.FrameworkTables`。作る側も codegen の除外側も同じ定数を使う。`GeneratorConf.FRAMEWORK_TABLES` は廃止 |
| **「送信済みなら打ち切る」判定をエラー経路にも通した**（F-C-13） | `Dispatcher` から `isSent()` の直書きが無くなり、判定は `Stage` だけになった |
| 使われていない `Generator.isGenerationRequired` を削除 | スキーマが変わっても版は変わらないので、生成を飛ばす判定には使えない。鮮度は `codegenCheck` が生成物そのものを突き合わせて見る |
| 同名の `FileCharDetecter` が 2 パッケージにあったのを解消 | `io.jimble.util.io.FileCharDetecter` を残し、`io.jimble.util.charset.FileCharDetecter` を削除（後者はどこからも使われていなかった） |

---

## 0.2.0（2026-09-08）

**PostgreSQL に対応した版。**アプリのコードは1行も変えずに
`db.xxx.product = postgresql` だけで動く。

### 追加

| | |
|---|---|
| **PostgreSQL 対応**（F-D-30） | SQL ビルダーとフレームワークの内部テーブルが `db.xxx.product`（`mysql` / `mariadb` / `postgresql`）で切り替わる。**SQL ビルダーを使うアプリ側のコードは変更なし。**識別子の囲み・関数名・upsert・`INSERT IGNORE`・`INTERVAL`・JSON の取り出し・真偽値と JSON のバインド・採番値の取り出しを方言に寄せた。その製品に書けないものは<b>SQL を組み立てたところで `DialectException`</b> になる |
| **テーブル定義クラスの生成が PostgreSQL でも動く**（D-98） | `./gradlew codegen` が `pg_catalog` を読む。連番・既定値・コメント・一意キーを MySQL と同じ形に揃える |
| **SQL DSL の充実**（F-D-31） | 文字列（`lower` / `substring` / `replace` / `lpad` / `locate` …）、数値（`abs` / `mod` / `power` / `greatest` …）、日付（`year` … `weekOfYear` / `dateAdd` / `dateDiff` / `unixTimestamp` …）、条件と型変換（`coalesce` / `nullif` / `ifThenElse` / `cast` / `regexp`）、集約（`countDistinct` / `stddev` / `groupConcat`）、**ウィンドウ関数**（`rowNumber` / `rank` / `lag` / `over` ＋ `PARTITION BY` / `ORDER BY` / `ROWS BETWEEN`）。約 60 個。**両製品で同じ答えを返す** |
| **SQL 結果のキャッシュ**（F-D-28） | `selectCached` / `selectListCached` で結果をキャッシュし、**更新系 SQL が走ったら関係するキャッシュだけを消す**。判定に主キーと一意キーを使う。置き場は `sql_cache.store`（memory / redis / db）。**既定は無効**（`sql_cache.enabled = false`。切っているときは更新側の処理が1行も走らない） |
| **同じ `AsyncData` を面ごとに違う形で返す**（F-A-11） | `TableNest.ON` / `OFF` / `AS_IS`。管理画面はテーブルネスト、ショップはフラット、を<b>同じクラスのまま</b>出し分けられる。既定は `AS_IS` で既存の出力は変わらない |
| **実装済みの API を MCP からも出す**（F-W-27 / F-MCP-15） | `RouteTool` でルートをそのまま MCP のツールにする。`before` / `after` / エラーハンドラ / 流量制限が<b>同じように効く</b> |
| **レートリミット**（F-R-15） | ルートに宣言し、ディスパッチャが `before` より前に見る。トークンバケット。置き場は `rate_limit.store`（memory / redis / db） |
| **グレースフルシャットダウン**（F-X-05） | SIGTERM で「ヘルスチェックだけ落とす → 待つ → 新規を断つ → 処理中を待つ → 止める」 |

### 変更

- `db.xxx.product` が設定に増えた。**書かなければ `mysql`**（既存の設定はそのまま動く）
- `jimble-db` が PostgreSQL の JDBC ドライバを `runtimeOnly` で持つようになった。
  要らなければ `exclude(group = "org.postgresql", module = "postgresql")` で外せる
- `SelectBuilder` / `InsertBuilder` などの `sql()` に `sql(Dialect)` が増えた。
  引数なしの `sql()` は<b>主データソースの製品</b>に落ちる（既存の呼び出しはそのまま）
- `application.local.conf` の `include "application.conf"` が効くようになった

### 直したもの

- **テーブル定義クラスの型の取り違え。**MySQL の `SHOW FULL COLUMNS` は enum / set の
  <b>値そのもの</b>を型名に含めるため、`enum('serial','parallel')` が `int`、
  `enum('unreal','real')` が `double` になっていた。enum / set は `String` に倒す。
  **enum 列を持つアプリは、`codegen` をかけたあとの差分を一度見ること**
- 数の列に関数の既定値（`nextval(...)`、`NaN`）が付いていると、
  生成される Java が<b>コンパイルできない</b>ことがあった
- `selectListWithRowCount` が `ORDER` も `LIMIT` も無い SQL で例外になっていた
- データソースを開くときの例外を「データベースが無い」に潰していたため、
  <b>パスワード違いでもデータベースを作りにいっていた</b>

### 気をつけること

- **テーブル定義クラスの生成（`codegen`）は再実行を推奨。**上の型の取り違えの修正が入る
- PostgreSQL で使うときの制限（空間関数の座標の順、`greatest` / `least` の NULL、
  正規表現の方言など）は [DB を使う](https://jimble.io/ja/db) と
  [SQL DSL](https://jimble.io/ja/sql) にまとめてある

---

## 0.1.0（2026-09-07）

最初の公開。

- Web（Router / Request / Response / SSE / WebSocket / セッション / アセット / SPA / MPA）
- DB（SQL ビルダー / トランザクション / マイグレーション / コード生成 / キャッシュ / 分散ロック）
- バッチ / MQ / バッチ管理画面
- MCP サーバー
- `jimble new` / `migrate` / `codegen` / `jimbleRun`（Gradle プラグイン）
- `AsyncData` の先読み

**アノテーションと DI を使わない。**コードを上から辿れば処理が分かることを最優先にする。
