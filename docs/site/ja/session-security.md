---
title: セッションと安全側の既定
summary: セッション・CSRF・Flash・署名付き Cookie
section: 基本
order: 8
---

# セッションと安全側の既定

## セッション

```java snippet=session-save
```

**自動保存はしません。** `save()` を呼ばなければ書かれません。
「読んだだけのリクエストで毎回書き込む」を避けるためです。

保存先は `application.conf` で選びます。

```conf
session {
	store       = "db"     # none | db | redis | cookie
	cookie_name = "sid"
}
```

| store | 向いているところ |
| --- | --- |
| `none` | セッションを使わない（既定） |
| `db` | サーバーが複数台。DB がある |
| `redis` | サーバーが複数台。速さが要る |
| `cookie` | サーバーに何も置きたくない。中身は署名される |

### ログインしたらセッション ID を振り直す

**ログインが通った直後に `regenerateId()` を呼んでください。**

```java
context.session().regenerateId();          // ID が変わる。中身は残る
context.session().put("staff_id", staffId);
context.session().save();                  // ← ここで新しい ID が発行される
```

ログインの前後で ID が変わらないと、**ログイン前に攻撃者が仕込んだ ID のまま、
そのセッションが権限を持ちます**（セッション固定化）。仕込む道はいくらでもあります
——公開ページのリンク、別のサブドメイン、ブラウザの拡張。

**中身は持ち越します。**ログインの直前に入れたもの（戻り先の URL、
入力途中のフォーム、CSRF トークン）が消えると、
**ログインした瞬間に元いた場所を見失います。**

> [!TRAP]
> **振り直したら `save()` してください。**`regenerateId()` は保存しません
> （自動保存をしないという決まりのままです）。振り直したあと保存せずに終わると、
> **古い側は消えていて新しい側は無い**——つまりログインしていない状態になります。
> **閉じるほうに倒れる**ので事故にはなりませんが、
> 「ログインしたのに 401」の原因はここです。

保存先で効き方が違います。

| store | 何が起きるか |
| --- | --- |
| `db` / `redis` | 古い行を消して、新しい ID で書き直す |
| `cookie` | **ID で引いていない**ので、中身の Cookie を書き直すだけ（古い値は署名と暗号化で無効） |
| `none` | 何も起きない |

## CSRF

```java snippet=csrf-form
```

`Csrf::verify` を `before` に置くと、その下のルートが守られます。
`GET` `HEAD` `OPTIONS` `TRACE` は素通しです（`Csrf.SAFE_METHODS`）。

トークンは `context.request().csrfToken()` で取り、フォームの hidden に入れます。

**トークンの寿命は `csrf.max_age`（既定 1 日）です。**
0.6.x までは `cookie.max_age`（既定 1 年）に相乗りしていたので、
アプリが自分の都合で `cookie.max_age` を短くすると
**CSRF トークンも一緒に短くなり**、出るのは「CSRF トークンがありません」の 403 だけでした。

## Flash

リダイレクトの先へ1回だけ渡すものです。

```java
context.flash().put("message", "保存しました");
context.response().redirect("/");
```

読んだ時点で消えます。Cookie に署名して入れています。

## Cookie の既定は secure = true

jimble の Cookie は既定で `Secure` が付きます。HTTPS でしか送られません。

**ローカルは http なので、そのままだとブラウザが Cookie を返しません。**
セッションも CSRF も Flash も、エラーを出さずに効かなくなります。

これは静かに壊れる典型なので、jimble は `env=local` かつ `cookie.secure = true`
のときに**起動時に WARN を出します**。

```
cookie.secure = true のままです（env=local）。
ローカルは http なので、ブラウザは Cookie を送り返しません。
application.conf に次を足してください。
  cookie { secure = false }
```

`jimble new` の雛形には最初からこの節が入っています。
**本番へ出すときは消してください。**

## Cookie

```java
// 30日
context.cookies().put("last_post", String.valueOf(id), 30L * 24 * 60 * 60);

String lastPost = context.cookies().get("last_post");
```

**書いた値は同じリクエストの中で読み返せます。** 受信した Cookie しか見えないと、
発行したばかりの CSRF トークンを読み直すたびに別のものが出てしまうためです。

署名を付けたいときは `Cookies.sign(value)` で署名し、`put(Cookie, 平文)` で入れます。

**読むときは `context.cookies().get("名前")` か `context.request().cookie("名前")` です。**
署名が合わなかった値はここに入りません（改ざんされた値がアプリに渡らないようにするためです）。

> [!WARN]
> **`unsignCookie("名前")` は署名を検証しません。**
> 名前に反して、返るのは**受信した生の値**です。無いときは `null` ではなく**空文字**が返ります。
> 検証済みの値が要るなら `cookie("名前")` を使ってください。

鍵は `application.conf` の `cookie.secret`（Cookie セッションは `session.secret`）で、
本番では環境変数から渡してください。

```conf
cookie {
	secret = ${?COOKIE_SECRET}
}
```

**鍵を設定しないと、署名の機能が丸ごと効きません。** 例外は出ず、Cookie は普通に読み書きできるので、
効いていないことに気づく手がかりがありません。起動時に警告を出しています。

### あとから署名を入れるとき

> [!TRAP]
> **`cookie.secret` を初めて設定した瞬間、いま配ってある Cookie が全部いっぺんに捨てられます。**
> `sid` も `csrf_token` も `remember` も flash もです——**全員ログアウト、フォームは 403**。
> **例外もログも出ません**（署名の合わない Cookie を捨てるのは、正しい動きだからです）。

