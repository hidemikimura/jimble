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
| `Auth.FULL_AUTH` | `false` | **いまパスワードを入れた人**だけが通れるか（下記） |

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

`Auth.attemptLogin` が、**待たせる → 照合する → 成功なら数えたものを消す**までやります。
`Auth.login` は**セッション ID を振り直してから**入れて、**保存まで**します
（[セッションとセキュリティ](./session-security)）。

> [!TRAP]
> **`PasswordUtil.check` を直に呼ばないでください。**
> ハッシュが `null` のとき**即座に `false` を返す**ので、
> **利用者がいないほうが目に見えて速くなります**（BCrypt は遅いのが仕事です）。
> 応答時間で**どの ID が存在するかを外から数えられます。**
>
> `Auth.attemptLogin` は、相手がいなくても**1回まわしてから** `false` を返します。
> **メッセージも分けないでください**——分けたら時間を合わせた意味がありません。

## 何度も間違えられたとき

`Auth.attemptLogin` は**失敗した回数を数えていて、次の試行を待たせます。**
書くことはありません（上のコードのままです）。

```
失敗 1〜3 回目 … 待たせない（打ち間違い）
4 回目 … 1 秒
5 回目 … 2 秒
6 回目 … 4 秒     …… 上限（既定 300 秒）まで
```

まだ待ち時間が残っていれば **429 と `Retry-After`** を返します
（401 と同じく、画面へ飛ばすかどうかは `error()` が決めます）。
24 時間間が空けば数え直します。

**「N 回で M 分ロック」にはしていません。**
アカウント単位で止める仕組みは**そのまま嫌がらせの道具になる**からです——
わざと間違えるだけで、その人を締め出せます。
待ち時間を倍にしていく形なら、攻撃者から見た試行速度は実質ゼロになり、
**正規の利用者は数秒待つだけ**で済みます。

> [!TRAP]
> **数える単位は「入力されたログイン ID」です。**
> 見つかった利用者の DB 上の ID を渡すと、**居ない ID のときだけ数えられません。**
> 総当たりは居ない ID から始まりますし、
> **「待たされるかどうか」でどの ID が在るかが分かってしまいます。**

| | |
| --- | --- |
| 置き場 | `auth_attempt` テーブル。**DB が無ければ何もしません**（ログに1度だけ出ます） |
| 単位 | ログイン ID（**大小と前後の空白はそろえて** SHA-256 で保存します。平文では残りません） |
| 設定 | `auth.lockout.*`（[設定](./config)） |
| 掃除 | 自動です（失敗を数えたついでに、1時間に1度）。手で呼ぶなら `Lockout.cleanup()` |
| 解除 | `Lockout.clear(loginId)` |

**[流量制限](./ratelimit)の代わりにはなりません。**
流量制限は **IP ごと**なので、1つのアカウントに 1000 個の IP から1回ずつ来ると発火しません。
こちらは**アカウントごと**なので、誰から来ても数えます。**両方掛けてください。**

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

## ログインしたままにする（remember-me）

```java
{
	before(Remember.restore(App::findPrincipal));   // 先に「思い出す」
	before(Auth::guard);                            // そのあと見張る

	post("/password", Password::change).attribute(Auth.FULL_AUTH, true);
}

// id から引き直す。役割をここで引くので、権限を剥奪すればすぐ効く
private static Principal findPrincipal (long id) {
	Data staff = findStaff(id);
	return staff.isEmpty() ? null
		: Principal.of(staff.getLong("id"), staff.getString("name"), staff.getString("role"));
}
```

ログインのときに、**印が付いていたときだけ**覚えます。

```java
Auth.login(context, principal);

if ("1".equals(request.getString("remember"))) {
	Remember.issue(context, principal);
}
```

> [!TRAP]
> **`before(Remember.restore(...))` は `before(Auth::guard)` より先に置いてください。**
> あとに置くと、**guard が「ログインしていない」と決めたあとで思い出す**ことになります。
>
> **いつも `issue` を呼ばないでください。**共用の端末で、**次の人が入れます。**

### Cookie を盗まれても、パスワードは変えられない

**これが remember-me を出せる理由です。**
思い出して戻ってきた人は「いまパスワードを入れた人」ではないので、
`attribute(Auth.FULL_AUTH, true)` を付けたルートでは **401** になります。

