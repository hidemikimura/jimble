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

		error((context, cause, statusCode) -> {
			if (cause instanceof ValidationException) {
				return;                            // 422 の本文 {"validation": {...}} は枠組みが付ける
			}
			// 500 番台は中身を返さない（DB の誤りや内部のパスが届く）。中身はログで見る
			context.response().code(statusCode).json("error"
				, statusCode < 500 ? cause.getMessage() : "サーバーで問題が起きました");
		});
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

**`Request` は `Data` ではない。**`context.request().getString("x")` は書けない（コンパイルエラー）。
値は `bodyAll()` / `body()` / `bodyQuery()` などから読む（`query()` はクエリの生の文字列）。
本文が `application/json` なのに読めなければ、`bodyAll()` などが **400 の `HttpException`** を投げる。

入れ子は `user.name=taro` でも `user[name]=taro` でも同じ。
`items[0].price` は添字、`items[].price` は末尾に追加。
**添字は 10,000 まで**（1回のリクエストで伸ばせる配列は合わせて 100,000 要素まで）。超えると 400（2.2.2）。

無いキー（キーが無い・空欄）は `getString` なら `null`、`getInt` なら `0`、`getBoolean` なら `false`。
「無い」と「0」を分けたいなら `getIntObject` か `isNull(key)`、既定値は `getInt("page", 1)`。
**あるのに読めない値（`"abc"`、int に `"1.5"`、真偽に `"yes"`）は `DataConversionException` → 500。**
黙って 0 にはならない。**利用者の入力は、getter で読む前に検査を通す**（下の「検査」）。
`paging()` は枠組みが寛容に読む（`?page=abc` は1ページ目）。

属性を決めた `Cookie` は `cookies().putSigned(cookie)` / `putUnsigned(cookie)` で書く（`put(Cookie)` は無い）。

## 検査（バリデーション）

```java
new ValidationRules()
	.put(Post.title, new ValidationRule().required().textLengthMax(100))
	.put(Post.category_id, new ValidationRule().required().integer(1, 9999))
	.validate(db, input);                  // 通らなければ ValidationException（422）。戻り値は無い

long categoryId = input.getLong(Post.category_id);   // 検査を通したあとなら、読めない値で 500 にならない
```

- **`validate(...)` は `void`。**通らなければ `ValidationException`（`HttpException` の子、422）を投げ、
  枠組みが `{"validation": {項目: [メッセージ]}}`（と入力値）で返す。自分で `if` を書かない
- **エラーの一覧が欲しいときだけ `errors(db, input)`**（`Data`。空なら通った）。本文を自分で作るならこちら
- 明細などの一覧は `validate(db, list)` / `errors(db, list)`（エラーのある行だけ、1始まりの `index` つき。`validate` の 422 は `{"rows": [...]}` を包む）
- **`required()`（= `empty()`）はキーが送られてこなくても失敗する。**ほかの規則は空を通すので、必須には `required()` を積む。
  **空の配列（`{"name": []}`）も「値が無い」として見る**（2.2.3。それまでは1度も検証されず、必須も素通りした）
  「登録のときだけ必須、更新は送られた項目だけ見る」は `insertRequired()` と `insertRequestChecker(...)`
- `ValidationExecutor`（ルートに積む検査）も同じ 422 の形。こちらは `addError(...)` で積み、`error` は通らず `onCancel` で返す

## 返す

```java
context.response().send("text");                   // text/plain
context.response().json("posts", list);            // 組み立てる。送信は実行の終わり
context.response().view("blog/posts.jte");
context.response().redirect("/");                  // 302
context.response().code(201).send();
```

`json()` は重ねて呼ぶと1つの JSON に足されていく。
**`view()` のページは、`Accept: application/json` で来てもテンプレートを描く**（2.2.3。それまではテンプレートに渡したデータを丸ごと JSON で返していた）。
JSON も返す口にしたいなら `json(...)` で別に返す（元に戻すなら `template.json_fallback = true`）。
**既定で `X-Content-Type-Options: nosniff`・`X-Frame-Options: SAMEORIGIN`・`Referrer-Policy` を付ける**（`security_headers`。自分で決めたヘッダは上書きしない）。
別のサイトの iframe に入れるページは、そのルートで `setResponseHeader("X-Frame-Options", ...)` を決めるか `security_headers.frame_options = ""`。
**返し方を2種類積む（`json(...)` と `redirect(...)` など）と `IllegalStateException`。**同じ種類を重ねるのはよい。
**そのあとに `send()` は書かない**——組み立てておけば、
ディスパッチャが実行の終わりに送る（`error` ハンドラの中でも同じ）。
サンプルはどれも `json()` で終わっている。
明示するのは**本文なしで終わらせたいとき**だけ（`code(204).send()`）。

