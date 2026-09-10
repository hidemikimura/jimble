import { UI, App, themeNames, notify, reorder } from '../../dist/index.js';
import type { Context } from '../../dist/index.js';
import '../staff/ecx-theme.js';

/**
 * 画面の骨組みのデモ
 *
 * <p>サイドバー・画面見出し・行のメニュー・つかんで並べ替え・飾り（アイコン）を一通り使う。</p>
 */

/** メーカー */
interface Maker {
	id: number;
	name: string;
	slug: string;
	published: boolean;
}

declare global {
	interface JimbleAppState {
		makers: Maker[];
		keyword: string;
		removing: Maker | null;
	}
}

/* ------------------------------------------------------------------
 * 一覧画面
 * ------------------------------------------------------------------ */

/**
 * メーカー一覧
 *
 * @return 画面
 */
const MakerListPage = () => UI.column(

	UI.pageHeader('メーカー')
		.description('つかんで並べ替えられます')
		.breadcrumb([{ label: 'ホーム', path: '/' }, { label: 'メーカー' }])
		.actions(
			UI.button('取り込み').icon('upload').onClick(() => notify.info('取り込みは省略')),
			UI.button('新規登録').primary().icon('plus').onClick(() => notify.success('新規登録は省略'))
		)
		.below(
			UI.text('keyword').placeholder('名前で絞り込み').bind('keyword')
		),

	UI.table<Maker>((ctx) => visible(ctx))
		.rowKey((maker) => String(maker.id))
		.column({ key: 'handle', label: '', width: '32px', cell: () => UI.icon('drag').sm() })
		.column({ key: 'name', label: '名前' })
		.column({ key: 'slug', label: '識別子' })
		.column({
			key: 'published',
			label: '公開',
			cell: (maker) => maker.published ? UI.icon('success').alt('公開中') : UI.icon('close').alt('非公開')
		})
		.column({
			key: 'actions',
			label: '',
			align: 'end',
			width: '56px',
			cell: (maker) => UI.menu()
				.go('編集', '/makers/edit', { icon: 'edit' })
				.item('複製', () => notify.info(maker.name + ' を複製しました'), { icon: 'copy' })
				.divider()
				.danger('削除', (ctx) => ctx.set('removing', maker), { icon: 'trash' })
		})
		.empty('メーカーがありません')
		.reorderable()
		.onReorder((detail, ctx) => {
			ctx.set('makers', reorder(ctx.get('makers'), detail));
			notify.success(detail.item.name + ' を ' + String(detail.to + 1) + ' 番目に移動しました');
		}),

	/* 削除の確認 */
	UI.dialog(
		UI.label((ctx) => (ctx.get('removing')?.name ?? '') + ' を削除します。元に戻せません。')
	)
		.title('削除の確認')
		.open((ctx) => ctx.get('removing') != null)
		.onClose((ctx) => ctx.set('removing', null))
		.footer(
			UI.button('やめる').quiet().onClick((ctx) => ctx.set('removing', null)),
			UI.button('削除する').danger().icon('trash').onClick((ctx) => {
				const target = ctx.get('removing');
				ctx.set('makers', ctx.get('makers').filter((maker) => maker.id !== target?.id));
				ctx.set('removing', null);
				notify.success('削除しました');
			})
		)

).gap('lg');

/**
 * 絞り込んだ一覧
 *
 * @param ctx コンテキスト
 * @return 一覧
 */
function visible (ctx: Context): Maker[] {

	const keyword = ctx.get('keyword').trim();
	if (keyword === '') {
		return ctx.get('makers');
	}
	return ctx.get('makers').filter((maker) => maker.name.includes(keyword) || maker.slug.includes(keyword));

}

/* ------------------------------------------------------------------
 * ほかの画面
 * ------------------------------------------------------------------ */

/**
 * 編集画面
 *
 * @return 画面
 */
const MakerEditPage = () => UI.column(

	UI.pageHeader('メーカーの編集')
		.breadcrumb([{ label: 'ホーム', path: '/' }, { label: 'メーカー', path: '/makers' }, { label: '編集' }])
		.actions(
			UI.button('削除').danger().quiet().icon('trash').onClick(() => notify.warning('削除は省略')),
			UI.button('保存').primary().icon('save').onClick(() => notify.success('保存しました'))
		),

	UI.card(
		UI.text('name').label('名前').value('サンプル電機'),
		UI.text('slug').label('識別子').value('sample')
	)

).gap('lg');

/**
 * ホーム
 *
 * @return 画面
 */
const HomePage = () => UI.column(

	UI.pageHeader('ホーム').description('サイドバー・見出し・メニュー・並べ替え・飾りのデモ'),

	UI.card(
		UI.heading('置いてあるもの'),
		UI.label('左のサイドバーは UI.sidebar()、上の見出しは UI.pageHeader() です。'),
		UI.label('メーカー一覧では、行の「…」メニューと、つかんで並べ替えが試せます。'),
		UI.row(
			UI.button('メーカーへ').primary().icon('arrow-right').go('/makers'),
			UI.button('危険操作').danger().quiet().icon('trash').onClick(() => notify.warning('押されました'))
		)
	)

).gap('lg');

/* ------------------------------------------------------------------
 * 起動
 * ------------------------------------------------------------------ */

App.of()

	.state({
		makers: [
			{ id: 1, name: 'サンプル電機', slug: 'sample', published: true },
			{ id: 2, name: '山田製作所', slug: 'yamada', published: true },
			{ id: 3, name: '青木工業', slug: 'aoki', published: false },
			{ id: 4, name: '緑川産業', slug: 'midorikawa', published: true }
		],
		keyword: '',
		removing: null
	})

	.theme('original')

	.layout((page) => UI.row(

		UI.sidebar()
			.heading('jimble 管理')
			.item('/', 'ホーム', { icon: 'home' })
			.group('商品')
			.item('/makers', 'メーカー', { icon: 'list', badge: 4 })
			.item('/products', '商品', { icon: 'box' })
			.group('設定')
			.item('/settings', '基本設定', { icon: 'settings' })
			.footer(UI.button('ログアウト').quiet().icon('logout').onClick(() => notify.info('ログアウトしました')))
			.width('220px'),

		UI.column(
			UI.row(
				UI.caption('テーマ'),
				...themeNames().map((name: string) => (ctx: Context) => themeButton(name, ctx))
			).gap('sm').justify('end'),
			page
		).gap('lg').pad('xl')

	).align('stretch').gap('none'))

	.route('/', HomePage)
	.route('/makers', MakerListPage)
	.route('/makers/edit', MakerEditPage)
	.route('/products', () => UI.pageHeader('商品').description('省略'))
	.route('/settings', () => UI.pageHeader('基本設定').description('省略'))
	.notFound(() => UI.empty('ページが見つかりません').action(UI.button('戻る').primary().go('/')))

	.mount();

/**
 * テーマ切替ボタン
 *
 * @param name テーマ名
 * @param ctx コンテキスト
 * @return ボタン
 */
function themeButton (name: string, ctx: Context) {

	const button = UI.button(name);
	if (ctx.app.themeName === name) {
		button.primary();
	} else {
		button.quiet();
	}

	return button.onClick(async (c) => {
		await c.app.useTheme(name);
	});

}
