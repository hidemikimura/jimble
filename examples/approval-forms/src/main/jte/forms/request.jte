@param io.jimble.util.data.Data data

<!doctype html>
<html lang="ja">
<head>
	<meta charset="utf-8">
	<title>申請を出す</title>
</head>
<body>

<h1>申請を出す</h1>

<%-- 検証に落ちて戻ってきたとき、ここに出る（要件 F-V-03） --%>
@if(data.getDataOptional("validation").size() > 0)
	<ul id="errors">
		@for(String name : data.getDataOptional("validation").keySet())
			<li>${name}：${String.valueOf(data.getDataOptional("validation").get(name))}</li>
		@endfor
	</ul>
@endif

<form method="post" action="/requests" enctype="multipart/form-data">
	<input type="hidden" name="csrf_token" value="${data.getString("csrf_token")}">

	<%--
		value に入れているのは「送られてきた値」である（要件 F-W-09）。
		検証に落ちたときに ValidationExecutor が応答へ載せてくれるので、
		入力し直さずに済む。初回は空なので何も出ない。
	--%>
	<label>種別
		<select name="kind">
			<option value="">選んでください</option>
			<option value="travel" selected="${"travel".equals(data.getStringOptional("kind"))}">出張</option>
			<option value="supply" selected="${"supply".equals(data.getStringOptional("kind"))}">備品</option>
			<option value="book" selected="${"book".equals(data.getStringOptional("kind"))}">書籍</option>
		</select>
	</label>

	<label>金額 <input type="text" name="amount" value="${data.getStringOptional("amount")}"></label>
	<label>希望日 <input type="text" name="needed_on" value="${data.getStringOptional("needed_on")}"></label>
	<label>備考 <textarea name="note">${data.getStringOptional("note")}</textarea></label>

	<%-- 明細。name が items[0][...] になっているのがネストパラメータ（要件 F-W-03） --%>
	<fieldset>
		<legend>明細</legend>
		<label>品目 <input type="text" name="items[0][name]"></label>
		<label>金額 <input type="text" name="items[0][amount]"></label>
		<label>品目 <input type="text" name="items[1][name]"></label>
		<label>金額 <input type="text" name="items[1][amount]"></label>
	</fieldset>

	<label>添付 <input type="file" name="attachment"></label>

	<%-- 下書きなら submit を送らない。必須が変わる（要件 F-V-02） --%>
	<button type="submit" name="submit" value="true">提出する</button>
	<button type="submit">下書きで保存</button>
</form>

</body>
</html>
