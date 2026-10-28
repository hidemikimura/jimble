---
name: jimble-web-auth-methods
description: jimble の Web でパスワード以外のログインの方法を書くときに使う。二要素認証（TOTP。Mfa.pending / complete / enroll / activate・回復コード・ログインの種別）、パスキー（Passkey・rp_id・登録は FULL_AUTH から・SaaS の PasskeyRp）、「Google でログイン」（OIDC。Oidc.start / callback・版による引数・二要素の画面を渡す）を含む。
---

# jimble の二要素認証・パスキー・OIDC

全体の決まりと、ほかの skill への案内は `jimble-web`。ログインと認可の基本は `jimble-web-auth`。

## 二要素認証（TOTP）

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
- **`auth.mfa.secret_key` が無ければ `enroll` は例外。**秘密鍵は暗号化して持つ（`cipher.key` を流用しない。`jimble-web-auth`）
- 回復コードは **`enroll` の戻り値でしか見られない**（DB には `auth.mfa.secret_key` を鍵にした HMAC だけ）。使うと消える
- **登録し直し（もう有効な人の `enroll`）は控えに置かれ、新しいコードで `activate` が通るまで、いまの設定が効く**（2.2.4）。途中でやめても外れない
- `Mfa.disable` は **`attribute(Auth.FULL_AUTH, true)` を付けたルートから**呼ぶ
- 総当たりは `Lockout` が抑える（超えると 429）。**一度通ったコードは再利用できない**
- QR 画像は作らない（`enrollment.uri()` を画面側で描く）。SMS / メールは無い
- **ログインの種別が複数ある（運用者と利用者など、別の表から ID を引く）なら種別を渡す**：
  `Mfa.isActive("operator", id)` / `Mfa.pending(context, principal, "operator")` /
  `Mfa.enroll("operator", id, 名前)` など。渡さないと**同じ数字の ID が同じ人として扱われ、秘密鍵を上書きし合う**。
  `complete()` は pending で預けた種別で確かめる（種別は渡さない）

## 「Google でログイン」（OIDC）

```java
get("/auth/google", Oidc.start("google")).attribute(Auth.PUBLIC, true);
get("/auth/google/callback", Oidc.callback("google", App::findOrCreate, "/login/code"))
	.attribute(Auth.PUBLIC, true);   // 第3引数はコードを入れる画面
```

**2引数・3引数の `Oidc.callback` は 2.5.2 以上が要る**（2.2.3〜2.5.1 はルートを登録したところで必ず例外。D-269）。
それより前の版なら4引数（`Oidc.callback("google", 関数, "/login/code", "")`）で種別を渡す。

`findOrCreate` は `OidcUser` を受け取って `Principal` を返す（入れないなら `null`）。
**引くのは `user.key()`（`provider:sub`）だけ**——メールが同じでも自動では結び付けない。

- **`Auth.NO_SESSION` を付けない。**state も nonce も PKCE の検証子もセッションに置く
- 設定は `auth.oidc.<名前>.*`。`client_secret` は環境変数から
- 認可コード + PKCE のみ。**暗黙フローは無い**
- ID トークンの検証は枠組みがやる（`alg` はヘッダを信じない・`iss` は完全一致・`aud`/`azp`・`exp`/`iat`・`nonce`）
- **断る理由は返さない**（401 だけ。どこまで通ったかを測らせない）
- **二要素認証を使うなら第3引数を渡す。**渡さないと、二要素を有効にしている人は 401 で入れない
  （0.6.0 は黙って入れていた＝OIDC だと二要素が飛んでいた）
- 3引数の `Oidc.callback(名前, 関数, 画面)` は、**そのルートの `Auth.REALM` の二要素認証を見る**（2.2.3。それまでは種別なしを見て、種別つきのルートでは二要素が素通りした）。
  別の種別を見るなら4引数で渡す

## パスキー（パスワードなしのログイン。2.4.0）

```java
post("/passkey/register/options", c -> c.response().json(Passkey.registrationOptions(c, loginId))).attribute(Auth.FULL_AUTH, true);
post("/passkey/register", c -> { Passkey.register(c, c.request().bodyJson(), "ノート PC"); c.response().json("ok", true); })
	.attribute(Auth.FULL_AUTH, true);
post("/passkey/login/options", c -> c.response().json(Passkey.loginOptions(c))).attribute(Auth.PUBLIC, true);
post("/passkey/login", c -> {
	if (!Passkey.login(c, c.request().bodyJson(), App::findPrincipal)) throw new HttpException(401, "...");
	c.response().json("ok", true);
}).attribute(Auth.PUBLIC, true);
get("/passkey.js", Passkey.script()).attribute(Auth.PUBLIC, true);   // ブラウザは JimblePasskey.register / login を呼ぶ
```

- `import io.jimble.web.auth.passkey.Passkey;`。**`auth.passkey.rp_id`（ドメイン）が要る**。あとから変えると登録したものが全部使えなくなる
- **登録は `Auth.FULL_AUTH` のルートから**（セッションを盗んだ人に自分のパスキーを足させない）。DB とセッションが要る（`NO_SESSION` を付けない）
- 通ったら中で `Auth.login` まで済む。**二要素認証のコードは聞かない**（パスキーが本人確認込み）。`FULL_AUTH` も通る
- ブラウザ側は自分で `navigator.credentials` を書かず、`Passkey.script()` の JS を使う（base64url の変換と CSRF のヘッダをやる）
- 一覧は `Passkey.list(id)`、削除は `Passkey.delete(id, 一覧の id)`（FULL_AUTH のルートから）。`Auth.revoke` ではパスキーは消えない
- **クライアントごとにドメインが違う（SaaS）なら `PasskeyRp.of(ドメイン, 名前)` を作り、4つのメソッドに同じものを渡す**（`loginOptions(c, rp)` / `login(c, rp, ...)` など）。
  ドメインはクライアントの表から引く（Host ヘッダをそのまま使わない）。A で登録したパスキーは B では使えない

詳しくは <https://jimble.io/ja/auth.md>。