パスワードの変更・退会・決済・連絡先の変更に付けてください。
コードの中で見るなら `Auth.fullyAuthenticated(context)` ですが、
**ルート属性のほうが書き忘れが起きません。**

### 盗まれたら気づく

Cookie には **`selector:validator`** の2つが入っていて、
**validator は使うたびに作り直します。**
盗まれた Cookie と本物の Cookie は同時に生きられないので、
**回転前の値が使われたら、それが盗まれた合図**です。

合図を見つけたら、**その利用者の記憶を全部消します**（ログにも残ります）。
先に使ったのが本人か盗んだ側かは**区別できない**ので、
片方だけ消すと**本人だけが締め出されて盗んだ側が残る**ことがあります。

| | |
| --- | --- |
| 置き場 | `auth_remember` テーブル。**DB が無ければ何もしません** |
| Cookie | `selector:validator`。**validator は SHA-256 にして保存**します（selector は引くための鍵なのでそのまま） |
| 期限 | 最後に使ってから 30 日、**かつ**発行から 90 日（使い続けても、いつかは必ず切れます） |
| 猶予 | 回した直後の 60 秒は古いほうも通します（**並列のリクエストで勝手にログアウトさせない**ため） |
| ログアウト | `Auth.logout` が消します |
| パスワード変更 | **`Remember.forgetAll(userId)` を呼んでください**（下記） |
| 設定 | `auth.remember.*`（[設定](./config)） |

> [!TRAP]
> **パスワードを変えたら `Remember.forgetAll(userId)` を呼んでください。**
> 呼ばないと、**盗まれた Cookie はそのまま使えます**——変えた意味がありません。
> 「全端末からログアウト」も同じものです。

## 「Google でログイン」（OpenID Connect）

```java
{
	before(Auth::guard);

	// どちらも「ログインは要らないが、セッションは要る」
	get("/auth/google", Oidc.start("google"))
		.attribute(Auth.PUBLIC, true);

	// 第3引数はコードを入れる画面。二要素認証を使うなら渡す
	get("/auth/google/callback", Oidc.callback("google", App::findOrCreate, "/login/code"))
		.attribute(Auth.PUBLIC, true);
}

// 名乗ってきた相手を、アプリの利用者に結び付ける。入れたくなければ null
private static Principal findOrCreate (OidcUser user) {

	Data staff = findByOidcKey(user.key());        // "google:1234567890"

	return staff.isEmpty() ? null
		: Principal.of(staff.getLong("id"), staff.getString("name"), staff.getString("role"));

}
```

設定は `auth.oidc.<名前>.*`（[設定](./config)）。**`client_secret` は環境変数から**入れてください。
`authorization_endpoint` などは書かなければ **discovery**（`issuer` + `/.well-known/openid-configuration`）で引きます。
**起動時には引きません**——引くと、プロバイダが落ちているあいだアプリが起動しなくなります。

> [!TRAP]
> **`Auth.NO_SESSION` を付けないでください。**`state` も `nonce` も PKCE の検証子も
> セッションに置くので、付けると**戻ってきたときに何も残っていません**。
>
> `Auth.PUBLIC` は要ります（まだログインしていない人が通る道なので）。
> **この2つが別の判断だという話は上のとおりです。**

### 認可コード + PKCE だけ

暗黙フローは持っていません。`start` が **state / nonce / PKCE の検証子**を作ってセッションに置き、
`callback` が突き合わせます。

| | 何を防ぐか |
| --- | --- |
| `state` | **ログイン CSRF。**攻撃者のコードを踏ませて、**被害者を攻撃者のアカウントでログインさせる**手 |
| `nonce` | **ID トークンの使い回し。**1度取れたトークンを何度でも持ち込む手 |
| **PKCE**（S256） | **認可コードの横取り。**検証子を知らないとトークンに換えられません |

`state` は**読んだ時点で捨てます**（1回だけにするのが役目なので）。

### ID トークンの検証

**外部のライブラリを入れていません。**JWKS が返す `n`/`e`（RSA）と `x`/`y`（EC）は
JDK の `KeyFactory` で公開鍵に戻せて、署名の検証も `java.security.Signature` がやります。

見ているのは次のとおりです。**どれも「見ていない」だけで通ってしまう**ところです。

