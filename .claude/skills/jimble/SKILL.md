---
name: jimble
description: Java の Web フレームワーク jimble（Java 25 + Helidon）でアプリを書く・直すときに最初に使う。注釈も DI も使わない書き方、Spring からの対応表、5つの決めごと、モジュール、書いたら確かめること（jimbleCheck・AGENTS.md）と、Web・DB・バッチ・設定・JSON・版の上げ方のどの skill を読むかの案内。
---

# jimble

Java 25 + Helidon の上に載る Web フレームワーク。**Spring ではない。**

## いちばん先に

**注釈は無い。DI も無い。起動時のクラスパス走査も無い。**
`@RestController` `@Autowired` `@Transactional` `@Component` `@Value` `@Bean`
`@Service` `@Repository` `@Configuration` `@PreAuthorize` ——
**これらは jimble に存在しない。**書くとコンパイルが通らない。

ルートは**初期化ブロックに並べる**（`{ }` はコンストラクタの前に走る初期化ブロック。**これがルート定義そのもの**）。
入口の `main` は `Bootstrap.load();`（アプリ側のクラス）→ `JimbleServer.start(new App());`（`jimble-web`）。

## import（推測しない）

```java
import io.jimble.web.server.JimbleApp;      // アプリの親（起動は io.jimble.web.server.JimbleServer）
import io.jimble.web.context.WebContext;    // ハンドラの引数。Context ではない
import io.jimble.web.http.HttpException;
import io.jimble.db.DuplicateKeyException; // 一意制約の違反。Spring の同名クラスではない
import io.jimble.util.data.Data;
import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;
```

**ハンドラの引数は `WebContext`。**`Context`（`io.jimble.core.context.Context`）は別物で、バッチや MQ も含めた「一つの実行」のほう。

## Spring の癖でよく間違えるところ

| 書きたくなるもの | jimble では |
| --- | --- |
| `@RestController` / `@GetMapping` | `get("/path", Controller::method)` を初期化ブロックに書く |
| `@Autowired` / コンストラクタ注入 | `new` する。`install(AdminController::new)` |
| `@Transactional` | `db.transaction(tx -> { ... })`（例外なら巻き戻す。検査例外は要らない） |
| `@RequestParam` / `@PathVariable` / `@RequestBody` | `context.request().bodyAll().getString("x")`（`Request` は `Data` ではない） |
| `@Value("${x}")` | `Conf.conf().getString("x", "既定")` |
| `@PreAuthorize("hasRole('X')")` | `.attribute(Auth.ROLE, "X")` をルートに付ける |
| `JpaRepository` / エンティティ | `SQL.select().from(Post.instance())`。テーブルクラスは codegen が作る |
| `Optional<Post> findById(id)` | `db.select(...)` が **`Optional<Data>`**。無ければ `.orElseThrow(() -> new HttpException(404, "..."))` |
| `DataAccessException` / Spring の `DuplicateKeyException` | `SqlExecuteException` / `DuplicateKeyException`（どちらも `io.jimble.db`。非検査） |
| `@Valid` / `BindingResult` | `rules.validate(db, input);`（通らなければ 422 で止まる）。一覧が欲しいなら `rules.errors(db, input)` |
| `application.properties` | `application.conf`（HOCON）。時間と大きさは単位を書く（`5m` / `10MiB`） |
| Spring Security の Bearer / JWT | `before(ApiToken.authenticate(...))` ＋ ルートに `attribute(ApiToken.ACCEPT, true)`（`jimble-web-api-token`） |
| `JavaMailSender` | `Mailer`（`io.jimble.util.mail`。MQ から送る形は `jimble-mq`） |

## 守っている5つの決めごと

1. **上から順に追えること** — `main` から目的の処理まで指でたどれる
2. **起動時にクラスパスを走査しない** — コントローラもバッチも自分で登録する
3. **黙って間違えないこと** — 「静かに効かなくなる」状態を潰す
4. **失敗は例外にする** — 戻り値で失敗を返さない（非検査の例外）。壊れ方はコンパイルエラーか例外だけ
5. **一つの実行 = 一つの Context** — `ScopedValue` で渡す。`ThreadLocal` ではない

