import { registerTheme, component } from '../core/theme.js';
import type { JbStack } from '../components/jb-stack.js';
import type { JbText } from '../components/jb-text.js';
import type { JbButton } from '../components/jb-button.js';
import type { JbInput } from '../components/jb-input.js';
import type { JbSelect } from '../components/jb-select.js';
import type { JbCheckbox } from '../components/jb-checkbox.js';
import type { JbRadio } from '../components/jb-radio.js';
import type { JbTabs } from '../components/jb-tabs.js';
import type { JbTable } from '../components/jb-table.js';
import type { JbPagination } from '../components/jb-pagination.js';
import type { JbDialog } from '../components/jb-dialog.js';
import type { JbToast } from '../components/jb-toast.js';
import type { JbForm } from '../components/jb-form.js';
import type { JbGrid } from '../components/jb-grid.js';
import type { JbStat } from '../components/jb-stat.js';
import type { JbChart } from '../components/jb-chart.js';
import type { JbFile } from '../components/jb-file.js';
import type { JbDateRange } from '../components/jb-daterange.js';
import type { JbBreadcrumb } from '../components/jb-breadcrumb.js';
import type { JbAccordion } from '../components/jb-accordion.js';
import type { JbEmpty } from '../components/jb-empty.js';
import type { JbIcon } from '../components/jb-icon.js';
import type { JbMenu } from '../components/jb-menu.js';
import type { JbNav } from '../components/jb-nav.js';
import type { JbPageHeader } from '../components/jb-page-header.js';
import { iconPaths } from './icons.js';
import { menuPosition } from './position.js';
import type { Align, ButtonVariant, Justify, Size, TextVariant, Width } from '../core/types.js';

/**
 * tailwind-dark テーマ
 *
 * <p>
 * マークアップは Tailwind の utility クラスで組む。
 * 使っているクラスだけを抽出した CSS を vendor/tailwind-dark.css.js に同梱してあり、
 * <b>このテーマを選んだときだけ</b>読み込まれる。
 * </p>
 *
 * <p>
 * クラス名は必ず「文字列そのまま」で書くこと。
 * 'bg-' + color のような組み立て方をすると、CSS 生成時に検出されない。
 * </p>
 */

/* 間隔 */
const GAP: Record<Size, string> = { none: 'gap-0', xs: 'gap-1', sm: 'gap-2', md: 'gap-3', lg: 'gap-5', xl: 'gap-8' };

/* 余白 */
const PAD: Record<Size, string> = { none: 'p-0', xs: 'p-1', sm: 'p-2', md: 'p-3', lg: 'p-5', xl: 'p-8' };

/* 交差軸 */
const ALIGN: Record<Align, string> = { start: 'items-start', center: 'items-center', end: 'items-end', stretch: 'items-stretch' };

/* 主軸 */
const JUSTIFY: Record<Justify, string> = {
	start: 'justify-start',
	center: 'justify-center',
	end: 'justify-end',
	between: 'justify-between'
};

/* 幅 */
const WIDTH: Record<Width, string> = { full: 'w-full', sm: 'w-full max-w-sm', md: 'w-full max-w-2xl', lg: 'w-full max-w-5xl' };

/* ボタン */
const BUTTON_BASE: string = 'inline-flex h-9 items-center justify-center gap-2 rounded-lg px-4 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-50';
const BUTTON: Record<ButtonVariant, string> = {
	default: 'bg-slate-700 text-slate-100 hover:bg-slate-600',
	primary: 'bg-indigo-500 text-white hover:bg-indigo-400',
	danger: 'bg-rose-600 text-white hover:bg-rose-500',
	quiet: 'bg-transparent px-2 text-indigo-300 hover:text-indigo-200',
	'quiet-danger': 'bg-transparent px-2 text-rose-400 hover:text-rose-300'
};

/* 文字 */
const TEXT: Record<TextVariant, string> = {
	title: 'text-2xl font-bold text-slate-100',
	heading: 'text-lg font-semibold text-slate-100',
	body: 'text-sm text-slate-200',
	caption: 'text-xs text-slate-400',
	danger: 'text-sm text-rose-400'
};

/* 入力 */
const FIELD_BASE = 'w-full rounded-lg border bg-slate-900 px-2 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/40 disabled:opacity-60';
const FIELD_NORMAL = 'border-slate-700 focus:border-indigo-500';
const FIELD_ERROR = 'border-rose-500 focus:border-rose-500 focus:ring-rose-500/40';

