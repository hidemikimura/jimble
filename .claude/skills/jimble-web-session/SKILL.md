---
name: jimble-web-session
description: jimble の Web でセッション・CSRF・Flash・Cookie を扱うときに使う。セッションは自動保存されない（save()）・data() は読み取り専用・destroy() の順・regenerateId、session.store と有効期限、Csrf::verify とトークンの渡し方・csrf.bind_session と SPA、Flash、署名つき Cookie（名前に結びつく・古い署名・accept_legacy_signature）、ローカルの cookie.secure を含む。
---

# jimble のセッション・CSRF・Flash・Cookie

全体の決まりと、ほかの skill への案内は `jimble-web`。ログインは `jimble-web-auth`。

## セッション

```java
context.session().put("staff_id", id);
context.session().save();                  // ← 呼ばないと書かれない
```

**自動保存はしない。**変えたら `save()`。`save()` のあとに変えても、もう一度 `save()` すれば保存される。
保存し忘れると、リクエストの終わりに「save() が呼ばれていません」と WARN が出る。

**書くのは `session().put(...)` / `remove(...)`。**`session().data()` は**読み取り専用の写し**で、
`session().data().put(...)` は `UnsupportedOperationException`。
**`destroy()` のあとの `put` / `remove` / `clear` は `IllegalStateException`。**ログアウトの処理は `destroy()` を最後に。
保存先は `application.conf` の `session.store`（`none` / `db` / `redis` / `cookie`。既定 `none`）。

**ログインが通ったら `regenerateId()`。**呼ばないと、
ログイン前に仕込まれた ID がそのまま権限を持つ（セッション固定化）。
中身は持ち越すが**保存はしないので `save()` まで書く**。
`Auth.login` を使えば振り直しと保存までやる。

**`Auth::guard` は `session()` に触るフィルタより先に登録する**（セッションを使うかをそこで決める。`jimble-web-auth`）。

**`session.store = "cookie"` はログアウトしても、盗まれた写しまでは無効にできない**（サーバーに何も置かないため）。
最後に使ってから `session.timeout`、発行から `session.absolute_timeout`（既定 1 日）で切れる（2.2.3）。
`db` / `redis` は **`session.absolute_timeout` を書いたときだけ**発行からの上限が効く（2.2.4。書かなければ上限なし＝使い続ければ延び続ける）。
すぐに無効にしたいなら `db` / `redis` にするか、`Auth.revoke(id)` を使う。

## CSRF

CSRF は `before` に `Csrf::verify`。`GET` `HEAD` `OPTIONS` `TRACE` は素通し。
トークンは `Csrf.token(context)` を hidden（`csrf_token`）に入れる（`context.request().csrfToken()` は使わない。読むのは `csrf-token` ヘッダで、`Csrf::verify` が見る `X-CSRF-Token` とは別物。発行もしない）。読むのはヘッダ（`X-CSRF-Token`）→ フォーム → JSON の本文。**クエリ文字列では受けない**。
`csrf.bind_session = true` でトークンをセッションに置き、ログインで作り直す。新しいトークンは応答の `X-CSRF-Token` ヘッダで返るので、**SPA はそれで差し替える**（しないとログイン後の POST が 403）。`session.store = "none"` では使えない（例外）。
`csrf.bind_session = true` なら、ログアウトのあとはトークンが無いので、次にフォームを出すところで受け取り直す。

## Flash

Flash は**次の1回のリクエストだけ**残る。読んだ時点で消える。

## Cookie

属性を決めた `Cookie` は `cookies().putSigned(cookie)` / `putUnsigned(cookie)` で書く（`put(Cookie)` は無い）。
**`after` で Cookie を足せない**（応答を送ったあとに走る）。`before` かハンドラの中で足す。

**署名つき Cookie の署名は Cookie の名前に結びついている**（2.2.3）。自分で署名するなら `Cookies.sign(名前, 値)`。
**`Cookies.sign(値)`（非推奨）の署名と 2.2.2 以前の署名は、2.5.2 から既定で読まれない**（`cookie.accept_legacy_signature = false`。D-291）。
2.2.2 以前から上げるときは、移す間だけ `true` にする（`sid` と `csrf_token` は読んだときに書き直される）。
署名は有効期限を含まない。**利用者を表す値を署名つき Cookie だけで信じない**（セッションに入れる）。
WebSocket の `WsSession.cookie(名前)` も署名を確かめた値を返す（2.2.4。合わなければ空文字）。自分で確かめるなら `Cookies.verify(名前, 値)`。

**`env=local` で `cookie.secure = true` のままだと**、ブラウザが Cookie を返さず
セッションも CSRF もエラーなしで効かなくなる（起動時に WARN が出る）。
`cookie { secure = false }` は **`application.local.conf`（1行目に `include "application.conf"`）にだけ**書く
（`application.conf` に書くと本番も引き継ぐ。ローカルでない環境で false なら起動時に WARN）。

詳しくは <https://jimble.io/ja/session-security.md>（鍵の入れ替えも）。
