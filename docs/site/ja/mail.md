---
title: メール
summary: SMTP で送る。MQ から送れば、落ちてもやり直せる
section: 実行基盤
order: 3
---

# メール

**SMTP でメールを送ります**（2.4.0 から）。依存は足していません（SMTP も MIME も JDK だけで書いています）。

```java
import io.jimble.util.mail.MailMessage;
import io.jimble.util.mail.Mailer;

Mailer.send(new MailMessage()
	.to("hanako@example.co.jp", "山田 花子")
	.subject("申請が承認されました")
	.text("山田 様\n\n経費の申請（No.123）が承認されました。"));
```

| | |
| --- | --- |
| 宛先 | `to` / `cc` / `bcc`（ヘッダには書かない）/ `replyTo`。From を書かなければ設定の `mail.from` |
| 本文 | `text(...)`、`html(...)`。両方入れると、メールソフトが選べる形（multipart/alternative）で送ります |
| 添付 | `attach("請求書.pdf", bytes, "application/pdf")`。日本語のファイル名もそのまま使えます |
| ヘッダ | `header("List-Unsubscribe", "<mailto:...>")`。From や Subject など、jimble が書くものは決められません |
| 文字 | UTF-8。件名と名前の日本語は RFC 2047、本文は base64 |

**改行を含む件名・名前・ヘッダ、山括弧や空白を含むアドレスは、作った時点で例外にします**（ヘッダの差し込みを止めるため）。
利用者が入れた値をそのまま渡しても、Bcc を足されることはありません。

> [!TRAP]
> **HTML だけのメールは迷惑メールに入りやすくなります。**HTML を送るときも `text(...)` を入れてください。
> 利用者が入れた値を HTML に入れるなら、テンプレート（jte）でエスケープしてから渡します。

## MQ から送る

**リクエストの処理の中で直に送らず、MQ に積んで、`execute()` から送ってください。**

- SMTP は遅く（数百ミリ秒〜数秒）、落ちることもあります。リクエストを待たせません
- **トランザクションと一緒に積めます。**申請が保存されなかったのに、メールだけ飛ぶ、が起きません
- 落ちたら、MQ がやり直します

```java
public MqStatus execute (DB db, Data row) {

	Data data = row.getDataOptional("data");

	try {
		Mailer.send(new MailMessage()
			.to(data.getString("to"))
			.subject("申請が承認されました")
			.text(data.getString("body")));
		return MqStatus.completed;
	} catch (MailException ex) {
		if (ex.isTransient()) {
			throw ex;                  // 一時的（相手が混んでいる・繋がらない）→ maxRetry() までやり直す
		}
		Log.error(ex, "メールを送れませんでした（やり直しても同じ）");
		return MqStatus.dead;          // 宛先が無い・認証が通らない → やり直さない
	}

}
```

`MailException.isTransient()` は、SMTP の 4xx と、繋がらない・途中で切れたときに `true` です。
5xx（宛先が無い・認証が通らない）と、メールの形の誤りは `false` です。

> **同じメールが2通届くことがあります。**MQ は「ちょうど1回」を約束しません（[MQ](./mq)）。
> 送ったあと、`completed` を書く前にプロセスが落ちると、もう一度送ります。大事な通知は、送った記録を見てから送ってください。

## 設定

```conf
mail {
	transport = "smtp"                   # smtp | log | memory
	from      = "noreply@example.com"    # From を書かないメールの差出人
	from_name = "承認ワークフロー"

	smtp {
		host     = "smtp.example.com"
		port     = 587                    # 書かなければ security から（starttls 587 / tls 465 / none 25）
		security = "starttls"             # starttls | tls | none
		username = "apikey"
		password = ""
		password = ${?SMTP_PASSWORD}
	}
}
```

Amazon SES・SendGrid・Google Workspace などは、どれも SMTP（STARTTLS の 587 と、利用者名・パスワード）で送れます。

| キー | 既定 | |
| --- | --- | --- |
| `mail.smtp.connect_timeout` | `10s` | 繋ぐまでの待ち |
| `mail.smtp.timeout` | `30s` | 1回の読み書きの待ち |
| `mail.smtp.helo` | この機械の名前 | EHLO で名乗る名前 |
| `mail.smtp.envelope_from` | From | 届かなかったときの知らせ（バウンス）の宛先 |

**安全のために断るもの**

| | |
| --- | --- |
| STARTTLS を選んだのに、相手が対応していない | 暗号化せずには続けず、例外にします |
| TLS の証明書のホスト名が違う | 送りません |
| 暗号化しない接続（`security = "none"`）で認証する | 設定の時点で例外にします（パスワードが平文で流れるため） |
| 宛先のうち1人でも断られた | 誰にも送りません（一部にだけ届くと、やり直したときに届いた人へもう一度送ることになるため） |

### 手元とテスト

**`transport` の既定は `smtp` です。**`mail.smtp.host` を書かずに送ると例外にします（送ったつもりで黙って届かない、を起こさないため）。

```conf
# conf/application.local.conf
mail.transport = "log"      # 送らずに、宛先・件名・本文をログに出す（WARN）
```

テストでは、送ったものをメモリに貯めて確かめます。

```java
MemoryTransport memory = new MemoryTransport();
Mailer.use(memory);

// ... 申請を承認する ...

assertEquals("申請が承認されました", memory.sent().get(0).subject());

Mailer.reset();
```

## HTTP の API で送る

SMTP ではなく、サービスの HTTP の API で送るなら、`MailTransport` を実装して差し替えます。

```java
Mailer.use(message -> {
	byte[] raw = message.toMime();     // 生のメール（生のメールを受け付ける API ならそのまま渡せる）
	// ... API を呼ぶ。失敗は MailException（一時的なら isTransient = true）で投げる ...
});
```

## 無いもの

| | |
| --- | --- |
| 受信（IMAP / POP3） | 送るだけです |
| ISO-2022-JP | UTF-8 だけです（いまのメールソフトと携帯のメールは、どれも UTF-8 を読めます） |
| HTML の中の画像を添付で埋め込む（cid） | まだありません。画像は URL で参照してください |
| テンプレート | 本文は文字列で渡します。jte で組み立てたものを渡せます |
