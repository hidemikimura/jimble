---
name: jimble-ui
description: jimble-ui（HTML と CSS を書かずに TypeScript のビルダーだけで画面を作るフロントフレームワーク）で画面やアプリを作る・直すときに使う。UI ビルダーの全 API、状態の宣言方法、テーマ、コンポーネントの増やし方、やりがちな間違いを含む。
---

# jimble-ui

Lit + Shadow DOM の上に作られたフロントフレームワーク。場所は `<リポジトリ>/jimble-ui`。

- **アプリのコードに HTML と CSS は出てこない。** タグ名も CSS クラス名も書かない
- 画面は「コンテキストを受け取ってビルダーを返す関数」
- HTML と CSS があるのは `src/themes/` のテーマだけ（original / bootstrap5 / tailwind-dark / 継承テーマ）

## 絶対規約

1. **アプリのコードに HTML / CSS を書かない。** 見た目が足りないときは、テーマのトークンを変えるか、テーマにコンポーネントを足す
2. **状態は `JimbleAppState` に宣言する。** 宣言していないキーは開発モードで例外になる
3. **DOM を直接触らない。** `document.querySelector` も `innerHTML` も使わない。表示は状態の写像
4. **業務ロジックは別モジュールに置く。** 画面は `onClick` / `enter` からそれを呼ぶだけ
5. **変更したら `npm run check`（型検査）と `npm run build:all`（ビルド）を必ず通す**

## 最小のアプリ

`examples/<アプリ名>/` に 3 ファイル置くのが基本形。

**index.html**（これがアプリ唯一の HTML。中身は増やさない）

```html
<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>タイトル</title>
<link rel="icon" href="data:,">
<script type="module" src="./app.js"></script>
```

**tsconfig.json**

```json
{
	"extends": "../../tsconfig.base.json",
	"include": ["**/*.ts"]
}
```

**app.ts**

```ts
import { UI, App } from '../../dist/index.js';
import type { Context } from '../../dist/index.js';

/* この画面が使う状態。ここに書いたものだけが読み書きできる */
declare global {
	interface JimbleAppState {
		form: { name: string; age: number | null };
		saving: boolean;
	}
}

const HomePage = (ctx: Context) => UI.card(
	UI.title('社員登録'),
	UI.text('name').label('氏名').required().bind('form.name'),
	UI.number('age').label('年齢').bind('form.age'),
	UI.button('登録').primary().loading(() => ctx.get('saving')).onClick(save)
).width('sm').gap('lg');

async function save (ctx: Context): Promise<void> {
	ctx.set('saving', true);
	await api.post('/staff', ctx.get('form'));
	ctx.set('saving', false);
	ctx.go('/');
}

App.of()
	.state({ form: { name: '', age: null }, saving: false })
	.theme('original')
	.route('/', HomePage)
	.mount();
```

ビルドは `npm run build:all`、確認は `npm start` → `http://localhost:8080/examples/<アプリ名>/index.html`。

**1 つの tsconfig に 2 つのアプリを入れない。** `JimbleAppState` はグローバルなので、アプリごとにフォルダと tsconfig を分ける。

## 状態

```ts
ctx.get('form.name')                      // 型が付く。綴り違いはコンパイルエラー
ctx.set('form.name', '山田')               // 設定すると再描画
ctx.store.patch({ list, loading: false })  // まとめて設定
ctx.store.declare('extra', 0)              // 初期状態に無いキーを後から足す
ctx.params.id / ctx.query.page             // ルートの :id と ?page=
```

- `.state({...})` に渡すオブジェクトは `JimbleAppState` の**全キー**を埋める。
  `store.patch({...})` は一部だけでよい（`Partial<状態>`）
- 深いパスは `'form.name'` のようにドットで書く。入れ子は 5 階層まで
  （`'form.notify.mail'` も可）。配列の要素は指せない
- 入力と状態を結ぶのは `.bind('form.name')`。`bind` できるのは
  文字・数値（`UI.text` / `UI.number`）、文字（`select` / `radio` / `tabs`）、真偽（`checkbox` / `toggle`）
- 検証エラーは `errors: Record<string, string>` の 1 つの入れ物にまとめると扱いやすい。
  `ctx.get('errors').name` のように参照でき、パスの型検査も通る。
  `.error()` は `string | undefined` を受けるので、キーが無い（＝エラー無し）ときは
  `undefined` のままでよい（表示されない）

