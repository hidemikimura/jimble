---
name: jimble
description: Java の Web フレームワーク jimble でアプリを書く・直すときに最初に使う。注釈も DI も使わない書き方、モジュール構成、Spring の癖でよく間違えるところ、詳しい説明の引き先を含む。
---

# jimble

Java 25 + Helidon の上に載る Web フレームワーク。**Spring ではない。**

## いちばん先に

**注釈は無い。DI も無い。起動時のクラスパス走査も無い。**
`@RestController` `@Autowired` `@Transactional` `@Component` `@Value` `@Bean`
`@Service` `@Repository` `@Configuration` `@PreAuthorize` ——
**これらは jimble に存在しない。**書くとコンパイルが通らない。

ルートは**初期化ブロックに並べる**。

```java
public class App extends JimbleApp {

	{
		before(Auth::guard);

		get("/posts", PostController::list);
		post("/posts", PostController::create);

		path("/admin", () -> {
			attribute(Auth.ROLE, "admin");
			get("/users", AdminController::users);
		});

		error((context, cause, statusCode) ->
			context.response().code(statusCode).json("error", cause.getMessage()));
	}

	public static void main (String[] args) {
		Bootstrap.load();                    // アプリ側のクラス。枠組みには無い
		JimbleServer.start(new App());
	}

}
```

`{ }` はコンストラクタの前に走る初期化ブロック。**これがルート定義そのもの。**

## import（推測しない）

```java
import io.jimble.web.server.JimbleApp;      // アプリの親
import io.jimble.web.server.JimbleServer;   // 起動
import io.jimble.web.context.WebContext;    // ハンドラの引数。Context ではない
import io.jimble.web.http.HttpException;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Principal;
import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.query.dsl.Dsl;      // now() など
import io.jimble.util.data.Data;
import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;
```

**ハンドラの引数は `WebContext`。**`Context`（`io.jimble.core`）は別物で、
Web だけでなくバッチや MQ も含めた「一つの実行」のほう。

**アクセサに `get` は付かない。**`principal.id()` / `principal.name()` /
`principal.hasRole("x")`。`Principal` は record。

## Spring の癖でよく間違えるところ

| 書きたくなるもの | jimble では |
| --- | --- |
| `@RestController` / `@GetMapping` | `get("/path", Controller::method)` を初期化ブロックに書く |
| `@Autowired` / コンストラクタ注入 | `new` する。`install(AdminController::new)` |
| `@Transactional` | `try (DBTransaction transaction = new DBTransaction()) { ... }` |
| `@Value("${x}")` | `Conf.conf().getString("x", "既定")` |
| `@PreAuthorize("hasRole('X')")` | `.attribute(Auth.ROLE, "X")` をルートに付ける |
| `JpaRepository` / エンティティ | `SQL.select().from(Post.instance())`。テーブルクラスは codegen が作る |
| 例外で DB エラーを拾う | **戻り値で返る。** `select` 系は `null`、更新系は `-1` |
| `application.properties` | `application.conf`（HOCON） |

## DB の書き方

**組み立てと実行が分かれている。**ビルダーは自分では走らない。

```java
DB db = MyExample.db();                       // codegen が作る入口

Data row = db.select(SQL.select()
	.from(Request.instance())
	.where(Request.id.eq(id)));

if (row == null) { ... }                      // エラーも「無い」も null

db.insert(SQL.insert(Notice.instance())       // insert(テーブル).value(列, 値)
	.value(Notice.request_id, id)
	.value(Notice.created_at, Dsl.now()));

if (db.isError()) { ... }                     // 書き込みは isError() で見る

db.update(SQL.update(Request.instance())
	.set(Request.status, "approved")
	.where(Request.id.eq(id)));
```

**列は静的フィールドで、名前は DB のまま**（`Request.decided_by`。camelCase ではない）。

トランザクションはこう書く。

```java
try (DBTransaction transaction = new DBTransaction(db)) {

	transaction.beginTransaction();

	// ... db.insert / db.update ...

	if (db.isError()) {
		transaction.rollbackEndTransaction();
		throw new HttpException(500, "更新できませんでした");
	}

	transaction.commitEndTransaction();

}
```

