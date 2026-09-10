import { UI, App, registerTheme, component } from '../../dist/index.js';
import type { Context, JbStack } from '../../dist/index.js';

/**
 * 「大きな音で壊す」の確認ページ
 *
 * <p>
 * AI や補完が生みやすい間違いを、わざと起こしてみるためのページ。
 * 開発モードでは、どれも <b>その場で例外＋画面下部の赤いパネル</b>になる。
 * </p>
 */

declare global {
	interface JimbleAppState {
		count: number;
	}
}

/* テンプレートが足りないテーマ（jb-stack しか持たない） */
registerTheme({
	name: 'bare',
	tokens: { 'color-bg': '#fff', 'color-text': '#111', 'font': 'system-ui' },
	components: {
		'jb-stack': component<JbStack>({
			styles: ':host { display: flex; flex-direction: column; gap: 12px; padding: 24px; }',
			template: (_el, html) => html`<slot></slot>`
		})
	}
});

const ErrorPage = (_ctx: Context) => UI.column(

	UI.title('わざと壊してみる'),
	UI.caption('開発モードでは、どれも即座に例外になり画面下部に赤く出ます'),

	UI.button('① 存在しないメソッドを呼ぶ').onClick(() => {
		/* AI が作りがちな「それらしいが無いメソッド」 */
		(UI.text('name') as unknown as { onSubmit: (f: () => void) => void }).onSubmit(() => undefined);
	}),

	UI.button('② 宣言していない状態を読む').onClick((ctx) => {
		/* TypeScript なら書けないが、JavaScript からは来うる */
		(ctx.store as unknown as { get: (p: string) => unknown }).get('conut');
	}),

	UI.button('③ 登録していないルートへ飛ぶ').onClick((ctx) => {
		ctx.go('/nowhere');
	}),

	UI.button('④ テンプレートの無いテーマに切り替える').onClick(async (ctx) => {
		await ctx.app.useTheme('bare');
	}),

	UI.label((ctx) => 'count: ' + String(ctx.get('count')))

).gap('md').pad('xl').width('md');

App.of()
	.state({ count: 0 })
	.theme('original')
	.route('/', ErrorPage)
	.mount();