## アプリ（App）

| メソッド | 説明 |
| --- | --- |
| `App.of()` | 作る |
| `.state(initial)` | 初期状態（必須） |
| `.theme(name, tokens?)` | `'original'` / `'bootstrap5'` / `'tailwind-dark'` / 継承テーマ名 |
| `.mode('production')` | 誤りで画面を落とさない（既定は development） |
| `.routing('history')` | pushState 方式（既定はハッシュ） |
| `.api(Api.of('/api'))` | `ctx.api` から使える REST クライアント |
| `.layout((page, ctx) => ...)` | 全画面共通の外枠 |
| `.route(path, view, { enter })` | 画面。`enter` は表示前に走る非同期処理（データ取得） |
| `.notFound(view)` | 未定義パス |
| `.mount(target?)` | 起動。省略すると body 直下に描画先を作る |
| `.useTheme(name)` | 起動後のテーマ切替（Promise） |

```ts
.route('/staff/:id', StaffPage, {
	enter: async (ctx) => {
		ctx.set('staff', await findStaffById(ctx.params.id ?? ''));
	}
})
```

## ビルダー一覧

すべて自身を返すので繋げられる。

**全ビルダー共通のメソッドはこの 3 つだけ**: `.when(条件)` `.unless(条件)` `.add(...子)`。
`.gap()` `.pad()` `.align()` `.justify()` `.width()` は **`UI.column` / `UI.row` / `UI.card` 専用**で、
`UI.form` や `UI.dialog` には付かない。間隔を空けたいときは中に `UI.column` を 1 枚挟む。

```ts
UI.form(UI.column(...).gap('lg')).onSubmit(save)   // ⭕
UI.form(...).gap('lg')                             // ❌ FormBuilder に gap は無い
```

### レイアウト・表示

| 生成 | メソッド |
| --- | --- |
| `UI.column(...子)` `UI.row(...子)` `UI.card(...子)` | `.gap(size)` `.pad(size)` `.align(a)` `.justify(j)` `.width(w)` `.wrap()` `.surface()` |
| `UI.title(text)` `UI.heading(text)` `UI.label(text)` `UI.caption(text)` | `.title()` `.heading()` `.caption()` `.danger()` |
| `UI.icon(name)` | `.sm()` `.lg()` `.size('sm'｜'md'｜'lg')` `.alt(text)` … 名前は下の一覧から選ぶ |

`size` = `none｜xs｜sm｜md｜lg｜xl` / `align` = `start｜center｜end｜stretch` /
`justify` = `start｜center｜end｜between` /
`width` = `sm｜md｜lg｜full`、または `'220px'` のような CSS の長さ（max-width として当たる）

`.bind()` を使わず値だけ渡したいときは `.value()`（`UI.text` / `UI.select` / `UI.radio` / `UI.tabs`）。

### 入力

| 生成 | メソッド |
| --- | --- |
| `UI.text(name)` `UI.number(name)` `UI.password(name)` `UI.date(name)` `UI.textarea(name)` | `.label()` `.placeholder()` `.hint()` `.error()` `.required()` `.disabled()` `.multiline()` `.bind(path)` `.value()` `.onInput(fn)` `.onChange(fn)` |
| `UI.select(name)` | `.options([{value,label,disabled?}])` `.label()` `.placeholder()` `.hint()` `.error()` `.required()` `.disabled()` `.bind(path)` `.value()` `.onChange(fn)` |
| `UI.checkbox(name)` `UI.toggle(name)`（スイッチ） | `.label()` `.hint()` `.error()` `.disabled()` `.checked()` `.bind(path)` `.onChange(fn)` |
| `UI.radio(name)` | `.options([...])` `.label()` `.inline()` `.hint()` `.error()` `.required()` `.disabled()` `.bind(path)` `.value()` `.onChange(fn)` |
| `UI.button(label)` | `.primary()` `.danger()` `.quiet()` `.icon(name)` `.disabled()` `.loading()` `.onClick(fn)` `.go(path)` |
| `UI.form(...子)` | `.onSubmit(fn)` … 子は何個でも渡せる。中の入力欄で **Enter が押されたら `.onSubmit` が走る**（複数行入力の中とボタン上では走らない）。Enter で走るのは `.onSubmit` だけで、中の他のボタンは反応しない |

