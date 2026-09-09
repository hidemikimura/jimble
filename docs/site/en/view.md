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
