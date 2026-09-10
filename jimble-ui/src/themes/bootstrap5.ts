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
import type { Align, ButtonVariant, Justify, Size, TextVariant } from '../core/types.js';

/**
 * bootstrap5 テーマ
 *
 * <p>
 * マークアップは Bootstrap 5 の utility / component クラスで組む。
 * Bootstrap 本体の CSS は vendor/bootstrap5.css.js に同梱してあり、
 * <b>このテーマを選んだときだけ</b>読み込まれる。
 * </p>
 */

/* 間隔（gap-*） */
const GAP: Record<Size, string> = { none: 'gap-0', xs: 'gap-1', sm: 'gap-2', md: 'gap-3', lg: 'gap-4', xl: 'gap-5' };

/* 余白（p-*） */
const PAD: Record<Size, string> = { none: 'p-0', xs: 'p-1', sm: 'p-2', md: 'p-3', lg: 'p-4', xl: 'p-5' };

/* 交差軸 */
const ALIGN: Record<Align, string> = {
	start: 'align-items-start',
	center: 'align-items-center',
	end: 'align-items-end',
	stretch: 'align-items-stretch'
};

/* 主軸 */
const JUSTIFY: Record<Justify, string> = {
	start: 'justify-content-start',
	center: 'justify-content-center',
	end: 'justify-content-end',
	between: 'justify-content-between'
};

/* ボタン */
const BUTTON: Record<ButtonVariant, string> = {
	default: 'btn btn-light border',
	primary: 'btn btn-primary',
	danger: 'btn btn-danger',
	quiet: 'btn btn-link text-decoration-none',
	'quiet-danger': 'btn btn-link text-danger text-decoration-none'
};

/* 文字 */
const TEXT: Record<TextVariant, string> = {
	title: 'fs-3 fw-bold',
	heading: 'fs-5 fw-semibold',
	body: '',
	caption: 'small text-secondary',
	danger: 'text-danger'
};

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

/* 幅と表示だけは CSS で持つ（Bootstrap に相当する utility が無いため） */
const HOST_STYLES = `
	:host { display: block; }
	:host([jb-width="full"]) { width: 100%; }
	:host([jb-width="sm"]) { width: 100%; max-width: 360px; }
	:host([jb-width="md"]) { width: 100%; max-width: 640px; }
	:host([jb-width="lg"]) { width: 100%; max-width: 960px; }
`;


/* ダイアログだけは位置決めの CSS が要る（Bootstrap の modal は表示制御を JS が持つため） */
const DIALOG_STYLES = `
	:host { display: none; }
	:host([jb-open]) { display: block; }
	.backdrop { position: fixed; inset: 0; background: rgba(0,0,0,.5); z-index: 1050; }
	.modal { position: fixed; inset: 0; z-index: 1055; overflow: auto; }
`;

/* トーストの色 */
const TOAST_CLASS: Record<string, string> = {
	info: 'text-bg-light border',
	success: 'text-bg-success',
	warning: 'text-bg-warning',
	danger: 'text-bg-danger'
};


/* Bootstrap に相当する utility が無いものだけ CSS を持つ */
const GRID_STYLES = `
	:host { display: grid; gap: 1rem; }
	:host([jb-columns="1"]) { grid-template-columns: 1fr; }
	:host([jb-columns="2"]) { grid-template-columns: repeat(2, 1fr); }
	:host([jb-columns="3"]) { grid-template-columns: repeat(3, 1fr); }
	:host([jb-columns="4"]) { grid-template-columns: repeat(4, 1fr); }
	:host([jb-columns="5"]) { grid-template-columns: repeat(5, 1fr); }
	:host([jb-columns="6"]) { grid-template-columns: repeat(6, 1fr); }
	:host([jb-gap="sm"]) { gap: .5rem; }
	:host([jb-gap="lg"]) { gap: 1.5rem; }
	:host([jb-gap="xl"]) { gap: 2rem; }
	@media (max-width: 720px) { :host { grid-template-columns: 1fr !important; } }
`;