### 一覧・画面部品

| 生成 | メソッド |
| --- | --- |
| `UI.table(items)` | `.column({key,label,cell?,sortable?,align?,width?})` `.rowKey((row, index) => string)` `.sort(key,order)` `.onSort(fn)` `.onRowClick(fn)` `.empty(text)` `.loading()` `.reorderable()` `.onReorder(fn)` |
| `UI.menu()`（行の「…」） | `.item(label, fn, {icon?,disabled?})` `.danger(label, fn, {...})` `.go(label, path, {...})` `.divider()` `.trigger(label)` `.icon(name)` `.alignStart()` `.disabled()` |
| `UI.pagination()` | `.page(n)` `.pages(n)` `.total(n)` `.summary(text)` `.onChange(fn)` |
| `UI.tabs([{value,label,badge?}])` | `.bind(path)` `.value()` `.onChange(fn)` |
| `UI.dialog(...子)` | `.title()` `.open(条件)` `.size('sm'｜'md'｜'lg')` `.closable()` `.footer(...ノード)` `.onClose(fn)` |
| `UI.each(items, (item, i, ctx) => ...)` | `.empty(ノード)` … `items` は配列でも `(ctx) => 配列` でもよい |
| `notify.success(msg)` | `notify` は `info` / `success` / `warning` / `error` / `show(msg,{duration})` / `clear()` |

**使えるアイコン名**（`UI.icon(name)` / `.icon(name)` / メニュー・サイドバーの `{ icon }` 共通）

```
操作  more more-vertical menu close plus minus check search edit trash copy save
      filter refresh download upload drag external
向き  chevron-up chevron-down chevron-left chevron-right
      arrow-up arrow-down arrow-left arrow-right
画面  home list grid box cart chart calendar settings
人    user users login logout lock
知らせ info warning error success star eye help
```

無い名前を渡すと「?」が出て、開発モードでは似た名前を教える（`アイコン "trahs" はありません → もしかして: trash`）。
**絵文字を直接書いてもよい**（`UI.empty('...').icon('📭')` は絵文字を受け取る別物）。

### 画面の骨組み（管理画面はまずこの 2 つ）

| 生成 | メソッド |
| --- | --- |
| `UI.sidebar()` | `.heading(text)` `.group(label)` `.item(path, label, {icon?,badge?,disabled?})` `.footer(...ノード)` `.width('220px')` `.fullHeight()` `.current(値)` `.onSelect(fn)` |
| `UI.pageHeader(題名)` | `.description(text)` `.breadcrumb([{label,path?}])` `.actions(...ノード)` `.below(...ノード)` |

`UI.sidebar()` は **`.item()` に渡したパスへ自分で遷移し、今いる場所を自分で光らせる**
（`/makers/edit` にいるときは `/makers` が光る）。遷移を自分で決めたいときだけ `.onSelect((value, ctx) => ...)` を書く。
`.group()` を挟むと、それ以降の項目がそのまとまりに入る。

`UI.pageHeader()` の `.actions()` は右側（ボタン）、`.below()` は見出しの下（検索欄やタブ）に入る。

```ts
.layout((page) => UI.row(

	UI.sidebar()
		.heading('aqSell 管理')
		.group('商品')
		.item('/makers', 'メーカー', { icon: 'list' })
		.item('/products', '商品', { icon: 'box' })
		.group('設定')
		.item('/staff', '担当者', { icon: 'user' })
		.footer(UI.button('ログアウト').quiet().icon('logout').onClick(logout))
		.width('220px'),

	UI.column(page).gap('lg').pad('xl')

).align('stretch').gap('none'))
```

### ダッシュボード・その他

