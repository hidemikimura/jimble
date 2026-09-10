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

	<label>ログインID <input type="text" name="login_id"></label>
	<label>パスワード <input type="password" name="password"></label>

	<button type="submit">入る</button>
</form>

<p>サンプルの利用者：member1 / approver1（パスワードはどちらも approval-sample）</p>

</body>
</html>