/**
 * クラス名をつなぐ
 *
 * @param {...string} names	クラス名
 * @return {string}	クラス
 */
const classes = (...names: (string | false | undefined)[]): string => names.filter(Boolean).join(' ');

/**
 * 対応表から引く（未指定なら既定値）
 *
 * @param map 対応表
 * @param key キー
 * @param fallback 既定値
 * @return クラス
 */
const pick = <K extends string>(map: Record<K, string>, key: K | undefined, fallback = ''): string =>
	(key == null ? undefined : map[key]) ?? fallback;

/* 表示と幅だけは :host に当てる（Shadow DOM のホストには class を付けられないため） */
const HOST_STYLES = `
	:host { display: block; }
	:host([jb-width="full"]) { width: 100%; }
	:host([jb-width="sm"]) { width: 100%; max-width: 24rem; }
	:host([jb-width="md"]) { width: 100%; max-width: 42rem; }
	:host([jb-width="lg"]) { width: 100%; max-width: 64rem; }
`;


/* ラベルと補足（各入力で共通） */
const LABEL_CLASS = 'mb-1 block text-xs font-semibold text-slate-400';
const HINT_CLASS = 'mt-1 text-xs text-slate-500';
const ERROR_CLASS = 'mt-1 text-xs text-rose-400';

/* ページ送り・表のボタン */
const PAGE_BUTTON = 'inline-flex h-8 min-w-8 items-center justify-center rounded-md border border-slate-700 bg-slate-800 px-2 text-sm text-slate-200 hover:bg-slate-700 disabled:opacity-40';
const PAGE_CURRENT = 'inline-flex h-8 min-w-8 items-center justify-center rounded-md border border-indigo-500 bg-indigo-500 px-2 text-sm font-semibold text-white';

/* トーストの色 */
const TOAST_CLASS: Record<string, string> = {
	info: 'border-slate-600 bg-slate-800 text-slate-100',
	success: 'border-emerald-500 bg-emerald-950 text-emerald-100',
	warning: 'border-amber-500 bg-amber-950 text-amber-100',
	danger: 'border-rose-500 bg-rose-950 text-rose-100'
};

/* ダイアログの幅 */
const DIALOG_WIDTH: Record<string, string> = {
	sm: 'max-w-sm',
	md: 'max-w-lg',
	lg: 'max-w-3xl'
};

/* ダイアログの位置決めだけは CSS で持つ */
const DIALOG_STYLES = `
	:host { display: none; }
	:host([jb-open]) { display: block; position: fixed; inset: 0; z-index: 1000; }
`;


/* グリッドと SVG は utility では表せないので CSS で持つ */
const GRID_STYLES = `
	:host { display: grid; gap: .75rem; }
	:host([jb-columns="1"]) { grid-template-columns: 1fr; }
	:host([jb-columns="2"]) { grid-template-columns: repeat(2, 1fr); }
	:host([jb-columns="3"]) { grid-template-columns: repeat(3, 1fr); }
	:host([jb-columns="4"]) { grid-template-columns: repeat(4, 1fr); }
	:host([jb-columns="5"]) { grid-template-columns: repeat(5, 1fr); }
	:host([jb-columns="6"]) { grid-template-columns: repeat(6, 1fr); }
	:host([jb-gap="sm"]) { gap: .5rem; }
	:host([jb-gap="lg"]) { gap: 1.25rem; }
	:host([jb-gap="xl"]) { gap: 2rem; }
	@media (max-width: 720px) { :host { grid-template-columns: 1fr !important; } }
`;

const CHART_STYLES = HOST_STYLES + `
	svg { display: block; width: 100%; overflow: visible; }
	.bar { fill: rgb(99 102 241); }
	.line { fill: none; stroke: rgb(129 140 248); stroke-width: 2; vector-effect: non-scaling-stroke; }
	.dot { fill: rgb(129 140 248); }
	.axis { stroke: rgb(51 65 85); }
`;

const FILE_STYLES = HOST_STYLES + `
	input[type="file"] { display: none; }
`;

/* カード面（stat / chart / empty で使い回す） */
const SURFACE = 'rounded-xl border border-slate-700 bg-slate-800 p-5';

