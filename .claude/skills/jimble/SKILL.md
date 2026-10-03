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

**ハンドラの引数は `WebContext`。**`Context`（`io.jimble.core`）は別物で、
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
| `application.properties` | `application.conf`（HOCON） |

## DB の書き方

**組み立てと実行が分かれている。**ビルダーは自分では走らない。

```java
DB db = MyExample.db();                       // codegen が作る入口

Data row = db.select(SQL.select()             // select は Optional<Data>。0件は空
	.from(Request.instance())
	.where(Request.id.eq(id)))
	.orElseThrow(() -> new HttpException(404, "申請がありません: " + id));

db.insert(SQL.insert(Notice.instance())       // insert(テーブル).value(列, 値)。戻り値は void
	.value(Notice.request_id, id)
	.value(Notice.created_at, Dsl.now()));   // 採番値が要るなら long id = db.insertKey(...)

int updated = db.update(SQL.update(Request.instance())   // update / delete は件数
	.set(Request.status, "approved")
	.where(Request.id.eq(id)));
```

**失敗は例外（`SqlExecuteException`、非検査）。**`isError()` は無い。
書かなければ上まで飛んで 500、トランザクションの中なら巻き戻る。
分けたいのは一意制約くらいなので、それだけ `catch (DuplicateKeyException e)` で受ける。
**非検査なのでコンパイラは catch を求めない。**利用者の入力を一意制約のある列に入れるところ（メールアドレス・ログイン ID など）では
必ず `DuplicateKeyException` を受けて 409 などにする。どこで何を受けるかの表は `jimble-db` の skill の「catch するところ」。

**列は静的フィールドで、名前は DB のまま**（`Request.decided_by`。camelCase ではない）。

トランザクションはこう書く。

```java
try (Tx tx = db.begin()) {

	// ... db.insert / db.update ...   失敗は例外 → try を抜けて巻き戻る

	if (!"pending".equals(row.getString(Request.status))) {
		throw new HttpException(409, "もう決まっています");   // 投げれば巻き戻る。rollback は書かない
	}

	tx.commit();           // 確定して終わる

}
```

短くするなら `db.transaction(tx -> { ... });`、値を返すなら `db.transactionResult(tx -> ...)`。
**トランザクションはこの3つだけ**（`DBTransaction` や `db.beginTransaction()` は無い）。

## 守っている5つの決めごと

1. **上から順に追えること** — `main` から目的の処理まで指でたどれる
2. **起動時にクラスパスを走査しない** — コントローラもバッチも自分で登録する
3. **黙って間違えないこと** — 「静かに効かなくなる」状態を潰す
4. **失敗は例外にする** — 戻り値で失敗を返さない（非検査の例外）。壊れ方はコンパイルエラーか例外だけ
5. **一つの実行 = 一つの Context** — `ScopedValue` で渡す。`ThreadLocal` ではない

**機能を足すか迷ったら、この5つで決まる。**

## 落とし穴（実際に踏んだもの）

- **1.x から上げるなら** <https://jimble.io/ja/migrate-2.md> を読み、`./gradlew jimbleCheck` を流す
  （`isError()` や `DBTransaction` などはコンパイルエラーになるので、そこは迷わない）
- **`select` は `Optional<Data>`。**`.get()` をそのまま書かない（0件で `NoSuchElementException` → 500）。
  「無ければ 404」は `.orElseThrow(() -> new HttpException(404, "..."))`、「無ければ null」は `.orElse(null)`
- **トランザクションの中で SQL の失敗を `catch` して続けない。**続けても `tx.commit()` が
  `TransactionException`（`DB_004`）で断り、全部巻き戻る。「あれば更新」は Tx の外で `DuplicateKeyException` を受けるか、
  ビルダーの `onDuplicateKeyUpdate(列, 値)`（MySQL / PostgreSQL の違いは吸収する）で書く
