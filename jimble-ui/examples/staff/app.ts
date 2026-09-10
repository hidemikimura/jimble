import { UI, App, themeNames, notify } from '../../dist/index.js';
import type { Context, SortOrder } from '../../dist/index.js';
import './ecx-theme.js';
import {
	DEPARTMENTS,
	EMPLOYMENTS,
	deleteStaff,
	departmentLabel,
	employmentLabel,
	findStaff,
	findStaffById,
	saveStaff,
	validateStaff
} from './staff-service.js';
import type { Staff, StaffForm } from './staff-service.js';

/**
 * この画面が使う状態
 *
 * <p>ここに書いたものだけが読み書きでき、綴りも型もコンパイル時に検査される。</p>
 */
declare global {
	interface JimbleAppState {
		keyword: string;
		filter: string;
		list: Staff[];
		loading: boolean;
		page: number;
		sortKey: string;
		sortOrder: SortOrder;
		form: StaffForm;
		errors: Record<string, string>;
		hasError: boolean;
		saving: boolean;
		staff: Staff | null;
		confirming: boolean;
		removing: boolean;
	}
}

/* 1 ページあたりの件数 */
const PAGE_SIZE = 5;

/* ------------------------------------------------------------------
 * 画面
 * ------------------------------------------------------------------ */

/**
 * 一覧画面
 *
 * @param ctx コンテキスト
 * @return 画面
 */
const StaffListPage = (ctx: Context) => UI.column(

	UI.row(
		UI.title('社員一覧'),
		UI.button('新規登録').primary().go('/staff/new')
	).justify('between'),

	UI.tabs((c) => [
		{ value: 'all', label: 'すべて', badge: c.get('list').length },
		{ value: 'active', label: '在職', badge: c.get('list').filter((staff) => staff.active).length },
		{ value: 'left', label: '退職', badge: c.get('list').filter((staff) => !staff.active).length }
	]).bind('filter').onChange(() => ctx.set('page', 1)),

	UI.row(
		UI.text('keyword')
			.placeholder('氏名・メールで絞り込み')
			.bind('keyword')
			.onInput(() => {
				ctx.set('page', 1);
				void reloadStaff(ctx);
			}),
		UI.button('クリア').quiet()
			.when(() => !!ctx.get('keyword'))
			.onClick(() => {
				ctx.set('keyword', '');
				void reloadStaff(ctx);
			})
	).align('end').gap('sm'),

	UI.table<Staff>((c) => paged(visible(c)))
		.rowKey((staff) => String(staff.id))
		.loading(() => ctx.get('loading'))
		.empty('該当する社員がいません')
		.sort(() => ctx.get('sortKey'), () => ctx.get('sortOrder'))
		.onSort((key, order) => ctx.store.patch({ sortKey: key, sortOrder: order }))
		.column({ key: 'name', label: '氏名', sortable: true })
		.column({ key: 'mail', label: 'メールアドレス' })
		.column({ key: 'age', label: '年齢', sortable: true, align: 'end', width: '80px' })
		.column({ key: 'department', label: '部署', cell: (staff) => departmentLabel(staff.department) })
		.column({ key: 'employment', label: '雇用形態', cell: (staff) => employmentLabel(staff.employment) })
		.column({
			key: 'active',
			label: '状態',
			align: 'center',
			cell: (staff) => UI.label(staff.active ? '在職' : '退職').caption()
		})
		.column({
			key: 'actions',
			label: '',
			align: 'end',
			width: '90px',
			cell: (staff) => UI.button('詳細').quiet().go('/staff/' + String(staff.id))
		}),

	UI.pagination()
		.page(() => ctx.get('page'))
		.pages((c) => Math.max(1, Math.ceil(visible(c).length / PAGE_SIZE)))
		.total((c) => visible(c).length)
		.onChange((page) => ctx.set('page', page))

).gap('lg').width('lg');

/**
 * 登録画面
 *
 * @param ctx コンテキスト
 * @return 画面
 */