const CHART_STYLES = HOST_STYLES + `
	svg { display: block; width: 100%; overflow: visible; }
	.bar { fill: var(--bs-primary, #0d6efd); }
	.line { fill: none; stroke: var(--bs-primary, #0d6efd); stroke-width: 2; vector-effect: non-scaling-stroke; }
	.dot { fill: var(--bs-primary, #0d6efd); }
	.axis { stroke: var(--bs-border-color, #dee2e6); }
`;

const FILE_STYLES = HOST_STYLES + `
	.drop { border: 1px dashed var(--bs-border-color, #dee2e6); cursor: pointer; }
	:host([jb-dragging]) .drop { border-color: var(--bs-primary, #0d6efd); }
	input[type="file"] { display: none; }
`;

/* つかんで並べ替えるときの目印（Bootstrap に相当する部品が無いので直接書く） */
const TABLE_DRAG_STYLES = `
	tbody tr.dragging { opacity: .4; }
	tbody tr.drop-before td { box-shadow: inset 0 2px 0 0 var(--bs-primary, #0d6efd); }
	tbody tr.drop-after td { box-shadow: inset 0 -2px 0 0 var(--bs-primary, #0d6efd); }
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
	.dropdown-menu { display: block; position: fixed; z-index: 40; min-width: 10rem; }
`;

/* jb-nav */
const NAV_STYLES = `
	:host { display: flex; flex-direction: column; height: 100%; }
	.foot { margin-top: auto; }
`;

/* jb-page-header */
const PAGE_HEADER_STYLES = `
	:host { display: block; }
	::slotted([slot="breadcrumb"]) { display: block; margin-bottom: .25rem; }
	::slotted([slot="below"]) { display: block; margin-top: .75rem; }
`;

