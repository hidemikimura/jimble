import { UI, App, themeNames, notify } from '../../dist/index.js';
import type { ChartPoint, Context } from '../../dist/index.js';
import '../staff/ecx-theme.js';
import { findByCategory, findDailySales, findSummary, yen } from './dashboard-service.js';
import type { Document, Summary } from './dashboard-service.js';

/**
 * この画面が使う状態
 */
declare global {
	interface JimbleAppState {
		summary: Summary | null;
		daily: ChartPoint[];
		category: ChartPoint[];
		period: { from: string; to: string };
		documents: Document[];
		loading: boolean;
	}
}

/* ------------------------------------------------------------------
 * 画面
 * ------------------------------------------------------------------ */

/**
 * ダッシュボード
 *
 * @param ctx コンテキスト
 * @return 画面
 */
const DashboardPage = (ctx: Context) => UI.column(

	UI.breadcrumb([
		{ label: 'ホーム', path: '/' },
		{ label: '売上ダッシュボード' }
	]),

	UI.row(
		UI.title('売上ダッシュボード'),
		UI.button('資料を見る').quiet().go('/documents')
	).justify('between'),

	UI.dateRange('period')
		.label('対象期間')
		.bind('period.from', 'period.to')
		.hint('期間を変えると再集計します')
		.onChange(() => void reload(ctx)),

	/* 統計タイル 4 枚 */
	UI.grid(
		UI.stat('売上', (c) => yen(c.get('summary')?.sales ?? 0)).unit('円').delta('+12.4%', 'up'),
		UI.stat('注文数', (c) => yen(c.get('summary')?.orders ?? 0)).unit('件').delta('+3.1%', 'up'),
		UI.stat('顧客数', (c) => yen(c.get('summary')?.customers ?? 0)).unit('人').delta('-1.2%', 'down'),
		UI.stat('返品率', (c) => String(c.get('summary')?.returnRate ?? 0)).unit('%').delta('横ばい', 'flat')
	).columns(4).gap('lg'),

	/* グラフ 2 枚 */
	UI.grid(
		UI.chart((c) => c.get('daily')).line().title('日別の売上').height(180),
		UI.chart((c) => c.get('category')).bar().title('部門別の売上').height(180)
	).columns(2).gap('lg'),

	/* 折りたたみ */
	UI.accordion()
		.section('breakdown', '内訳の見かた',
			UI.label('日別は 1 日ごとの売上合計、部門別は期間内の合計です。'),
			UI.caption('※ 返品分は差し引いていません')
		)
		.section('source', 'データの出どころ',
			UI.label('jimble-web の /api/report を想定しています。')
		)
		.open(['breakdown'])
		.single()

).gap('lg').width('lg');

/**
 * 資料画面（空状態とファイル添付）
 *
 * @param ctx コンテキスト
 * @return 画面
 */
const DocumentsPage = (ctx: Context) => UI.column(

	UI.breadcrumb([
		{ label: 'ホーム', path: '/' },
		{ label: '資料' }
	]),

	UI.title('資料'),

	UI.file('report')
		.label('資料を添付')
		.accept('.pdf,.csv,image/*')
		.multiple()
		.hint('PDF・CSV・画像。複数まとめて放り込めます')
		.onSelect(attach),

	UI.empty('まだ資料がありません')
		.icon('📭')
		.description('上の枠にファイルを放り込むと、ここに並びます')
		.action(UI.button('ダッシュボードへ戻る').primary().go('/'))
		.when(() => ctx.get('documents').length === 0),

	UI.grid(
		UI.each((c) => c.get('documents'), (document) => UI.card(
			UI.heading(document.name),
			UI.caption(String(Math.ceil(document.size / 1024)) + 'KB ／ ' + document.at)
		).gap('xs').pad('md'))
	).min('240px').gap('md').when(() => ctx.get('documents').length > 0),

	UI.button('ダッシュボードへ戻る').go('/').when(() => ctx.get('documents').length > 0)

).gap('lg').width('lg');

/* ------------------------------------------------------------------
 * ロジック
 * ------------------------------------------------------------------ */

/**
 * 集計し直す
 *
 * @param ctx コンテキスト
 * @return 処理
 */
async function reload (ctx: Context): Promise<void> {

	ctx.set('loading', true);

	const [summary, daily, category] = await Promise.all([
		findSummary(),
		findDailySales(days(ctx)),
		findByCategory()
	]);

	ctx.store.patch({ summary, daily, category, loading: false });

}

/**
 * 対象期間の日数を求める
 *
 * @param ctx コンテキスト
 * @return 日数（1〜14）
 */
function days (ctx: Context): number {

	const from = Date.parse(ctx.get('period.from'));
	const to = Date.parse(ctx.get('period.to'));

	if (Number.isNaN(from) || Number.isNaN(to) || to <= from) {
		return 7;
	}
	return Math.min(14, Math.max(1, Math.round((to - from) / 86_400_000)));

}

/**
 * 資料を添付する
 *
 * @param files ファイル
 * @param ctx コンテキスト
 * @return なし
 */
function attach (files: File[], ctx: Context): void {

	if (files.length === 0) {
		return;
	}

	const added = files.map((file) => ({
		name: file.name,
		size: file.size,
		at: new Date().toLocaleString('ja-JP')
	}));

	ctx.set('documents', [...ctx.get('documents'), ...added]);
	notify.success(String(added.length) + ' 件の資料を添付しました');

}

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

/* ------------------------------------------------------------------
 * 起動
 * ------------------------------------------------------------------ */

App.of()

	.state({
		summary: null,
		daily: [],
		category: [],
		period: { from: '2026-09-01', to: '2026-09-08' },
		documents: [],
		loading: false
	})

	.theme('original')

	.layout((page) => UI.column(
		UI.row(
			UI.column(
				UI.heading('jimble-ui ダッシュボード'),
				UI.caption('統計・グラフ・添付・空状態のデモ')
			).gap('xs'),
			UI.row(
				UI.caption('テーマ'),
				...themeNames().map((name: string) => (ctx: Context) => themeButton(name, ctx))
			).gap('sm')
		).justify('between').align('end').width('lg'),
		page
	).gap('xl').pad('xl').align('center'))

	.route('/', DashboardPage, { enter: reload })
	.route('/documents', DocumentsPage)
	.notFound(() => UI.empty('ページが見つかりません').icon('🔍').action(UI.button('戻る').primary().go('/')))

	.mount();