const StaffNewPage = (ctx: Context) => UI.column(

	UI.title('社員登録'),

	UI.form(
		UI.card(
			UI.text('name')
				.label('氏名').required().placeholder('山田 太郎')
				.bind('form.name').onInput(revalidateStaff)
				.error(() => ctx.get('errors').name),

			UI.number('age')
				.label('年齢').required()
				.bind('form.age').onInput(revalidateStaff)
				.error(() => ctx.get('errors').age),

			UI.text('mail')
				.label('メールアドレス').placeholder('yamada@example.com')
				.bind('form.mail').onInput(revalidateStaff)
				.error(() => ctx.get('errors').mail),

			UI.select('department')
				.label('部署').required()
				.options(DEPARTMENTS)
				.bind('form.department')
				.error(() => ctx.get('errors').department),

			UI.radio('employment')
				.label('雇用形態').inline()
				.options(EMPLOYMENTS)
				.bind('form.employment'),

			UI.row(
				UI.checkbox('active').label('在職中').bind('form.active'),
				UI.toggle('notify').label('通知を受け取る').bind('form.notify')
			).gap('xl'),

			UI.textarea('note')
				.label('備考').hint('所属や引き継ぎ事項など（Enter で送信されます）')
				.bind('form.note'),

			UI.row(
				UI.button('キャンセル').go('/'),
				UI.button('登録する').primary().loading(() => ctx.get('saving')).onClick(submitStaff)
			).justify('end').gap('sm')
		).gap('lg')
	).onSubmit(submitStaff),

	UI.label('入力内容を確認してください').danger().when(() => ctx.get('hasError'))

).gap('lg').width('md');

/**
 * 詳細画面
 *
 * @param ctx コンテキスト
 * @return 画面
 */
const StaffDetailPage = (ctx: Context) => UI.column(

	UI.title(() => ctx.get('staff')?.name ?? '社員詳細'),

	UI.card(
		UI.caption('社員ID'),
		UI.label(() => String(ctx.get('staff')?.id ?? '')),
		UI.caption('メールアドレス'),
		UI.label(() => ctx.get('staff')?.mail ?? '-'),
		UI.caption('部署'),
		UI.label(() => departmentLabel(ctx.get('staff')?.department ?? '')),
		UI.caption('雇用形態'),
		UI.label(() => employmentLabel(ctx.get('staff')?.employment ?? '')),
		UI.caption('状態'),
		UI.label(() => (ctx.get('staff')?.active === true ? '在職' : '退職'))
	).gap('xs').when(() => ctx.get('staff') != null),

	UI.label('社員が見つかりません').danger().when(() => ctx.get('staff') == null),

	UI.row(
		UI.button('一覧へ戻る').go('/'),
		UI.button('削除').danger()
			.when(() => ctx.get('staff') != null)
			.onClick(() => ctx.set('confirming', true))
	).gap('sm'),

	UI.dialog(
		UI.label(() => (ctx.get('staff')?.name ?? '') + ' を削除します。元に戻せません。')
	)
		.title('削除の確認')
		.size('sm')
		.open(() => ctx.get('confirming'))
		.onClose(() => ctx.set('confirming', false))
		.footer(
			UI.row(
				UI.button('やめる').onClick(() => ctx.set('confirming', false)),
				UI.button('削除する').danger().loading(() => ctx.get('removing')).onClick(removeStaff)
			).justify('end').gap('sm')
		)

).gap('lg').width('md');

/* ------------------------------------------------------------------
 * ロジック（画面から分離）
 * ------------------------------------------------------------------ */

/**
 * タブと並べ替えを反映した一覧を作る
 *
 * @param ctx コンテキスト
 * @return 社員一覧
 */
