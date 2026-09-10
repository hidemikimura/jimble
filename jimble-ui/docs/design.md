# jimble-ui 設計書

版: 0.8.0 / 2026-09-10

## 0. ビルドと実行

```
npm install          # typescript と lit の型だけ（実行時の依存はゼロ / TypeScript 5.9 で検証）
npm run build:all    # src → dist、examples/*.ts → examples/*.js
npm start            # http://localhost:8080/examples/staff/index.html
npm run check        # 型検査だけ（本体と全 examples）
```

配信するのは `dist/` と `vendor/` と HTML だけ。バンドラは使わない。

## 1. 目的

Java 側で `SQLBuilder` が「SQL 文字列を書かずに、型と補完の効くビルダーでクエリを組む」ことを実現しているのと同じことを、
フロントエンドで実現する。

* **HTML と CSS を書かない／読まない**
* **画面の構造とロジックを 1 つのコードで書きつつ、業務ロジックは分離できる**
* **SPA として成立する**
* **AI に書かせたコードの誤りが、その場で分かる**（型で止める・実行時に大きな音で壊す）

## 2. 何をどこに隔離するか

```
┌─────────────────────────────────────────────┐
│ アプリのコード（画面 + ロジック）             │  ← JavaScript だけ
│   UI.column(UI.text('name').bind('form.name'))│     HTML/CSS は 1 文字も出てこない
├─────────────────────────────────────────────┤
│ ビルダー層  src/builders/                     │  ← 「何を出すか」を組み立てる
│   StackBuilder / InputBuilder / ButtonBuilder │     DOM はまだ作らない
├─────────────────────────────────────────────┤
│ コンポーネント層  src/components/             │  ← 状態と振る舞いだけ
│   jb-stack / jb-input / jb-button / jb-text   │     マークアップも CSS も持たない
├─────────────────────────────────────────────┤
│ テーマ層  src/themes/                         │  ← HTML と CSS が存在する唯一の場所
│   original / bootstrap5 / tailwind-dark       │     差し替え可能。Shadow DOM 内に適用される
├─────────────────────────────────────────────┤
│ lit（同梱 vendor/lit.js）                     │
└─────────────────────────────────────────────┘
```

隔離は 3 重になっている。

1. **記述の隔離**：アプリのコードは `UI.*` しか呼べない。タグ名も CSS クラス名も出てこない。
2. **実行時の隔離**：コンポーネントは Shadow DOM を使う。外の CSS は中に入らず、中の CSS は外に漏れない。
3. **見た目の隔離**：コンポーネント自身もマークアップを持たない。
   「どんな HTML を出し、どんな CSS を当てるか」はテーマが持ち、実行時に差し替えられる。

## 3. SQLBuilder との対応

| jimble-db (Java) | jimble-ui (JavaScript) | 役割 |
| --- | --- | --- |
| `SQL`（静的入口） | `UI`（静的入口） | ビルダーを作り始める場所 |
| `AbstractBuilder` / `IBuilder` | `Builder` | フルーエントな組み立ての基底 |
| `sql(Dialect)` | `render(Context)` | 出力を作る唯一のメソッド |
| `Dialect`（接続先の方言） | `Context`（状態・ルータ・API） | 出力時に外から与えられる環境 |
| `params()` | `Store` | 値の置き場所 |
| `Table` / `Column` 定義 | `jb-*` コンポーネント | 組み立ての語彙 |
| `Dsl.now()` などの関数群 | `UI.text()` などの生成関数 | 語彙への入口 |
| `Dialects`（MySQL / PostgreSQL） | `Theme`（original / bootstrap5 / tailwind-dark） | 最終的な出力の方言 |
| `DB.query(builder)` | `App.mount()` | 組み立て結果を実行する |

「ビルダーは状態を持たず、出力時に環境を受け取る」という性質が同じなので、
`SELECT` を組み立てる感覚のまま画面が書ける。

## 4. 誤りを見つける仕組み

このフレームワークは「AI にフロントを書かせる」ことを前提にしている。
AI が間違える箇所はほぼ決まっているので、そこを<b>型</b>と<b>実行時の即時エラー</b>で塞ぐ。

