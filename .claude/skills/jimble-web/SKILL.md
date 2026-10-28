---
name: jimble-web
description: jimble の Web 層を書くときに最初に使う。ルートを並べる（初期化ブロック・コントローラの install）、フィルタと属性が「書いた場所」にしか効かないこと、大文字小文字と正規の URL、確定後にフィルタを足さないなどの全体の決まりと、入力（jimble-web-input）・返す/エラー/静的ファイル（jimble-web-response）・セッション/CSRF/Cookie（jimble-web-session）・ログインと認可（jimble-web-auth・jimble-web-auth-methods・jimble-web-api-token）・プロキシ/流量制限/MCP（jimble-web-server）のどの skill を読むかの案内。
---

# jimble の Web 層

**注釈は無い。**ルートは初期化ブロックに並べる。

```java
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import io.jimble.web.context.WebContext;      // ハンドラの引数。Context ではない
import io.jimble.web.http.HttpException;
import io.jimble.web.router.AttributeKey;
import io.jimble.web.auth.Auth;
import io.jimble.web.auth.Principal;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.validation.ValidationRules;
import io.jimble.web.validation.ValidationRule;
import io.jimble.web.validation.ValidationException;   // 422（HttpException の子）
import io.jimble.util.data.Data;
```

## ルートを並べる

```java
public class App extends JimbleApp {

	{
		before(Auth::guard);

		get("/posts",      PostController::list);
		get("/posts/{id}", PostController::detail);
		post("/posts",     PostController::create);

		path("/admin", () -> {
			attribute(Auth.ROLE, "admin");
			install(AdminController::new);         // 走査しない。自分で登録する
		});

		error(...);                                // 返し方は jimble-web-response の「エラー」
	}

	public static void main (String[] args) {
		Bootstrap.load();                          // ← アプリ側のクラス。枠組みには無い
		JimbleServer.start(new App());
	}

}
```

`Bootstrap` は**アプリが自分で書くもの**（設定と DB の読み込みをまとめた入口）。
枠組みのクラスではないので、import 先を探さないこと。

コントローラは `Controller` を継承し、初期化ブロックにルートを書いて `install(X::new)`。
`Router` を直接受け取って書くところでは `router.path("/admin", admin -> { ... })`（ブロックの形だけ。`router.path("/admin")` は無い）。

**確定後にフィルタを足すと落ちる**（「足したのに効かない」を作らないため）。ルート定義は初期化ブロックの中で完結させる。

## フィルタと属性は「書いた場所」に付く。パスには付かない

```java
path("/admin", () -> {
	before(AdminController::requireAuth);
	get("/users", ...);              // 効く
	install(GroupController::new);   // 効く（中のルートも全部）
});

path("/admin", () -> {
	get("/login", ...);              // 効かない。別のブロック
});
```

**パスが同じでも、別のブロックで登録したルートには効かない。**
「`/admin` 配下は全部認証」にしたいなら、`/admin` のルートを1か所にまとめる。
逆に、**誰かが別の場所で `/admin/...` を足しても、知らないうちに捕まらない。**

ブロックの中では **`before` を書いた位置とルートの位置の前後は関係ない**（ブロック全体に効く）。

属性も同じ。強い順に **ルート > 内側のブロック > 外側のブロック > キーの既定値**。

```java
path("/docs", () -> {
	attribute(PUBLIC, true);                            // ブロック全部
	get("/guide", Guide::show);
	get("/me", Me::show).attribute(PUBLIC, false);      // ここだけ上書き
});
```

`AttributeKey` は既定値を持つので、**`null` の判定が要らない**。
**キーは作ったインスタンスそのもの**なので、`static final` で1つだけ持って使い回す
（同じ名前で別に `new` したものは別のキー。同名が2つあると `seal()` で落ちる）。

```java
static final AttributeKey<Boolean> NO_AUTH = new AttributeKey<>("no_auth", false);
...
if (context.route().route().attribute(NO_AUTH)) { return; }
```

**未マッチ（404）で呼ばれるのは、いちばん外側の `error` だけ。**

ルートの絶対パスのパターンは `context.route().route().fullPattern()`（`path("/admin", ...)` の中の `/users/{id}` なら `/admin/users/{id}`。2.5.11）。
`pattern()` は `path()` の中の相対のまま。

**`after` で Cookie やヘッダを足せない。**応答を送ったあとに走るので例外になる（`after` の中なのでログに出るだけで、届かない）。
`before` かハンドラの中で足す。

### 大文字小文字と正規の URL（`router.ignore_case` / `router.redirect_to_canonical`）

末尾と連続のスラッシュは前から無視する（`/a/b` `/a/b/` `/a//b` は同じルート）。どちらの設定も既定 `false`。

- **`router.ignore_case = true` にすると `/ADMIN` が `/admin` のルートに当たる。**`request().path()` は打たれたとおりの綴りを返すので、
  **パスの文字列で判定する before や前段の ACL はすり抜ける**。判定はルートの属性か `fullPattern()` で書く
- **`router.redirect_to_canonical = true` を一緒に入れる**と、登録した綴りへ 301 で寄せる（アプリには1つの綴りしか来ない）
- **`path()` の中のルートの寄せ先は 2.5.10 まで壊れていた**（`/ADMIN/USERS/1` → `/users/USERS/1`。D-304）。両方使うなら 2.5.11 以上

## 全体の決まり

- **1.x のコードを直すなら** <https://jimble.io/ja/migrate-2.md> を読み、`./gradlew jimbleCheck` を流す
  （`request().getString(...)` / `cookies().put(cookie)` / `router.path("/x")` はコンパイルエラーになる）
- **設定の値には単位を書く**（`session.timeout = 30m` / `upload.max_file_size = 10MiB`）。素の数値は起動時に落ちる（`jimble-config`）

## どの skill を読むか

**書く前に、該当する skill を読む。**落とし穴（実際に踏んだもの）は、それぞれの skill に分けて書いてある。

| やること | skill |
| --- | --- |
| 入力を読む（`bodyAll` など）・入れ子と上限・アップロード・検査（`ValidationRules`）・`paging()` | `jimble-web-input` |
| 返す（`json` / `view` / `redirect`・`send`・`outputStream`）・エラー（`error(...)`・例外とコード）・静的ファイル・SPA | `jimble-web-response` |
| セッション（`save()`）・CSRF・Flash・Cookie の署名・`cookie.secure` | `jimble-web-session` |
| ログインと認可（フィルタの順・`Auth` の属性・remember-me・締め出し・`Auth.REALM`・ロックアウト・パスワードのハッシュ） | `jimble-web-auth` |
| 二要素認証（TOTP）・パスキー・「Google でログイン」（OIDC） | `jimble-web-auth-methods` |
| API のトークン（`Authorization: Bearer`・スコープ・マルチテナントの Host の照合） | `jimble-web-api-token` |
| プロキシの後ろ（`trust_proxy`）・リバースプロキシ・送り元の IP・流量制限・CORS・アクセスログ・MCP | `jimble-web-server` |

## 詳しいことは引く

**推測で書かない。**

| | |
| --- | --- |
| ルーティング・フィルタ・属性・コントローラ | <https://jimble.io/ja/routing.md> |
| テンプレート（jte） | <https://jimble.io/ja/view.md> |
| SSE / WebSocket | <https://jimble.io/ja/sse.md> / <https://jimble.io/ja/websocket.md> |
| メール（SMTP。送るのは MQ から。`jimble-mq` の skill） | <https://jimble.io/ja/mail.md> |
| よくある落とし穴 | <https://jimble.io/ja/pitfalls.md> |