**機能を足すか迷ったら、この5つで決まる。**

## どの skill を読むか

**書く前に、該当する skill を読む。**細かい決まりと落とし穴（実際に踏んだもの）は、それぞれの skill にだけある。
Web・DB・バッチは、まず入口の skill を読み、その案内から分野の skill へ進む（import も各 skill の頭にある）。

| やること | skill |
| --- | --- |
| ルート・ハンドラ・入力・返す・セッション・ログイン・プロキシの後ろ | `jimble-web`（→ `jimble-web-*`） |
| SQL ビルダー・`Data` の形・トランザクション・マイグレーション / コード生成・DB と Redis の設定 | `jimble-db`（→ `jimble-db-*`） |
| バッチ・cron のスケジューラ・MQ（DB のテーブルがキュー）・メール送信 | `jimble-batch`（→ `jimble-batch-*` / `jimble-mq*`） |
| `application.conf`（HOCON）・秘密を環境変数から読む・単位・環境の切り替え | `jimble-config` |
| JSON（`Dson` / `Json`）・型変換（`Converter`）・`Data` の基本 | `jimble-json` |
| jimble の版を上げる・1.x のコードを直す・非推奨を書き換える・skill を揃える | `jimble-upgrade` |

## モジュール

| | |
| --- | --- |
| `jimble-web` | ルーティング・入出力・セッション・CSRF・認証・SSE・WebSocket |
| `jimble-db` | SQL ビルダー・マイグレーション・コード生成・キャッシュ・ロック |
| `jimble-util` | `Data` / `Log` / `Conf` / `Dson` / ハッシュ / メール（`Mailer`） |
| `jimble-core` | `Context` / `Executor` |
| `jimble-batch` `jimble-mq` | バッチとキュー（**MQ は DB のテーブル**） |
| `jimble-mcp` `jimble-otel` `jimble-batch-manager` | MCP サーバー・分散トレーシング・バッチ管理画面 |
| `jimble-cli` | `jimble new`（雛形）・`jimble version`・migrate・codegen |

Gradle プラグインは `io.jimble.jte`（テンプレート変換）/ `io.jimble.run`（ホットリロード）/
`io.jimble.db`（migrate・codegen）。

## 書いたら確かめる

```bash
./gradlew jimbleCheck     # 既知の落とし穴と 1.x の書き方を見つける（直し方と引き先つき）
./gradlew jimbleSkills    # jimble の版を上げたら、skill をその版に揃える（jimble-upgrade）
```

- **`jimbleCheck` が `ERROR` を出したら直す。**出力にある「直し方」と「詳しく」の URL を見る（推測で直さない）。
  誤検知なら、その行か前の行に `// jimble-check:ignore J101` と書く
- **アプリ固有の決まりは skill を直さず、プロジェクトの根の `AGENTS.md` に書く。**skill を直すと `jimbleSkills` で揃えられなくなる
- jimble が出す例外や警告の多くには **`詳しく: https://jimble.io/ja/〜.md`** が付いている。そのページを取って読む
- **非推奨（`[removal]` の警告）は書き換える。新しく書くコードで使わない**（置き換え先は `jimble-upgrade`）
- **`@CheckReturnValue`（`io.jimble.util.annotation`）が付いたメソッドの戻り値は捨てない。**
  値を返すだけのもの（`db.select(...)`・`db.begin()`・`rules.errors(...)`・`Column.as(...)`）で、捨てると何もしていない。
  Error Prone と IntelliJ は捨てた行を指摘する

## 詳しいことは引く

**推測で書かない。**全ページが Markdown でそのまま取れる。

- 目次：<https://jimble.io/ja/llms.txt>（英語は `/en/`）
- 全文1枚：<https://jimble.io/ja/llms-full.txt>
- 個別：`https://jimble.io/ja/<名前>.md`（分野ごとの引き先は、それぞれの skill の終わりにある）

skill に無いもの：テンプレート（jte）は `view.md`、ログは `log.md`、HTTP クライアント・CSV・XML は `util.md`、
よくある落とし穴は `pitfalls.md`、考え方の理由は `principles.md`。

**版で挙動が変わることがある。**版を上げる・古い版のコードを直すときは `jimble-upgrade`。
