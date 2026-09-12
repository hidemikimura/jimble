@param io.jimble.util.data.Data data

<!doctype html>
<html lang="ja">
<head>
	<meta charset="utf-8">
	<title>確認コード</title>
</head>
<body>

<h1>確認コード</h1>

<%--
	この画面が出ている人は、パスワードは合っているが「まだログインしていない」。
	/me も /requests も 401 になる（要件 F-W-32）。
--%>
<p>認証アプリに出ている6桁を入れてください。</p>

@if(!data.getString("message").isEmpty())
	<p id="flash">${data.getString("message")}</p>
@endif

<form method="post" action="/login/code">
	<%-- CSRF トークン（要件 F-S-06）。これが無いと POST は 403 になる --%>
	<input type="hidden" name="csrf_token" value="${data.getString("csrf_token")}">

	<%--
		回復コードも同じ欄に入れる。
		利用者から見て2つの欄に分ける理由が無いうえ、分けると
		「どちらの欄で断られたか」で当たりを絞れてしまう。
	--%>
	<label>コード <input type="text" name="code" inputmode="numeric" autocomplete="one-time-code"></label>

	<button type="submit">確認する</button>
</form>

<p>認証アプリを失くしたときは、登録のときに控えた回復コードを同じ欄に入れてください。</p>

</body>
</html>
