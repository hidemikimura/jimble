---
title: File upload
summary: Receiving multipart, temp files, limits, saving and downloading
section: Basics
order: 3
---

# File upload

Files arriving as `multipart/form-data` come out of `bodyFile()`.

```java
Data files = context.request().bodyFile();
```

> [!TRAP]
> **Files appear in neither `bodyAll()` nor `bodyForm()`.** Only in `bodyFile()`.
> The text fields sitting in the same body still come out of `bodyAll()`, as usual.

## Receiving

```java snippet=upload-read
```

What you pull out is an `UploadFile`. It is **a container with no methods**, and this is all it holds.

| Field | What it holds |
| --- | --- |
| `name` | The form's `name` attribute |
| `fileName` | The file name **the client claimed** (`""` when absent) |
| `contentType` | The Content-Type **the client claimed** (`""` when absent) |
| `fileSize` | The number of bytes actually written out |
| `file` | The temp file (`java.io.File`) |

> [!WARN]
> **Even a single file is wrapped in a `List`.**
> Cast the value from `bodyFile()` straight to `UploadFile` and it blows up.
> Since several files can be sent under one `name`, always treating it as a list is the correct shape.

## Temp files

| | |
| --- | --- |
| Where they go | `upload.temp_dir` (defaults to `java.io.tmpdir`) |
| Name | `jimble-upload-<sequence>.tmp`. **The original file name is not used** |
| When they vanish | **When the request ends** (the context closes) |

> [!TRAP]
> **You cannot touch `uploadFile.file` after the request has ended.**
> Hand the work to an async job, or store only the path in the DB and read it later,
> and you grab **a file that is already gone**. Copy anything you want to keep, inside the handler.

Temp files do not get `deleteOnExit()`.
If the process dies they stay behind, and we leave that to the OS cleaning out its temp directory.

## Limits

**The limits apply whether or not you configure them** (requirement NF-S-05).

```conf
upload {
	max_file_size  = 10485760   # per file. 10MB by default
	max_total_size = 52428800   # per request, in total. 50MB by default
	max_files      = 20         # how many
	temp_dir       = ""         # empty means java.io.tmpdir
}
```

Going over throws on the spot, and **the half-written temp files are deleted too.**

The messages themselves come out of jimble in Japanese; the English in
parentheses is a gloss, not what the response says.

| Condition | What comes back |
| --- | --- |
| Too many files | **413**「アップロードできるファイル数は N 件までです」（"at most N files"） |
| One file, or the total, over the limit | **413**「アップロードのサイズが上限（N バイト）を超えています」（"over the N byte limit"） |
| Not readable as multipart | **400**「multipart を読めませんでした」（"could not read the multipart body"） |

Size is checked **as the data is read**. Measure it after taking everything in
and you have already written the excess to disk.

> [!WARN]
> **`server.max_request_size` (10MB by default) bites first.**
> `upload.max_total_size` defaults to 50MB, so **out of the box, 50MB never arrives.**
> If you want to allow large uploads, raise both.

## Saving

**There is no save helper.** You copy `uploadFile.file` with the JDK's `Files`.

```java snippet=upload-save
```

Three things matter.

| | |
| --- | --- |
| **You pick the name** | `UUID.randomUUID() + extension` |
| **Check the extension against an allowlist** | Reject with 400 when it fails |
| `Files.copy` | The original is deleted anyway, so copy or move — either is fine |

> [!TRAP]
> **Do not use `uploadFile.fileName` as the destination.**
> jimble **does not inspect this name at all** (it is exactly what the client claimed).
> A name like `../../etc/passwd` lets the write land outside your storage directory.

> [!WARN]
> **`contentType` is not inspected either.** It is the client's own declaration.
> An executable will arrive claiming to be `image/png`.
> Write the extension allowlist, and content inspection if you need it, yourself.

## Mixing with text fields

They sit in the same body, so **we parse once and hold the result** (the body can only be read once).
Parts with no file name are treated as text fields and appear in `bodyAll()`.

> [!NOTE]
> Text fields are read as **UTF-8, always**, and **held entirely in memory** (there is no size limit).
> The only thing guarding this is `server.max_request_size`.

## Sending files back (download)

```java
context.response().download(file);                  // attach under the real file name
context.response().download(file, "report.xlsx");   // attach under a name you choose
context.response().file(file);                      // inline (type detected from the content)
context.response().file(file, "image/png");         // inline (type given)
context.response().stream(in, "text/csv");          // stream
context.response().stream(in, "text/csv", length);  // stream of known length
```

`Content-Disposition` is written in the RFC 5987 form (`filename*=UTF-8''...`),
so **non-ASCII file names go through as they are.**

> [!NOTE]
> The compatibility `filename="..."` (the ASCII form) is not written alongside it.
> Also, **a space in the name becomes `+`** (not `%20`).
> If that bothers you, replace spaces with `_` before you pass the name in.
