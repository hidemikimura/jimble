@import io.jimble.util.data.Data
@import java.util.List

@param Data data

<%--
	申請一覧（同じレイアウトを使う2枚目）

	<b>ここが「レイアウトを共有している」ことの証拠になる。</b>
	ヘッダを1か所直せば両方変わる、が形として見える。
--%>

!{List<Data> requests = data.getDataList("requests");}

@template.layout.page(data = data, content = @`

	@template.tag.card(title = "申請", body = @`
		@if(requests.isEmpty())
			<p>ありません。</p>
		@else
			<table>
				<tr><th>種別</th><th>金額</th><th>状態</th></tr>
				@for(Data request : requests)
					<tr>
						<td>${request.getString("kind")}</td>
						<td>${request.getLong("amount")}</td>
						<td>${request.getString("status")}</td>
					</tr>
				@endfor
			</table>
		@endif
	`)

`)