| 生成 | メソッド |
| --- | --- |
| `UI.grid(...子)` | `.columns(1〜6)` `.gap(size)` `.min('240px')`（min を指定すると幅に応じて自動で折り返す。狭い画面では 1 列になる） |
| `UI.stat(label, value)` | `.unit(text)` `.delta(text, 'up'｜'down'｜'flat')` `.hint(text)` … value は**整形済みの文字列**を渡す |
| `UI.chart(points)` | `.bar()` `.line()` `.title(text)` `.height(px)` `.empty(text)` … points は `{label, value}[]` |
| `UI.file(name)` | `.label()` `.accept('.pdf,image/*')` `.multiple()` `.hint()` `.error()` `.disabled()` `.onSelect((files, ctx) => ...)` … 押して選ぶのと放り込むの両方 |
| `UI.dateRange(name)` | `.label()` `.bind(fromPath, toPath)` `.hint()` `.error()` `.required()` `.disabled()` `.onChange((from, to, ctx) => ...)` |
| `UI.breadcrumb([{label, path?}])` | `.separator(text)` `.onSelect(fn)` … `path` のある項目を押すとその画面へ移る（`onSelect` 省略時） |
| `UI.accordion()` | `.section(value, label, ...子)` `.open([value])` `.single()` `.onToggle(fn)` … 開閉は部品が持つ |
| `UI.empty(heading)` | `.icon('📭')` `.description(text)` `.action(...ノード)` |

```ts
UI.grid(
	UI.stat('売上', (c) => yen(c.get('sales'))).unit('円').delta('+12.4%', 'up'),
	UI.stat('注文数', (c) => yen(c.get('orders'))).unit('件').delta('-1.2%', 'down')
).columns(4).gap('lg')

UI.chart((c) => c.get('daily')).line().title('日別の売上').height(180)

UI.empty('まだ資料がありません')
	.icon('📭')
	.description('上の枠にファイルを放り込むと、ここに並びます')
	.action(UI.button('戻る').primary().go('/'))
```

`UI.each` は**複数のノードに展開される**ので、`UI.grid(UI.each(...))` と書けばそれぞれがグリッドのセルになる。

表の `key` は**任意の文字列**でよい（`'actions'` のような列も作れる）。
`cell` を省くと `row[key]` をそのまま文字列にして出す。`sortable: true` の列は
押されると `onSort(key, order, ctx)` が呼ばれる — **並べ替えるのは画面側の仕事**で、
表は「押された」ことを伝えるだけ。`select` / `radio` の `options` の `value` は文字列。

## ハンドラの引数（間違えやすい）

**`ctx` が第 1 引数とは限らない。** 値を受け取るものは `(値, ctx)` の順。

| 書く場所 | 引数 |
| --- | --- |
| `.onClick(fn)` `.onSubmit(fn)` `.onClose(fn)` | `(ctx)` |
| `.route(path, view, { enter })` | `(ctx)` |
| 入力の `.onInput(fn)` `.onChange(fn)` | `(value: unknown, ctx)` |
| select / radio / tabs の `.onChange(fn)` | `(value: string, ctx)` |
| checkbox / toggle の `.onChange(fn)` | `(checked: boolean, ctx)` |
| pagination の `.onChange(fn)` | `(page: number, ctx)` |
| table の `.onSort(fn)` | `(key: string, order: 'asc'｜'desc', ctx)` |
| table の `.onRowClick(fn)` | `(row, ctx)` |
| table の `.onReorder(fn)` | `(detail: {item, from, to, reference, position}, ctx)` |
| menu の `.item(label, fn)` `.danger(label, fn)` | `(ctx)` |
| sidebar の `.onSelect(fn)` | `(value: string, ctx)` |
| table の `.column({ cell })` | `(row, ctx)` |
| `UI.each(items, fn)` | `(item, index, ctx)` |
| `UI.file(...)` の `.onSelect(fn)` | `(files: File[], ctx)` |
| `UI.dateRange(...)` の `.onChange(fn)` | `(from: string, to: string, ctx)` |
| `UI.accordion()` の `.onToggle(fn)` | `(value: string, open: boolean, ctx)` |
| `UI.breadcrumb(...)` の `.onSelect(fn)` | `(path: string, ctx)` |
| 値を関数で書くとき（`.disabled()` など） | `(ctx)` |

入力・選択・ファイルの第 1 引数 `name` は、イベントに載る**識別子**（`detail.name`）。
状態との結び付けは `name` ではなく `.bind(path)` が行うので、両者は別物。

```ts
/* 入力の検証を使い回すときは第 1 引数が値であることに注意 */
function revalidate (_value: unknown, ctx: Context): void {
	if (!ctx.get('hasError')) { return; }
	ctx.store.patch({ errors: validate(ctx.get('form')) });
}
UI.text('name').bind('form.name').onInput(revalidate);
```

戻り値は何でもよい（`ctx.set(...)` をそのまま返しても通る）。

