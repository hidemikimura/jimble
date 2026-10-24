---
name: jimble
description: Java の Web フレームワーク jimble（Java 25 + Helidon）でアプリを書く・直すときに最初に使う。注釈も DI も使わない書き方、Spring から書き換えるときの対応表、設定（application.conf / HOCON・環境変数・単位）、モジュール構成、jimble の版を上げるとき（jimbleCheck / jimbleSkills / migrate-2）、詳しい説明の引き先と、Web・DB・バッチのどの skill を見るかを含む。
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
			context.response().code(statusCode).json("error"
				, statusCode < 500 ? cause.getMessage() : "サーバーで問題が起きました"));   // 500 番台は中身を返さない
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
import io.jimble.db.Tx;                    // トランザクション（db.begin()）
import io.jimble.db.SqlExecuteException;   // SQL の失敗（非検査）
import io.jimble.db.DuplicateKeyException; // 一意制約の違反。Spring の同名クラスではない
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.query.dsl.Dsl;      // now() など
import io.jimble.util.data.Data;
import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;
```

**ハンドラの引数は `WebContext`。**`Context`（`io.jimble.core.context.Context`）は別物で、
Web だけでなくバッチや MQ も含めた「一つの実行」のほう。

**アクセサに `get` は付かない。**`principal.id()` / `principal.name()` /
`principal.hasRole("x")`。`Principal` は **final class**（`Principal.of(id, name, role)` で作る。record ではない）。

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
| Spring Security の Bearer / JWT | `before(ApiToken.authenticate(...))` ＋ ルートに `attribute(ApiToken.ACCEPT, true)`（`jimble-web` の skill） |
| `JavaMailSender` | `Mailer`（`io.jimble.util.mail`。MQ から送る形は `jimble-batch` の skill） |

## DB の書き方

**組み立てと実行が分かれている。**ビルダーは自分では走らない。

```java
DB db = MyExample.db();                       // codegen が作る入口

Data row = db.select(SQL.select()             // select は Optional<Data>。0件は空
	.from(Request.instance())
	.where(Request.id.eq(id)))
	.orElseThrow(() -> new HttpException(404, "申請がありません: " + id));

int updated = db.update(SQL.update(Request.instance())   // update / delete は件数
	.set(Request.status, "approved")
	.where(Request.id.eq(id)));

