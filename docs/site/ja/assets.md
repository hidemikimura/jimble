---
title: 静的ファイルと SPA
summary: assets / SPA / MPA。jar の中から配る、ETag、書き換え
section: 基本
order: 7
---

# 静的ファイルと SPA

配り方は3つあります。**どれもルーティング本体とは別のハンドラ**です（要件 F-W-20）。

| やりたいこと | 使うもの | 当たらなかったら |
| --- | --- | --- |
| CSS・JS・画像をそのまま配る | `AssetHandler` | 404 |
| SPA（どのパスでも同じ index.html） | `SpaHandler` | いちばん近い `index.html` |
| MPA（ディレクトリごとに index.html） | `MpaHandler` | 拡張子が無ければ `<パス>/index.html` |

**中身はクラスパスから読みます。** jar に入れたまま配れますし、
開発中は `build/resources/main` から読まれます。**どちらでも同じ動きです**（要件 F-W-18）。

## 静的ファイル

```java snippet=assets-mount
```

第2引数は**クラスパス上のディレクトリ**です。ファイルシステムのパスではありません。
`AssetHandler.mount("/assets", "assets")` なら
`src/main/resources/assets/app.css` が `/assets/app.css` で出ます。

`GET` と `HEAD` の2本（`/assets/*`）が登録されます。

> [!WARNING]
> **`/assets` 自身（末尾なし）には応えません。**登録されるのは `/assets/*` だけです。
> SPA と MPA は接頭辞そのものも登録するので、そこだけ形が違います。

登録されたルートは `routes()` で受け取れます。**属性を付けられます。**

```java
AssetController assets = AssetHandler.mount("/assets", "assets");
for (Route route : assets.routes()) {
	route.attribute(NO_AUTH, true);
}
install(() -> assets);
```

`SpaHandler.mount(...)` / `MpaHandler.mount(...)` でも `routes()` は同じように使えます。
ただし**本数が違います**——SPA と MPA は接頭辞自身も登録するので4本です
（`/app` と `/app/*` の `GET` と `HEAD`）。

### キャッシュ

| 拡張子 | `Cache-Control` |
| --- | --- |
| `.js` `.css` `.woff` `.woff2` | `public,max-age=31536000,immutable` |
| そのほか | `public,max-age=<assets.max_age>,must-revalidate` |

動的なレスポンスの既定は `no-store` です（要件 F-X-07）。**静的配信だけがここを上書きします。**

```conf
assets {
	max_age            = 0        # 通常のファイル
	immutable_max_age  = 31536000 # 内容ハッシュ付きのファイル向け（1年）
	etag               = true
	if_modified_since  = true
}
```

> [!WARN]
> `immutable` が付くのは**拡張子だけ**で判断しています。
> `app.js` を中身を変えて置き直しても、**ブラウザは1年取りに来ません。**
> ファイル名にハッシュを入れる（`app.9f3a1c.js`）か、`assets.immutable_max_age` を下げてください。

### 条件付き GET

- `ETag` は弱い ETag（パス・更新時刻・サイズの md5）
- `Last-Modified` は `URLConnection` の更新時刻
- 判定は **`If-None-Match` が先**。無ければ `If-Modified-Since`
- 一致したら **本文を読まずに 304**

### 無いもの

| | |
| --- | --- |
| Range（部分取得） | **ありません。**`206` は返りません |
| 個別の圧縮 | **ありません。**サーバー全体の `server.compression`（helidon）に任せています |
| ディレクトリ一覧 | 出しません |

## SPA

```java snippet=spa-mount
```

`/app/...` に来たとき、こう探します。

1. 実ファイルがあれば**それを返す**（`AssetHandler` と同じヘッダ）
2. 無ければ、そのパスから上へ順に `index.html` を探す
3. 最後にベース直下の `index.html`
4. それも無ければ 404

**通常のルートと共存します。**`/api/items` を先に書いてあれば SPA には食われません。
ツリーの優先順（固定セグメント &gt; パスパラメータ &gt; ワイルドカード）がそのまま効きます。

### パスごとに index.html を書き換える

クローラや OGP のために `<title>` や `<meta>` を差し替えたいときに使います。

```java snippet=spa-rewrite
```

パスの書き方は本体のルートと同じです（`{id}`、末尾の `/*`）。
**マッチも本体と同じルートツリーがやります**（要件 D-75）。

| | |
| --- | --- |
| 優先順 | 固定 &gt; 変数 &gt; ワイルドカード。**書いた順には依存しない** |
| パスパラメータ | `context.request().bodyPath().getString("id")` |
| 重複したパス | 登録時に落ちる |
| 本体のルート一覧 | **混ざらない**（別インスタンス。要件 F-W-20） |

> [!NOTE]
> 書き換えたときだけ `ETag` と `Last-Modified` は付きません（中身がリクエストごとに変わるため）。
> `Cache-Control` は `public,max-age=<assets.max_age>,must-revalidate` になります。

## MPA

```java snippet=mpa-mount
```

拡張子があればファイル、無ければ `<パス>/index.html` を返します。
`/docs/guide` → `docs/guide/index.html` です。

## 安全側の既定

パスは配る前に必ず検査します。**危ないものは 404** です（何があるかを教えないため）。

弾くもの：NUL 文字、バックスラッシュ、空のセグメント（`//`）、`.` と `..` のセグメント。

> [!TIP]
> `%2e%2e` のようなパーセントエンコードも弾けます。
> セグメントごとにデコードしてから検査するので、`..` が復元されたところで捕まります。

## 開発中に直したとき

`jimbleRun` の監視対象には `.html` / `.js` / `.css` が入っています。
保存すればリソースがビルドし直され、次のリクエストで反映されます（[ホットリロード](./hot-reload)）。

> [!TRAP]
> **アプリを止めずにファイルだけ差し替えると、古いままになることがあります。**
> 「ファイルがあるか」と `index.html` の中身をメモリに覚えているためで、
> 消す口は `Resources.clearCache()` しかありません。`jimbleRun` を使っていれば入れ替えのたびに作り直されます。
