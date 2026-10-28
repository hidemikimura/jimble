---
name: jimble-web-api-token
description: jimble の Web で API のトークン（Authorization Bearer）を発行・検証するときに使う。ApiToken.authenticate の置き場所（いちばん先）、ACCEPT と SCOPE、発行と一覧・止め方、ログインの種別（Auth.REALM）、lookup の3つの形とマルチテナントの Host の照合（PrincipalLookup）、自分で Bearer を確かめるときの sessionStarted を含む。
---

# jimble の API のトークン（Authorization: Bearer。2.4.0、種別は 2.5.10、テナントの照合は 2.5.11）

全体の決まりと、ほかの skill への案内は `jimble-web`。ログインと認可の基本（フィルタの順）は `jimble-web-auth`。
Spring Security の Bearer / JWT の代わりがこれ。

```java
before(ApiToken.authenticate(App::findPrincipal));   // いちばん先（Remember.restore・Csrf::verify・Auth::guard より前）
before(Auth::guard);
path("/api", () -> {
	attribute(ApiToken.ACCEPT, true);                 // 受けるのは宣言したルートだけ（無いルートに Bearer は 401）
	get("/requests", Api::list).attribute(ApiToken.SCOPE, "requests:read");   // 足りなければ 403
});
ApiToken.Issued issued = ApiToken.issue(me.id(), "連携", Set.of("requests:read"), Duration.ofDays(90));   // FULL_AUTH のルートから。token() は一度だけ見せる
```

- `io.jimble.web.auth.ApiToken`。DB に持つ不透明なトークン（JWT は出さない）。DB にはハッシュだけ
- トークンの人はそのリクエストだけログイン（Cookie なし）。**FULL_AUTH には入れない**。CSRF は見ない。スコープはトークンの人にだけ効く
- **`Auth.revoke` / `revokeOthers` でそれより前のトークンも止まる**。個別に止めるなら `ApiToken.revoke(realm, id, トークンの id)`
- 一覧は `ApiToken.list(realm, id)`（トークンそのものは無い）、全部止めるなら `ApiToken.revokeAll(realm, id)`、
  いまのトークンのスコープは `ApiToken.scopes(context)`（種別なしは `realm` に `""`）
- **トークンはルートの `Auth.REALM` の種別で引く。**種別を付けたブロックで受けるなら、発行も
  `ApiToken.issue("operator", id, ...)`。種別なしの `issue(id, ...)` で出したトークンは、種別つきのルートでは 401
- **`ApiToken.authenticate` より先にセッションを読まれていると、トークンのリクエストが例外になる**

## lookup の形

**`lookup` は3つの形がある**（どれも止めた・消した人なら `null` → 401）。

| 形 | 使うとき |
| --- | --- |
| `id -> ...` | 利用者の表が1つ |
| `(realm, id) -> ...`（2.5.10） | **管理者と顧客で表が別。**ID だけでは、ID 1 がどちらの ID 1 か引き分けられない。種別なしのルートでは `realm` は `""` |
| `(context, realm, id) -> ...`（`PrincipalLookup`。2.5.11） | **マルチテナントの SaaS。**`context.request().host()` のテナントと利用者のテナントを照合する |

```java
before(ApiToken.authenticate((c, realm, id) -> {
	Tenant tenant = Tenants.byHost(c.request().host()).orElse(null);   // アプリ側のクラス
	Principal member = App.findMember(id);
	return tenant != null && member != null && App.tenantOf(id) == tenant.id() ? member : null;   // 違えば null
}));
```

- **照合しないと、テナント A の利用者のトークンがテナント B のホストで通る。**違えば `null` を返す（止めた人と同じ 401。テナントが違うことは応答で分からない）
- `lookup` の中で使ってよいのは `c.request()`（`host()`・ヘッダー・パス）と `c.route()`（属性）だけ。
  **`c.session()` は例外（500）**、`c.response()` は書かない、`Auth.principal(c)` はまだ ANONYMOUS
- **断るときは例外ではなく `null`**（例外を投げると 500）
- **セッションで入る人の照合は別に要る**（Cookie もほかのテナントのホストへ送れる）。`Auth::guard` のあとに、ログイン中の人のテナントと Host を比べる before を置く

## 自分で Bearer を確かめるなら

`ApiToken` を使わずに自分で Bearer を確かめる before では、`context.sessionStarted()`（2.4.1）でセッションを始めずに確かめる
（`session()` を呼ぶと始まり、あとの `Auth::guard` が `NO_SESSION` のルートで保存先を変えられず 500）。

詳しくは <https://jimble.io/ja/auth.md>。