export default registerTheme({

	name: 'bootstrap5',

	tokens: {
		'font': "system-ui, -apple-system, 'Hiragino Kaku Gothic ProN', 'Noto Sans JP', sans-serif",
		'color-bg': '#f8f9fa',
		'color-text': '#212529',
		'font-size-body': '1rem'
	},

	base: [
		'html, body { margin: 0; padding: 0; min-height: 100%; }',
		'body { background: var(--jb-color-bg); color: var(--jb-color-text); font-family: var(--jb-font); }',
		'*, *::before, *::after { box-sizing: border-box; }'
	].join('\n'),

	shared: [() => import('../../vendor/bootstrap5.css.js')],

	components: {

		'jb-stack': component<JbStack>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class=${classes(
					el.surface ? 'card' : 'd-flex',
					el.surface ? 'd-flex' : '',
					el.direction === 'row' ? 'flex-row' : 'flex-column',
					pick(GAP, el.gap, 'gap-3'),
					pick(PAD, el.pad, el.surface ? 'p-4' : ''),
					pick(ALIGN, el.align, el.direction === 'row' ? 'align-items-center' : ''),
					pick(JUSTIFY, el.justify),
					el.wrap ? 'flex-wrap' : ''
				)}><slot></slot></div>
			`
		}),

		'jb-text': component<JbText>({
			styles: HOST_STYLES,
			template: (el, html) => html`<span class=${pick(TEXT, el.variant)}>${el.text}</span>`
		}),

		'jb-button': component<JbButton>({
			styles: ':host { display: inline-block; }',
			template: (el, html) => html`
				<button
					type="button"
					class=${pick(BUTTON, el.variant, BUTTON.default)}
					?disabled=${el.disabled || el.loading}
					@click=${(e: Event) => el.handleClick(e)}>
					${el.loading ? html`<span class="spinner-border spinner-border-sm me-2"></span>` : ''}${!el.loading && el.icon
						? html`<jb-icon class="me-1" jb-name=${el.icon} jb-size="sm"></jb-icon>`
						: ''}${el.label}
				</button>
			`
		}),

		'jb-input': component<JbInput>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div>
					${el.label
						? html`<label class="form-label mb-1">
								${el.label}${el.required ? html`<span class="text-danger ms-1">*</span>` : ''}
							</label>`
						: ''}
					${el.multiline
						? html`<textarea
								class=${classes('form-control', el.error ? 'is-invalid' : '')}
								rows="4"
								.value=${el.value ?? ''}
								placeholder=${el.placeholder ?? ''}
								?disabled=${el.disabled}
								@input=${(e: Event) => el.handleInput(e)}
								@change=${(e: Event) => el.handleChange(e)}></textarea>`
						: html`<input
								class=${classes('form-control', el.error ? 'is-invalid' : '')}
								type=${el.type || 'text'}
								.value=${el.value ?? ''}
								placeholder=${el.placeholder ?? ''}
								?disabled=${el.disabled}
								@input=${(e: Event) => el.handleInput(e)}
								@change=${(e: Event) => el.handleChange(e)}>`}
					${el.error
						? html`<div class="invalid-feedback d-block">${el.error}</div>`
						: el.hint ? html`<div class="form-text">${el.hint}</div>` : ''}
				</div>
			`
		})
,

		'jb-select': component<JbSelect>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div>
					${el.label
						? html`<label class="form-label mb-1">${el.label}${el.required ? html`<span class="text-danger ms-1">*</span>` : ''}</label>`
						: ''}
					<select
						class=${classes('form-select', el.error ? 'is-invalid' : '')}
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
						? html`<div class="invalid-feedback d-block">${el.error}</div>`
						: el.hint ? html`<div class="form-text">${el.hint}</div>` : ''}
				</div>
			`
		}),

		'jb-checkbox': component<JbCheckbox>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class="form-check">
					<input
						class=${classes('form-check-input', el.error ? 'is-invalid' : '')}
						type="checkbox"
						.checked=${el.checked}
						?disabled=${el.disabled}
						@change=${(e: Event) => el.handleChange(e)}>
					<label class="form-check-label">${el.label}</label>
					${el.error
						? html`<div class="invalid-feedback d-block">${el.error}</div>`
						: el.hint ? html`<div class="form-text">${el.hint}</div>` : ''}
				</div>
			`
		}),

		'jb-switch': component<JbCheckbox>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class="form-check form-switch">
					<input
						class="form-check-input"
						type="checkbox"
						role="switch"
						.checked=${el.checked}
						?disabled=${el.disabled}
						@change=${(e: Event) => el.handleChange(e)}>
					<label class="form-check-label">${el.label}</label>
					${el.hint ? html`<div class="form-text">${el.hint}</div>` : ''}
				</div>
			`
		}),

		'jb-radio': component<JbRadio>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div>
					${el.label
						? html`<label class="form-label mb-1">${el.label}${el.required ? html`<span class="text-danger ms-1">*</span>` : ''}</label>`
						: ''}
					<div>
						${el.options.map((option) => html`
							<div class=${classes('form-check', el.inline ? 'form-check-inline' : '')}>
								<input
									class="form-check-input"
									type="radio"
									name=${el.name}
									value=${option.value}
									?checked=${option.value === el.value}
									?disabled=${el.disabled || option.disabled === true}
									@change=${(e: Event) => el.handleChange(e)}>
								<label class="form-check-label">${option.label}</label>
							</div>
						`)}
					</div>
					${el.error
						? html`<div class="invalid-feedback d-block">${el.error}</div>`
						: el.hint ? html`<div class="form-text">${el.hint}</div>` : ''}
				</div>
			`
		}),

		'jb-tabs': component<JbTabs>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<ul class="nav nav-tabs">
					${el.tabs.map((tab) => html`
						<li class="nav-item">
							<button
								class=${classes('nav-link', tab.value === el.value ? 'active' : '')}
								@click=${() => el.handleSelect(tab.value)}>
								${tab.label}
								${tab.badge == null ? '' : html`<span class="badge text-bg-secondary ms-1">${tab.badge}</span>`}
							</button>
						</li>
					`)}
				</ul>
			`
		}),

		'jb-table': component<JbTable>({
			styles: HOST_STYLES + TABLE_DRAG_STYLES,
			template: (el, html) => html`
				<div class="card">
					<div class="table-responsive">
						<table class="table table-hover align-middle mb-0">
							<thead>
								<tr>
									${el.columns.map((column) => html`
										<th
											class=${classes('text-secondary small', column.align === 'center' ? 'text-center' : '', column.align === 'end' ? 'text-end' : '')}
											style=${(column.width == null ? '' : 'width:' + column.width + ';') + (column.sortable === true ? 'cursor:pointer' : '')}
											@click=${() => el.handleSort(column)}>
											${column.label}
											${el.sortKey === column.key ? html`<span class="text-primary ms-1">${el.sortOrder === 'asc' ? '▲' : '▼'}</span>` : ''}
										</th>
									`)}
								</tr>
							</thead>
							<tbody>
								${el.loading
									? html`<tr><td class="text-center text-secondary py-5" colspan=${el.columns.length}>読み込み中…</td></tr>`
									: el.rows.length === 0
										? html`<tr><td class="text-center text-secondary py-5" colspan=${el.columns.length}>${el.empty}</td></tr>`
										: el.rows.map((row, index) => html`
											<tr
												class=${el.dragState(index)}
												style=${el.clickable || el.reorderable ? 'cursor:pointer' : ''}
												draggable=${el.reorderable ? 'true' : 'false'}
												@click=${() => el.handleRowClick(row, index)}
												@dragstart=${(e: DragEvent) => el.handleDragStart(index, e)}
												@dragover=${(e: DragEvent) => el.handleDragOver(index, e)}
												@drop=${(e: DragEvent) => el.handleDrop(index, e)}
												@dragend=${() => el.handleDragEnd()}>
												${row.cells.map((cell, position) => html`
													<td class=${classes(
														el.columns[position]?.align === 'center' ? 'text-center' : '',
														el.columns[position]?.align === 'end' ? 'text-end' : ''
													)}>${cell}</td>
												`)}
											</tr>
										`)}
							</tbody>
						</table>
					</div>
				</div>
			`
		}),

		'jb-pagination': component<JbPagination>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class="d-flex align-items-center justify-content-between flex-wrap gap-2">
					<span class="text-secondary small">${el.summary || '全 ' + String(el.total) + ' 件'}</span>
					<ul class="pagination pagination-sm mb-0">
						<li class=${classes('page-item', el.page <= 1 ? 'disabled' : '')}>
							<button class="page-link" @click=${() => el.handleSelect(el.page - 1)}>前へ</button>
						</li>
						${el.pageNumbers().map((number) => number === 0
							? html`<li class="page-item disabled"><span class="page-link">…</span></li>`
							: html`
								<li class=${classes('page-item', number === el.page ? 'active' : '')}>
									<button class="page-link" @click=${() => el.handleSelect(number)}>${number}</button>
								</li>
							`)}
						<li class=${classes('page-item', el.page >= el.pages ? 'disabled' : '')}>
							<button class="page-link" @click=${() => el.handleSelect(el.page + 1)}>次へ</button>
						</li>
					</ul>
				</div>
			`
		}),

		'jb-dialog': component<JbDialog>({
			styles: DIALOG_STYLES,
			template: (el, html) => html`
				${el.open ? html`
					<div class="backdrop" @click=${() => el.handleClose()}></div>
					<div class="modal d-block">
						<div class=${classes('modal-dialog modal-dialog-centered', el.size === 'sm' ? 'modal-sm' : '', el.size === 'lg' ? 'modal-lg' : '')}>
							<div class="modal-content">
								<div class="modal-header">
									<h5 class="modal-title">${el.title}</h5>
									${el.closable ? html`<button class="btn-close" @click=${() => el.handleClose()}></button>` : ''}
								</div>
								<div class="modal-body"><slot></slot></div>
								<div class="modal-footer"><slot name="footer"></slot></div>
							</div>
						</div>
					</div>
				` : ''}
			`
		}),

		'jb-toast': component<JbToast>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class=${classes('toast show d-flex align-items-center', TOAST_CLASS[el.variant] ?? TOAST_CLASS['info'])}>
					<div class="toast-body flex-grow-1">${el.message}</div>
					<button class="btn-close me-2" @click=${() => el.handleClose()}></button>
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
				<div class="card h-100">
					<div class="card-body">
						<div class="text-secondary small fw-semibold">${el.label}</div>
						<div class="fs-3 fw-bold">${el.value}${el.unit ? html`<span class="fs-6 text-secondary ms-1">${el.unit}</span>` : ''}</div>
						${el.delta
							? html`<div class=${classes('small fw-bold', el.trend === 'up' ? 'text-success' : el.trend === 'down' ? 'text-danger' : 'text-secondary')}>
									${el.trend === 'up' ? '▲' : el.trend === 'down' ? '▼' : ''} ${el.delta}
								</div>`
							: ''}
						${el.hint ? html`<div class="small text-secondary">${el.hint}</div>` : ''}
					</div>
				</div>
			`
		}),

		'jb-chart': component<JbChart>({
			styles: CHART_STYLES,
			template: (el, html, svg) => html`
				<div class="card">
					<div class="card-body">
						${el.title ? html`<h6 class="card-title fw-semibold mb-3">${el.title}</h6>` : ''}
						${el.points.length === 0
							? html`<div class="text-center text-secondary py-5">${el.empty}</div>`
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
								<div class="d-flex justify-content-between mt-1">
									${el.points.map((point) => html`<span class="small text-secondary">${point.label}</span>`)}
								</div>
							`}
					</div>
				</div>
			`
		}),

		'jb-file': component<JbFile>({
			styles: FILE_STYLES,
			template: (el, html) => html`
				<div>
					${el.label ? html`<label class="form-label mb-1">${el.label}</label>` : ''}
					<label
						class="drop d-flex flex-column align-items-center gap-1 rounded p-4 text-center bg-body-tertiary text-secondary"
						@dragover=${(e: DragEvent) => el.handleDragging(e, true)}
						@dragleave=${(e: DragEvent) => el.handleDragging(e, false)}
						@drop=${(e: DragEvent) => el.handleDrop(e)}>
						<span>ここに放り込むか、押して選んでください</span>
						${el.accept ? html`<span class="small">${el.accept}</span>` : ''}
						<input type="file" accept=${el.accept} ?multiple=${el.multiple} ?disabled=${el.disabled}
							@change=${(e: Event) => el.handleSelect(e)}>
					</label>
					${el.files.length === 0 ? '' : html`
						<ul class="list-group list-group-flush mt-2">
							${el.files.map((file) => html`
								<li class="list-group-item d-flex justify-content-between align-items-center px-0 py-1 small">
									<span>${file.name}（${Math.ceil(file.size / 1024)}KB）</span>
									<button class="btn btn-sm btn-link p-0" @click=${() => el.handleClear()}>取消</button>
								</li>
							`)}
						</ul>
					`}
					${el.error
						? html`<div class="invalid-feedback d-block">${el.error}</div>`
						: el.hint ? html`<div class="form-text">${el.hint}</div>` : ''}
				</div>
			`
		}),

		'jb-daterange': component<JbDateRange>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div>
					${el.label
						? html`<label class="form-label mb-1">${el.label}${el.required ? html`<span class="text-danger ms-1">*</span>` : ''}</label>`
						: ''}
					<div class="d-flex align-items-center gap-2">
						<input type="date" class="form-control" .value=${el.from} ?disabled=${el.disabled}
							@change=${(e: Event) => el.handleFrom(e)}>
						<span class="text-secondary">〜</span>
						<input type="date" class="form-control" .value=${el.to} ?disabled=${el.disabled}
							@change=${(e: Event) => el.handleTo(e)}>
					</div>
					${el.error
						? html`<div class="invalid-feedback d-block">${el.error}</div>`
						: el.hint ? html`<div class="form-text">${el.hint}</div>` : ''}
				</div>
			`
		}),

		'jb-breadcrumb': component<JbBreadcrumb>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<nav>
					<ol class="breadcrumb mb-0">
						${el.items.map((item) => html`
							<li class=${classes('breadcrumb-item', item.path == null ? 'active' : '')}>
								${item.path == null
									? html`<span>${item.label}</span>`
									: html`<button class="btn btn-link p-0 align-baseline" @click=${() => el.handleSelect(item)}>${item.label}</button>`}
							</li>
						`)}
					</ol>
				</nav>
			`
		}),

		'jb-accordion': component<JbAccordion>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class="accordion">
					${el.sections.map((section) => html`
						<div class="accordion-item">
							<h2 class="accordion-header">
								<button
									class=${classes('accordion-button', el.isOpen(section.value) ? '' : 'collapsed')}
									@click=${() => el.handleToggle(section.value)}>
									${section.label}
								</button>
							</h2>
							${el.isOpen(section.value)
								? html`<div class="accordion-collapse show"><div class="accordion-body"><slot name=${'section-' + section.value}></slot></div></div>`
								: ''}
						</div>
					`)}
				</div>
			`
		}),

		'jb-empty': component<JbEmpty>({
			styles: HOST_STYLES,
			template: (el, html) => html`
				<div class="card bg-body-tertiary border-0 text-center">
					<div class="card-body py-5">
						${el.icon ? html`<div class="fs-1">${el.icon}</div>` : ''}
						<div class="fw-semibold">${el.heading}</div>
						${el.description ? html`<div class="small text-secondary">${el.description}</div>` : ''}
						<div class="mt-3"><slot></slot></div>
					</div>
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
					class="btn btn-sm btn-light border-0 text-secondary"
					type="button"
					aria-haspopup="menu"
					aria-expanded=${el.open ? 'true' : 'false'}
					?disabled=${el.disabled}
					@click=${(e: Event) => el.handleToggle(e)}>
					${el.icon ? html`<jb-icon jb-name=${el.icon} jb-size="sm"></jb-icon>` : ''}
					${el.label ? html`<span class="ms-1">${el.label}</span>` : ''}
				</button>
				${el.open ? html`
					<ul class="dropdown-menu shadow-sm" role="menu" style=${menuPosition(el)}>
						${el.items.map((item) => item.divider === true
							? html`<li><hr class="dropdown-divider"></li>`
							: html`
								<li>
									<button
										class=${classes('dropdown-item d-flex align-items-center gap-2', item.danger === true ? 'text-danger' : '', item.disabled === true ? 'disabled' : '')}
										type="button"
										role="menuitem"
										?disabled=${item.disabled === true}
										@click=${(e: Event) => el.handleSelect(item, e)}>
										${item.icon ? html`<jb-icon jb-name=${item.icon} jb-size="sm"></jb-icon>` : ''}
										<span>${item.label}</span>
									</button>
								</li>
							`)}
					</ul>
				` : ''}
			`
		}),

		'jb-nav': component<JbNav>({
			styles: NAV_STYLES,
			template: (el, html) => html`
				<div class="d-flex flex-column h-100 p-3 bg-white border-end">
					${el.heading ? html`<div class="fs-5 fw-bold mb-3">${el.heading}</div>` : ''}
					<slot></slot>
					<ul class="nav nav-pills flex-column gap-1">
						${el.items.map((item, index) => html`
							${el.startsGroup(index)
								? html`<li class="text-secondary small fw-bold text-uppercase mt-3 mb-1 px-2">${item.group}</li>`
								: ''}
							<li class="nav-item">
								<button
									class=${classes('nav-link d-flex align-items-center gap-2 w-100 text-start', el.isCurrent(item) ? 'current active' : 'link-dark', item.disabled === true ? 'disabled' : '')}
									type="button"
									?disabled=${item.disabled === true}
									@click=${() => el.handleSelect(item)}>
									${item.icon ? html`<jb-icon jb-name=${item.icon} jb-size="sm"></jb-icon>` : ''}
									<span class="flex-grow-1 text-truncate">${item.label}</span>
									${item.badge == null ? '' : html`<span class="badge bg-secondary-subtle text-secondary-emphasis">${item.badge}</span>`}
								</button>
							</li>
						`)}
					</ul>
					<div class="foot pt-3"><slot name="footer"></slot></div>
				</div>
			`
		}),

		'jb-page-header': component<JbPageHeader>({
			styles: PAGE_HEADER_STYLES,
			template: (el, html) => html`
				<slot name="breadcrumb"></slot>
				<div class="d-flex flex-wrap align-items-start gap-3">
					<div class="flex-grow-1 min-w-0">
						<div class="fs-3 fw-bold">${el.heading}</div>
						${el.description ? html`<div class="small text-secondary">${el.description}</div>` : ''}
					</div>
					<div class="d-flex flex-wrap align-items-center gap-2"><slot></slot></div>
				</div>
				<slot name="below"></slot>
			`
		})

	}

});