### 4.1 型で止める

アプリは状態の形を 1 か所で宣言する。以降、パスも値も検査される。

```ts
declare global {
	interface JimbleAppState {
		form: { name: string; age: number | null };
		list: Staff[];
	}
}

ctx.get('form.name');        // string
ctx.get('form.nmae');        // コンパイルエラー（存在しないパス）
ctx.set('form.name', 123);   // コンパイルエラー（型違い）
UI.text('name').bind('form.nmae');  // コンパイルエラー
```

`JimbleAppState` を拡張しなければ何でも通る（型の恩恵は無いが、素の JavaScript でも動く）。
既定の `JimbleAppState` は索引シグネチャ（`[key: string]: any`）だけを持ち、
`Path<T>` はその索引シグネチャ由来のキーを落としてから組み立てる。
そのため「拡張したときだけ厳しくなる」が成立する。

> 状態の型はグローバル拡張で 1 か所に置く。ビルダーや画面関数を総ジェネリクスにすると、
> `Context<JimbleAppState>` と `Context<Record<string, any>>` が混ざって破綻するため
> （TypeScript 5.9 で実際に踏んだ）。アプリ 1 つ ＝ tsconfig 1 つ ＝ 状態 1 つ。

### 4.2 実行時に大きな音で壊す

型で防げないもの（JavaScript から呼ぶ、動的な値、テーマの取り違え）は実行時に落とす。

| 誤り | 何が起きるか |
| --- | --- |
| 存在しないメソッド（`UI.text('x').onSubmit(...)`） | 即例外。`もしかして: onChange / onInput` と候補を出す |
| 宣言していない状態を読み書き（`ctx.get('conut')`） | 即例外。`もしかして: count` |
| 登録していないルートへの遷移（`ctx.go('/nowhere')`） | 即例外。登録済みパスの一覧を出す |
| テーマにテンプレートが無いコンポーネント | その場所に赤い枠で表示 ＋ 例外。**黙って空白にしない** |
| 描画中・onClick 内・enter 内の例外 | 捕捉して赤いパネルに出す（画面全体は巻き添えにしない） |

* 開発モードでは例外＋画面下部の赤いパネル。本番モードでは `console.error` だけで画面は落とさない。
  切り替えは `App.of().mode('production')`。
* 存在しないメソッドの検出は、開発モードのみビルダーに `Proxy` を掛けて行う（本番では素通し）。
  メソッドが `this` を返すため、Proxy は連鎖の途中で外れないよう包み直している。
* 同じ内容は 1 回だけ表示する（再描画のたびに増えない）。

「黙って空白」「黙って undefined」を無くすことが、この層の唯一の目的。

## 5. 実行モデル

```
状態(Store) ──変更──▶ App が再描画を予約(rAF)
                          │
                          ▼
            画面関数(ctx) ──▶ ビルダー木 ──render(ctx)──▶ lit テンプレート
                                                              │
                                                              ▼
                                                     lit が差分だけ DOM に反映
```

* 画面は毎回まるごと組み直すが、DOM を触るのは lit の差分適用だけなので、入力欄のフォーカスもカーソル位置も保たれる。
* 1 マイクロタスク内の複数の状態変更は 1 回の描画にまとめられる。
* 仮想 DOM の実装を自前で持たない。差分処理は lit に委ねる。

## 6. テーマ（マークアップと CSS の外出し）

同じ `UI.button('登録する').primary()` が、テーマによって別の HTML になる。

| テーマ | jb-button が出す HTML |
| --- | --- |
| `original` | `<button><span>登録する</span></button>` ＋ `:host` に当てた独自 CSS |
| `bootstrap5` | `<button type="button" class="btn btn-primary">登録する</button>` |
| `tailwind-dark` | `<button type="button" class="inline-flex h-9 ... bg-indigo-500 text-white">登録する</button>` |

### 6.1 テーマ定義

