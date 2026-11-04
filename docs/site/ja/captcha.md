---
title: ロボット対策（ALTCHA）
summary: 外のサービスに登録せずに、フォームの送り手が人かを確かめる
section: 基本
order: 11
---

# ロボット対策（ALTCHA）

フォームを送る前に、**ブラウザに少し重い計算を解かせ**、答えをサーバーで確かめます（proof-of-work）。
画像の文字を読ませる CAPTCHA と違い、利用者は「私はロボットではありません」に印を付けて数秒待つだけです。

- **外のサービスへの登録が要りません。**reCAPTCHA や Turnstile と違い、運営者もテナントも、どこにも申し込みません
- **別のサーバーは要りません。**問題を作るのも確かめるのも、アプリの中です
- **ドメインを登録しません。**独自ドメインのテナントがいくつあっても、そのまま使えます
- 利用者のデータは外に出ません

ブラウザ側は [ALTCHA](https://altcha.org) のウィジェット（`altcha` 3.x。MIT）を使います。
jimble が作るのは、サーバー側の 2 つ（問題を返すルートと、答えを確かめるフィルタ）です。

## 書くこと

```java snippet=altcha-routes
```

`Altcha::verify` は `before` と同じで、**書いたブロックにだけ**かかります。
GET などの状態を変えないメソッドは素通しするので、フォームを出すルートと同じブロックに置けます。

鍵を設定に書きます。**32 文字以上で、必ず環境変数から読んでください。**

```conf
altcha {
	secret = ${?ALTCHA_SECRET}
}
```

鍵を書いていない・短いときは、問題を作るところ（`/altcha/challenge`）と確かめるところで止まります。

## ウィジェットを置く

```html
<script type="module" src="https://cdn.jsdelivr.net/npm/altcha@3.5.0/dist/main/altcha.js"></script>

<form method="post" action="/contact">
	...
	<altcha-widget challenge="/altcha/challenge"></altcha-widget>
	<button>送る</button>
</form>
```

- 版は**決めて書きます**（`altcha@3` のように幅を持たせない）。npm から入れて自分で配ってもかまいません
- ウィジェットは、解いた答えをフォームの `altcha` 項目に入れて送ります
- フォームでなく `fetch` で送るなら、答えを JSON の本文の `altcha` か、`X-Altcha` ヘッダに入れます
- CSP を掛けているアプリは、ウィジェットが Web Worker を使うので、ALTCHA の文書を見て許可を足してください（jimble は既定で CSP を出しません）

## 断られたとき

答えが無い・形が違う・期限切れ・書き換えてある・一度使った、のどれでも **403** です。

| 結果（`AltchaResult`） | 文言 |
| --- | --- |
| `MISSING` | 「ロボットでないことの確認（ALTCHA）が送られていません」 |
| `MALFORMED` | 「ロボットでないことの確認（ALTCHA）の形が正しくありません」 |
| `EXPIRED` | 「ロボットでないことの確認（ALTCHA）の期限が切れています。もう一度確かめてください」 |
| `INVALID` | 「ロボットでないことを確かめられませんでした」 |
| `REPLAYED` | 「ロボットでないことの確認（ALTCHA）はもう使われています。もう一度確かめてください」 |

403 にせず、フォームを出し直したいときは、`Altcha.check` で結果を受け取ります。

```java
post("/contact", context -> {
	AltchaResult result = Altcha.check(context.request().bodyForm().getStringOptional(Altcha.FIELD_NAME));
	if (!result.verified()) {
		// result.message() を出してフォームを返す
	}
	...
});
```

> [!WARN]
> **`Altcha.check` は、通った答えを「使った」と記録します。**同じ答えで 2 回呼ぶと、2 回目は `REPLAYED` です。
> `before(Altcha::verify)` を掛けたルートの中で、もう一度 `check` を呼ばないでください。

## 同じ答えは 1 回だけ

一度通った答えは、**期限まで「使った」印を置いて**、もう一度は通しません。
印は流量制限と同じ置き場（`rate_limit.store`）に置きます。

> [!WARN]
> **複数台に並べるなら、`rate_limit.store` を `redis` か `db` にしてください。**
> `memory` のままだと、印はその台にしかないので、別の台に送られた同じ答えは通ります。

置き場が使えないとき（Redis が落ちたなど）は、**ログに出して通します**。流量制限と同じ扱いで、
置き場が止まっただけで全部のフォームが送れなくなるのを避けるためです。署名と答えは確かめてあります。

## 重さと期限

```conf
altcha {
	cost        = 5000    # 1 回の計算の重さ（PBKDF2 の繰り返し回数）
	counter_max = 10000   # 答えの数の上限。counter_max / 2 〜 counter_max から選ぶ
	expires     = 10m     # 問題の期限
}
```

ブラウザは答えの数を 0 から順に試すので、計算はおよそ `cost × 答えの数` 回です。
**既定のままで、10 コアの Mac の Chrome で、答えが上限近くのとき 3.1 秒**でした。
古いスマートフォンはその数倍かかります。重くするほどボットは嫌がりますが、人も待ちます。

ウィジェットは、ページを開いたときではなく**触ったときに問題を取りに来る**ので、期限は長くしなくてかまいません。

## これだけでは止めきれない

proof-of-work は**ボットに手間を掛けさせる**ものです。計算を厭わないボットは通ります。

- 送る回数は [レートリミット](./ratelimit) で絞る（`/altcha/challenge` にも掛けておくと、問題を作らせ続ける攻撃も絞れます）
- ログインの試行は `Lockout.attempt` で絞る（[認証](./auth)）