/* つかんで並べ替えるときの目印（utility クラスで書けないので直接書く） */
const TABLE_DRAG_STYLES = `
	tbody tr.dragging { opacity: .4; }
	tbody tr.drop-before td { box-shadow: inset 0 2px 0 0 #818cf8; }
	tbody tr.drop-after td { box-shadow: inset 0 -2px 0 0 #818cf8; }
`;

/* jb-icon */
const ICON_STYLES = `
	:host { display: inline-flex; align-items: center; justify-content: center; color: inherit; vertical-align: middle; }
	svg { width: 18px; height: 18px; display: block; }
	:host([jb-size="sm"]) svg { width: 14px; height: 14px; }
	:host([jb-size="lg"]) svg { width: 24px; height: 24px; }
`;

/* jb-menu */
const MENU_STYLES = `
	:host { display: inline-block; position: relative; }
	.list { position: fixed; z-index: 40; }
`;

/* jb-nav */
const NAV_STYLES = `
	:host { display: flex; flex-direction: column; height: 100%; }
`;

/* jb-page-header */
const PAGE_HEADER_STYLES = `
	:host { display: block; }
	::slotted([slot="breadcrumb"]) { display: block; margin-bottom: .25rem; }
	::slotted([slot="below"]) { display: block; margin-top: .75rem; }
`;