```js
registerTheme({
	name: 'bootstrap5',
	tokens: { 'color-bg': '#f8f9fa' },          // --jb-* として :root に流す
	base: 'body { ... }',                        // 文書側（Shadow DOM の外）の土台 CSS
	shared: [() => import('../../vendor/bootstrap5.css.js')],  // 全コンポーネント共通 CSS（遅延読み込み）
	components: {
		'jb-button': {
			styles: ':host { display: inline-block; }',
			template: (el, html) => html`
				<button class=${BUTTON[el.variant]}
					?disabled=${el.disabled}
					@click=${(e) => el.handleClick(e)}>${el.label}</button>`
		}
	}
});
```

* `template(el, html)` は **コンポーネントの状態（el）だけ**を見て HTML を作る純粋な関数。
* CSS は `CSSStyleSheet` にして `adoptedStyleSheets` で Shadow Root に流し込む。
  同じシートを全インスタンスで共有するので、Bootstrap のような大きい CSS でも実体は 1 つ。
* Bootstrap のように `:root` へ変数を定義する CSS は、文書側にも適用する
  （カスタムプロパティは Shadow DOM の内側へ継承されるため、これで中でも `--bs-*` が効く）。

### 6.2 選び方・切り替え方

```js
App.of().theme('bootstrap5').mount();     // 起動時に指定
await ctx.app.useTheme('tailwind-dark');  // 実行中に切り替え（状態は保持される）
```

`App#mount()` はテーマの CSS 読み込みを待ってから最初の描画を行う（未装飾の一瞬を出さないため）。
切り替え時は、表示中の全コンポーネントが `subscribeTheme` 経由で
スタイルシートの差し替えと再描画を行う。

### 6.3 テーマの継承

既存テーマを継承して、**書いた差分だけ**を上書きできる。
社内デザインを作るのに、全コンポーネントを書き直す必要はない。

```js
Theme.extend('original', {
	name: 'ecx',
	tokens: { 'color-primary': '#0f766e', 'radius': '4px' },        // 1. 色と角丸だけ差し替え
	components: {
		'jb-stack': { styles: ':host([jb-surface]) { border-left: 3px solid var(--jb-color-primary); }' },  // 2. CSS を追加
		'jb-input': { template: (el, html) => html`...` }            // 3. テンプレートを差し替え
	}
});
```

マージ規則は次のとおり。

| 項目 | 規則 |
| --- | --- |
| `tokens` | 親に重ねる（同名は子が勝つ） |
| `base` | 子が指定すれば置き換え、省略すれば親のまま |
| `shared` | 親の**後ろに**足す |
| `components[tag].template` | 指定すれば差し替え、省略すれば親のまま |
| `components[tag].styles` | 親の CSS の**後ろに**足す（同じ宣言なら後勝ちで上書きできる） |
| `components[tag].replaceStyles` | 親の CSS を捨てて置き換える |

* 多段の継承（`original` → `ecx` → `ecx-dark`）もできる。循環は起動時に例外で落とす。
* CSS は「親のシート → 子のシート」の順で `adoptedStyleSheets` に並ぶので、
  子は同じセレクタを書くだけで上書きできる。詳細度を上げる必要はない。
* テンプレートを差し替えるときは、そのコンポーネントのマークアップを全部書く
  （部分的な差し込みは持たせていない。テンプレートは「状態 → HTML」の純粋な関数のままにしたいため）。

`examples/ecx-theme.js` が実例（約 90 行で社内デザインのテーマになる）。

### 6.4 CSS フレームワークの取り込み方

| テーマ | CSS の作り方 | サイズ |
| --- | --- | --- |
| `original` | テーマファイル内に直接書く | 数 KB |
| `ecx`（`original` を継承） | 差分だけ書く | 1KB 未満 |
| `bootstrap5` | npm の `bootstrap.min.css` をそのまま文字列モジュール化 | 227KB |
| `tailwind-dark` | tailwindcss CLI にテーマファイルを走査させ、**使っているクラスだけ**抽出 | 10KB |

生成は `sh tools/build-themes.sh` で再実行できる。
Tailwind は「クラス名を文字列そのままで書く」のが条件（`'bg-' + color` のような組み立ては検出されない）。

## 7. 主要 API

### 7.1 入口