大きいものは `context.response().outputStream()` に直接書く。
**呼んだ時点でステータス・Cookie・Cache-Control が決まる**ので、`code(...)` やヘッダはその前に（送ったあとのヘッダ・Cookie は例外）。
書いたものは `flush()` するまで溜まる——届いたそばから見せたいなら書くたびに `flush()`。
`InputStream` は `send(in, "型")` に渡せば、続きが来ていないところで送り出す（中継に使える）。

## セッション・CSRF・Flash

```java
context.session().put("staff_id", id);
context.session().save();                  // ← 呼ばないと書かれない
```

**自動保存はしない。**変えたら `save()`。`save()` のあとに変えても、もう一度 `save()` すれば保存される。
保存し忘れると、リクエストの終わりに「save() が呼ばれていません」と WARN が出る。

**書くのは `session().put(...)` / `remove(...)`。**`session().data()` は**読み取り専用の写し**で、
`session().data().put(...)` は `UnsupportedOperationException`。`destroy()` のあとの `put` / `remove` / `clear` は `IllegalStateException`。
保存先は `application.conf` の `session.store`（`none` / `db` / `redis` / `cookie`。既定 `none`）。

**ログインが通ったら `regenerateId()`。**呼ばないと、
ログイン前に仕込まれた ID がそのまま権限を持つ（セッション固定化）。
中身は持ち越すが**保存はしないので `save()` まで書く**。
`Auth.login` を使えば振り直しと保存までやる。

CSRF は `before` に `Csrf::verify`。`GET` `HEAD` `OPTIONS` `TRACE` は素通し。
トークンは `Csrf.token(context)` を hidden（`csrf_token`）に入れる（`context.request().csrfToken()` は送られてきたヘッダを読むだけで、発行しない）。読むのはヘッダ（`X-CSRF-Token`）→ フォーム → JSON の本文。**クエリ文字列では受けない**。
`csrf.bind_session = true` でトークンをセッションに置き、ログインで作り直す。新しいトークンは応答の `X-CSRF-Token` ヘッダで返るので、**SPA はそれで差し替える**（しないとログイン後の POST が 403）。`session.store = "none"` では使えない（例外）。

Flash は**次の1回のリクエストだけ**残る。読んだ時点で消える。

**署名つき Cookie の署名は Cookie の名前に結びついている**（2.2.3）。自分で署名するなら `Cookies.sign(名前, 値)`（`Cookies.sign(値)` は非推奨）。
署名は有効期限を含まない。**利用者を表す値を署名つき Cookie だけで信じない**（セッションに入れる）。

**`session.store = "cookie"` はログアウトしても、盗まれた写しまでは無効にできない**（サーバーに何も置かないため）。
最後に使ってから `session.timeout`、発行から `session.absolute_timeout`（既定 1 日）で切れる（2.2.3）。
`db` / `redis` は **`session.absolute_timeout` を書いたときだけ**発行からの上限が効く（2.2.4。書かなければ上限なし＝使い続ければ延び続ける）。
すぐに無効にしたいなら `db` / `redis` にするか、`Auth.revoke(id)` を使う。

## エラー

```java
throw new HttpException(401, "ログインしてください");
```

| 例外 | コード |
| --- | --- |
| `HttpException` | `statusCode()` の値 |
| `ValidationException` | 422（`validate(...)` が投げる） |
| `NotFoundException` | 404 |
| `SqlExecuteException` などそのほか | **500** |

`error(...)` は**画面へ飛ばすか JSON かをアプリが決める**ところ。
枠組み側（`Auth.guard` など）は投げるだけ。

**`CodeException` は HTTP のコードに効かない**（非検査。投げれば 500）。
**`validate(...)` の `ValidationException` は `error` を通る。**`error` で本文を組むと、枠組みの
`{"validation": ...}` は付かない——上の例のように素通しするか、`e.errors()` を自分で返す。
**`ValidationExecutor` の失敗は `error` を通らない**（`cancel()` で抜けて `onCancel` が 422 を返す）。見た目を変えるなら `onCancel` を見る。

自分で `AbstractExecutor` を書くなら、**`cancel()` はそこで抜ける**（後ろの行は走らない。枠組みが `onCancel` を呼ぶ）。

## ログインと認可

