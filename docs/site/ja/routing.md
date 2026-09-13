---
title: ルーティング
summary: get / post / path / before / after / error とコントローラの分け方
section: 基本
order: 1
---

# ルーティング

## 定義するところ

`JimbleApp` を継承したクラスの初期化ブロックが、ルート定義です。

```java
public class App extends JimbleApp {

	{
		get("/hello", context -> context.response().send("hello"));
	}

	public static void main (String[] args) {
		JimbleServer.start(new App());
	}

}
```

使えるメソッドは `get` `post` `put` `patch` `delete` `head` `options` `trace`、
それと全部に登録する `any` です。すべて小文字です。

## パスパラメータ

```java
get("/users/{id}", context ->
	context.response().send(context.route().variables().get("id")));
```

- `{id}` は1区切りぶん。`%2F` を含んでいても1つの値として取れます
- `*` はワイルドカード。`context.route().variables().wildcard()` で残り全部が取れます

同じパスに複数当たる場合は、**より具体的なほうが勝ちます**
（`/users/me` は `/users/{id}` より先）。
順番は **固定パス > パスパラメータ > ワイルドカード**で、
同じ階層にパスパラメータが複数あるときは**登録した順**に試します。

**同じパス・同じメソッドを2度登録すると、その場で例外になります。**
起動してから気づくより早いほうがよいからです。

### 一生呼ばれないルート

重複ではないのに、**どんなリクエストでも他が先に当たる**ことがあります。

```java
get("/users/{id}", ...);
get("/users/{userId}", ...);   // ← 一生呼ばれない
```

名前が違うだけなので登録は通りますが、`/users/5` は必ず1本目に当たります。
**起動時に見つけて警告します。**

```
WARN  ルート: GET     /users/{userId} は一生呼ばれません（GET     /users/{id} が先に当たります）
```

警告は起動ログの何十行にも紛れるので、**CI では例外にしてください**。

```conf
server {
	strict_routes = true
}
```

なお `/users/me` が `/users/{id}` より先に当たるのは正しい動きなので、警告しません
（`{id}` は `me` 以外のすべてで呼ばれます）。見ているのは**1本も来ないもの**だけです。

### メソッドだけ違うとき

パスは合っていてメソッドだけ違うときは、404 ではなく **405** が返り、`Allow` が付きます。

```
$ curl -i -X POST http://localhost:9000/hello
HTTP/1.1 405 Method Not Allowed
Allow: GET
```

## パスの揺れ

**末尾のスラッシュと連続したスラッシュは、前から無視します。**
`/a/b` `/a/b/` `/a//b` `//a/b` は**すべて同じルート**です。

ただし**無視するだけなので、同じ内容が複数の URL で 200 を返します**。
キャッシュも検索エンジンも前段の ACL も、それらを**別の URL として数えます**。
1つに寄せたいときは:

```conf
router {
	redirect_to_canonical = true    # /a/ や //a を /a へ 301
}
```

> [!NOTE]
> **寄せるのは `GET` と `HEAD` だけです。**
> `POST` を 301 で返すと、**ブラウザが本文を落として `GET` に化けます**——
> 送ったつもりの登録が消えるので、`POST` はそのまま受けます。
> クエリ文字列は付けたまま寄せます。

**大文字小文字を区別しないこともできます。**

```conf
router {
	ignore_case = true              # /ADMIN も /admin のルートに当たる
}
```

見ないのは**ルートに書いた固定の部分だけ**です。
`/Users/{id}` なら `Users` のところだけを同一視し、**`{id}` に入る値はそのまま渡します**——
大文字を含む ID（Hashids など）を勝手に小文字にしないためです。

> [!WARN]
> **前段と食い違うことがあります。**
> `/ADMIN` が `/admin` のルートに当たるようになるので、
> **プロキシの ACL や `before` フックがパス文字列で判定していると、そちらだけすり抜けます**。
> `redirect_to_canonical` を一緒に入れてください——
> **ルートに書いた綴りへ 301 で寄せる**ので、アプリには1つの綴りしか来なくなります。

> [!NOTE]
> **綴りだけ違うルートを2本書くと、起動時に落ちます**（`ignore_case` が有効なときだけ）。
> `/Admin` と `/admin` の両方があると**どちらに当たるか決められない**うえ、
> **片方にだけ認証を書いていたら緩いほうに当たりえます**。

`context.request().path()` は、**ルーターが見たのと同じ形**（スラッシュを直したもの）を返します。
受け取ったままのパスが要るときは `rawPath()` を使ってください。

## まとめる

```java snippet=csrf-form
```

`path()` の中で登録したものには、同じ `before` が付きます。
`path()` は入れ子にできます。

## フィルタ

| 登録 | いつ走るか |
| --- | --- |
| `before(handler)` | ハンドラの前 |
| `after(handler)` | ハンドラの後（レスポンスを送る前） |
| `error(handler)` | 例外が出たとき・どのルートにも当たらなかったとき |

`before` の中で例外を投げると、そこで止まって `error` に行きます。
認証はここでやります。

```java
static void requireAuth (WebContext context) {

	if (!"secret".equals(context.request().header().getString("x-token"))) {
		throw new HttpException(401, "認証が必要です");
	}

}
```

## フィルタの効く範囲

**フィルタは「書いた場所」に付きます。パスには付きません。**
効くのは、同じブロックで登録したルートと、そこから `path()` / `install()` で
ネストしたものだけです。

```java
path("/admin", () -> {
	before(AdminController::requireAuth);
	get("/users", ...);          // ← 効く
	install(GroupController::new);   // ← 効く（中のルートも全部）
});

path("/admin", () -> {
	get("/login", ...);          // ← 効かない（別のブロック）
});
```