| API | 説明 |
| --- | --- |
| `App.of()` | アプリを作る |
| `.state(obj)` | 初期状態 |
| `.theme('bootstrap5')` | テーマ（original / bootstrap5 / tailwind-dark） |
| `.useTheme(name)` | 実行中のテーマ切替（Promise） |
| `.mode('production')` | 本番モード（誤りで画面を落とさない） |
| `Theme.extend(親, 差分)` | テーマを継承して登録する |
| `themeNames()` | 登録済みテーマ名の一覧 |
| `.api(Api.of('/api'))` | API クライアント（`ctx.api`） |
| `.layout((page, ctx) => ...)` | 全画面共通の外枠 |
| `.route(path, view, { enter })` | 画面。`enter` は表示前に走る非同期処理（データ取得） |
| `.notFound(view)` | 未定義パス |
| `.mode('history')` | pushState 方式に変更（既定はハッシュ） |
| `.mount(target?)` | 起動。引数を省略すると body 直下に描画先を作る |

### 7.2 ビルダー

| API | 説明 |
| --- | --- |
| `UI.column(...)` / `UI.row(...)` / `UI.card(...)` | レイアウト。`.gap() .pad() .align() .justify() .width() .wrap()` |
| `UI.title() / heading() / label() / caption()` | 文字表示 |
| `UI.text(name)` / `number` / `password` / `date` / `textarea` | 入力。`.label() .placeholder() .hint() .required() .disabled() .error() .bind() .value() .onInput() .onChange()` |
| `UI.button(label)` | ボタン。`.primary() .danger() .quiet() .icon(name) .disabled() .loading() .onClick() .go()`。`.danger().quiet()` で「枠なしの赤」 |
| `UI.icon(name)` | 飾り。`.sm() .lg() .size() .alt()`。図形はテーマ層（`src/themes/icons.ts`）が持つ |
| `UI.select(name)` | 選択。`.options() .bind() .label() .placeholder() .required() .error() .onChange()` |
| `UI.checkbox(name)` / `UI.toggle(name)` | チェックボックス / スイッチ。`.label() .bind() .onChange()` |
| `UI.radio(name)` | ラジオ。`.options() .inline() .bind() .onChange()` |
| `UI.form(...)` | フォーム。中の入力で Enter が押されたら `.onSubmit()` が走る |
| `UI.tabs(items)` | タブ。`.bind() .onChange()`。`badge` で件数を出せる |
| `UI.table(items)` | 表。`.column({key,label,cell,sortable,align,width}) .rowKey() .sort() .onSort() .onRowClick() .empty() .loading() .reorderable() .onReorder()` |
| `UI.menu()` | 行の「…」メニュー。`.item(label, fn) .danger(label, fn) .go(label, path) .divider() .trigger() .icon() .alignStart()` |
| `UI.sidebar()` | サイドバー。`.heading() .group() .item(path, label, {icon,badge}) .footer(...) .width() .fullHeight() .current() .onSelect()` |
| `UI.pageHeader(題名)` | 画面見出し。`.description() .breadcrumb() .actions(...) .below(...)` |
| `UI.pagination()` | ページ送り。`.page() .pages() .total() .onChange()` |
| `UI.dialog(...)` | ダイアログ。`.title() .open() .size() .footer(...) .onClose()`。Esc と背景クリックで閉じる |
| `UI.each(items, (item, i) => ...)` | 繰り返し。`.empty(node)` |
| `notify.success(msg)` | 知らせ（トースト）。`info / success / warning / error`、`{ duration }` |
| 共通 | `.when(cond)` / `.unless(cond)` / `.add(...children)` |

コンポーネントは 26 種。すべて 3 テーマに実装がある。

| 分類 | コンポーネント |
| --- | --- |
| レイアウト | `jb-stack`（縦・横・カード）、`jb-grid`（カード並べ）、`jb-form` |
| 表示 | `jb-text`（大見出し / 見出し / 本文 / 注釈 / 警告）、`jb-breadcrumb`、`jb-empty` |
| 入力 | `jb-input`（文字・数値・パスワード・日付・複数行）、`jb-select`、`jb-checkbox`、`jb-switch`、`jb-radio`、`jb-button`、`jb-file`（放り込み対応）、`jb-daterange` |
| 一覧 | `jb-table`（並べ替え・読み込み中・0 件表示）、`jb-pagination`、`jb-tabs`、`jb-accordion` |
| 数字・グラフ | `jb-stat`（統計タイル）、`jb-chart`（棒・折れ線） |
| 一覧の操作 | `jb-menu`（ドロップダウン。行の「…」） |
| 画面の骨組み | `jb-nav`（サイドバー）、`jb-page-header`（題名・説明・右のボタン） |
| 飾り | `jb-icon`（名前 → SVG。図形はテーマ層の `icons.ts`） |
| 重ね表示 | `jb-dialog`、`jb-toast` |