## 値は「値」でも「関数」でもよい

ほぼすべての引数は、値そのものでも `ctx` を受け取る関数でも書ける。
**状態から計算する値は必ず関数で書く**（値で書くと初回の値で固定される）。

```ts
UI.button('削除')
	.danger()
	.disabled((ctx) => ctx.get('selected').length === 0)   // 状態から計算
	.onClick(deleteSelected);                              // ロジックは外の関数

UI.label(() => ctx.get('staff')?.name ?? '（未選択）')
UI.text('name').error(() => ctx.get('errors').name)
```

## TypeScript の作法

`tsconfig.base.json` は `noUnusedLocals` / `noUnusedParameters` / `noImplicitOverride` を有効にしている。

- 使わない引数は `_` で始める（`(_value, ctx) => ...` / `(_ctx) => ...`）
- 画面関数は `ctx` を使わないなら `(_ctx: Context) => ...` と書く

## 画面の型

### 一覧（タブ・検索・表・ページ送り）

```ts
const ListPage = (ctx: Context) => UI.column(

	UI.row(
		UI.title('社員一覧'),
		UI.button('新規登録').primary().go('/staff/new')
	).justify('between'),

	UI.tabs((c) => [
		{ value: 'all', label: 'すべて', badge: c.get('list').length },
		{ value: 'active', label: '在職', badge: c.get('list').filter((s) => s.active).length }
	]).bind('filter'),

	UI.text('keyword').placeholder('氏名で絞り込み').bind('keyword')
		.onInput(() => void reload(ctx)),

	UI.table<Staff>((c) => paged(c, visible(c)))
		.rowKey((staff) => String(staff.id))
		.loading(() => ctx.get('loading'))
		.empty('該当する社員がいません')
		.sort(() => ctx.get('sortKey'), () => ctx.get('sortOrder'))
		.onSort((key, order) => ctx.store.patch({ sortKey: key, sortOrder: order }))
		.column({ key: 'name', label: '氏名', sortable: true })
		.column({ key: 'age', label: '年齢', sortable: true, align: 'end', width: '80px' })
		.column({ key: 'department', label: '部署', cell: (s) => departmentLabel(s.department) })
		.column({ key: 'actions', label: '', align: 'end',
			cell: (s) => UI.button('詳細').quiet().go('/staff/' + String(s.id)) }),

	UI.pagination()
		.page(() => ctx.get('page'))
		.pages((c) => Math.max(1, Math.ceil(visible(c).length / 20)))
		.total((c) => visible(c).length)
		.onChange((page) => ctx.set('page', page))

).gap('lg').width('lg');

/* 絞り込み・並べ替え・ページ送りは画面側の派生計算 */
function visible (ctx: Context): Staff[] {
	const filter = ctx.get('filter');
	const key = ctx.get('sortKey');
	const order = ctx.get('sortOrder');

	const list = ctx.get('list').filter((s) => filter === 'all' || (filter === 'active' && s.active));
	if (!key) { return list; }

	return [...list].sort((a, b) => {
		const left = (a as unknown as Record<string, unknown>)[key];
		const right = (b as unknown as Record<string, unknown>)[key];
		const compared = typeof left === 'number' && typeof right === 'number'
			? left - right
			: String(left).localeCompare(String(right), 'ja');
		return order === 'asc' ? compared : -compared;
	});
}

function paged (ctx: Context, list: Staff[]): Staff[] {
	const page = Math.max(1, ctx.get('page'));
	return list.slice((page - 1) * 20, page * 20);
}
```

### 行のメニューと、つかんで並べ替え

行にボタンを 3 つ並べるより、**「…」のメニュー 1 つ**にまとめるほうが読みやすい。
並び順を持つ一覧は `.reorderable()` を足すだけでつかんで動かせるようになる。

```ts
import { UI, reorder } from 'jimble-ui';

UI.table<Maker>((c) => c.get('list'))
	.rowKey((maker) => String(maker.id))
	.column({ key: 'handle', label: '', width: '32px', cell: () => UI.icon('drag').sm() })
	.column({ key: 'name', label: '名前' })
	.column({ key: 'actions', label: '', align: 'end', width: '56px',
		cell: (maker) => UI.menu()
			.go('編集', '/makers/edit?id=' + String(maker.id), { icon: 'edit' })
			.divider()
			.danger('削除', (c) => c.set('removing', maker), { icon: 'trash' }) })

	.reorderable()
	.onReorder(async (detail, c) => {
		c.set('list', reorder(c.get('list'), detail));      // 画面を先に動かす
		await moveTo(detail.item.id, detail.to);            // サーバへ送る
	})
```

