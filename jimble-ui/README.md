# jimble-ui

HTML と CSS を **Shadow DOM の内側に隔離** し、画面の組み立てもロジックも
**TypeScript のビルダーだけ**で書くためのフロントフレームワーク（Lit ベース）。

AI にフロントを書かせることを前提に、**間違いが必ず表に出る**ように作っている。

jimble の `SQLBuilder` が SQL 文字列を隠したのと同じ発想で、`UIBuilder` が HTML と CSS を隠す。

```ts
import { UI, App } from 'jimble-ui';

App.of()
	.state({ form: {} })
	.route('/', (ctx) => UI.card(
		UI.title('社員登録'),
		UI.text('name').label('氏名').required().bind('form.name'),
		UI.number('age').label('年齢').bind('form.age'),
		UI.button('登録する').primary().onClick(async (ctx) => {
			await ctx.api.post('/staff', ctx.get('form'));
			ctx.go('/done');
		})
	).width('sm'))
	.mount();
```

## 動かす

```
npm install        # typescript と lit の型だけ（実行時の依存はゼロ）
npm run build:all  # src → dist、examples/*.ts → examples/*.js
npm start          # → http://localhost:8080/examples/staff/index.html
```

lit は `vendor/lit.js`（19KB）に同梱済み。配信するのは `dist/` と `vendor/` と HTML だけで、
バンドラは使わない。

## 間違いが表に出る

状態の形を 1 か所で宣言すると、以降のパスと値が型で検査される。

```ts
declare global {
	interface JimbleAppState {
		form: { name: string; age: number | null };
	}
}

ctx.get('form.nmae');               // コンパイルエラー
ctx.set('form.name', 123);          // コンパイルエラー
UI.text('name').bind('form.nmae');  // コンパイルエラー
```

型で防げないものは、開発モードで即座に例外＋画面下部の赤いパネルになる。

| 誤り | 出るもの |
| --- | --- |
| `UI.text('x').onSubmit(...)` | `InputBuilder に onSubmit はありません → 使えるのは: ... onChange / onInput` |
| `ctx.get('conut')` | `"conut" は初期状態に宣言されていません → もしかして: count` |
| `ctx.go('/nowhere')` | `そのルートは登録されていません → 登録済み: /` |
| テーマにテンプレートが無い | その場所に赤い枠。**黙って空白にしない** |

本番モードでは `console.error` だけで画面は落とさない（`App.of().mode('production')`）。
`examples/errors/` が確認用のページ。

## 3 つのゴール

| ゴール | 実現方法 |
| --- | --- |
| HTML・CSS を書かない／見ない | すべての DOM と CSS は `src/themes/` のテーマにある。アプリ側は `UI.*` のビルダーしか触らない |
| ロジックを分離できる | 画面は「コンテキスト → ビルダー」の純粋な関数。業務処理は `onClick` / `enter` から呼ぶ別モジュールに置く |
| SPA になる | `App.of().route(...).mount()`。ハッシュ／History どちらのルーティングにも対応 |

## 使える部品

| 分類 | ビルダー |
| --- | --- |
| レイアウト | `UI.column()` `UI.row()` `UI.card()` |
| 表示 | `UI.title()` `UI.heading()` `UI.label()` `UI.caption()` `UI.icon()` |
| 入力 | `UI.text()` `UI.number()` `UI.password()` `UI.date()` `UI.textarea()` `UI.select()` `UI.checkbox()` `UI.toggle()` `UI.radio()` `UI.button()` |
| フォーム | `UI.form()`（中の入力で Enter を押すと `.onSubmit()`） |
| 一覧 | `UI.table()`（並べ替え・つかんで並べ替え・0 件表示・読み込み中）`UI.pagination()` `UI.tabs()` `UI.each()` `UI.menu()`（行の「…」） |
| 画面の骨組み | `UI.sidebar()` `UI.pageHeader()` |
| 重ね表示 | `UI.dialog()`（Esc・背景クリックで閉じる）`notify.success()` |
| ダッシュボード | `UI.grid()` `UI.stat()` `UI.chart()`（棒・折れ線） |
| その他 | `UI.file()`（放り込み対応）`UI.dateRange()` `UI.breadcrumb()` `UI.accordion()` `UI.empty()` |