### 7.3 Shadow DOM ならではの実装

管理画面の部品を Shadow DOM の中に閉じ込めると、素直に書けない箇所がいくつかある。踏んだものを残しておく。

| 事象 | 対処 |
| --- | --- |
| 入力欄がそれぞれ別の Shadow DOM にいるため、`<form>` の「Enter で送信」が効かない | `jb-form` が `keydown` を拾って `jb-submit` を投げる。複数行入力の中の Enter は改行のまま |
| 表のセルに「詳細」ボタンのような部品を置きたい | ビルダー側でセルを描画して `TableRow.cells` に渡し、テーマは中身を知らずに `<td>` に置くだけにした |
| ダイアログの下部ボタン | 名前付きスロット（`footer`）。ビルダーが `<div slot="footer">` で包む |
| Bootstrap の modal は表示制御を JS が持つ | テーマ側で `d-block` を付け、背景と位置決めだけ CSS で足した |
| トーストはアプリの描画木の外に出す必要がある | `notify` が body 直下に置き場所を作り、`jb-toast` を差し込む |
| SVG の中身を lit の `html` で作ると描画されない（HTML 要素として解釈される） | テンプレートの第 3 引数に `svg` を渡し、`jb-chart` の中身はそれで作る |
| グリッドは utility クラスでは表しきれない | `jb-grid` は 3 テーマとも CSS Grid を直接書いている（列数は属性、自動折り返しは `min`） |
| 表の中でドロップダウンを開くと、表の `overflow` に切り取られる | `jb-menu` は開いた瞬間にボタンの位置を測って持ち（`anchor`）、テーマは一覧を `position: fixed` で置く。位置の組み立ては `themes/position.ts` の `menuPosition()` |
| 同じアイコンの図形を 3 テーマに書くと 3 重管理になる | 図形（SVG の `d`）は `src/themes/icons.ts` に 1 か所だけ置き、各テーマはそれを `svg` テンプレートで並べる。アイコンを増やすのはここ 1 ファイル |
| つかんで並べ替え（drag & drop）の状態をどこに置くか | `jb-table` が `dragIndex` / `overIndex` / `dropPosition` を持ち、`dragState(index)` で `dragging` / `drop-before` / `drop-after` を返す。テーマはそれを class に流すだけ。実際に並びを変えるのは画面側（`onReorder` → `reorder(list, detail)`） |

### 7.4 値は「値」でも「関数」でもよい

```js
UI.button('削除')
	.danger()
	.disabled((ctx) => ctx.get('selected').length === 0)   // 状態から計算
	.onClick(deleteSelected);                              // ロジックは外の関数
```

`resolve()` が両方を受けるので、静的な値と派生値を同じ書き方で扱える。

### 7.5 状態

```js
ctx.get('form.name');            // ドット区切りで取得
ctx.set('form.name', '山田');    // 設定すると再描画
ctx.store.patch({ list, loading: false });
```

`UI.text('name').bind('form.name')` と書けば、入力とストアが双方向に結び付く。

## 7.6 AI に書かせるためのリファレンス（skill）

このフレームワークは学習データに存在しないので、AI は前提知識をまったく持たない。
そこで **`jimble-ui/docs/SKILL.md`** に、これだけ読めば書ける分量のリファレンスを置いている。

リポジトリで Claude Code に読ませるときは、リポジトリ直下にコピーする。

```
mkdir -p .claude/skills/jimble-ui
cp jimble-ui/docs/SKILL.md .claude/skills/jimble-ui/SKILL.md
```