```java
before(Remember.restore(App::findPrincipal));   // remember-me を使うなら先に
before(Auth::guard);                            // これを「いちばん最初に近く」

// ログインの種別が複数ある（別の表から ID を引く）なら、remember-me も種別を渡す（D-183）
// before(Remember.restore("operator", Ops::findStaff));
// Remember.issue(context, principal, "operator");

// 締め出す（2.1。F-W-33）。remember-me の記憶も一緒に消える
// Auth.revokeOthers(context);          // パスワードを変えたあと：いまの端末だけ残す
// Auth.revoke(id);  Auth.revoke("operator", id);   // 管理画面・バッチから：全部終わらせる

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
if (!Mfa.complete(context, context.request().bodyAll().getString("code"))) {   // 通れば中で Auth.login まで済む
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
- 回復コードは **`enroll` の戻り値でしか見られない**（DB には `auth.mfa.secret_key` を鍵にした HMAC だけ）。使うと消える
- **登録し直し（もう有効な人の `enroll`）は控えに置かれ、新しいコードで `activate` が通るまで、いまの設定が効く**（2.2.4）。途中でやめても外れない
- `Mfa.disable` は **`attribute(Auth.FULL_AUTH, true)` を付けたルートから**呼ぶ
- 総当たりは `Lockout` が抑える（超えると 429）。**一度通ったコードは再利用できない**
- QR 画像は作らない（`enrollment.uri()` を画面側で描く）。SMS / メールは無い
- **ログインの種別が複数ある（運用者と利用者など、別の表から ID を引く）なら種別を渡す**：
  `Mfa.isActive("operator", id)` / `Mfa.pending(context, principal, "operator")` /
  `Mfa.enroll("operator", id, 名前)` など。渡さないと**同じ数字の ID が同じ人として扱われ、秘密鍵を上書きし合う**。
  `complete()` は pending で預けた種別で確かめる（種別は渡さない）。OIDC の3引数の `Oidc.callback(名前, 関数, 画面)` は、
  **そのルートの `Auth.REALM` の二要素認証を見る**（2.2.3。それまでは種別なしを見て、種別つきのルートでは二要素が素通りした）。
  別の種別を見るなら4引数で渡す
- **ログインを自分で書くなら、`Lockout.waitSeconds` → 確かめる → `Lockout.fail` の順にしない**（同時に送られると素通りする）。
  **`Lockout.attempt(key)` で、試す前に数える**（0 なら試してよい。成功したら `Lockout.clear(key)`）。`Auth.attemptLogin` はこれを使っている

## 落とし穴（実際に踏んだもの）

- **1.x のコードを直すなら** <https://jimble.io/ja/migrate-2.md> を読み、`./gradlew jimbleCheck` を流す
  （`request().getString(...)` / `cookies().put(cookie)` / `router.path("/x")` はコンパイルエラーになる）
- **利用者の入力を検査せずに `getInt` / `getLong` で読まない。**`?id=abc` で `DataConversionException` → **500**。
  先に `rules.validate(db, input);` を通せば 422 で返る。キーが無い・空欄は 0 のままなので、必須は `required()` で落とす
- **`Data errors = rules.validate(...)` と書かない。**`validate` は `void`（通らなければ投げる）。一覧は `errors(...)`
- **部分更新（PATCH）の規則に `required()` を付けると、送られてこない項目で 422 になる。**
  「登録のときだけ必須」は `insertRequired()`（登録かどうかは `insertRequestChecker(...)` で決める）
- **`error(...)` が `ValidationException` にも本文を組むと、422 の `{"validation": ...}` が消える**（上の「エラー」）
- **`session().data().put(...)` は例外。**`session().put(...)`（そして `save()`）
- **`Auth::guard` はいちばん最初に登録する。**セッションを使うかどうかをここで決めるので、
  先に誰かが `session()` を触ると間に合わない
- **`PUBLIC` と `NO_SESSION` は別の判断。**ログインの入口は
  「ログインは要らないが**セッションは要る**」。一緒にすると**誰もログインできなくなる**
  （302 は返るのに次のリクエストで 401 になる）
- **`after` で Cookie やヘッダを足せない。**応答を送ったあとに走るので例外になる（`after` の中なのでログに出るだけで、届かない）。
  `before` かハンドラの中で足す
- **`destroy()` のあとにセッションを変えると `IllegalStateException`。**ログアウトの処理は `destroy()` を最後に
- **`json(...)` と `redirect(...)` のように返し方を2種類積むと `IllegalStateException`。**どちらかに決めてから書く
- **`paging()` のあとに `paging(50)` を呼ぶと `IllegalStateException`。**件数を変えるなら最初から `paging(50)`
- **`send()` を2回呼ぶとエラー。**`after` や `error` の中では `isSent()` を見てから触る
- **確定後にフィルタを足すと落ちる**（「足したのに効かない」を作らないため）。
  ルート定義は初期化ブロックの中で完結させる
- **`env=local` で `cookie.secure = true` のままだと**、ブラウザが Cookie を返さず
  セッションも CSRF もエラーなしで効かなくなる（起動時に WARN が出る）。
  `cookie { secure = false }` は **`application.local.conf` にだけ**書く（`application.conf` に書くと本番も引き継ぐ）
- **ロードバランサの後ろで `trust_proxy = true` にするなら、`X-Forwarded-For` は右から読む**（2.2.3）。
  中継が2段以上なら `server.trusted_proxies`（IP / CIDR）を書く。`CF-Connecting-IP` / `X-Real-IP` は
  `server.client_ip_header` に書いたときだけ信じる。`RateLimit.perIp` はここで決めた IP で数える
- **`new Cors().addAllowOrigin("*")` と `allowCredentials(true)` は一緒に使えない**（例外。2.2.3）。資格情報つきなら、オリジンを並べる
- **SPA の書き換え（`SpaRewriter`）で差し込む値は `SpaRewriter.escapeHtml(...)` を通す。**パスの値はデコード済みで、そのまま HTML になる
- **`ReverseProxy` は `.` / `..` を含むパスを 400 で断る**（2.2.2）。転送先の `Host` やヘッダを決めるなら `preserveHost()` /
  `setHeader(...)`、`Location` の書き換えは既定で入る（`noRedirectRewrite()` で外す）（2.2.0）
- **`BasicAuth` は、成功したらセッション ID を作り直し、失敗を `Lockout` で数える**（2.2.3）
- **`/mcp` には認証が無い。**`McpController` を継承したクラスの初期化ブロックに `before(...)` を書く。
  **別のブロックの `path("/mcp", () -> before(...))` は効かない**（フィルタは書いたブロックのルートにしか付かない）
- **パスワードを変えたら `Auth.revokeOthers(context)`。`Remember.forgetAll` だけではほかの端末のセッションが残る**
  （消えるのは remember-me の記憶だけ）。管理画面から止めるなら `Auth.revoke(id)`。**これからのログインは止めない**ので、
  盗まれたアカウントは先にパスワードを変えるか止めてから。**役割を剥奪したときも `revoke`**（役割はログイン時にセッションへ写すので残る）。
  複数台ではほかの台で効くまで最大 5 秒（`auth.revocation.cache_ttl`）。張りっぱなしの WebSocket は切れない
- **同じブラウザで別々にログインさせたいなら、ブロックに `Auth.REALM` を付ける**（1.4.0。D-185）。
  ログイン・コード・ログアウトの口も同じブロックに置く。remember-me の種別も同じ名前にする（`issue` は違うと例外、`restore` は違うと何もしない。ブロックには同じ名前の `restore` と `Auth::guard` をブロックの中に置く。アプリ全体の guard はブロックの restore より先に走る）
- **`AssetHandler` は `.js` / `.css` / `.woff` / `.woff2` を1年キャッシュさせる**（`public,max-age=31536000,immutable`）。
  ファイル名にハッシュが入っているかは見ず、**拡張子だけ**で決める。`app.js` の中身を変えて置き直しても、
  一度読んだブラウザは**1年取りに来ない**（`immutable` なので、ふつうのリロードでは確かめにも来ない）。
  ファイル名にハッシュを入れる（`app.9f3a1c.js`）か、`<script src="/assets/app.js?v=2">` のように参照を変える。
  開発中は `application.local.conf` などで `assets.immutable_max_age = 0s`（**単位を書く。素の `0` は起動時に落ちる**）。
  そのほかの拡張子は `assets.max_age`（既定 `0s`）＋ `must-revalidate` で、毎回確かめに来る

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
| プロキシの後ろ（`trust_proxy`）・リバースプロキシ | <https://jimble.io/ja/server.md> |
| MCP サーバー | <https://jimble.io/ja/mcp.md> |
| ファイルアップロード | <https://jimble.io/ja/upload.md> |
| SSE / WebSocket | <https://jimble.io/ja/sse.md> / <https://jimble.io/ja/websocket.md> |
| よくある落とし穴 | <https://jimble.io/ja/pitfalls.md> |

## 設定の値には単位を書く（要件 D-159）

時間と大きさは **単位を値に書く**（`session.timeout = 30m` / `upload.max_file_size = 10MiB`）。
**素の数値は起動時に落ちる。**`MB` は 1000 の3乗、`MiB` は 1024 の3乗。

`db.<名前>` は **snake_case**（`maximum_pool_size` / `idle_timeout` / `schema`）。
**表に無いキーを書くと起動時に落ちる**（camelCase の古い綴りは警告つきで読む）。
