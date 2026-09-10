@param String current

<%--
	共通のナビ（部品）

	レイアウトから呼ばれる。ページからも直接呼べる。
	いま見ているところに印を付けたいので、名前を受け取る。

	<b>Data ではなく String を受け取っている。</b>
	部品は「渡されたものだけで描ける」ほうが使い回せる——
	Data を受け取ると、呼ぶ側が「どのキーが要るのか」を
	テンプレートの中身を読まないと分からなくなる。
--%>

<nav>
	<ul>
		<li><a href="/" class="${"home".equals(current) ? "current" : ""}">ホーム</a></li>
		<li><a href="/requests" class="${"requests".equals(current) ? "current" : ""}">申請一覧</a></li>
		<li><a href="/app/" class="${"app".equals(current) ? "current" : ""}">申請アプリ</a></li>
		<li><a href="/guide/" class="${"guide".equals(current) ? "current" : ""}">手引き</a></li>
	</ul>
</nav>
