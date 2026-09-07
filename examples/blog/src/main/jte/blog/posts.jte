@import db.blog_example.table.post.Post
@import io.jimble.util.data.Data
@import java.util.List

@param Data data

!{List<Data> posts = data.getDataList("posts");}

<!doctype html>
<html lang="ja">
<head>
	<meta charset="UTF-8">
	<title>${data.getString("title")}</title>
</head>
<body>
	<h1>${data.getString("title")}</h1>

	@if(posts.isEmpty())
		<p>まだ記事がありません。</p>
	@else
		<ul>
			@for(Data post : posts)
				<li>
					<b>${post.getString(Post.title)}</b>
					<time datetime="${post.getDateString(Post.created_at, "yyyy-MM-dd HH:mm:ss")}">
						${post.getDateString(Post.created_at, "yyyy/MM/dd")}
					</time>
					<p>${post.getString(Post.body)}</p>
				</li>
			@endfor
		</ul>
	@endif
</body>
</html>