**移行期間を作ってください。**

```conf
cookie {
	secret          = ${?COOKIE_SECRET}
	accept_unsigned = true    # ← 入れ替えが済むまでの間だけ
}
```

`accept_unsigned = true` の間は、**署名の無い Cookie も読みます**。
書くほうは最初から署名するので、**放っておけば署名つきに入れ替わります**。

**入れ替わったら false に戻してください。**戻さないと、
**署名を外した Cookie が通り続ける**ので、署名を入れた意味がなくなります。
通した回数は `cookie.unsigned` に出ます——**これが 0 になってから**戻します。

## 鍵を入れ替える

**鍵は入れ替えられます。誰もログアウトしません。**

新しい鍵を `secret` に、いままでの鍵を `previous_secrets` に書きます。
**書くのは常に `secret`、読むときだけ `previous_secrets` も試します。**

```conf
cookie {
	secret           = ${?COOKIE_SECRET}       # 新しい鍵
	previous_secrets = [${?COOKIE_SECRET_OLD}] # いままでの鍵
}

session {
	secret           = ${?SESSION_SECRET}
	previous_secrets = [${?SESSION_SECRET_OLD}]
}
```

手順は3段です。

1. **新しい鍵を先頭にして、いままでの鍵を `previous_secrets` に残す。** デプロイします
2. **待つ。** アクセスしてきた人から順に、古い鍵の Cookie が新しい鍵で書き直されます
3. **`previous_secrets` を消す。** もう一度デプロイして完了です

### いつ 3 に進めるか

**勘で決めないでください。** 古い鍵で読めた回数がメトリクス（`Metrics.snapshot()`）に出ます。

| 名前 | 意味 |
|---|---|
| `cookie.stale_secret` | 古い鍵で署名された Cookie を読んだ回数 |
| `session.stale_secret` | 古い鍵で暗号化されたセッションを読んだ回数 |

**これが増えなくなったら、古い鍵を捨てられます。** 起動ログにも本数が出ます（`鍵=cookie=2, session=2`）。
2 のままなら入れ替えの途中、`previous_secrets` を消し忘れていればここで気づけます。

いつまでも 0 にならない場合は、その分だけ「たまにしか来ない人」が残っているということです。
Cookie の寿命（`cookie.max_age`。既定は1年）が過ぎれば必ず消えるので、それが最長の待ち時間です。

### 自分で書いた Cookie は自分で書き直してください

枠組みが出す `sid` と `csrf_token` は自動で署名し直します。
**アプリが `cookies().put(...)` で書いた Cookie は自動では書き直しません**——
有効期限をいくつにすべきかは、書いた側にしか分からないためです（ブラウザは有効期限を送ってきません）。

```java
if (context.cookies().isStale("last_post")) {
	context.cookies().put("last_post", context.cookies().get("last_post"), 30 * 24 * 60 * 60);
}
```

書き直さなくても読めますが、その Cookie が消えるまで古い鍵を捨てられません。

### パスワードの鍵は入れ替えられません

**`cipher.key` と `hash.password.pepper` はこの仕組みの対象外です。**
これらで作った値は Cookie ではなく **DB のパスワードカラム**に入っていて、
書き直せるのは**ログインに成功した瞬間だけ**です（平文がそのときしか無いため）。
つまり、しばらくログインしていない人がいる限り古い鍵を捨てられません。

替えるなら、アプリ側でこうします。

1. 新しい鍵で `createHash` するように切り替える
2. **ログイン成功時に、古い方式のハッシュだったら作り直して保存する**
   （`check(input, hash, encrypted)` で方式を指定して照合できます）
3. 全員が入れ替わったことを確認してから古い鍵を捨てる

3 を確認する手立ては jimble にはありません。**「最終ログイン日時が古い人を数える」など、
アプリ側で持っている情報で判断してください。**

## パスワード

`PasswordUtil` は BCrypt でハッシュ化します。**そのうえで暗号化するかどうかは選べます。**

```java
String hash = PasswordUtil.createHash(password);

if (PasswordUtil.check(input, user.getString("password"))) {
	// 一致した
}
```

```conf
hash {
	password {
		# cipher.* を書くなら、これも必ず書く（書かないと起動時に落ちる）
		encrypt = true
		pepper  = ${?PASSWORD_PEPPER}
	}
}

cipher {
	key = ${?CIPHER_KEY}   # 16 / 24 / 32 バイト
	iv  = ${?CIPHER_IV}    # 16 バイト
}
```

**既定は「鍵があれば暗号化する」です。** 固定の `false` にすると、暗号化されたハッシュを
保存している既存のアプリが、鍵を設定しているのに平文として照合してしまい、
**全員ログインできなくなります。** どちらで動いているかは起動時のログに出ます
（`パスワード暗号化=あり`）。明示したいときは `encrypt` を書いてください。

暗号化を後から入れる・外すときは、保存済みのハッシュを作り直す必要があります。
`createHash(password, encrypt)` と `check(input, hash, encrypted)` で片方ずつ指定できます。

`CipherUtil` は **AES/CBC で IV が固定**です。既に保存されている暗号文を読むために残してあります。
**新しく暗号化するものには使わないでください。** 同じ平文が必ず同じ暗号文になり、改ざん検知もありません。
新しく作るものは `Aead`（AES-256-GCM）を使います。