`detail` は `{ item（動かした行）, from（元の位置）, to（動かした後の位置）, reference（落とした先の行）, position: 'before'｜'after' }`。
`reorder(配列, detail)` は**並べ替えた新しい配列**を返す（元の配列は変えない）。
サーバが「1 つ前へ / 1 つ後ろへ」しか受け付けないなら `detail.from` と `detail.to` の差で回数を決める。

危険な操作の文字ボタンは `.danger().quiet()`（枠なしの赤）。

### フォーム（検証つき）

```ts
const NewPage = (ctx: Context) => UI.column(
	UI.title('社員登録'),
	UI.form(
		UI.card(
			UI.text('name').label('氏名').required().bind('form.name')
				.onInput(revalidate).error(() => ctx.get('errors').name),
			UI.select('department').label('部署').required()
				.options(DEPARTMENTS).bind('form.department')
				.error(() => ctx.get('errors').department),
			UI.radio('employment').label('雇用形態').inline()
				.options(EMPLOYMENTS).bind('form.employment'),
			UI.row(
				UI.checkbox('active').label('在職中').bind('form.active'),
				UI.toggle('notify').label('通知を受け取る').bind('form.notify')
			).gap('xl'),
			UI.row(
				UI.button('キャンセル').go('/'),
				UI.button('登録する').primary().loading(() => ctx.get('saving')).onClick(submit)
			).justify('end').gap('sm')
		).gap('lg')
	).onSubmit(submit)
).gap('lg').width('md');

async function submit (ctx: Context): Promise<void> {
	const errors = validate(ctx.get('form'));
	ctx.store.patch({ errors, hasError: Object.keys(errors).length > 0 });
	if (Object.keys(errors).length > 0) {
		notify.warning('入力内容を確認してください');
		return;
	}
	ctx.set('saving', true);
	await saveStaff(ctx.get('form'));
	ctx.store.patch({ saving: false, form: {} });
	notify.success('登録しました');
	ctx.go('/');
}
```

### 確認ダイアログ

```ts
UI.dialog(UI.label(() => (ctx.get('staff')?.name ?? '') + ' を削除します。元に戻せません。'))
	.title('削除の確認')
	.size('sm')
	.open(() => ctx.get('confirming'))
	.onClose(() => ctx.set('confirming', false))
	.footer(
		UI.row(
			UI.button('やめる').onClick(() => ctx.set('confirming', false)),
			UI.button('削除する').danger().loading(() => ctx.get('removing')).onClick(remove)
		).justify('end').gap('sm')
	)
```

開閉の状態は画面が持つ。ダイアログは開いていない間は中身を描画しない。

## サーバとの通信

`fetch` を画面から直接呼ばない。`Api` に集約する。

```ts
App.of().api(Api.of('/api'))...

const list = await ctx.api!.get<Staff[]>('/staff', { keyword: 'yamada' });
await ctx.api!.post('/staff', ctx.get('form'));
```

デモのようにサーバが無い場合は、`examples/<アプリ>/xxx-service.ts` に業務ロジックを分けて置く。

## コンポーネントを増やす

**全テーマに実装する**のが規約（テーマに無いコンポーネントは赤枠で「テンプレートがありません」と出る）。

1. `src/components/jb-xxx.ts` … `JbElement` を継承し、**プロパティと `handleXxx`（テーマから呼ぶ処理）だけ**を書く。
   マークアップも CSS も書かない。値が変わったら `jb-change` を `bubbles: true, composed: true` で投げる。
   **属性名は必ず `jb-` を付ける**（`align` や `width` は HTML の既定スタイルが解釈してしまう）
2. `src/components/index.ts` に足す
3. `src/themes/{original,bootstrap5,tailwind-dark}.ts` の `components` に
   `'jb-xxx': component<JbXxx>({ styles, template })` を足す ← HTML と CSS を書くのはここだけ
   テンプレートの引数は `(el, html, svg)`。**SVG の中身は `svg` で作る**
   （`html` で作ると HTML 要素として解釈され、描画されない）