## 守っている5つの決めごと

1. **上から順に追えること** — `main` から目的の処理まで指でたどれる
2. **起動時にクラスパスを走査しない** — コントローラもバッチも自分で登録する
3. **黙って間違えないこと** — 「静かに効かなくなる」状態を潰す
4. **例外にしないところ** — DB のエラーは戻り値
5. **一つの実行 = 一つの Context** — `ScopedValue` で渡す。`ThreadLocal` ではない

**機能を足すか迷ったら、この5つで決まる。**

## 落とし穴（実際に踏んだもの）

- **`request()` から直接は読めない。** 送られてきた値は `context.request().bodyAll()`
  の中にある。`request().getString("x")` は**黙って null を返す**
- **SELECT の結果はテーブル名でネストされている。**
  文字列のキーで引くと空が返る。**列オブジェクトを渡せばそのまま引ける** →
  `row.getString(Staff.name)`（`row.getString("name")` は空）
- **セッションは自動保存されない。** `context.session().save()` を明示的に呼ぶ。
  1リクエストにつき1回だけ効く
- **`Auth::guard` はいちばん最初に登録する。** セッションを使うかどうかをここで決めるので、
  先に誰かが `session()` を触ると間に合わない
- **`env=local` で `cookie.secure = true` のままだと**、ブラウザが Cookie を返さず、
  セッションも CSRF もエラーなしで効かなくなる（起動時に WARN が出る）

## モジュール

| | |
| --- | --- |
| `jimble-web` | ルーティング・リクエスト/レスポンス・セッション・CSRF・認証・SSE・WebSocket |
| `jimble-db` | SQL ビルダー・マイグレーション・コード生成・キャッシュ・ロック |
| `jimble-util` | `Data` / `Log` / `Conf` / `Dson` / ハッシュ |
| `jimble-core` | `Context` / `Executor` |
| `jimble-batch` `jimble-mq` | バッチとキュー。**MQ は DB のテーブルがキュー**（Kafka も Rabbit も要らない） |
| `jimble-mcp` `jimble-otel` `jimble-batch-manager` | MCP サーバー・分散トレーシング・バッチ管理画面 |

Gradle プラグインは `io.jimble.jte`（テンプレート変換）/ `io.jimble.run`（ホットリロード）/
`io.jimble.db`（migrate・codegen）。

## もっと深いところ

同じところに skill が2本ある。**そちらのほうが詳しい。**

| | |
| --- | --- |
| `jimble-db` | SQL ビルダー・`Data` の形・トランザクション・マイグレーション / コード生成 |
| `jimble-web` | ルーティングとフィルタの効く範囲・入力と出力・セッション / CSRF・エラー処理・認証 |
| `jimble-batch` | バッチ（登録の順序・中断・チャンク）と MQ（DB のテーブルがキュー） |

## 詳しいことは引く

**推測で書かない。**全ページが Markdown でそのまま取れる。

- 目次：<https://jimble.io/ja/llms.txt>（英語は `/en/`）
- 全文1枚：<https://jimble.io/ja/llms-full.txt>
- 個別：`https://jimble.io/ja/<名前>.md`

よく引くもの：

| 知りたいこと | 引き先 |
| --- | --- |
| ルーティング・`before`/`after`・コントローラの分け方 | `routing.md` |
| 入力の読み方・返し方 | `request-response.md` |
| SQL ビルダー・`Data` の形 | `sql.md` / `db.md` |
| トランザクション | `transaction.md` |
| マイグレーションとコード生成 | `codegen.md` |
| ログインと認可・ロックアウト・remember-me | `auth.md` |
| セッション・CSRF・Cookie | `session-security.md` |
| 設定ファイルの全項目 | `config.md` |
| テンプレート（jte） | `view.md` |
| よくある落とし穴 | `pitfalls.md` |
| 考え方の理由 | `principles.md` |

**版で挙動が変わることがある。**手元の版は `jimble version`、
変更は <https://github.com/hidemikimura/jimble/blob/main/CHANGELOG.md> を見る。
