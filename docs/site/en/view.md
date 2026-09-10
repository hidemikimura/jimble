---
title: Templates
summary: jte. It compiles, so your types hold
section: Basics
order: 6
---

# Templates

jimble's templates are [jte](https://jte.gg/).
Rather than interpreting strings at runtime, **it compiles them to Java.**

```java snippet=view-route
```

Whatever you put in with `putData()` arrives as `Data`, and `view()` names the template.

## The template side

`src/main/jte/blog/posts.jte`:

```jte
@import db.blog_example.table.post.Post
@import io.jimble.util.data.Data
@import java.util.List

@param Data data

!{List<Data> posts = data.getDataList("posts");}

<h1>${data.getString("title")}</h1>

@for(Data post : posts)
	<b>${post.getString(Post.title)}</b>
	<p>${post.getString(Post.body)}</p>
@endfor
```

- `${ }` is **HTML-escaped**. Use `$unsafe{ }` to emit raw output
- Because of `@param` and `@import`, your types hold inside the template too
- You can look values up by `Column`, as in `Post.title`, so you never scatter string keys through your templates

## Sharing the frame

Writing `<html>` on every page means **changing the header means changing every page.**
Keep the frame in one layout, and let pages write only their own content.

`src/main/jte/layout/page.jte`:

```jte
@import gg.jte.Content
@import io.jimble.util.data.Data

@param Data data
@param Content content

<!doctype html>
<html lang="en">
<head>
	<title>${data.getString("title")}</title>
	<link rel="stylesheet" href="/assets/app.css">
</head>
<body>
	@template.tag.nav(current = data.getString("nav"))

	<main>${content}</main>
</body>
</html>
```

`src/main/jte/pages/home.jte`:

```jte
@import io.jimble.util.data.Data

@param Data data

@template.layout.page(data = data, content = @`
	<p>The content goes here.</p>
`)
```

`Content` is "something to render later". Pass a block wrapped in `@` and backticks,
and it lands where the layout writes `${content}`.

**The call name mirrors the directory layout.**
`src/main/jte/layout/page.jte` is `@template.layout.page(...)`,
`src/main/jte/tag/nav.jte` is `@template.tag.nav(...)`.

## Reusable parts

The same mechanism gives you reusable parts.

`src/main/jte/tag/card.jte`:

```jte
@import gg.jte.Content

@param String title
@param Content body
@param String note = ""

<section class="card">
	<h2>${title}</h2>
	${body}
	@if(!note.isEmpty())
		<p class="note">${note}</p>
	@endif
</section>
```

- **`@param` can carry a default** (`= ""`). Callers may then omit it
- **A part travels better when it takes what it needs rather than a whole `Data`.**
  Hand it `Data` and the caller has to read the template to learn which keys matter

`examples/approval-pages` has all of this running.

> [!TIP]
> **Share the layout with your error pages too.**
> Otherwise your 404 is the one page that looks like a different site.

## Comments

**A jte comment is `<%--` … `--%>`.**

```jte
<%-- this does not reach the output --%>
```

> [!WARNING]
> **`@*` … `*@` is not a comment.**
> It is written out verbatim, and **any `@template.` inside it is executed**.
> Put a layout's own usage example inside one, inside that layout, and you get
> **infinite recursion and a `StackOverflowError`** (we walked into this one).
>
> **Comments do not nest.** Write the closing marker inside your prose and the comment ends there.
>
> **HTML comments (`<!-- -->`) are also stripped by default.**
> Markers you want the browser or an outside tool to see belong in a static file, not a template.

## Building

Add the `io.jimble.jte` plugin and `src/main/jte` is converted to Java
before `compileJava`.

```kotlin
plugins {
	application
	id("io.jimble.jte")
}
```

**A mistake in a template breaks the build.** You never discover it by hitting a 404 at runtime.

What you can configure is in [the Gradle plugins](./gradle).

The compiler (`gg.jte:jte`) is used at build time only;
the application's runtime classpath carries nothing but `gg.jte:jte-runtime`.

## Hot reload

If you started with `./gradlew jimbleRun`, the moment you edit a `.jte`
the conversion and the build run, and the next request serves it.
