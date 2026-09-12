---
name: jimble-web
description: jimble の Web 層を書くときに使う。ルーティングとフィルタの効く範囲、入力の読み方と返し方、セッション・CSRF・Flash、エラー処理、認証（ロックアウト・remember-me・OIDC・二要素認証）、実際に踏んだ落とし穴を含む。
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

		error((context, cause, statusCode) ->
			context.response().code(statusCode).json("error", cause.getMessage()));
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

## 入力を読む

| | 中身 |
| --- | --- |
| `bodyPath()` | パスパラメータ（`/users/{id}`） |
| `bodyQuery()` | クエリ文字列 |
| `bodyForm()` | フォーム / multipart |
| `bodyJson()` | JSON の本文 |
| `bodyAll()` | 全部重ねたもの（**path → query → form → json** の順で上書き） |

```java
Data input = context.request().bodyAll();
String title = input.getString("title");
```

入れ子は `user.name=taro` でも `user[name]=taro` でも同じ。
`items[0].price` は添字、`items[].price` は末尾に追加。

無いキーは `getString` なら `null`、`getInt` なら `0`、`getBoolean` なら `false`。
「無い」と「0」を分けたいなら `getIntObject` か `isNull(key)`。

## 返す

```java
context.response().send("text");                   // text/plain
context.response().json("posts", list);            // 組み立てる。送信は実行の終わり
context.response().view("blog/posts.jte");
context.response().redirect("/");                  // 302
context.response().code(201).send();
```

`json()` は重ねて呼ぶと1つの JSON に足されていく。
**そのあとに `send()` は書かない**——組み立てておけば、
ディスパッチャが実行の終わりに送る（`error` ハンドラの中でも同じ）。
サンプルはどれも `json()` で終わっている。
明示するのは**本文なしで終わらせたいとき**だけ（`code(204).send()`）。

大きいものは `context.response().outputStream()` に直接書く。

## セッション・CSRF・Flash

```java
context.session().put("staff_id", id);
context.session().save();                  // ← 呼ばないと書かれない
```

**自動保存はしない。**`save()` は1リクエストにつき1回だけ効く。
保存先は `application.conf` の `session.store`（`none` / `db` / `redis` / `cookie`。既定 `none`）。

**ログインが通ったら `regenerateId()`。**呼ばないと、
ログイン前に仕込まれた ID がそのまま権限を持つ（セッション固定化）。
中身は持ち越すが**保存はしないので `save()` まで書く**。
`Auth.login` を使えば振り直しと保存までやる。

CSRF は `before` に `Csrf::verify`。`GET` `HEAD` `OPTIONS` `TRACE` は素通し。
トークンは `context.request().csrfToken()` を hidden に入れる。

Flash は**次の1回のリクエストだけ**残る。読んだ時点で消える。

## エラー

```java
throw new HttpException(401, "ログインしてください");
```

| 例外 | コード |
| --- | --- |
| `HttpException` | `statusCode()` の値 |
| `NotFoundException` | 404 |
| そのほか全部 | **500** |

`error(...)` は**画面へ飛ばすか JSON かをアプリが決める**ところ。
枠組み側（`Auth.guard` など）は投げるだけ。

**`CodeException` は HTTP のコードに効かない**（投げれば 500）。
**検証（`ValidationExecutor`）の失敗は `error` を通らない**——
例外を投げずに 422 を返すので、見た目を変えるなら `onCancel` を見る。

## ログインと認可

```java
before(Remember.restore(App::findPrincipal));   // remember-me を使うなら先に
before(Auth::guard);                            // これを「いちばん最初に近く」

get("/requests", RequestController::list);                              // 既定で要ログイン
get("/approvals", Approval::list).attribute(Auth.ROLE, "approver");     // 役割つき
post("/password", Password::change).attribute(Auth.FULL_AUTH, true);    // 要パスワード再入力
```

| 属性 | 既定 | 何を決めるか |
| --- | --- | --- |
| `Auth.PUBLIC` | `false` | ログインが要らないか |
| `Auth.ROLE` | `""` | 要る役割 |
| `Auth.NO_SESSION` | `false` | セッションをまったく使わないか |
| `Auth.FULL_AUTH` | `false` | いまパスワードを入れた人だけか |

**既定は「閉じている」。**書き忘れたルートは開かない。
`Auth.principal(context)` は **`null` を返さない**（未ログインなら `Principal.ANONYMOUS`）。
アクセサは record 形式（`me.id()` / `me.name()` / `me.hasRole("x")`）。

### 「Google でログイン」（OIDC）

```java
get("/auth/google", Oidc.start("google")).attribute(Auth.PUBLIC, true);
get("/auth/google/callback", Oidc.callback("google", App::findOrCreate, "/login/code"))
	.attribute(Auth.PUBLIC, true);   // 第3引数はコードを入れる画面
```

`findOrCreate` は `OidcUser` を受け取って `Principal` を返す（入れないなら `null`）。
**引くのは `user.key()`（`provider:sub`）だけ**——メールが同じでも自動では結び付けない。