export default registerTheme({

	name: 'tailwind-dark',

	tokens: {
		'font': "system-ui, -apple-system, 'Hiragino Kaku Gothic ProN', 'Noto Sans JP', sans-serif",
		'color-bg': '#0f172a',
		'color-text': '#e2e8f0',
		'font-size-body': '14px'
	},

	base: [
		'html, body { margin: 0; padding: 0; min-height: 100%; }',
		'body { background: var(--jb-color-bg); color: var(--jb-color-text);',
		' font-family: var(--jb-font); font-size: var(--jb-font-size-body); }',
		'*, *::before, *::after { box-sizing: border-box; }'
	].join('\n'),

	shared: [() => import('../../vendor/tailwind-dark.css.js')],

	components: {

		'jb-stack': component<JbStack>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class=${classes(
					'flex min-w-0',
					el.direction === 'row' ? 'flex-row' : 'flex-col',
					pick(GAP, el.gap, 'gap-3'),
					pick(PAD, el.pad, el.surface ? 'p-5' : ''),
					pick(ALIGN, el.align, el.direction === 'row' ? 'items-center' : ''),
					pick(JUSTIFY, el.justify),
					el.wrap ? 'flex-wrap' : '',
					el.surface ? 'rounded-xl border border-slate-700 bg-slate-800 shadow-sm' : '',
					pick(WIDTH, el.width)
				)}><slot></slot></div>
			`
		}),

		'jb-text': component<JbText>({
			styles: HOST_STYLES,
			template: (el, html) => html`<span class=${pick(TEXT, el.variant, TEXT.body)}>${el.text}</span>`
		}),

		'jb-button': component<JbButton>({
			styles: ':host { display: inline-block; }',
			template: (el, html) => html`
				<button
					type="button"
					class=${classes(BUTTON_BASE, pick(BUTTON, el.variant, BUTTON.default))}
					?disabled=${el.disabled || el.loading}
					@click=${(e: Event) => el.handleClick(e)}>
					${el.loading
						? html`<span class="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-r-transparent"></span>`
						: ''}${!el.loading && el.icon ? html`<jb-icon jb-name=${el.icon} jb-size="sm"></jb-icon>` : ''}${el.label}
				</button>
			`
		}),

		'jb-input': component<JbInput>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div>
					${el.label
						? html`<label class="mb-1 block text-xs font-semibold text-slate-400">
								${el.label}${el.required ? html`<span class="ml-1 text-rose-400">*</span>` : ''}
							</label>`
						: ''}
					${el.multiline
						? html`<textarea
								class=${classes(FIELD_BASE, 'min-h-24 py-2', el.error ? FIELD_ERROR : FIELD_NORMAL)}
								.value=${el.value ?? ''}
								placeholder=${el.placeholder ?? ''}
								?disabled=${el.disabled}
								@input=${(e: Event) => el.handleInput(e)}
								@change=${(e: Event) => el.handleChange(e)}></textarea>`
						: html`<input
								class=${classes(FIELD_BASE, 'h-9', el.error ? FIELD_ERROR : FIELD_NORMAL)}
								type=${el.type || 'text'}
								.value=${el.value ?? ''}
								placeholder=${el.placeholder ?? ''}
								?disabled=${el.disabled}
								@input=${(e: Event) => el.handleInput(e)}
								@change=${(e: Event) => el.handleChange(e)}>`}
					${el.error
						? html`<p class="mt-1 text-xs text-rose-400">${el.error}</p>`
						: el.hint ? html`<p class="mt-1 text-xs text-slate-500">${el.hint}</p>` : ''}
				</div>
			`
		})
,

		'jb-select': component<JbSelect>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div>
					${el.label
						? html`<label class=${LABEL_CLASS}>${el.label}${el.required ? html`<span class="ml-1 text-rose-400">*</span>` : ''}</label>`
						: ''}
					<select
						class=${classes(FIELD_BASE, 'h-9', el.error ? FIELD_ERROR : FIELD_NORMAL)}
						?disabled=${el.disabled}
						@change=${(e: Event) => el.handleChange(e)}>
						${el.placeholder || !el.value
							? html`<option value="" ?selected=${!el.value}>${el.placeholder || '選択してください'}</option>`
							: ''}
						${el.options.map((option) => html`
							<option value=${option.value} ?disabled=${option.disabled === true} ?selected=${option.value === el.value}>${option.label}</option>
						`)}
					</select>
					${el.error
						? html`<p class=${ERROR_CLASS}>${el.error}</p>`
						: el.hint ? html`<p class=${HINT_CLASS}>${el.hint}</p>` : ''}
				</div>
			`
		}),

		'jb-checkbox': component<JbCheckbox>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div>
					<label class="inline-flex cursor-pointer items-center gap-2">
						<input
							type="checkbox"
							class="h-4 w-4 rounded border-slate-600 bg-slate-900 accent-indigo-500"
							.checked=${el.checked}
							?disabled=${el.disabled}
							@change=${(e: Event) => el.handleChange(e)}>
						<span class="text-sm text-slate-200">${el.label}</span>
					</label>
					${el.error
						? html`<p class=${ERROR_CLASS}>${el.error}</p>`
						: el.hint ? html`<p class=${HINT_CLASS}>${el.hint}</p>` : ''}
				</div>
			`
		}),

		'jb-switch': component<JbCheckbox>({
			styles: HOST_STYLES + `
				input:checked + span { background-color: rgb(99 102 241); }
				input:checked + span > span { transform: translateX(1rem); }
			`,
			template: (el, html) => html`
				<div>
					<label class="inline-flex cursor-pointer items-center gap-2">
						<input
							type="checkbox"
							class="sr-only"
							.checked=${el.checked}
							?disabled=${el.disabled}
							@change=${(e: Event) => el.handleChange(e)}>
						<span class="relative h-5 w-9 rounded-full bg-slate-600 transition">
							<span class="absolute left-0.5 top-0.5 h-4 w-4 rounded-full bg-white transition"></span>
						</span>
						<span class="text-sm text-slate-200">${el.label}</span>
					</label>
					${el.hint ? html`<p class=${HINT_CLASS}>${el.hint}</p>` : ''}
				</div>
			`
		}),

		'jb-radio': component<JbRadio>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div>
					${el.label
						? html`<label class=${LABEL_CLASS}>${el.label}${el.required ? html`<span class="ml-1 text-rose-400">*</span>` : ''}</label>`
						: ''}
					<div class=${classes('flex', el.inline ? 'flex-row flex-wrap gap-5' : 'flex-col gap-2')}>
						${el.options.map((option) => html`
							<label class="inline-flex cursor-pointer items-center gap-2">
								<input
									type="radio"
									class="h-4 w-4 border-slate-600 bg-slate-900 accent-indigo-500"
									name=${el.name}
									value=${option.value}
									?checked=${option.value === el.value}
									?disabled=${el.disabled || option.disabled === true}
									@change=${(e: Event) => el.handleChange(e)}>
								<span class="text-sm text-slate-200">${option.label}</span>
							</label>
						`)}
					</div>
					${el.error
						? html`<p class=${ERROR_CLASS}>${el.error}</p>`
						: el.hint ? html`<p class=${HINT_CLASS}>${el.hint}</p>` : ''}
				</div>
			`
		}),

		'jb-tabs': component<JbTabs>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class="flex gap-5 border-b border-slate-700">
					${el.tabs.map((tab) => html`
						<button
							class=${classes(
								'-mb-px border-b-2 px-1 py-2 text-sm font-semibold transition',
								tab.value === el.value
									? 'border-indigo-400 text-indigo-300'
									: 'border-transparent text-slate-400 hover:text-slate-200'
							)}
							@click=${() => el.handleSelect(tab.value)}>
							${tab.label}
							${tab.badge == null
								? ''
								: html`<span class="ml-1 rounded-full bg-slate-700 px-2 text-xs text-slate-200">${tab.badge}</span>`}
						</button>
					`)}
				</div>
			`
		}),

		'jb-table': component<JbTable>({
			styles: HOST_STYLES + TABLE_DRAG_STYLES,
			template: (el, html) => html`
				<div class="overflow-x-auto rounded-xl border border-slate-700 bg-slate-800">
					<table class="w-full border-collapse text-sm">
						<thead>
							<tr>
								${el.columns.map((column) => html`
									<th
										class=${classes(
											'border-b border-slate-700 px-3 py-2 text-xs font-semibold text-slate-400',
											column.align === 'center' ? 'text-center' : column.align === 'end' ? 'text-right' : 'text-left',
											column.sortable === true ? 'cursor-pointer hover:text-slate-200' : ''
										)}
										style=${column.width == null ? '' : 'width:' + column.width}
										@click=${() => el.handleSort(column)}>
										${column.label}
										${el.sortKey === column.key ? html`<span class="ml-1 text-indigo-300">${el.sortOrder === 'asc' ? '▲' : '▼'}</span>` : ''}
									</th>
								`)}
							</tr>
						</thead>
						<tbody>
							${el.loading
								? html`<tr><td class="px-3 py-8 text-center text-slate-400" colspan=${el.columns.length}>読み込み中…</td></tr>`
								: el.rows.length === 0
									? html`<tr><td class="px-3 py-8 text-center text-slate-400" colspan=${el.columns.length}>${el.empty}</td></tr>`
									: el.rows.map((row, index) => html`
										<tr
											class=${classes(
												'border-b border-slate-700 last:border-0 text-slate-200',
												el.clickable ? 'cursor-pointer hover:bg-slate-700' : '',
												el.reorderable ? 'cursor-grab' : '',
												el.dragState(index)
											)}
											draggable=${el.reorderable ? 'true' : 'false'}
											@click=${() => el.handleRowClick(row, index)}
											@dragstart=${(e: DragEvent) => el.handleDragStart(index, e)}
											@dragover=${(e: DragEvent) => el.handleDragOver(index, e)}
											@drop=${(e: DragEvent) => el.handleDrop(index, e)}
											@dragend=${() => el.handleDragEnd()}>
											${row.cells.map((cell, position) => html`
												<td class=${classes(
													'px-3 py-2',
													el.columns[position]?.align === 'center' ? 'text-center' : el.columns[position]?.align === 'end' ? 'text-right' : 'text-left'
												)}>${cell}</td>
											`)}
										</tr>
									`)}
						</tbody>
					</table>
				</div>
			`
		}),

		'jb-pagination': component<JbPagination>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class="flex flex-wrap items-center justify-between gap-3">
					<span class="text-xs text-slate-400">${el.summary || '全 ' + String(el.total) + ' 件'}</span>
					<div class="flex items-center gap-1">
						<button class=${PAGE_BUTTON} ?disabled=${el.page <= 1} @click=${() => el.handleSelect(el.page - 1)}>前へ</button>
						${el.pageNumbers().map((number) => number === 0
							? html`<span class="px-1 text-slate-500">…</span>`
							: html`<button class=${number === el.page ? PAGE_CURRENT : PAGE_BUTTON} @click=${() => el.handleSelect(number)}>${number}</button>`)}
						<button class=${PAGE_BUTTON} ?disabled=${el.page >= el.pages} @click=${() => el.handleSelect(el.page + 1)}>次へ</button>
					</div>
				</div>
			`
		}),

		'jb-dialog': component<JbDialog>({
			styles: DIALOG_STYLES,
			template: (el, html) => html`
				${el.open ? html`
					<div class="absolute inset-0 bg-slate-950/70" @click=${() => el.handleClose()}></div>
					<div class=${classes('relative mx-auto mt-24 w-[calc(100%-2rem)] overflow-hidden rounded-xl border border-slate-700 bg-slate-800 shadow-2xl', DIALOG_WIDTH[el.size] ?? DIALOG_WIDTH['md'])}>
						<div class="flex items-center justify-between gap-3 border-b border-slate-700 px-5 py-4">
							<span class="text-base font-semibold text-slate-100">${el.title}</span>
							${el.closable
								? html`<button class="text-lg leading-none text-slate-400 hover:text-slate-200" @click=${() => el.handleClose()}>×</button>`
								: ''}
						</div>
						<div class="px-5 py-4 text-slate-200"><slot></slot></div>
						<div class="border-t border-slate-700 px-5 py-4"><slot name="footer"></slot></div>
					</div>
				` : ''}
			`
		}),

		'jb-toast': component<JbToast>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class=${classes('flex min-w-60 max-w-sm items-center gap-3 rounded-lg border px-4 py-3 text-sm shadow-lg', TOAST_CLASS[el.variant] ?? TOAST_CLASS['info'])}>
					<span class="flex-1">${el.message}</span>
					<button class="text-base leading-none opacity-70 hover:opacity-100" @click=${() => el.handleClose()}>×</button>
				</div>
			`
		}),

		'jb-form': component<JbForm>({
			styles: ':host { display: contents; }',
			template: (_el, html) => html`<slot></slot>`
		})