db.transaction(tx -> { ... });                // 例外で抜ければ巻き戻る。try (Tx tx = db.begin()) { ...; tx.commit(); } でも書ける
```

- **失敗は例外（`SqlExecuteException`、非検査）。**`isError()` は無い。利用者の入力を一意制約のある列に入れるところだけ `DuplicateKeyException` を受けて 409 などにする
- **列は静的フィールドで、名前は DB のまま**（`Request.decided_by`。camelCase ではない）
- **ビルダーの結果はテーブル名でネストする**（`row.getString(Request.status)` と列で引く）。文字列の SQL の結果は平ら

**書く前に `jimble-db` の skill を読む。**トランザクション・catch するところ・Data の形・JSON の列・マイグレーションとコード生成はそちらにだけ書いてある。

## 守っている5つの決めごと

1. **上から順に追えること** — `main` から目的の処理まで指でたどれる
2. **起動時にクラスパスを走査しない** — コントローラもバッチも自分で登録する
3. **黙って間違えないこと** — 「静かに効かなくなる」状態を潰す
4. **失敗は例外にする** — 戻り値で失敗を返さない（非検査の例外）。壊れ方はコンパイルエラーか例外だけ
5. **一つの実行 = 一つの Context** — `ScopedValue` で渡す。`ThreadLocal` ではない

**機能を足すか迷ったら、この5つで決まる。**

## 落とし穴（実際に踏んだもの）

ここには入口で踏みやすいものだけを置く。**Web・DB・バッチの細かい落とし穴は、それぞれの skill にだけ書いてある**（ここに写さない）。

- **1.x から上げるなら** <https://jimble.io/ja/migrate-2.md> を読み、`./gradlew jimbleCheck` を流す
  （`isError()` や `DBTransaction` などはコンパイルエラーになるので、そこは迷わない）。
  2.2.2 以前から上げるときは、古い Cookie の署名が 2.5.2 から既定で読まれない（`jimble-web` の skill の Cookie の項）
- **`select` は `Optional<Data>`。**`.get()` をそのまま書かない（0件で `NoSuchElementException` → 500）。
  「無ければ 404」は `.orElseThrow(() -> new HttpException(404, "..."))`、「無ければ null」は `.orElse(null)`
- **トランザクションの中で SQL の失敗を `catch` して続けない。**`tx.commit()` が `DB_004` で断り、全部巻き戻る（`jimble-db`）
- **利用者の入力を `getInt` / `getLong` で読む前に検査する。**`"abc"` は `DataConversionException` で **500**（黙って 0 にはならない）。
  先に `rules.validate(db, input);` を通せば 422 で返る
- **JSON の小数は `Double` で返る**（2.5.6 まで `Float`）。`(Float)` でキャストせず `getDouble` / `getBigDecimal` で読む
- **壊れた JSON は `JsonParseException`**（2.6.0〜。区切りの抜け `{"a":1 "b":2}`・値の無いキー・壊れたエスケープも。例外に行と文字の位置が載る）。
  クォートの無いキー・シングルクォート・末尾のカンマ・コメントは読める。数値は 1000 文字・入れ子は 512 段まで
- **JSON に書くと、`LocalDate` は `yyyy/MM/dd`、`Instant` などはサーバーの時間帯で `yyyy/MM/dd HH:mm:ss`、Bean は宣言した順**（2.7.0〜）。
  時差を残すなら `Json.write(value, JsonWriteOptions.builder().keepOffset(true).build())`（`jimble-json`）
- **JSON を Bean / record に読むと、読めない値は `JsonParseException`**（2.7.0〜。`"abc"` → int・`1.5` → int・桁あふれ。場所 `$.items[1].qty` が載る）。
  setter があれば setter に入る。知らないキーは飛ばす。`List<Item>` は `Dson.decodes(json, List.class, Item.class)` か `Json.read(json, new TypeRef<List<Item>>() {})`
- **`Data.put` は `Data` を返さない**（`Map.put` なので前の値）。続けて書くなら `putData("a", 1).putData("b", 2)`
- **リクエストを `apply(Data)` / `setRow(Data)` / `valueRow(Data)` にそのまま渡さない。**隠れた列や `role` を当てられる。
  許す列を渡す形にする：`apply(data, User.name, User.created_at)`（`jimble-db`）
- **`Migration.install()` の戻り値で起動を止めない。**既定（`on_startup = auto`）は**ローカルでは流さず `false` を返す**
  （ローカルは `./gradlew migrate`）。`DBUtil.load(...)` より前に呼ぶ（`jimble-db`）
- **秘密の設定は2行で書く。**既定の行を先に、`${?環境変数}` の行をあとに置く。

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
- **時間と大きさの設定は単位を書く**（`5m` / `200ms` / `10MiB`）。素の数値は起動時に落ちる（`sql_cache.ttl` も 2.5.2 からこの決まりに入った）
- **環境は `-Djimble.env` で決める。**読むファイルだけ替えるなら `-Djimble.conf=application.batch`（2.5.3〜。環境は変えない）
- **フィルタの順は `ApiToken.authenticate` → `Remember.restore` → `Auth::guard` → `session()` に触るフィルタ。**
  `Auth::guard` がセッションを使うかを決めるので、先に `session()` を触ると間に合わない（`jimble-web`）
- **セッションは自動保存されない。**変えたら `context.session().save()`。`session().data()` は読み取り専用（`jimble-web`）
- **ローカルで `cookie.secure = true` のままだと、セッションも CSRF もエラーなしで効かない。**
  `cookie { secure = false }` はローカル用の `application.local.conf` にだけ書く（`jimble-web`）
- **`error(...)` で 500 番台の `cause.getMessage()` を返さない。**DB の誤りや内部のパスが相手に届く
- **`StringUtil.createPassword` / `randomNumberString` は暗号用の乱数**（トークンや初期パスワードに使ってよい）。
  ファイル名に使うなら `FileUtil.safeFileName(名前, 拡張子を含めるか, 置き換える文字)`
- **`/mcp` そのものには認証が無い。**`McpController` を継承したクラスの初期化ブロックに `before(...)` を書く（`jimble-web`）

## モジュール

| | |
| --- | --- |
| `jimble-web` | ルーティング・リクエスト/レスポンス・セッション・CSRF・認証・SSE・WebSocket |
| `jimble-db` | SQL ビルダー・マイグレーション・コード生成・キャッシュ・ロック |
| `jimble-util` | `Data` / `Log` / `Conf` / `Dson` / ハッシュ / メール（`Mailer`） |
| `jimble-core` | `Context` / `Executor` |
| `jimble-batch` `jimble-mq` | バッチとキュー。**MQ は DB のテーブルがキュー**（Kafka も Rabbit も要らない） |
| `jimble-mcp` `jimble-otel` `jimble-batch-manager` | MCP サーバー・分散トレーシング・バッチ管理画面 |
| `jimble-cli` | `jimble new`（雛形）・`jimble version`・migrate・codegen |

Gradle プラグインは `io.jimble.jte`（テンプレート変換）/ `io.jimble.run`（ホットリロード）/
`io.jimble.db`（migrate・codegen）。

## もっと深いところ

同じところに skill が3本ある。**そちらのほうが詳しい。**

| | |
| --- | --- |
| `jimble-db` | SQL ビルダー・`Data` の形・トランザクション・catch するところ・JSON の列・マイグレーション / コード生成・ロック・SQL 結果キャッシュと Redis |
| `jimble-web` | ルーティングとフィルタの効く範囲・入力と出力・アップロード・セッション / CSRF / Cookie・エラー処理・認証（OIDC・二要素・パスキー・API のトークン）・プロキシの後ろ |
| `jimble-batch` | バッチ（登録の順序・中断・チャンク・cron・管理画面）と MQ（DB のテーブルがキュー）・メール送信 |

**DB を触るなら `jimble-db`、ルートやハンドラなら `jimble-web`、バッチや MQ なら `jimble-batch` を、書く前に読む。**

## 書いたら確かめる

```bash
./gradlew jimbleCheck     # jimble の既知の落とし穴と 1.x の書き方を見つける（直し方と引き先つき）
./gradlew jimbleSkills    # jimble の版を上げたら、この skill をその版に揃える
```

- **`jimbleCheck` が `ERROR` を出したら直す。**出力にある「直し方」と「詳しく」の URL を見る（推測で直さない）。
  誤検知なら、その行か前の行に `// jimble-check:ignore J101` と書く