- **`Auth.NO_SESSION` を付けない。**state も nonce も PKCE の検証子もセッションに置く
- 設定は `auth.oidc.<名前>.*`。`client_secret` は環境変数から
- 認可コード + PKCE のみ。**暗黙フローは無い**
- ID トークンの検証は枠組みがやる（`alg` はヘッダを信じない・`iss` は完全一致・`aud`/`azp`・`exp`/`iat`・`nonce`）
- **断る理由は返さない**（401 だけ。どこまで通ったかを測らせない）
- **二要素認証を使うなら第3引数を渡す。**渡さないと、二要素を有効にしている人は 401 で入れない
  （0.6.0 は黙って入れていた＝OIDC だと二要素が飛んでいた）

### 二要素認証（TOTP）

```java
// パスワードが合ったあと
if (Mfa.isActive(staffId)) {
	Mfa.pending(context, principal);        // ログインさせない。セッションに預けるだけ
	context.response().redirect("/login/code");
	return;
}

Auth.login(context, principal);
```

```java
// POST /login/code（Auth.PUBLIC が要る。NO_SESSION は付けない）
if (!Mfa.complete(context, request.getString("code"))) {   // 通れば中で Auth.login まで済む
	context.flash().put("message", "コードが違います");
}
```

登録は `Mfa.enroll(利用者 ID, 表示名)` → **QR を見せる** → `Mfa.activate(利用者 ID, code)`。
**`enroll` だけでは有効にならない**（読み取りに失敗した人を締め出さないため）。

- **コードを入れるまでは「ログインしていない」。**`Auth.principal` は `ANONYMOUS`
- **`auth.mfa.secret_key` が無ければ `enroll` は例外。**秘密鍵は暗号化して持つ
- **`cipher.key` を流用しない。**`cipher.*` を書くと **`hash.password.encrypt` も書かないと起動しない**。
  `true` にすると**保存済みの BCrypt が「暗号化済み」として読まれ、全員入れなくなる**。
  二要素には `auth.mfa.secret_key` を使う
- 回復コードは **`enroll` の戻り値でしか見られない**（DB にはハッシュだけ）。使うと消える
- `Mfa.disable` は **`attribute(Auth.FULL_AUTH, true)` を付けたルートから**呼ぶ
- 総当たりは `Lockout` が抑える（超えると 429）。**一度通ったコードは再利用できない**
- QR 画像は作らない（`enrollment.uri()` を画面側で描く）。SMS / メールは無い

## 落とし穴（実際に踏んだもの）

- **`context.request().getString("x")` はコンパイルが通って `null` を返す。**
  `Request` も `Data` なので通ってしまう。`bodyAll()` を通す
- **`Auth::guard` はいちばん最初に登録する。**セッションを使うかどうかをここで決めるので、
  先に誰かが `session()` を触ると間に合わない
- **`PUBLIC` と `NO_SESSION` は別の判断。**ログインの入口は
  「ログインは要らないが**セッションは要る**」。一緒にすると**誰もログインできなくなる**
  （302 は返るのに次のリクエストで 401 になる）
- **`after` で Cookie を足しても遅い。**応答を送ったあとに走るので、ヘッダはもう出ている
- **`save()` は1リクエストに1回。**`before` で先に保存すると、
  そのリクエストの本来の保存が黙って捨てられる
- **`send()` を2回呼ぶとエラー。**`after` や `error` の中では `isSent()` を見てから触る
- **確定後にフィルタを足すと落ちる**（「足したのに効かない」を作らないため）。
  ルート定義は初期化ブロックの中で完結させる
- **`env=local` で `cookie.secure = true` のままだと**、ブラウザが Cookie を返さず
  セッションも CSRF もエラーなしで効かなくなる（起動時に WARN が出る）
- **OIDC のコールバックで `session().save()` を自分で呼ばない。**保存は1リクエストに1回で、
  先に呼ぶと**そのあとの `Auth.login` の保存が黙って捨てられる**（「入れたのに次で 401」）

## 詳しいことは引く

**推測で書かない。**

| | |
| --- | --- |
| ルーティング・フィルタ・属性・コントローラ | <https://jimble.io/ja/routing.md> |
| 入力と出力 | <https://jimble.io/ja/request-response.md> |
| エラー処理 | <https://jimble.io/ja/errors.md> |
| セッション・CSRF・Cookie・鍵の入れ替え | <https://jimble.io/ja/session-security.md> |
| ログイン・認可・ロックアウト・remember-me・OIDC・二要素認証 | <https://jimble.io/ja/auth.md> |
| 検証とページング | <https://jimble.io/ja/validation.md> |
| テンプレート（jte） | <https://jimble.io/ja/view.md> |
| 静的ファイル・SPA | <https://jimble.io/ja/assets.md> |
| 流量制限 | <https://jimble.io/ja/ratelimit.md> |
| ファイルアップロード | <https://jimble.io/ja/upload.md> |
| SSE / WebSocket | <https://jimble.io/ja/sse.md> / <https://jimble.io/ja/websocket.md> |
| よくある落とし穴 | <https://jimble.io/ja/pitfalls.md> |

## 設定の値には単位を書く（要件 D-159）

時間と大きさは **単位を値に書く**（`session.timeout = 30m` / `upload.max_file_size = 10MiB`）。
**素の数値は起動時に落ちる。**`MB` は 1000 の3乗、`MiB` は 1024 の3乗。

`db.<名前>` は **snake_case**（`maximum_pool_size` / `idle_timeout` / `schema`）。
**表に無いキーを書くと起動時に落ちる**（camelCase の古い綴りは警告つきで読む）。
