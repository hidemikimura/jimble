@param io.jimble.util.data.Data data

<!doctype html>
<html lang="ja">
<head>
	<meta charset="utf-8">
	<title>パスキー</title>
</head>
<body>

<h1>パスキー</h1>

<%--
	パスキーで入ると、ログインIDもパスワードも二要素認証のコードも要らない（D-261）。
	端末の本人確認（指紋・顔・PIN）がその代わりになる。
--%>
<p>パスキーを登録すると、次からはパスワードを入れずにログインできます。</p>

@if(!data.getString("message").isEmpty())
	<p id="flash">${data.getString("message")}</p>
@endif

<h2>登録しているパスキー</h2>

@if(data.getDataList("passkeys").isEmpty())
	<p>まだありません。</p>
@else
	<table>
		<tr><th>名前</th><th>登録した日時</th><th>最後に使った日時</th><th></th></tr>
		@for(io.jimble.util.data.Data passkey : data.getDataList("passkeys"))
			<tr>
				<td>${passkey.getString("label").isEmpty() ? "（名前なし）" : passkey.getString("label")}</td>
				<td>${java.time.Instant.ofEpochSecond(passkey.getLong("created_at")).toString()}</td>
				<td>${passkey.getLong("last_used_at") == 0 ? "まだ使っていません" : java.time.Instant.ofEpochSecond(passkey.getLong("last_used_at")).toString()}</td>
				<td>
					<%-- 消すのはパスワードを入れて入った人だけ（POST /passkey/delete に FULL_AUTH） --%>
					<form method="post" action="/passkey/delete">
						<input type="hidden" name="csrf_token" value="${data.getString("csrf_token")}">
						<input type="hidden" name="id" value="${passkey.getString("id")}">
						<button type="submit">消す</button>
					</form>
				</td>
			</tr>
		@endfor
	</table>
@endif

<h2>このパスキーを登録する</h2>

@if(!data.getBoolean("full_auth"))
	<%--
		「ログインしたまま」で戻ってきた人は登録できない（FULL_AUTH）。
		セッションを盗んだ人が自分のパスキーを足せないようにするため。
	--%>
	<p>登録するには、いちどログアウトして、パスワードで入り直してください。</p>
@else
	<form id="register">
		<input type="hidden" name="csrf_token" value="${data.getString("csrf_token")}">
		<label>名前（見分けるため） <input type="text" name="label" placeholder="ノート PC" maxlength="100"></label>
		<button type="submit">登録する</button>
	</form>
	<p id="register-error" role="alert"></p>
@endif

<p><a href="/me">戻る</a></p>

<script src="/passkey.js"></script>
<script>
	const form = document.getElementById("register");

	if (form) {
		form.addEventListener("submit", async (event) => {
			event.preventDefault();
			const errorArea = document.getElementById("register-error");
			errorArea.textContent = "";
			try {
				await JimblePasskey.register("/passkey/register/options", "/passkey/register", {
					csrfToken: form.elements.csrf_token.value,
					extra: { label: form.elements.label.value }
				});
				location.reload();
			} catch (error) {
				if (error.name !== "NotAllowedError") {
					errorArea.textContent = error.status === 409 ? "このパスキーはもう登録されています" : error.message;
				}
			}
		});
	}
</script>

</body>
</html>