| | |
| --- | --- |
| `alg` | **ヘッダを信じません。**表に載っている RS/ES だけを通します。`none` は「署名が空でも検証したことになる」、`HS256` は**公開鍵を共有鍵として渡すと誰でも署名できる** |
| `kid` | その鍵で検証します。JWKS 側が `alg` を名乗っていれば、それとも一致すること |
| `iss` | **1文字違わず一致。**前方一致にすると `https://accounts.google.com.evil.jp` が通ります |
| `aud` | 自分が入っていること。**複数あれば `azp` も**（別のクライアント宛てを持ち込ませない） |
| `exp` / `iat` / `nbf` | 時計のずれぶんだけ緩めます。**未来に発行されたことになっているものは通しません** |
| `nonce` | こちらが送ったものと一致すること |

**断る理由は返しません。**「nonce が合いません」と返すと、**どこまで通ったかを外から測れます**。
ログにだけ残して、返すのは 401 です。

> [!TRAP]
> **メールが同じでも、既存の利用者に自動で結び付けません。**
> 引くのは `provider + sub` の組（`user.key()`）だけです。
>
> メール一致で自動的に結び付けると、**メールを検証していないプロバイダが1つ混ざるだけで
> アカウント乗っ取りになります**。既存のアカウントに紐付けたいなら、
> **ログイン済みの状態で明示的に「連携する」**操作をさせてください。
>
> `user.emailVerified()` は見られますが、**それを信じるかどうかはアプリの判断**です。

### 二要素認証と一緒に使うとき

**コードを入れる画面のパスを、第3引数で渡してください。**

渡しておくと、二要素認証を有効にしている人が「Google でログイン」から来たときに、
パスワードで入るときと同じように `Mfa.pending` へ倒してその画面へ飛ばします。

> [!TRAP]
> **渡さないと、その人は入れません**（401 になります）。
> 0.6.0 では**黙ってログインさせていた**ので、
> **「Google でログイン」を選ぶだけで二要素が飛んでいました**。
> 素通りさせるくらいなら入れないほうがよい、という判断です（0.6.1）。

### やらないこと

| | |
| --- | --- |
| アクセストークンの保管 | しません。ここは「誰がログインしたか」までです。外部 API を叩くなら、返ってきたトークンをアプリが自分で持ってください（保管・暗号化・失効・スコープの追加同意まで面倒を見ることになります） |
| jimble が認可サーバーになる | ありません |
| ルートを自動で生やす | しません。`get("/auth/google", ...)` と自分で書きます（[原則](./principles)） |

## 二要素認証（TOTP）

パスワードが合ったあと、**まだログインさせずに**コードを待ちます。

```java
// パスワードが合ったあと
if (Mfa.isActive(staff.getLong("id"))) {
	Mfa.pending(context, principal);        // ログインさせない。セッションに預けるだけ
	context.response().redirect("/login/code");
	return;
}

Auth.login(context, principal);
```

```java
// POST /login/code
if (!Mfa.complete(context, request.getString("code"))) {
	context.flash().put("message", "コードが違います");
}

context.response().redirect(Mfa.isPending(context) ? "/login/code" : "/");
```

`complete` が true を返したときは、**中で `Auth.login` まで済んでいます。**

> [!TRAP]
> **コードを入れるまでは「ログインしていない」扱いです。**
> `Auth.principal(context)` は `Principal.ANONYMOUS` を返し、
> `/login/code` 以外のルートは通れません。
>
> つまり **`/login/code` には `Auth.PUBLIC` が要ります**（まだログインしていない人が通る道なので）。
> `Auth.NO_SESSION` は付けないでください——途中の人はセッションに預けてあります。

### 登録

```java
Mfa.Enrollment enrollment = Mfa.enroll(staffId, "member1@example.com");

// enrollment.uri() を QR にして見せる（otpauth://totp/...）
// enrollment.recoveryCodes() は「この一度だけ」見せる
```

**`enroll` だけでは有効になりません。**
認証アプリに入れてもらってから、出てきたコードで `Mfa.activate(staffId, code)` を呼びます。

```java
if (!Mfa.activate(staffId, request.getString("code"))) {
	context.flash().put("message", "コードが合いません。もう一度お試しください");
}
```