内容は「絶対規約 5 つ / 最小のアプリ / 状態 / App の全メソッド / ビルダー全 API /
ハンドラの引数 / 画面の型 3 つ / コンポーネントの増やし方 / テーマの継承 / やりがちな間違い」。

### 検証のしかた

リファレンスだけを渡した AI に画面を作らせ、コンパイルが通るか・実際に動くかで測った。
**src / dist / 既存 examples は読ませない**のが条件。

| 試行 | 課題 | コンパイル失敗 | 実行時 |
| --- | --- | --- | --- |
| 1 回目 | 商品マスタ（一覧＋表・タブ・ページ送り／登録／削除ダイアログ） | 1 回 | 動作。コンソールエラーなし |
| 2 回目 | システム設定（タブで出し分け・スイッチ連動・Enter 送信・履歴表） | 1 回 | 動作。コンソールエラーなし |
| 3 回目 | アクセス解析（統計タイル・グラフ 2 種・日付範囲・折りたたみ・添付・空状態） | 1 回 | 動作。コンソールエラーなし |

失敗した 2 回はどちらもリファレンスの穴だった。

1. `.onInput(fn)` の第 1 引数を `ctx` だと思った（実際は `(value, ctx)`）
   → **ハンドラの引数の一覧表**を追加
2. `UI.form(...).gap()` と書いた（`.gap()` は column / row / card 専用）
   → **全ビルダー共通のメソッドは 3 つだけ**と明記し、間違い例を載せた

3 回目の失敗は `noUnusedParameters`（使わない `ctx` は `_ctx` と書く）で、API の誤解ではなかった。

ほかに「`rowKey` の引数」「`bind` の入れ子の深さ」「`error()` が `undefined` を受けるか」
「`UI.form` に複数の子を渡せるか」「`UI.each` に関数を渡せるか」「`UI.grid` の中で `UI.each` が
展開されるか」「`name` 引数は何に使われるか」が読み取れないという指摘があり、すべて反映した。

**リファレンスを直す手順**：AI に新しい画面を書かせ、詰まった箇所を聞き、その穴を埋める。
コンポーネントを増やしたら、この検証をやり直す。

## 7.7 実アプリでの試用（aqSell 管理画面）

既存の aqSell 管理画面（Lit + Tailwind + Vite）の 1 業務
（ログイン → メーカー一覧 → 登録・編集 → 削除）を jimble-ui で作り直して確かめた。
成果物は別リポジトリ（`~/Downloads/jimble-ui-aqsell-admin`）。

**相性がよかった点**

* API 層（`domain/`）はそのまま移せた。`{ success, unauthed, data, error, validation }` の戻り値をそのまま使える
* 既存も Lit なので、コンポーネントの考え方（プロパティとイベント）が一致している
* ルーティングが `path / render / enter` と同じ形で、`enter` でのデータ取得もそのまま移った
* CSRF と Cookie は `Api` を継承して足せた（`protected send()` が拡張点として効いた）

**この試用で足したもの**

| 追加 | きっかけ |
| --- | --- |
| `UI.select().value()` / `UI.radio().value()` | 「状態に結び付けず値だけ渡す」場面。`UI.text()` にはあったのに select に無かった |
| `.width('220px')` のような任意の長さ | サイドバーの幅。トークン（sm/md/lg/full）だけでは足りない |

**足りなかったので 0.8.0 で足し、この試用を書き直したもの**

行の「…」ドロップダウンメニュー（`UI.menu()`）/ つかんで並べ替え（`.reorderable()` + `.onReorder()`）/
アイコン（`UI.icon()`・ボタンやメニューの `icon`）/ 危険操作の文字ボタン（`.danger().quiet()`）/
サイドバーと画面見出し（`UI.sidebar()` / `UI.pageHeader()`）。

書き直した結果、一覧の行は「つかむ目印＋データ＋『…』1 つ」になり、外枠と見出しは
アプリ側で組む必要がなくなった。並べ替えは既存 API の
`move_any_previous` / `move_any_back`（指定データの前／後ろへ）と 1 対 1 に対応するため、
**ドラッグ 1 回につき API 1 回**で済む（↑↓ の連打が不要）。
画面の並びを先に入れ替え、失敗したら戻す。

