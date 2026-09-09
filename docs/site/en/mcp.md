---
title: MCP
summary: Stand up a Model Context Protocol server (2026-07-28 / Streamable HTTP)
section: Protocols
order: 3
---

# MCP

Expose your application's features in a form an AI can call.
What jimble implements is the **2026-07-28** revision of Streamable HTTP.

## Listing what you expose

```java
public class BlogMcp extends McpController {

	{
```

```java snippet=mcp-controller
```

```java
	}

}
```

```java
install(BlogMcp::new);
```

**Read it top to bottom and you know everything this server exposes.**
No annotations, no classpath scanning. Java MCP implementations usually declare things with
annotations and scan at startup, and then you cannot tell which classes were picked up until you run it.

## Writing a tool

```java snippet=mcp-tool
```

`description()` is **read by the model**. It is not a comment for humans.
Write when to use it, and **what it cannot do**.
One sentence saying it cannot return history or forecasts cuts out a lot of pointless calls.

You declare the shape of the input as JSON Schema.

```java snippet=mcp-input-schema
```

## Returning a failure

There are two kinds of failure.

| Which one | How to return it |
| --- | --- |
| The model can read it and fix it (bad argument, no such target) | `ToolResult.error("...")` |
| It cannot be fixed (the DB is down, the configuration is missing) | Throw an exception |

`ToolResult.error()` sets `isError` and returns it **as a normal response**.
The model reads that, changes the arguments, and calls again.
Turn it into an exception and all the model sees is "it broke".

## Exposing an API you already have

Often the API comes first and MCP comes later.
Write the same logic a second time inside the tool and **one of the two will go stale.** Guaranteed.

`RouteTool` **turns a route you have already registered into a tool, as it is**.

```java
public class BlogMcp extends McpController {

	{
		tool("list_posts", RouteTool.of("GET", "/api/posts")
			.description("Return the list of posts. page picks the page")
			.input(JsonSchema.object()
				.integer("page", "Page number, starting at 1").min(1)));

		tool("get_post", RouteTool.of("GET", "/api/posts/{id}")
			.description("Return one post")
			.input(JsonSchema.object()
				.string("id", "Post ID").required()));

		tool("create_post", RouteTool.of("POST", "/api/posts")
			.description("Create one post")
			.input(JsonSchema.object()
				.string("title", "Title").required()
				.string("body", "Body").required()));
	}

}
```

No HTTP is involved. The dispatcher **calls that route directly**.

> [!note]
> **`before` and `after` both apply.**
> An API with its authentication in a `before` is still authenticated when it is called through MCP
> (the headers and cookies of the MCP request carry straight through).
> There is no separate internal-only path, so **you cannot add authentication and forget one side**.

### Where each argument goes

| Argument | Where it goes |
| --- | --- |
| Same name as a `{name}` in the path | Into the path |
| The rest (`GET` / `DELETE` / `HEAD`) | The query string |
| The rest (anything else) | The JSON body |

Put the `{name}`s from the path into `input(...)` and mark them `required()`.
Leave them out and the model has no idea what to pass.

A route containing a wildcard (`/files/*`) **is refused at registration time**.
Letting it through with no way to fill it in would give you a silent 404 on every call.

### What comes back

| What the API returned | The tool result |
| --- | --- |
| 2xx with a JSON object | Both `structuredContent` and the body as text |
| 2xx with anything else | The body as text |
| 4xx | `isError` (**the body is passed through untouched**) |
| 5xx | `isError` (**the body is not passed**) |

A 4xx body was written for a client to read, so handing it to the model as-is lets the model
fix things ("no such post: 999").

A 5xx body was never written for anyone to read. Depending on the error handler it can carry
a stack trace, so **we do not pass it** (it still goes to the log).

### Things to watch for

- An API that takes an upload cannot be called (ownership of the temp file gets murky, so the file is empty)
- An API that returns a large file puts all of it in memory
- An API that returns bytes still compressed (`response().cache(...)`) becomes an `isError`, because it cannot be read

> [!trap]
> **If you can share the domain layer, do that first.**
> `RouteTool` is the tool for "I want to expose the thing that is **already assembled as an API** —
> validation, formatting, permissions and all — exactly as it is."
> The inside is **a different transaction from the outside**, so you cannot use it to call
> several APIs from one tool and commit them together.

### Calling it without a tool

The same machinery works from an ordinary handler.

```java
CallResponse response = context.dispatcher().call(context
	, CallRequest.of("GET", "/api/posts").query("page", "2"));

Data json = response.json();
```

Nesting has a limit (8).
A route that calls itself would go down forever, so it stops there and throws.

## Resources and prompts

```java
resource("blog://latest", LatestPostsResource::new);
prompt("summarize", SummarizePrompt::new);
```

A resource is read-only data. A prompt is a canned instruction.

## One way in

The only thing exposed is `POST /mcp`.

- **GET and DELETE are both refused with a 405.** Both left the spec in 2026-07-28
- **There are no sessions.** `Mcp-Session-Id` is not used
- **The server never sends a request.** It only sends responses

To change the path, configure it.

```conf
mcp {
	path            = "/mcp"
	name            = "blog"
	version         = "1.0.0"
	allowed_origins = ["https://example.com"]
}
```

## Always configure Origin

Without `allowed_origins` there is nothing to check the `Origin` header against.
An MCP server a browser can reach is **a target for DNS rebinding**.
The more local the server, the more dangerous it is (`localhost` exists on everybody's machine).

## Connecting to it

```json
{
	"mcpServers": {
		"blog": {
			"type": "http",
			"url": "http://localhost:9000/mcp"
		}
	}
}
```