> [!TRAP]
> **2段階にしてあるのは、締め出さないためです。**
> `enroll` の時点で有効にすると、**QR の読み取りに失敗した人が二度と入れなくなります。**

### 秘密鍵は暗号化して持ちます

**`auth.mfa.secret_key` が設定されていなければ、`enroll` は例外を投げて断ります。**

TOTP の秘密鍵は、パスワードのハッシュとは違います。
ハッシュは漏れても解くのに手間がかかりますが、**秘密鍵は漏れたらその場でコードが作れます。**
平文で持つと、「2要素を入れてあるのに、DB が漏れたら全員突破される」ものになります。

> [!TRAP]
> **`cipher.key` を流用しないでください。**
> `cipher.*` を書くと、**`hash.password.encrypt` も書かないと起動しません**。
>
> **いま平文の BCrypt を保存しているアプリが、二要素認証のために `cipher.key` を
> 設定して `encrypt = true` と書くと、保存済みのパスワードが「暗号化済み」として読まれ、
> 全員入れなくなります。** 返るのは「IDかパスワードが違います」だけです。
> **鍵を分けてあるのはこのためです**——二要素には `auth.mfa.secret_key` を使ってください。

### 回復コード

認証アプリを入れた端末は失くします。回復コードはそのための逃げ道です。

| | |
| --- | --- |
| 出る場所 | `enroll` の戻り値だけ。**DB には SHA-256 しか残りません** |
| 数 | 10 個（`auth.mfa.recovery_codes`） |
| 使い方 | コードの欄にそのまま入れます。`verify` が認証アプリ → 回復コードの順に見ます |
| 使ったら | **消えます。**1つは1回だけです |
| 残りの数 | `Mfa.remainingRecoveryCodes(userId)` |

**残りが 0 になっても知らせません。**画面に出すかどうかはアプリの判断です。

### 総当たりへの備え

| | |
| --- | --- |
| 待たせる | `Lockout` と同じ仕掛けです（鍵は `mfa:<利用者 ID>`）。**6桁は 100 万通りしかないので、抑えないと1日で当たります** |
| 待たせ方 | 3回までは待たせず、そのあと 1 → 2 → 4 … 秒。上限 300 秒。超えたら **429** |
| 窓 | 前後1つ（`auth.mfa.window`）。**広げるほど当たりも増えます**——10 にすると当たりが 21 通りぶんになります |
| 使い回し | **一度通ったコードは、その窓が終わるまで通りません。**同じコードを覗き見て入れ直す手を止めます |
| 猶予 | パスワードが通ってからコードまで 300 秒（`auth.mfa.pending`）。過ぎたら**はじめからやり直し**です |

### やめるとき

```java
post("/mfa/disable", Mfa2::disable).attribute(Auth.FULL_AUTH, true);
```

> [!TRAP]
> **`Mfa.disable` を呼ぶ前に、本人であることを確かめてください。**
> ここが緩いと、**Cookie を盗んだ側が2要素を外せます**——入れた意味がなくなります。
> `attribute(Auth.FULL_AUTH, true)` を付けて、**いまパスワードを入れた人だけ**にしてください。

### 仕様と、やらないこと

| | |
| --- | --- |
| 方式 | TOTP（RFC 6238 / RFC 4226）。HMAC-SHA1、6桁、30 秒。**認証アプリが揃って読めるのがこれだけ**だからです |
| 置き場 | `auth_mfa` と `auth_mfa_recovery`。**DB が要ります**（無ければ `enroll` は例外） |
| QR 画像 | 作りません。`enrollment.uri()` を渡すので、画面側で描いてください（依存を増やさないため） |
| SMS / メール | ありません。SMS は SIM の乗っ取りで抜かれます |
| WebAuthn / パスキー | まだありません |
| 「信頼した端末」 | ありません。remember-me が近いことをしますが、**別のものです**（あちらはパスワードの代わり） |


動いているものは `examples/approval-auth` にあります（ログイン → コード → 登録 → 解除まで）。

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
| 「あと何日で切れるか」を利用者に見せる口 | ありません。行を自分で引いてください |
| JWT | **出しません。**失効できず鍵の管理が増えます。API の認証が要るなら、DB に持つ不透明なトークンにしてください |
| SAML | まだありません |
| 権限（permission）の対応表 | 役割の文字列だけです |

動いているものは `examples/approval-auth` にあります。