- **利用者の入力を `getInt` / `getLong` で読む前に検査する。**`"abc"` や `"1.5"` は `DataConversionException` で **500** になる
  （黙って 0 にはならない）。先に `rules.validate(db, input);` を通せば 422 で返る。
  **キーが無い・空文字は 0 / null のまま**（例外にならない）
- **非推奨の `selectOrThrow` / `selectListOrThrow` / `insertNoReturnKey` を書かない。**
  `select` / `selectList` / `insert`（件数が要るなら `execute`）で同じことになる
- **ビルダーで組んだ SELECT の結果はテーブル名でネストされている。**
  文字列のキーで引くと何も取れない（`null`）。**列オブジェクトを渡せばそのまま引ける** →
  `row.getString(Staff.name)`（`row.getString("name")` は `null`）
- **文字列の SQL（`db.select("SELECT ...")`）の結果はネストしない。**平らな Data が返り、
  `row.getData(Staff.instance())` は `null`。結合すると同じ名前の列（`id` など）は**黙ってあとの値で上書き**される。
  別名を付けるか、`テーブル__列` の別名でネストさせる（`jimble-db` の skill）
- **`DuplicateKeyException` の import を間違えない。**`org.springframework.dao` ではなく `io.jimble.db`
- **JSON の列（MySQL の `JSON` / PostgreSQL の `json`・`jsonb`）は読んだ時点で `Data` / `List` になっている。**
  配列の列を `getString` すると**先頭の要素だけ**が返る（JSON の文字ではない）。配列は `getStringList` / `getDataList`、
  文字のまま欲しいなら `CAST(列 AS CHAR)`（MySQL）/ `列::text`（PostgreSQL）で読む
- **一番外が配列の JSON は `Dson.decodes(json, List.class)` で読む。**`Data.class`（や引数なし）だと
  `{"0": ..., "1": ...}` になる。渡した文字が JSON でなければ `JsonParseException`（空文字と `"null"` は `null`）
- **`Data.put` は `Data` を返さない。**`Map.put` なので戻り値は**前の値**（`Object`）。
  `new Data().put("a", 1).put("b", 2)` はコンパイルが通らず、`return data.put("x", v);` は Data ではなく前の値（初めて入れたなら `null`）を返す。
  続けて書くなら **`putData("a", 1).putData("b", 2)`**（こちらは自身を返す）
- **`Migration.install()` の戻り値は「登録したか」で、流れたかではない。**`DBUtil.load(...)` より前に呼び、
  実際に流れるのは `load` の中。`false` は `migration.on_startup = false` のときだけなので、落とす判定に使わない。
  `DBUtil.load(...)` は `void` で、繋がらない・流せないときは例外で起動が止まる（`if` で囲まない）
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
- **セッションは自動保存されない。** 変えたら `context.session().save()` を明示的に呼ぶ。
  書くのは `session().put(...)`。**`session().data()` は読み取り専用の写し**で、`put` すると `UnsupportedOperationException`
- **`Auth::guard` はいちばん最初に登録する。** セッションを使うかどうかをここで決めるので、
  先に誰かが `session()` を触ると間に合わない
- **`env=local` で `cookie.secure = true` のままだと**、ブラウザが Cookie を返さず、
  セッションも CSRF もエラーなしで効かなくなる（起動時に WARN が出る）。
  **`cookie { secure = false }` はローカル用の `application.local.conf`（1行目に `include "application.conf"`）にだけ書く。**
  `application.conf` に書くと本番も引き継ぐ（ローカルでない環境で false なら起動時に WARN）
- **`error(...)` で 500 番台の `cause.getMessage()` を返さない。**DB の誤り（重複したキーの値）や内部のパスが相手に届く
- **リクエストを `apply(Data)` / `setRow(Data)` / `valueRow(Data)` にそのまま渡さない。**どの列名でも受け付けるので、
  隠れた列を当てられたり（`?where[users][password_hash|starts_with]=...`）、`role` を足されたりする。
  **許す列を渡す形**にする：`apply(data, User.name, User.created_at)` / `setRow(form, User.nickname)`（ほかの列は `SqlBuildException`）