function visible (ctx: Context): Staff[] {

	const filter = ctx.get('filter');
	const key = ctx.get('sortKey');
	const order = ctx.get('sortOrder');

	const list = ctx.get('list').filter((staff) => filter === 'all'
		|| (filter === 'active' && staff.active)
		|| (filter === 'left' && !staff.active));

	if (!key) {
		return list;
	}

	return [...list].sort((a, b) => {
		const left = (a as unknown as Record<string, unknown>)[key];
		const right = (b as unknown as Record<string, unknown>)[key];
		const compared = typeof left === 'number' && typeof right === 'number'
			? left - right
			: String(left).localeCompare(String(right), 'ja');
		return order === 'asc' ? compared : -compared;
	});

}

/**
 * 現在のページ分だけ取り出す
 *
 * @param list 社員一覧
 * @return 社員一覧
 */
function paged (list: Staff[]): Staff[] {

	const page = Math.max(1, APP.store.get('page'));
	return list.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

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

/**
 * 一覧を読み込む
 *
 * @param ctx コンテキスト
 * @return 処理
 */
async function reloadStaff (ctx: Context): Promise<void> {

	ctx.set('loading', true);
	const list = await findStaff(ctx.get('keyword'));
	ctx.store.patch({ list, loading: false });

}

/**
 * 入力中に再検証する（一度エラーを出した後だけ）
 *
 * @param value 入力値
 * @param ctx コンテキスト
 * @return なし
 */
function revalidateStaff (_value: unknown, ctx: Context): void {

	if (!ctx.get('hasError')) {
		return;
	}

	const errors = validateStaff(ctx.get('form'));
	ctx.store.patch({ errors, hasError: Object.keys(errors).length > 0 });

}

/**
 * 登録する
 *
 * @param ctx コンテキスト
 * @return 処理
 */
async function submitStaff (ctx: Context): Promise<void> {

	const form = ctx.get('form');
	const errors = validateStaff(form);

	ctx.store.patch({ errors, hasError: Object.keys(errors).length > 0 });
	if (Object.keys(errors).length > 0) {
		notify.warning('入力内容を確認してください');
		return;
	}

	ctx.set('saving', true);
	const staff = await saveStaff(form);
	ctx.store.patch({ saving: false, form: {}, errors: {}, hasError: false, page: 1 });

	notify.success(staff.name + ' を登録しました');
	ctx.go('/');

}

/**
 * 削除する
 *
 * @param ctx コンテキスト
 * @return 処理
 */
async function removeStaff (ctx: Context): Promise<void> {

	const staff = ctx.get('staff');
	if (staff == null) {
		return;
	}

	ctx.set('removing', true);
	await deleteStaff(staff.id);
	ctx.store.patch({ removing: false, confirming: false, staff: null });

	notify.success(staff.name + ' を削除しました');
	ctx.go('/');

}

/* ------------------------------------------------------------------
 * 起動
 * ------------------------------------------------------------------ */

const APP = App.of()

	.state({
		keyword: '',
		filter: 'all',
		list: [],
		loading: false,
		page: 1,
		sortKey: '',
		sortOrder: 'asc',
		form: {},
		errors: {},
		hasError: false,
		saving: false,
		staff: null,
		confirming: false,
		removing: false
	})

	.theme('original')

	/* 全画面共通の枠 */
	.layout((page) => UI.column(
		UI.row(
			UI.column(
				UI.heading('jimble-ui デモ'),
				UI.caption('HTML と CSS を 1 行も書かない管理画面')
			).gap('xs'),
			UI.row(
				UI.caption('テーマ'),
				...themeNames().map((name: string) => (ctx: Context) => themeButton(name, ctx))
			).gap('sm')
		).justify('between').align('end').width('lg'),
		page
	).gap('xl').pad('xl').align('center'))

	.route('/', StaffListPage, { enter: reloadStaff })
	.route('/staff/new', StaffNewPage)
	.route('/staff/:id', StaffDetailPage, {
		enter: async (ctx) => {
			ctx.store.patch({ confirming: false });
			ctx.set('staff', await findStaffById(ctx.params.id ?? ''));
		}
	})
	.notFound(() => UI.card(
		UI.title('ページが見つかりません'),
		UI.button('一覧へ戻る').primary().go('/')
	))

	.mount();
