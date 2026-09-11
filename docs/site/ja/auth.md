---
title: ログインと認可
summary: before 1行と、ルート属性だけ。既定は「閉じている」
section: 基本
order: 9
---

# ログインと認可

**注釈はありません。**`before` を1行と、ルートに付ける属性だけです。

```java
public class App extends JimbleApp {

	{
		before(Auth::guard);                                    // これだけ

		path("/public", () -> {
			attribute(Auth.PUBLIC, true);                       // ブロックごと公開
			attribute(Auth.NO_SESSION, true);
			get("/guide", Guide::show);
		});

		get("/login",  Login::show).attribute(Auth.PUBLIC, true);
		post("/login", Login::submit).attribute(Auth.PUBLIC, true);

		get("/requests",  RequestController::list);             // 既定で要ログイン
		get("/approvals", ApprovalController::list).attribute(Auth.ROLE, "approver");
	}

}
```

`before(Auth::guard)` は**いちばん最初に**登録してください。
セッションを使うかどうかをここで決めるので、
**先に誰かが `session()` を触ると間に合いません。**

## 既定は「閉じている」

`Auth.PUBLIC` の既定は `false`（＝ログインが要る）です。

**ルートを足した人が何も書かなければ閉じています。**
逆にすると、書き忘れたルートが黙って開きます。
どちらも「書き忘れ」ですが、**倒れる先が違います。**

## ルート属性

| 属性 | 既定 | 何を決めるか |
| --- | --- | --- |
| `Auth.PUBLIC` | `false` | ログインが要らないか |
| `Auth.ROLE` | `""` | 要る役割。空なら問わない |
| `Auth.NO_SESSION` | `false` | セッションをまったく使わないか |

ブロックに書けば、その中のルート全部に付きます（[ルーティング](./routing)）。
1本だけ違うなら、そのルートで上書きします。

> [!TRAP]
> **`PUBLIC` と `NO_SESSION` は別の判断です。**
> ログインの入口は**ログインが要らないが、セッションは要ります**——
> CSRF トークンも、ログイン後のセッションも、そこで持つからです。
>
> 一緒にすると**ログイン画面自身がセッションを持てず、誰もログインできなくなります**
> （302 は返るのに、次のリクエストで 401 になります。実際に踏みました）。
>
> `NO_SESSION` を付けてよいのは、**本当に公開のページだけ**です。

## ログインさせる

```java
Data staff = findStaff(loginId);

if (!Auth.checkPassword(password, staff.isEmpty() ? null : staff.getString("password_hash"))) {
	context.flash().put("message", "ログインIDかパスワードが違います");
	context.response().redirect("/login");
	return;
}

Auth.login(context, Principal.of(
	staff.getLong("id"), staff.getString("name"), staff.getString("role")));

context.response().redirect("/me");
```

`Auth.login` は**セッション ID を振り直してから**入れて、**保存まで**します
（[セッションとセキュリティ](./session-security)）。

> [!TRAP]
> **`PasswordUtil.check` を直に呼ばないでください。**
> ハッシュが `null` のとき**即座に `false` を返す**ので、
> **利用者がいないほうが目に見えて速くなります**（BCrypt は遅いのが仕事です）。
> 応答時間で**どの ID が存在するかを外から数えられます。**
>
> `Auth.checkPassword` は、相手がいなくても**1回まわしてから** `false` を返します。
> **メッセージも分けないでください**——分けたら時間を合わせた意味がありません。

## いま誰か

```java
Principal me = Auth.principal(context);

me.id();                  // 0 なら未ログイン
me.name();
me.hasRole("approver");
```

**`null` は返りません。**ログインしていなければ `Principal.ANONYMOUS` です。

`Principal` が持つのは **id / 表示名 / 役割の3つだけ**です。
利用者のオブジェクトを丸ごとセッションに入れると、

- DB で名前を直しても**ログインし直すまで古いまま**
- 権限を剥奪しても**セッションが切れるまで効かない**
- Cookie セッションなら、**その全部がブラウザへ出ていく**

残りは要るときに DB から引いてください。引くのが重いなら[キャッシュ](./cache)の仕事です。

## ログアウト

```java
Auth.logout(context);
```

**セッションを丸ごと捨てます。**ログインの鍵だけ消すと、
買い物かごや下書きが**次の利用者に見えます**（共用の端末で効きます）。

## 401 と 403 の返し方

`Auth.guard` は `HttpException` を投げるだけです。
**画面へ飛ばすか JSON を返すかは、アプリの `error()` が決めます。**

```java
error((context, cause, statusCode) -> {

	if (statusCode == 401 && !context.request().acceptJson()) {
		context.response().redirect("/login");
		return;
	}

	context.response().code(statusCode).json("error", cause.getMessage());

});
```

**役割が足りないときは 403 で、401 ではありません。**
ログインし直しても結果が変わらないことを、状態コードで言います。
401 を返すと、利用者は**入り直せば見られると思って何度も試します。**

## Basic 認証

運用向けの口には Basic 認証が使えます（[リクエストとレスポンス](./request-response)）。

```java
path("/ops", () -> {
	before(BasicAuth.of("ops", System.getenv("OPS_PASSWORD")));
	attribute(Auth.PUBLIC, true);      // セッションのログインとは別の仕組み
	get("/whoami", Ops::whoami);
});
```

## やらないこと

| | |
| --- | --- |
| 注釈（`@PreAuthorize` のようなもの） | [原則](./principles)のとおり使いません。ルート属性で宣言します |
| ロックアウト・remember-me | まだありません |
| JWT | **出しません。**失効できず鍵の管理が増えます。API の認証が要るなら、DB に持つ不透明なトークンにしてください |
| OAuth / OIDC / SAML | まだありません |
| 権限（permission）の対応表 | 役割の文字列だけです |

動いているものは `examples/approval-auth` にあります。