- **1つの値を書く場所（`eq` / `set` / `value` など）にリストや配列を渡すと `SqlBuildException`**（2.2.2）。
  JSON の配列や `a[]=` はそのままリストになるので、検査で弾くか `in(...)` を使う
- **`a.or(b).and(c)` は `(a OR b) AND c`**（左から読んだとおり。2.2.3）。`a OR (b AND c)` は `a.or(b.and(c))` か `Dsl.anyOf`
- **`view()` のページは、`Accept: application/json` でもテンプレートを描く**（2.2.3。テンプレートに渡したデータを丸ごと返さない）。
  1つの口で JSON も返したいなら `json(...)` で別に返す
- **`IPUtil.inRange(対象, 範囲)` の対象は IP の字面だけ**（2.2.4。名前は DNS で引かず、範囲外）。送り元の IP は `context.request().address()` を渡す
- **`StringUtil.createPassword` / `randomNumberString` は暗号用の乱数**（2.2.4）。トークンや初期パスワードに使ってよい。
  ファイル名に使うなら `FileUtil.safeFileName`（`..` や点だけの名前は置き換える）
- **`/mcp` そのものには認証が無い。**`McpController` を継承したクラスの初期化ブロックに `before(...)` を書く
  （`Origin` の検査はブラウザしか止めない。**別のブロックの `path("/mcp", () -> before(...))` は効かない**）

## モジュール

| | |
| --- | --- |
| `jimble-web` | ルーティング・リクエスト/レスポンス・セッション・CSRF・認証・SSE・WebSocket |
| `jimble-db` | SQL ビルダー・マイグレーション・コード生成・キャッシュ・ロック |
| `jimble-util` | `Data` / `Log` / `Conf` / `Dson` / ハッシュ / メール（`Mailer`） |
| `jimble-core` | `Context` / `Executor` |
| `jimble-batch` `jimble-mq` | バッチとキュー。**MQ は DB のテーブルがキュー**（Kafka も Rabbit も要らない） |
| `jimble-mcp` `jimble-otel` `jimble-batch-manager` | MCP サーバー・分散トレーシング・バッチ管理画面 |

Gradle プラグインは `io.jimble.jte`（テンプレート変換）/ `io.jimble.run`（ホットリロード）/
`io.jimble.db`（migrate・codegen）。

## もっと深いところ

同じところに skill が3本ある。**そちらのほうが詳しい。**

| | |
| --- | --- |
| `jimble-db` | SQL ビルダー・`Data` の形・トランザクション・マイグレーション / コード生成 |
| `jimble-web` | ルーティングとフィルタの効く範囲・入力と出力・セッション / CSRF・エラー処理・認証 |
| `jimble-batch` | バッチ（登録の順序・中断・チャンク）と MQ（DB のテーブルがキュー） |

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
  `selectOrThrow` / `selectListOrThrow` → `select` / `selectList`、`insertNoReturnKey` → `insert`（件数が要るなら `execute`）、
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
| ログインと認可・ロックアウト・remember-me | `auth.md` |
| セッション・CSRF・Cookie | `session-security.md` |
| 設定ファイルの全項目 | `config.md` |
| テンプレート（jte） | `view.md` |
| よくある落とし穴 | `pitfalls.md` |
| プロキシの後ろ（`trust_proxy`）・リバースプロキシ | `server.md` |
| HTTP クライアント・CSV・XML | `util.md` |
| メール（SMTP） | `mail.md` |
| MCP サーバー | `mcp.md` |
| 考え方の理由 | `principles.md` |

**版で挙動が変わることがある。**手元の版は `jimble version`、
変更は <https://github.com/hidemikimura/jimble/blob/main/CHANGELOG.md> を見る。