パスが同じでも、**別のブロックで登録したルートには効きません。**
だから「`/admin` 配下は全部認証」を成り立たせたいときは、
`/admin` のルートを1か所にまとめてください。
逆に言うと、**誰かが別の場所で `/admin/...` を足しても、
知らないうちにフィルタに捕まることがありません。**

ブロックの中では、`before` を書いた位置とルートを書いた位置の前後は関係ありません。
ブロック全体に効きます。

フィルタはルートごとに**起動時に1度だけ**組み立てます。
そのため、**確定した後にフィルタを足すと落ちます**（「足したのに効かない」を作らないため）。
ルート定義はコントローラの初期化ブロックの中で完結させてください。

**未マッチ（404）のときに呼ばれるのは、一番外側の `error` だけ**です。
どのルートにも当たっていないので、内側のブロックが決まりません。

## ルートごとの印

フィルタを1本だけ外したいときは、注釈ではなく**属性**を使います。

```java
static final AttributeKey<Boolean> NO_AUTH = new AttributeKey<>("no_auth", false);

get("/health_check", context -> context.response().send()).attribute(NO_AUTH, true);
```

```java
if (context.route().route().attribute(NO_AUTH)) {
	return;
}
```

`AttributeKey` は既定値を持ちます。付いていないルートでは既定値が返るので、
`null` の判定が要りません。

> [!TIP]
> **キーは「作ったインスタンスそのもの」です。**
> `static final` で1つだけ持って、それを使い回してください。
> 同じ名前で別に `new` したものは**別のキー**なので、
> 片方に入れてもう片方から読むと既定値が返ります。
>
> **枠組みのキー（`Auth.PUBLIC` など）と同じ名前で作っても、枠を奪うことはありません。**
> ただし**ログでは見分けが付かない**ので、
> 同じ名前のキーが2つあると**起動時に落ちます**——名前を変えてください。

### ブロック単位で付ける

**ルート1本ずつに書かなくて済みます。**`attribute()` をブロックに書くと、
そのブロックの中のルート全部に付きます。

```java
path("/docs", () -> {

	attribute(PUBLIC, true);          // ← このブロックは全部公開

	get("/guide", Guide::show);       // 公開
	get("/faq",   Faq::show);         // 公開
	get("/me",    Me::show).attribute(PUBLIC, false);   // ここだけ要ログイン

});
```

強い順に **ルート > 内側のブロック > 外側のブロック > キーの既定値**です。

**書く場所は問いません。**ブロックの最後に書いても、その前に登録したルートに付きます
（`before` と同じで、配るのは起動時の確定のときです）。

> [!TRAP]
> **フィルタと同じで、パスには付きません。**効くのは
> **同じブロックで登録したルート**と、そこから `path()` / `install()` で
> ネストしたものだけです。パスが同じでも、別のブロックで登録したルートには付きません。
>
> ```java
> path("/docs", () -> {
> 	attribute(PUBLIC, true);
> 	get("/guide", ...);        // ← 付く
> });
>
> path("/docs", () -> {
> 	get("/internal", ...);     // ← 付かない（別のブロック）
> });
> ```
>
> **付いているかどうかがコードを読んで分かる**ようにするためです
> （[フィルタの効く範囲](#フィルタの効く範囲)と同じ決まり）。

`rateLimit()` も、この仕組みの上に乗っています
（[流量制限](./ratelimit)）。

## コントローラに分ける

1ファイルが長くなったら `Controller` に切り出します。

```java
public class PostController extends Controller {

	{
		get("/posts", context -> context.response().json("posts", listPosts()));
	}

}
```

```java
install(PostController::new);
```

`install()` に渡すのは**インスタンスではなくコンストラクタ参照**です。
走査はしないので、`install()` を書かないと登録されません。
起動ログのルート一覧で確認してください。

## エラーの扱い

```java snippet=error-handler
```

`HttpException(404, "...")` を投げると、そのステータスで `error` に入ります。
`error` を複数登録すると、登録した順に呼ばれます。
どれかがレスポンスを送ったら、そこで止まります。

ステータスコードの決まり方、未マッチ（404）の扱い、検証失敗との違いは
[エラー処理](./errors) にまとめてあります。

## 実装済みのルートを内側から呼ぶ

登録済みのルートは、HTTP を通さずに呼べます。

```java
CallResponse response = context.dispatcher().call(context
	, CallRequest.of("GET", "/api/posts").query("page", "2"));

Data json = response.json();
```

**通常のリクエストとまったく同じ道を通ります。**
`before` / `after` / エラーハンドラ / [レートリミット](./ratelimit) が効き、
違うのは送り先だけです（ネットワークに出す代わりに `CallResponse` で受け取ります）。

**内側は外側のリクエストの続きとして走ります。**
ヘッダ・Cookie・セッション・Flash は外側のものをそのまま使い、
実行 ID も引き継ぐのでログは 1 本に繋がります。
個別に変えたいヘッダは `.header("X-Token", "...")` で上書きしてください。

内側で発行した Cookie も、外側のレスポンスに載って相手に届きます。

> [!trap]
> **内側は外側とは別のトランザクションです。**
> DB 接続もコンテキストごとに持つので、
> 外側で開けたトランザクションの中には入りません。
> まとめてコミットしたい処理は、ドメイン層で共有してください。

いちばんの用途は、[MCP](./mcp) から API をそのまま公開することです。
`RouteTool` を使うと、ルート 1 本がそのままツールになります。
