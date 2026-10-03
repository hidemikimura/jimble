@param io.jimble.util.data.Data data

<!doctype html>
<html lang="ja">
<head>
	<meta charset="utf-8">
	<title>ログイン</title>
</head>
<body>

<h1>ログイン</h1>

@if(!data.getString("message").isEmpty())
	<p id="flash">${data.getString("message")}</p>
@endif

<form method="post" action="/login">
	<%-- CSRF トークン（要件 F-S-06）。これが無いと POST は 403 になる --%>
	<input type="hidden" name="csrf_token" value="${data.getString("csrf_token")}">

	<%-- autocomplete に webauthn を足すと、入力欄の候補にパスキーが出る（D-261） --%>
	<label>ログインID <input type="text" name="login_id" autocomplete="username webauthn"></label>
	<label>パスワード <input type="password" name="password"></label>

	<%-- 印が付いたときだけ覚える（要件 F-W-30）。共用の端末で常に覚えると、次の人が入れる --%>
	<label><input type="checkbox" name="remember" value="1"> ログインしたままにする</label>

	<button type="submit">入る</button>
</form>

<%--
	パスキーでログインする（D-261）。ログインIDもパスワードも入れない。
	パスキーは /passkeys で登録する（いちどパスワードで入ってから）。
--%>
<p>
	<button type="button" id="passkey-login">パスキーでログイン</button>
	<span id="passkey-error" role="alert"></span>
</p>

<p>サンプルの利用者：member1 / approver1（パスワードはどちらも approval-sample）</p>

<script src="/passkey.js"></script>
<script>
	// トークンはフォームの hidden から読む（テンプレートの値を JS の中に直に書かない）
	const csrfToken = () => document.querySelector("input[name=csrf_token]").value;
	const errorArea = document.getElementById("passkey-error");

	// 入力欄の候補にパスキーを出す。待っているあいだにボタンが押されたら止める
	let autofill = new AbortController();

	JimblePasskey.login("/passkey/login/options", "/passkey/login", { csrfToken: csrfToken(), conditional: true, signal: autofill.signal })
		.then(result => { if (result) location.href = result.next; })
		.catch(() => { /* 止めた・使えない・取り消した。ボタンからやり直せる */ });

	document.getElementById("passkey-login").addEventListener("click", async () => {
		autofill.abort();
		errorArea.textContent = "";
		try {
			const result = await JimblePasskey.login("/passkey/login/options", "/passkey/login", { csrfToken: csrfToken() });
			location.href = result.next;
		} catch (error) {
			if (error.name !== "NotAllowedError" && error.name !== "AbortError") {
				errorArea.textContent = error.message;
			}
		}
	});
</script>

</body>
</html>