```ts
UI.table<Staff>((ctx) => ctx.get('list'))
	.rowKey((staff) => String(staff.id))
	.column({ key: 'name', label: '氏名', sortable: true })
	.column({ key: 'department', label: '部署', cell: (staff) => departmentLabel(staff.department) })
	.column({ key: 'actions', label: '', cell: (staff) => UI.button('詳細').quiet().go('/staff/' + staff.id) })
	.sort((ctx) => ctx.get('sortKey'), (ctx) => ctx.get('sortOrder'))
	.onSort((key, order, ctx) => ctx.store.patch({ sortKey: key, sortOrder: order }));
```

## テーマ

マークアップと CSS はコンポーネントではなく **テーマ** が持つ。
アプリのコードは 1 行も変えずに、見た目を丸ごと入れ替えられる。

```js
App.of().theme('bootstrap5').mount();     // 起動時に指定
await ctx.app.useTheme('tailwind-dark');  // 実行中に切り替え（状態は保持される）
```

| テーマ | 中身 | CSS |
| --- | --- | --- |
| `original` | 素の Shadow DOM ＋ デザイントークン（`--jb-*`） | テーマ内に直接記述 |
| `bootstrap5` | Bootstrap 5 のクラスで組んだマークアップ | `vendor/bootstrap5.css.js`（227KB・遅延読み込み） |
| `tailwind-dark` | Tailwind のクラスで組んだダークテーマ | `vendor/tailwind-dark.css.js`（10KB・使用クラスのみ抽出） |
| `ecx`（例） | `original` を継承し、差分だけ書いた社内デザイン | 親のCSS ＋ 差分 |

テーマは継承できる。書いた差分だけが上書きされ、残りは親のまま。

```js
Theme.extend('original', {
	name: 'ecx',
	tokens: { 'color-primary': '#0f766e', 'radius': '4px' },    // 色と角丸だけ変える
	components: {
		'jb-stack': { styles: ':host([jb-surface]) { border-left: 3px solid var(--jb-color-primary); }' },
		'jb-input': { template: (el, html) => html`...` }        // 必要なものだけ差し替え
	}
});
```

実例は `examples/ecx-theme.js`（約 90 行）。

CSS は選んだテーマの分だけ読み込まれ、`adoptedStyleSheets` で各 Shadow Root に流し込まれる。
`vendor/` の CSS を作り直すには `sh tools/build-themes.sh`。

## AI に書かせる

`docs/SKILL.md` が AI 向けのリファレンス。
これだけ読めば画面が書けるように、全 API・ハンドラの引数・画面の型・やりがちな間違いをまとめてある。

```
# このリポジトリで Claude Code に読ませる場合
mkdir -p .claude/skills/jimble-ui
cp jimble-ui/docs/SKILL.md .claude/skills/jimble-ui/SKILL.md
```

リファレンスだけを渡した AI に画面を 2 本書かせて検証済み（どちらもコンパイル失敗 1 回、
その 2 回はリファレンスの穴だったので埋めた）。詳細は `docs/design.md` の 7.6。

## 構成

```
src/           TypeScript の実装
	core/        Builder 基底・状態ストア・ルータ・アプリ・テーマ機構・API・診断・型
	components/  コンポーネント（状態と振る舞いだけ。見た目は持たない）
	themes/      テーマ（HTML と CSS はここだけ）
	builders/    コンポーネントに対応するビルダー
	ui.js        入口（SQL クラスに相当）
examples/
	staff/       デモ SPA（社員一覧・登録・詳細＋テーマ切替、継承テーマの例）
	dashboard/   ダッシュボード（統計タイル・グラフ・ファイル添付・空状態）
	login/       ログイン画面
	errors/      わざと壊して診断を確認するページ
tools/         vendor/ の CSS を作り直すスクリプト
dist/          tsc の出力（.js と .d.ts）。配信するのはここ
vendor/        lit・Bootstrap・Tailwind（同梱）
docs/design.md 設計書
```

詳細は [docs/design.md](docs/design.md) を参照。