実 API（手元の jooby）で、ログイン・一覧・並べ替え・メニュー・編集・削除まで動作を確認済み。

**残っている課題**：ページをまたぐ並べ替え（現状は同一ページ内のみ）。

## 8. ディレクトリ構成

```
jimble-ui/
	src/                  TypeScript の実装（tsc で dist/ を作る）
		core/
			builder.ts    ビルダー基底・値の解決・ノード描画
			store.ts      状態ストア（購読・バッチ通知・未宣言キーの検出）
			router.ts     ハッシュ / History ルータ
			app.ts        起動・再描画・レイアウト
			context.ts    描画コンテキスト
			theme.ts      テーマ機構（テンプレート・CSS・継承）
			api.ts        REST クライアント
			dev.ts        診断（即例外・画面表示・候補提示・Proxy 見張り）
			types.ts      共有する型（状態パスの型を含む）
		components/       コンポーネント 26 種（状態と振る舞いだけ）
		core/reorder.ts   並べ替えの内容（ReorderDetail）と reorder() ヘルパー
		themes/           テーマ（HTML と CSS の唯一の置き場）
			original.ts       素の Shadow DOM 実装
			bootstrap5.ts     Bootstrap 5 のクラスで組む
			tailwind-dark.ts  Tailwind のクラスで組む（ダーク）
			icons.ts          アイコンの図形（3 テーマ共通）
			position.ts       ドロップダウンの位置決め（3 テーマ共通）
			（継承テーマの例は examples/ecx-theme.js）
		builders/         コンポーネントに 1 対 1 で対応するビルダー
		ui.js             UI 静的入口
		index.js          公開 API
	dist/                 tsc の出力（.js と .d.ts）。配信するのはここ
	examples/
		staff/            デモ SPA（一覧・登録・詳細＋テーマ切替）
		dashboard/        ダッシュボード（統計・グラフ・添付・空状態）
		login/            ログイン画面
		errors/           わざと壊して診断を確認するページ
		parts/            管理画面の骨組み（サイドバー・見出し・行のメニュー・並べ替え）
	tools/                vendor/ の CSS を作り直すスクリプト
	docs/SKILL.md         AI 向けリファレンス（skill の中身）
	vendor/
		lit.js            同梱した lit（ビルド不要にするため）
		bootstrap5.css.js Bootstrap 5（bootstrap5 テーマ用・遅延読み込み）
		tailwind-dark.css.js Tailwind 抽出済み（tailwind-dark テーマ用・遅延読み込み）
	docs/design.md        この文書
```

## 9. コンポーネントの増やし方

「選択（`UI.select`）」を追加する場合の手順。

1. `src/components/jb-select.ts` を作る。`JbElement` を継承し、
   **プロパティと `handleXxx`（テンプレートから呼ばれる処理）だけ**を書く。
   マークアップも CSS も書かない。値が変わったら `jb-change` を
   `bubbles: true, composed: true` で投げる。
   **属性名には `jb-` を付ける**（`align` や `width` は HTML の既定スタイルが解釈してしまうため）。
2. `src/components/index.ts` に追加する。
3. **各テーマ**の `components` に `'jb-select': component<JbSelect>({ styles, template })` を足す
   （`original` / `bootstrap5` / `tailwind-dark`）。ここが唯一 HTML と CSS を書く場所。
4. `src/builders/select.ts` に `SelectBuilder extends Builder` を作り、`template(ctx)` で
   `html\`<jb-select .options=${...} @jb-change=${...}></jb-select>\`` を返す。
5. `src/ui.ts` に `select (name) { return guard(new SelectBuilder(name)); }` を足し、`src/index.ts` から公開する。
   （`guard` を通すと、存在しないメソッドの検出が効くようになる）

テーマに定義が無いコンポーネントは何も描画されない（`template` が無ければ `nothing`）。
新しいコンポーネントを足したら全テーマに足す、というのが規約。
ただし継承テーマ（`Theme.extend`）は親から自動的に引き継ぐので、書く必要は無い。

アイコンを増やすときはコンポーネントを作らない。`src/themes/icons.ts` の `ICONS` に
`'名前': ['<path の d>', ...]`（24×24 の枠に線だけで描く）を足せば、3 テーマすべてで使える。