4. `src/builders/xxx.ts` に `XxxBuilder extends Builder` を作り、`template(ctx)` で
   `` html`<jb-xxx .prop=${...} @jb-change=${...}></jb-xxx>` `` を返す
5. `src/ui.ts` に `xxx (name) { return guard(new XxxBuilder(name)); }` を足し、`src/index.ts` から公開する

tailwind-dark に手を入れたら `sh tools/build-themes.sh` で CSS を作り直す。
**Tailwind のクラス名は文字列そのままで書く**（`'bg-' + color` のような組み立ては抽出されない）。

## テーマを継承する

社内デザインは差分だけ書く。書かなかったものは親のまま。

```ts
Theme.extend('original', {
	name: 'ecx',
	tokens: { 'color-primary': '#0f766e', 'radius': '4px' },
	components: {
		'jb-stack': { styles: ':host([jb-surface]) { border-left: 3px solid var(--jb-color-primary); }' },
		'jb-input': { template: (el, html) => html`...` }
	}
});
```

`tokens` は親に重ねる / `styles` は親の CSS の**後ろに**足す（後勝ち）/ `replaceStyles` は置き換え /
`template` は指定すれば差し替え、省略すれば親のまま。実例は `examples/staff/ecx-theme.ts`。

## やりがちな間違い

| これは動かない | こう書く |
| --- | --- |
| `UI.text('x').onSubmit(...)` | 入力に onSubmit は無い。`UI.form(...).onSubmit(...)` |
| `ctx.get('conut')` | `JimbleAppState` に宣言したキーだけ。開発モードでは候補付きで落ちる |
| `ctx.go('/nowhere')` | 登録済みルートのみ。落ちるときは登録済みパスが表示される |
| `UI.column(...).style(...)` / `class` | 見た目はテーマの仕事。`.gap()` `.pad()` などトークンで指定する |
| `UI.button('x').onClick(() => location.href = '/y')` | `.go('/y')` か `ctx.go('/y')` |
| `.disabled(ctx.get('busy'))` | 状態から計算する値は関数で `.disabled((c) => c.get('busy'))` |
| `.state({ a: 1 })` に無いキーを後から使う | 最初から全キーを書くか `store.declare()` |
| 新コンポーネントを 1 テーマだけに実装 | 全テーマに実装する（無いテーマでは赤枠が出る） |
| `.onInput((ctx) => ...)` | 第 1 引数は値。`.onInput((_value, ctx) => ...)` |
| `UI.form(...).gap('lg')` | `.gap()` は column / row / card 専用。`UI.form(UI.column(...).gap('lg'))` |
| 破壊的な操作のボタンをフォームの中に置く | Enter では走らないが、確認ダイアログを必ず挟む |
| 表の `onSort` で並べ替えたつもりになる | 並べ替えるのは画面側。`onSort` は状態を書き換えるだけ |
| `document.querySelector('...')` で DOM を触る | 状態を変えて再描画させる |
| テーマで SVG の中身を `` html`<rect .../>` `` で作る | `` svg`<rect .../>` ``（テンプレートの第 3 引数） |
| `UI.stat('売上', 12480000)` | 表示は整形して渡す。`UI.stat('売上', (c) => yen(c.get('sales')))` |
| 行に「編集」「複製」「削除」を 3 つ並べる | `UI.menu()` にまとめる（`.danger()` で削除だけ赤くする） |
| `.reorderable()` だけ書いて並びが戻る | 並べ替えるのは画面側。`.onReorder()` で `reorder(list, detail)` を状態に書き戻す |
| サイドバーを `UI.column(UI.button(...)...)` で自作する | `UI.sidebar()`。今いる場所の判定も遷移も入っている |
| 題名とボタンを `UI.row(...).justify('between')` で毎回組む | `UI.pageHeader(題名).actions(...)` |
| `UI.icon('削除')` | 名前は英語の決まった語（`trash`）。一覧は「使えるアイコン名」を見る |

## 動作確認

```
npm run check      # 型検査（本体と全 examples）
npm run build:all  # src → dist、examples/*.ts → examples/*.js
npm start          # http://localhost:8080/examples/<アプリ>/index.html
```

開発モードでは、誤りはその場で例外になり画面下部に赤いパネルで出る。
`examples/errors/` がその確認用ページ。