,

		'jb-grid': component<JbGrid>({
			styles: GRID_STYLES,
			template: (el, html) => html`
				<style>${el.min ? ':host { grid-template-columns: repeat(auto-fill, minmax(' + el.min + ', 1fr)); }' : ''}</style>
				<slot></slot>
			`
		}),

		'jb-stat': component<JbStat>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class=${classes(SURFACE, 'flex h-full flex-col gap-1')}>
					<span class="text-xs font-semibold text-slate-400">${el.label}</span>
					<span class="text-2xl font-bold text-slate-100">
						${el.value}${el.unit ? html`<span class="ml-1 text-sm font-semibold text-slate-400">${el.unit}</span>` : ''}
					</span>
					${el.delta
						? html`<span class=${classes('text-xs font-bold', el.trend === 'up' ? 'text-emerald-400' : el.trend === 'down' ? 'text-rose-400' : 'text-slate-400')}>
								${el.trend === 'up' ? '▲' : el.trend === 'down' ? '▼' : ''} ${el.delta}
							</span>`
						: ''}
					${el.hint ? html`<span class="text-xs text-slate-500">${el.hint}</span>` : ''}
				</div>
			`
		}),

		'jb-chart': component<JbChart>({
			styles: CHART_STYLES,
			template: (el, html, svg) => html`
				<div class=${SURFACE}>
					${el.title ? html`<div class="mb-3 text-base font-semibold text-slate-100">${el.title}</div>` : ''}
					${el.points.length === 0
						? html`<div class="py-8 text-center text-slate-400">${el.empty}</div>`
						: html`
							<svg viewBox="0 0 100 100" preserveAspectRatio="none" style=${'height:' + String(el.height) + 'px'}>
								${el.type === 'bar'
									? el.points.map((point, index) => {
										const width = 100 / el.points.length;
										const height = el.ratio(point.value) * 96;
										return svg`<rect class="bar"
											x=${String(index * width + width * 0.15)}
											y=${String(100 - height)}
											width=${String(width * 0.7)}
											height=${String(height)}></rect>`;
									})
									: svg`
										<polyline class="line" points=${el.polyline(100, 96)}></polyline>
										${el.points.map((point, index) => svg`<circle class="dot"
											cx=${String(el.points.length === 1 ? 50 : (index / (el.points.length - 1)) * 100)}
											cy=${String(96 - el.ratio(point.value) * 96)}
											r="1.2"></circle>`)}
									`}
								${svg`<line class="axis" x1="0" y1="100" x2="100" y2="100"></line>`}
							</svg>
							<div class="mt-1 flex justify-between">
								${el.points.map((point) => html`<span class="text-xs text-slate-400">${point.label}</span>`)}
							</div>
						`}
				</div>
			`
		}),

		'jb-file': component<JbFile>({
			styles: FILE_STYLES,
			template: (el, html) => html`
				<div>
					${el.label ? html`<label class=${LABEL_CLASS}>${el.label}</label>` : ''}
					<label
						class=${classes(
							'flex cursor-pointer flex-col items-center gap-1 rounded-lg border border-dashed p-8 text-center text-sm text-slate-400',
							el.dragging ? 'border-indigo-400 bg-slate-700' : 'border-slate-600 bg-slate-900',
							el.error ? 'border-rose-500' : ''
						)}
						@dragover=${(e: DragEvent) => el.handleDragging(e, true)}
						@dragleave=${(e: DragEvent) => el.handleDragging(e, false)}
						@drop=${(e: DragEvent) => el.handleDrop(e)}>
						<span>ここに放り込むか、押して選んでください</span>
						${el.accept ? html`<span class="text-xs text-slate-500">${el.accept}</span>` : ''}
						<input type="file" accept=${el.accept} ?multiple=${el.multiple} ?disabled=${el.disabled}
							@change=${(e: Event) => el.handleSelect(e)}>
					</label>
					${el.files.length === 0 ? '' : html`
						<div class="mt-2 flex flex-col gap-1">
							${el.files.map((file) => html`
								<span class="flex items-center justify-between text-xs text-slate-300">
									<span>${file.name}（${Math.ceil(file.size / 1024)}KB）</span>
									<button class="text-indigo-300 hover:text-indigo-200" @click=${() => el.handleClear()}>取消</button>
								</span>
							`)}
						</div>
					`}
					${el.error
						? html`<p class=${ERROR_CLASS}>${el.error}</p>`
						: el.hint ? html`<p class=${HINT_CLASS}>${el.hint}</p>` : ''}
				</div>
			`
		}),

		'jb-daterange': component<JbDateRange>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div>
					${el.label
						? html`<label class=${LABEL_CLASS}>${el.label}${el.required ? html`<span class="ml-1 text-rose-400">*</span>` : ''}</label>`
						: ''}
					<div class="flex items-center gap-2">
						<input type="date" class=${classes(FIELD_BASE, 'h-9', FIELD_NORMAL)} .value=${el.from}
							?disabled=${el.disabled} @change=${(e: Event) => el.handleFrom(e)}>
						<span class="text-slate-400">〜</span>
						<input type="date" class=${classes(FIELD_BASE, 'h-9', FIELD_NORMAL)} .value=${el.to}
							?disabled=${el.disabled} @change=${(e: Event) => el.handleTo(e)}>
					</div>
					${el.error
						? html`<p class=${ERROR_CLASS}>${el.error}</p>`
						: el.hint ? html`<p class=${HINT_CLASS}>${el.hint}</p>` : ''}
				</div>
			`
		}),

		'jb-breadcrumb': component<JbBreadcrumb>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<nav class="flex flex-wrap items-center gap-2 text-xs">
					${el.items.map((item, index) => html`
						${index === 0 ? '' : html`<span class="text-slate-500">${el.separator}</span>`}
						${item.path == null
							? html`<span class="text-slate-400">${item.label}</span>`
							: html`<button class="text-indigo-300 hover:text-indigo-200" @click=${() => el.handleSelect(item)}>${item.label}</button>`}
					`)}
				</nav>
			`
		}),

		'jb-accordion': component<JbAccordion>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class="overflow-hidden rounded-xl border border-slate-700 bg-slate-800">
					${el.sections.map((section, index) => html`
						<div class=${index === 0 ? '' : 'border-t border-slate-700'}>
							<button
								class="flex w-full items-center justify-between gap-3 px-5 py-3 text-left text-sm font-semibold text-slate-100 hover:bg-slate-700"
								@click=${() => el.handleToggle(section.value)}>
								<span>${section.label}</span>
								<span class=${classes('text-slate-400 transition', el.isOpen(section.value) ? 'rotate-90' : '')}>▶</span>
							</button>
							${el.isOpen(section.value)
								? html`<div class="px-5 pb-5 text-slate-200"><slot name=${'section-' + section.value}></slot></div>`
								: ''}
						</div>
					`)}
				</div>
			`
		}),

		'jb-empty': component<JbEmpty>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class="flex flex-col items-center gap-2 rounded-xl border border-dashed border-slate-600 bg-slate-800 p-8 text-center">
					${el.icon ? html`<span class="text-3xl">${el.icon}</span>` : ''}
					<span class="text-base font-semibold text-slate-100">${el.heading}</span>
					${el.description ? html`<span class="text-xs text-slate-400">${el.description}</span>` : ''}
					<span class="mt-3"><slot></slot></span>
				</div>
			`
		}),

		'jb-icon': component<JbIcon>({
			styles: ICON_STYLES,
			template: (el, html, svg) => html`
				<svg
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="1.8"
					stroke-linecap="round"
					stroke-linejoin="round"
					role=${el.alt ? 'img' : 'presentation'}
					aria-label=${el.alt || 'アイコン'}
					aria-hidden=${el.alt ? 'false' : 'true'}>
					${iconPaths(el.name).map((path) => svg`<path d=${path}></path>`)}
				</svg>
			`
		}),

		'jb-menu': component<JbMenu>({
			styles: MENU_STYLES,
			template: (el, html) => html`
				<button
					class="inline-flex h-8 items-center gap-1 rounded-lg px-2 text-sm text-slate-400 transition hover:bg-slate-700 hover:text-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
					type="button"
					aria-haspopup="menu"
					aria-expanded=${el.open ? 'true' : 'false'}
					?disabled=${el.disabled}
					@click=${(e: Event) => el.handleToggle(e)}>
					${el.icon ? html`<jb-icon jb-name=${el.icon} jb-size="sm"></jb-icon>` : ''}
					${el.label ? html`<span>${el.label}</span>` : ''}
				</button>
				${el.open ? html`
					<div class="list min-w-40 rounded-xl border border-slate-700 bg-slate-800 p-1 shadow-xl" role="menu" style=${menuPosition(el)}>
						${el.items.map((item) => item.divider === true
							? html`<div class="my-1 h-px bg-slate-700"></div>`
							: html`
								<button
									class=${classes(
										'flex w-full items-center gap-2 whitespace-nowrap rounded-lg px-2 py-2 text-left text-sm transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50',
										item.danger === true ? 'text-rose-400' : 'text-slate-200'
									)}
									type="button"
									role="menuitem"
									?disabled=${item.disabled === true}
									@click=${(e: Event) => el.handleSelect(item, e)}>
									${item.icon ? html`<jb-icon jb-name=${item.icon} jb-size="sm"></jb-icon>` : ''}
									<span>${item.label}</span>
								</button>
							`)}
					</div>
				` : ''}
			`
		}),

		'jb-nav': component<JbNav>({
			styles: NAV_STYLES,
			template: (el, html) => html`
				<div class="flex h-full flex-col gap-2 border-r border-slate-700 bg-slate-800 p-3">
					${el.heading ? html`<div class="px-2 pb-2 text-lg font-bold text-slate-100">${el.heading}</div>` : ''}
					<slot></slot>
					<div class="flex flex-col gap-0.5">
						${el.items.map((item, index) => html`
							${el.startsGroup(index)
								? html`<div class="px-2 pb-1 pt-3 text-xs font-bold tracking-wide text-slate-500">${item.group}</div>`
								: ''}
							<button
								class=${classes(
									'flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left text-sm transition disabled:cursor-not-allowed disabled:opacity-50',
									el.isCurrent(item) ? 'current bg-indigo-500 text-white' : 'text-slate-200 hover:bg-slate-700'
								)}
								type="button"
								?disabled=${item.disabled === true}
								@click=${() => el.handleSelect(item)}>
								${item.icon ? html`<jb-icon jb-name=${item.icon} jb-size="sm"></jb-icon>` : ''}
								<span class="flex-1 truncate">${item.label}</span>
								${item.badge == null
									? ''
									: html`<span class="rounded-full bg-slate-700 px-2 py-0.5 text-xs text-slate-300">${item.badge}</span>`}
							</button>
						`)}
					</div>
					<div class="mt-auto pt-3"><slot name="footer"></slot></div>
				</div>
			`
		}),

		'jb-page-header': component<JbPageHeader>({
			styles: PAGE_HEADER_STYLES,
			template: (el, html) => html`
				<slot name="breadcrumb"></slot>
				<div class="flex flex-wrap items-start gap-3">
					<div class="min-w-0 flex-1">
						<div class="text-2xl font-bold text-slate-100">${el.heading}</div>
						${el.description ? html`<div class="text-xs text-slate-400">${el.description}</div>` : ''}
					</div>
					<div class="flex flex-wrap items-center gap-2"><slot></slot></div>
				</div>
				<slot name="below"></slot>
			`
		})

	}

});