Tailwind 系のテーマに手を入れたら `sh tools/build-themes.sh` で CSS を作り直す。

## 10. jimble（Java）との接続

* 画面のデータ取得・更新は `Api`（`ctx.api`）に集約する。画面のコードは `fetch` を直接呼ばない。
* `jimble-web` 側の REST が `{ list: [...], paging: {...} }` のような形を返す前提で、
  一覧・ページング用のビルダー（次期）を用意すると、管理画面が定型で書けるようになる。
* 将来 Java 側に `UIBuilder` を置いて JSON を吐く（サーバー駆動 UI）ことも可能な形にしてある。
  ビルダーは状態を持たない木なので、`render(ctx)` の代わりに `json()` を実装すれば、
  同じ語彙を Java から流し込める。今回はそこまで作らない。

## 11. 設計上の判断

| 判断 | 理由 | 捨てたもの |
| --- | --- | --- |
| Lit + Shadow DOM | CSS の衝突が構造的に起きない。Web 標準なので寿命が長い。1 コンポーネントに HTML/CSS/挙動が閉じる | Shadow DOM 越しのグローバル CSS 適用（トークンで代替） |
| ビルダーは DOM を作らず「木」を返す | 描画時に環境を渡す SQLBuilder と同じ形にできる。将来 JSON 化・サーバー駆動 UI に開ける | 直接 DOM 生成による最速の初期描画 |
| 画面まるごと再構築 + lit 差分 | 実装が単純で、状態と DOM がずれない | 部分再描画による大規模画面での最適化（必要になったら領域単位の再描画を足す） |
| ハッシュルータ既定 | サーバー設定なしで動く。`.mode('history')` で切替可能 | きれいな URL（切替で解決） |
| lit を同梱 | ビルド工程ゼロで動かせる。CDN にも npm にも依存しない | 依存の自動更新（`vendor/lit.js` を作り直す） |
| TypeScript（tsc のみ、バンドラ無し） | 型で AI の誤りを止められる。出力は素の ESM なので、配信は dist を置くだけで済む | 開発にビルド工程が要る（`npm run build`） |
| 状態の型はグローバル拡張で 1 か所に | ビルダーや画面関数を総ジェネリクスにせずに済む。アプリ 1 つ ＝ 状態 1 つという実態にも合う | 1 つの tsconfig に 2 つのアプリを入れられない（例では staff / login / errors を別プロジェクトにしている） |
| テンプレートをテーマへ外出し | 見た目の総入れ替えがアプリのコードに影響しない。CSS フレームワークの採用・乗り換えがテーマ 1 ファイルの差し替えになる | コンポーネント単体での完結性（新規コンポーネントは全テーマに実装が要る） |
| テーマは継承できる | 社内デザインを差分だけで作れる。親テーマの改善が子にも入る | 親のマークアップに依存するため、親のテンプレートを変えると子の CSS が空振りすることがある |
| CSS は `adoptedStyleSheets` で流し込む | シートの実体をインスタンス間で共有できる。テーマ切替時の差し替えが 1 行 | 対応ブラウザが Chrome / Safari 16.4+ / Firefox 101+ に限られる |

## 12. 今後

1. ~~入力の語彙~~ / ~~一覧の語彙~~ / ~~画面の語彙~~ … 0.5.0 で実装（残りは `file` / `date range` / `autocomplete`）
2. **フォーム**：`UI.form(schema)` で検証規則をまとめて宣言し、`errors` を自動で配る
3. **表の追加機能**：行の選択（チェックボックス）、固定列、列の出し分け（つかんで並べ替えは 0.8.0 で実装）
4. **繰り返しの最適化**：`.key(fn)` と lit の `repeat` による差分の安定化
6. **型**：`.d.ts` を生成し、`UI.` の補完を SQLBuilder 並みにする
7. **サーバー駆動 UI**：`Builder#json()` と Java 側 `UIBuilder`
8. **テーマ**：ライト版 Tailwind、Bootstrap のダーク（`data-bs-theme`）、印刷用テーマ。
   テーマごとのスクリーンショット比較（見た目の回帰検知）
