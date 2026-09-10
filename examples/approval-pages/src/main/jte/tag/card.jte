@import gg.jte.Content

@param String title
@param Content body
@param String note = ""

<%--
	見出しつきの箱（部品）

	<b>Content を受け取る部品</b>である。レイアウトと同じ仕組みで、
	「枠は決まっているが中身は呼ぶ側が決める」ものを作れる。

	note には既定値を持たせてある（jte は @param に = で既定値を書ける）。
	書かなければ空文字なので、呼ぶ側は note を省略できる。
--%>

<section class="card">
	<h2>${title}</h2>

	${body}

	@if(!note.isEmpty())
		<p class="note">${note}</p>
	@endif
</section>
