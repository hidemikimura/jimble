---
name: jimble-web-auth
description: jimble の Web でログインと認可を書くときに使う。フィルタの順（ApiToken.authenticate → Remember.restore → Auth::guard → session() に触るフィルタ）、Auth の属性（PUBLIC・ROLE・NO_SESSION・FULL_AUTH）と既定で閉じていること、Principal、remember-me、締め出し（revoke・revokeOthers）、ログインの種別（Auth.REALM）、ロックアウト（Lockout.attempt）、BasicAuth、パスワードのハッシュ（bcrypt・cipher.key）を含む。
---

# jimble のログインと認可

全体の決まりと、ほかの skill への案内は `jimble-web`。二要素・パスキー・OIDC は `jimble-web-auth-methods`、API のトークンは `jimble-web-api-token`。

```java
// 順番は ApiToken.authenticate → Remember.restore → Auth::guard → session() に触るフィルタ
before(Remember.restore(App::findPrincipal));   // remember-me を使うなら guard より先に
before(Auth::guard);                            // session() に触るフィルタより先に

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
`Auth.FULL_AUTH` は既定では「このセッションでパスワードを入れて入った人」なら何時間後でも通す。
**ログインからの時間で切るなら `auth.full_auth_max_age = 15m` など**（2.2.4。過ぎると 401。パスワードを入れ直させる）。
`Auth.principal(context)` は **`null` を返さない**（未ログインなら `Principal.ANONYMOUS`）。
アクセサは record 形式（`me.id()` / `me.name()` / `me.hasRole("x")`。`get` は付かない）。
`Principal` は **final class**（`Principal.of(id, name, role)` で作る。record ではない）。

## 落とし穴（実際に踏んだもの）

- **`Auth::guard` は `session()` に触るフィルタより先に登録する。**セッションを使うかどうかをここで決めるので、
  先に誰かが `session()` を触ると間に合わない。前に置いてよいのは `ApiToken.authenticate` と `Remember.restore` だけ
  （この順。`ApiToken.authenticate` より先にセッションを読まれていると、トークンのリクエストが例外になる）
- **`PUBLIC` と `NO_SESSION` は別の判断。**ログインの入口は
  「ログインは要らないが**セッションは要る**」。一緒にすると**誰もログインできなくなる**
  （302 は返るのに次のリクエストで 401 になる）
- **パスワードを変えたら `Auth.revokeOthers(context)`。`Remember.forgetAll` だけではほかの端末のセッションが残る**
  （消えるのは remember-me の記憶だけ）。管理画面から止めるなら `Auth.revoke(id)`。**これからのログインは止めない**ので、
  盗まれたアカウントは先にパスワードを変えるか止めてから。**役割を剥奪したときも `revoke`**（役割はログイン時にセッションへ写すので残る）。
  複数台ではほかの台で効くまで最大 5 秒（`auth.revocation.cache_ttl`）。張りっぱなしの WebSocket は切れない
- **同じブラウザで別々にログインさせたいなら、ブロックに `Auth.REALM` を付ける**（1.4.0。D-185）。
  ログイン・コード・ログアウトの口も同じブロックに置く。remember-me の種別も同じ名前にする（`issue` は違うと例外、`restore` は違うと何もしない。ブロックには同じ名前の `restore` と `Auth::guard` をブロックの中に置く。アプリ全体の guard はブロックの restore より先に走る）
- **ログインを自分で書くなら、`Lockout.waitSeconds` → 確かめる → `Lockout.fail` の順にしない**（同時に送られると素通りする）。
  **`Lockout.attempt(key)` で、試す前に数える**（0 なら試してよい。成功したら `Lockout.clear(key)`）。`Auth.attemptLogin` はこれを使っている
- **`BasicAuth` は、成功したらセッション ID を作り直し、失敗を `Lockout` で数える**（2.2.3）
- **セッションで入る人のテナントの照合は自分で書く**（Cookie もほかのテナントのホストへ送れる）。`Auth::guard` のあとに、ログイン中の人のテナントと Host を比べる before を置く（`jimble-web-api-token`）
- `Auth.login` はセッション ID の振り直しと保存までやる。自分でやるなら `regenerateId()` と `save()`（`jimble-web-session`）

## パスワードのハッシュと鍵

- パスワードのハッシュ（bcrypt）のコストは `hash.password.cost`（既定 10、4〜31。2.2.4）。
  **bcrypt は 72 バイトより後ろを見ない**（超えると1度だけ WARN）。長いパスフレーズを許すなら、画面で上限を決める
- **`cipher.key` を流用しない。**`cipher.*` を書くと **`hash.password.encrypt` も書かないと起動しない**。
  `true` にすると**保存済みの BCrypt が「暗号化済み」として読まれ、全員入れなくなる**。
  二要素には `auth.mfa.secret_key` を使う
- **`StringUtil.createPassword` / `randomNumberString` は暗号用の乱数**（トークンや初期パスワードに使ってよい）
- **トークンなどの秘密はパスに入れない**（`/reset/{token}` ではなく `/reset?token=...`。`jimble-web-server` のアクセスログ）

詳しくは <https://jimble.io/ja/auth.md>。