- **アプリ固有の決まりは skill を直さず、プロジェクトの根の `AGENTS.md` に書く。**skill を直すと `jimbleSkills` で揃えられなくなる
- jimble が出す例外や警告の多くには **`詳しく: https://jimble.io/ja/〜.md`** が付いている。そのページを取って読む
- **非推奨（`[removal]` の警告）は書き換える。新しく書くコードで使わない**——
  `selectOrThrow` / `selectListOrThrow` → `select` / `selectList`、`insertNoReturnKey` → `insert`（1件の INSERT なので件数は 1）、
  `DB.isBatchSuccess` と `RedisLockStatus.Failed` は要らない（失敗は例外）。詳しくは https://jimble.io/ja/migrate-2.md
- **`@CheckReturnValue`（`io.jimble.util.annotation`）が付いたメソッドの戻り値は捨てない。**
  値を返すだけのもの（`db.select(...)`・`db.begin()`・`rules.errors(...)`・`Column.as(...)`）で、捨てると何もしていない。
  Error Prone と IntelliJ は捨てた行を指摘する

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
| ログインと認可・ロックアウト・remember-me・OIDC・二要素認証・パスキー・API のトークン | `auth.md` |
| セッション・CSRF・Cookie | `session-security.md` |
| 設定ファイルの全項目・環境・`-Djimble.conf` | `config.md` |
| テンプレート（jte） | `view.md` |
| よくある落とし穴 | `pitfalls.md` |
| プロキシの後ろ（`trust_proxy`・`isSecure()`・HSTS）・リバースプロキシ | `server.md` |
| 検査（バリデーション）・エラー処理 | `validation.md` / `errors.md` |
| アップロード | `upload.md` |
| 流量制限 | `ratelimit.md` |
| SQL 結果キャッシュ・Redis | `cache.md` |
| ログ（`jimble new` の雛形は 2.5.2 でアクセスログを `AsyncAppender` に替えた。前の版で作ったアプリは書き換える） | `log.md` |
| バッチ・MQ | `batch.md` / `mq.md` |
| HTTP クライアント・CSV・XML | `util.md` |
| メール（SMTP） | `mail.md` |
| MCP サーバー | `mcp.md` |
| 考え方の理由 | `principles.md` |

**版で挙動が変わることがある。**手元の版は `jimble version`、
変更は <https://github.com/hidemikimura/jimble/blob/main/CHANGELOG.md> を見る。
