@import db.blog_example.table.post.Post
@import io.jimble.util.data.Data
@import java.util.List

@param Data data

!{List<Data> posts = data.getDataList("posts");}

<!doctype html>
<html lang="ja">
<head>
	<meta charset="UTF-8">
	<title>記事を書く</title>
</head>
<body>
	<h1>記事を書く</h1>

	@if(!data.getString("message").isEmpty())
		<p id="flash">${data.getString("message")}</p>
	@endif

	@if(!data.getString("last_post").isEmpty())
		<p id="last-post">前に登録したのは ID ${data.getString("last_post")} です。</p>
	@endif

	<form method="post" action="/form" enctype="multipart/form-data">

		<%-- トークンはこの1行で入る。無いと 403 になる --%>
		<input type="hidden" name="csrf_token" value="${data.getString("csrf_token")}">

		<p><label>タイトル <input type="text" name="title" required></label></p>
		<p><label>本文 <textarea name="body"></textarea></label></p>
		<p><label>公開する <input type="checkbox" name="published" value="true"></label></p>
		<p><label>画像 <input type="file" name="image"></label></p>

		<p><button type="submit">登録する</button></p>

	</form>

	<h2>いまある記事</h2>

	@if(posts.isEmpty())
		<p>まだ記事がありません。</p>
	@else
		<ul>
			@for(Data post : posts)
				<li>
					<a href="/posts/${post.getString(Post.id)}">${post.getString(Post.title)}</a>
				</li>
			@endfor
		</ul>
	@endif

</body>
</html>
