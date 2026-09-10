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

/**
 * original テーマ
 *
 * <p>
 * 外部の CSS フレームワークに依存しない、素の Shadow DOM 実装。
 * 見た目はすべてデザイントークン（--jb-*）から作る。
 * </p>
 */

/* デザイントークン */
export const TOKENS: Record<string, string> = {

	'font': "system-ui, -apple-system, 'Hiragino Kaku Gothic ProN', 'Noto Sans JP', sans-serif",

	'color-bg': '#f5f6f8',
	'color-surface': '#ffffff',
	'color-text': '#1b1f24',
	'color-muted': '#6b7280',
	'color-border': '#d8dde3',
	'color-primary': '#2563eb',
	'color-primary-text': '#ffffff',
	'color-danger': '#d92d20',
	'color-focus': 'rgba(37, 99, 235, .25)',

	'space-none': '0px',
	'space-xs': '4px',
	'space-sm': '8px',
	'space-md': '12px',
	'space-lg': '20px',
	'space-xl': '32px',

	'radius': '8px',

	'font-size-title': '22px',
	'font-size-heading': '17px',
	'font-size-body': '14px',
	'font-size-caption': '12px'

};

/* jb-stack */
const STACK_STYLES = `
	:host {
		display: flex;
		flex-direction: column;
		gap: var(--jb-space-md, 12px);
		min-width: 0;
	}
	:host([jb-direction="row"]) { flex-direction: row; align-items: center; }

	:host([jb-gap="none"]) { gap: 0; }
	:host([jb-gap="xs"]) { gap: var(--jb-space-xs, 4px); }
	:host([jb-gap="sm"]) { gap: var(--jb-space-sm, 8px); }
	:host([jb-gap="md"]) { gap: var(--jb-space-md, 12px); }
	:host([jb-gap="lg"]) { gap: var(--jb-space-lg, 20px); }
	:host([jb-gap="xl"]) { gap: var(--jb-space-xl, 32px); }

	:host([jb-pad="xs"]) { padding: var(--jb-space-xs, 4px); }
	:host([jb-pad="sm"]) { padding: var(--jb-space-sm, 8px); }
	:host([jb-pad="md"]) { padding: var(--jb-space-md, 12px); }
	:host([jb-pad="lg"]) { padding: var(--jb-space-lg, 20px); }
	:host([jb-pad="xl"]) { padding: var(--jb-space-xl, 32px); }

	:host([jb-align="start"]) { align-items: flex-start; }
	:host([jb-align="center"]) { align-items: center; }
	:host([jb-align="end"]) { align-items: flex-end; }
	:host([jb-align="stretch"]) { align-items: stretch; }

	:host([jb-justify="start"]) { justify-content: flex-start; }
	:host([jb-justify="center"]) { justify-content: center; }
	:host([jb-justify="end"]) { justify-content: flex-end; }
	:host([jb-justify="between"]) { justify-content: space-between; }

	:host([jb-wrap]) { flex-wrap: wrap; }

	:host([jb-width="full"]) { width: 100%; }
	:host([jb-width="sm"]) { width: 100%; max-width: 360px; }
	:host([jb-width="md"]) { width: 100%; max-width: 640px; }
	:host([jb-width="lg"]) { width: 100%; max-width: 960px; }

	:host([jb-surface]) {
		background: var(--jb-color-surface, #fff);
		border: 1px solid var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
		padding: var(--jb-space-lg, 20px);
	}
	:host([jb-surface][jb-pad="sm"]) { padding: var(--jb-space-sm, 8px); }
	:host([jb-surface][jb-pad="md"]) { padding: var(--jb-space-md, 12px); }
	:host([jb-surface][jb-pad="lg"]) { padding: var(--jb-space-lg, 20px); }
`;

/* jb-text */
const TEXT_STYLES = `
	:host {
		display: block;
		color: var(--jb-color-text, #1b1f24);
		font-family: var(--jb-font, system-ui);
		font-size: var(--jb-font-size-body, 14px);
		line-height: 1.6;
		white-space: pre-wrap;
	}
	:host([jb-variant="title"]) {
		font-size: var(--jb-font-size-title, 22px);
		font-weight: 700;
		line-height: 1.35;
	}
	:host([jb-variant="heading"]) {
		font-size: var(--jb-font-size-heading, 17px);
		font-weight: 600;
		line-height: 1.4;
	}
	:host([jb-variant="caption"]) {
		font-size: var(--jb-font-size-caption, 12px);
		color: var(--jb-color-muted, #6b7280);
	}
	:host([jb-variant="danger"]) { color: var(--jb-color-danger, #d92d20); }
`;

/* jb-button */
const BUTTON_STYLES = `
	:host { display: inline-block; font-family: var(--jb-font, system-ui); }
	button {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: var(--jb-space-sm, 8px);
		min-height: 36px;
		padding: 0 var(--jb-space-lg, 20px);
		border: 1px solid var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
		background: var(--jb-color-surface, #fff);
		color: var(--jb-color-text, #1b1f24);
		font-family: inherit;
		font-size: var(--jb-font-size-body, 14px);
		font-weight: 600;
		cursor: pointer;
		transition: filter .15s, box-shadow .15s;
	}
	button:hover:not(:disabled) { filter: brightness(.97); }
	button:focus-visible { outline: none; box-shadow: 0 0 0 3px var(--jb-color-focus, #bfd3ff); }
	button:disabled { opacity: .5; cursor: not-allowed; }
	:host([jb-variant="primary"]) button {
		background: var(--jb-color-primary, #2563eb);
		border-color: var(--jb-color-primary, #2563eb);
		color: var(--jb-color-primary-text, #fff);
	}
	:host([jb-variant="danger"]) button {
		background: var(--jb-color-danger, #d92d20);
		border-color: var(--jb-color-danger, #d92d20);
		color: #fff;
	}
	:host([jb-variant="quiet"]) button {
		background: transparent;
		border-color: transparent;
		color: var(--jb-color-primary, #2563eb);
		padding: 0 var(--jb-space-sm, 8px);
	}
	:host([jb-variant="quiet-danger"]) button {
		background: transparent;
		border-color: transparent;
		color: var(--jb-color-danger, #d92d20);
		padding: 0 var(--jb-space-sm, 8px);
	}
	:host([jb-variant="quiet"]) button:hover:not(:disabled),
	:host([jb-variant="quiet-danger"]) button:hover:not(:disabled) {
		background: rgba(0,0,0,.05);
		filter: none;
	}
	.spinner {
		width: 13px;
		height: 13px;
		border: 2px solid currentColor;
		border-right-color: transparent;
		border-radius: 50%;
		animation: spin .7s linear infinite;
	}
	@keyframes spin { to { transform: rotate(360deg); } }
`;

/* jb-input */
const INPUT_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	label { display: flex; flex-direction: column; gap: var(--jb-space-xs, 4px); }
	.label {
		font-size: var(--jb-font-size-caption, 12px);
		font-weight: 600;
		color: var(--jb-color-muted, #6b7280);
	}
	.required { color: var(--jb-color-danger, #d92d20); margin-left: 2px; }
	input, textarea {
		width: 100%;
		min-height: 36px;
		padding: 0 var(--jb-space-sm, 8px);
		border: 1px solid var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
		background: var(--jb-color-surface, #fff);
		color: var(--jb-color-text, #1b1f24);
		font-family: inherit;
		font-size: var(--jb-font-size-body, 14px);
	}
	textarea { min-height: 88px; padding: var(--jb-space-sm, 8px); resize: vertical; }
	input:focus, textarea:focus {
		outline: none;
		border-color: var(--jb-color-primary, #2563eb);
		box-shadow: 0 0 0 3px var(--jb-color-focus, #bfd3ff);
	}
	input:disabled, textarea:disabled { opacity: .6; cursor: not-allowed; }
	:host([jb-error]:not([jb-error=""])) input,
	:host([jb-error]:not([jb-error=""])) textarea { border-color: var(--jb-color-danger, #d92d20); }
	.message { font-size: var(--jb-font-size-caption, 12px); color: var(--jb-color-muted, #6b7280); }
	.message.error { color: var(--jb-color-danger, #d92d20); }
`;


/* 入力の共通部分（select / checkbox / radio が使い回す） */
const FIELD_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	.label {
		display: block;
		margin-bottom: var(--jb-space-xs, 4px);
		font-size: var(--jb-font-size-caption, 12px);
		font-weight: 600;
		color: var(--jb-color-muted, #6b7280);
	}
	.required { color: var(--jb-color-danger, #d92d20); margin-left: 2px; }
	.message { display: block; margin-top: var(--jb-space-xs, 4px); font-size: var(--jb-font-size-caption, 12px); color: var(--jb-color-muted, #6b7280); }
	.message.error { color: var(--jb-color-danger, #d92d20); }
`;

/* jb-select */
const SELECT_STYLES = FIELD_STYLES + `
	select {
		width: 100%;
		min-height: 36px;
		padding: 0 var(--jb-space-sm, 8px);
		border: 1px solid var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
		background: var(--jb-color-surface, #fff);
		color: var(--jb-color-text, #1b1f24);
		font-family: inherit;
		font-size: var(--jb-font-size-body, 14px);
	}
	select:focus { outline: none; border-color: var(--jb-color-primary, #2563eb); box-shadow: 0 0 0 3px var(--jb-color-focus); }
	select:disabled { opacity: .6; cursor: not-allowed; }
	:host([jb-error]:not([jb-error=""])) select { border-color: var(--jb-color-danger, #d92d20); }
`;

/* jb-checkbox */
const CHECKBOX_STYLES = FIELD_STYLES + `
	.row { display: inline-flex; align-items: center; gap: var(--jb-space-sm, 8px); cursor: pointer; }
	input { width: 16px; height: 16px; accent-color: var(--jb-color-primary, #2563eb); cursor: pointer; }
	input:disabled { cursor: not-allowed; }
	.text { font-size: var(--jb-font-size-body, 14px); color: var(--jb-color-text, #1b1f24); }
`;

/* jb-switch */
const SWITCH_STYLES = FIELD_STYLES + `
	.row { display: inline-flex; align-items: center; gap: var(--jb-space-sm, 8px); cursor: pointer; }
	input { position: absolute; opacity: 0; width: 0; height: 0; }
	.track {
		position: relative;
		width: 38px;
		height: 22px;
		border-radius: 999px;
		background: var(--jb-color-border, #d8dde3);
		transition: background .15s;
		flex: none;
	}
	.thumb {
		position: absolute;
		top: 3px;
		left: 3px;
		width: 16px;
		height: 16px;
		border-radius: 50%;
		background: #fff;
		box-shadow: 0 1px 2px rgba(0,0,0,.25);
		transition: transform .15s;
	}
	input:checked + .track { background: var(--jb-color-primary, #2563eb); }
	input:checked + .track .thumb { transform: translateX(16px); }
	input:focus-visible + .track { box-shadow: 0 0 0 3px var(--jb-color-focus); }
	.text { font-size: var(--jb-font-size-body, 14px); }
`;

/* jb-radio */
const RADIO_STYLES = FIELD_STYLES + `
	.options { display: flex; flex-direction: column; gap: var(--jb-space-sm, 8px); }
	:host([jb-inline]) .options { flex-direction: row; flex-wrap: wrap; gap: var(--jb-space-lg, 20px); }
	.option { display: inline-flex; align-items: center; gap: var(--jb-space-sm, 8px); cursor: pointer; font-size: var(--jb-font-size-body, 14px); }
	input { width: 16px; height: 16px; accent-color: var(--jb-color-primary, #2563eb); cursor: pointer; }
`;

/* jb-tabs */
const TABS_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	.tabs { display: flex; gap: var(--jb-space-lg, 20px); border-bottom: 1px solid var(--jb-color-border, #d8dde3); }
	.tab {
		position: relative;
		padding: var(--jb-space-sm, 8px) 2px;
		border: 0;
		background: none;
		color: var(--jb-color-muted, #6b7280);
		font-family: inherit;
		font-size: var(--jb-font-size-body, 14px);
		font-weight: 600;
		cursor: pointer;
	}
	.tab:hover { color: var(--jb-color-text, #1b1f24); }
	.tab.active { color: var(--jb-color-primary, #2563eb); }
	.tab.active::after {
		content: '';
		position: absolute;
		left: 0; right: 0; bottom: -1px;
		height: 2px;
		background: var(--jb-color-primary, #2563eb);
	}
	.badge {
		margin-left: 6px;
		padding: 0 6px;
		border-radius: 999px;
		background: var(--jb-color-border, #d8dde3);
		color: var(--jb-color-text, #1b1f24);
		font-size: var(--jb-font-size-caption, 12px);
	}
`;

/* jb-table */
const TABLE_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	.wrap {
		overflow-x: auto;
		background: var(--jb-color-surface, #fff);
		border: 1px solid var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
	}
	table { width: 100%; border-collapse: collapse; font-size: var(--jb-font-size-body, 14px); }
	th, td { padding: var(--jb-space-sm, 8px) var(--jb-space-md, 12px); text-align: left; border-bottom: 1px solid var(--jb-color-border, #d8dde3); }
	th {
		color: var(--jb-color-muted, #6b7280);
		font-size: var(--jb-font-size-caption, 12px);
		font-weight: 600;
		white-space: nowrap;
		user-select: none;
	}
	th.sortable { cursor: pointer; }
	th.sortable:hover { color: var(--jb-color-text, #1b1f24); }
	.order { margin-left: 4px; color: var(--jb-color-primary, #2563eb); }
	tbody tr:last-child td { border-bottom: 0; }
	tbody tr.clickable { cursor: pointer; }
	tbody tr.clickable:hover { background: rgba(0,0,0,.03); }
	.align-center { text-align: center; }
	.align-end { text-align: right; }
	.state { padding: var(--jb-space-xl, 32px); text-align: center; color: var(--jb-color-muted, #6b7280); }

	:host([jb-reorderable]) tbody tr { cursor: grab; }
	tbody tr.dragging { opacity: .4; }
	tbody tr.drop-before td { box-shadow: inset 0 2px 0 0 var(--jb-color-primary, #2563eb); }
	tbody tr.drop-after td { box-shadow: inset 0 -2px 0 0 var(--jb-color-primary, #2563eb); }
`;

/* jb-pagination */
const PAGINATION_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	.pager { display: flex; align-items: center; justify-content: space-between; gap: var(--jb-space-md, 12px); flex-wrap: wrap; }
	.summary { font-size: var(--jb-font-size-caption, 12px); color: var(--jb-color-muted, #6b7280); }
	.buttons { display: flex; align-items: center; gap: 4px; }
	button {
		min-width: 32px;
		height: 32px;
		padding: 0 var(--jb-space-sm, 8px);
		border: 1px solid var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
		background: var(--jb-color-surface, #fff);
		color: var(--jb-color-text, #1b1f24);
		font-family: inherit;
		font-size: var(--jb-font-size-body, 14px);
		cursor: pointer;
	}
	button:hover:not(:disabled) { filter: brightness(.97); }
	button:disabled { opacity: .4; cursor: not-allowed; }
	button.current {
		background: var(--jb-color-primary, #2563eb);
		border-color: var(--jb-color-primary, #2563eb);
		color: var(--jb-color-primary-text, #fff);
		font-weight: 700;
	}
	.gap { padding: 0 4px; color: var(--jb-color-muted, #6b7280); }
`;

/* jb-dialog */
const DIALOG_STYLES = `
	:host { display: none; }
	:host([jb-open]) { display: block; position: fixed; inset: 0; z-index: 1000; font-family: var(--jb-font, system-ui); }
	.overlay { position: absolute; inset: 0; background: rgba(15, 23, 42, .45); }
	.panel {
		position: relative;
		margin: 10vh auto 0;
		width: calc(100% - 32px);
		max-width: 480px;
		max-height: 80vh;
		display: flex;
		flex-direction: column;
		background: var(--jb-color-surface, #fff);
		border-radius: var(--jb-radius, 8px);
		box-shadow: 0 16px 48px rgba(15, 23, 42, .25);
		overflow: hidden;
	}
	:host([jb-size="sm"]) .panel { max-width: 360px; }
	:host([jb-size="lg"]) .panel { max-width: 800px; }
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--jb-space-md, 12px);
		padding: var(--jb-space-lg, 20px);
		border-bottom: 1px solid var(--jb-color-border, #d8dde3);
	}
	.title { font-size: var(--jb-font-size-heading, 17px); font-weight: 700; color: var(--jb-color-text, #1b1f24); }
	.close { border: 0; background: none; color: var(--jb-color-muted, #6b7280); font-size: 20px; line-height: 1; cursor: pointer; }
	.body { padding: var(--jb-space-lg, 20px); overflow: auto; }
	.foot { padding: var(--jb-space-lg, 20px); border-top: 1px solid var(--jb-color-border, #d8dde3); }
	.foot ::slotted(*) { display: flex; justify-content: flex-end; gap: var(--jb-space-sm, 8px); }
`;

/* jb-toast */
const TOAST_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	.toast {
		display: flex;
		align-items: center;
		gap: var(--jb-space-md, 12px);
		min-width: 240px;
		max-width: 360px;
		padding: var(--jb-space-md, 12px) var(--jb-space-lg, 20px);
		border-radius: var(--jb-radius, 8px);
		background: var(--jb-color-surface, #fff);
		border-left: 4px solid var(--jb-color-primary, #2563eb);
		box-shadow: 0 8px 24px rgba(15, 23, 42, .18);
		font-size: var(--jb-font-size-body, 14px);
		color: var(--jb-color-text, #1b1f24);
	}
	:host([jb-variant="success"]) .toast { border-left-color: #16a34a; }
	:host([jb-variant="warning"]) .toast { border-left-color: #d97706; }
	:host([jb-variant="danger"]) .toast { border-left-color: var(--jb-color-danger, #d92d20); }
	.message { flex: 1; }
	.close { border: 0; background: none; color: var(--jb-color-muted, #6b7280); font-size: 16px; line-height: 1; cursor: pointer; }
`;

/* jb-form */
const FORM_STYLES = `
	:host { display: contents; }
`;


/* jb-grid */
const GRID_STYLES = `
	:host { display: grid; gap: var(--jb-space-md, 12px); }
	:host([jb-columns="1"]) { grid-template-columns: 1fr; }
	:host([jb-columns="2"]) { grid-template-columns: repeat(2, 1fr); }
	:host([jb-columns="3"]) { grid-template-columns: repeat(3, 1fr); }
	:host([jb-columns="4"]) { grid-template-columns: repeat(4, 1fr); }
	:host([jb-columns="5"]) { grid-template-columns: repeat(5, 1fr); }
	:host([jb-columns="6"]) { grid-template-columns: repeat(6, 1fr); }
	:host([jb-gap="none"]) { gap: 0; }
	:host([jb-gap="xs"]) { gap: var(--jb-space-xs, 4px); }
	:host([jb-gap="sm"]) { gap: var(--jb-space-sm, 8px); }
	:host([jb-gap="md"]) { gap: var(--jb-space-md, 12px); }
	:host([jb-gap="lg"]) { gap: var(--jb-space-lg, 20px); }
	:host([jb-gap="xl"]) { gap: var(--jb-space-xl, 32px); }
	@media (max-width: 720px) {
		:host { grid-template-columns: 1fr !important; }
	}
`;

/* jb-stat */
const STAT_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	.tile {
		display: flex;
		flex-direction: column;
		gap: var(--jb-space-xs, 4px);
		padding: var(--jb-space-lg, 20px);
		background: var(--jb-color-surface, #fff);
		border: 1px solid var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
	}
	.label { font-size: var(--jb-font-size-caption, 12px); font-weight: 600; color: var(--jb-color-muted, #6b7280); }
	.value { font-size: 26px; font-weight: 700; line-height: 1.2; color: var(--jb-color-text, #1b1f24); }
	.unit { margin-left: 4px; font-size: var(--jb-font-size-body, 14px); font-weight: 600; color: var(--jb-color-muted, #6b7280); }
	.delta { font-size: var(--jb-font-size-caption, 12px); font-weight: 700; }
	:host([jb-trend="up"]) .delta { color: #16a34a; }
	:host([jb-trend="down"]) .delta { color: var(--jb-color-danger, #d92d20); }
	:host([jb-trend="flat"]) .delta { color: var(--jb-color-muted, #6b7280); }
	.hint { font-size: var(--jb-font-size-caption, 12px); color: var(--jb-color-muted, #6b7280); }
`;

/* jb-chart */
const CHART_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	.frame {
		padding: var(--jb-space-lg, 20px);
		background: var(--jb-color-surface, #fff);
		border: 1px solid var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
	}
	.title { margin-bottom: var(--jb-space-md, 12px); font-size: var(--jb-font-size-heading, 17px); font-weight: 600; }
	svg { display: block; width: 100%; overflow: visible; }
	.bar { fill: var(--jb-color-primary, #2563eb); }
	.line { fill: none; stroke: var(--jb-color-primary, #2563eb); stroke-width: 2; vector-effect: non-scaling-stroke; }
	.dot { fill: var(--jb-color-primary, #2563eb); }
	.axis { stroke: var(--jb-color-border, #d8dde3); stroke-width: 1; }
	.labels { display: flex; justify-content: space-between; margin-top: var(--jb-space-xs, 4px); }
	.labels span { font-size: var(--jb-font-size-caption, 12px); color: var(--jb-color-muted, #6b7280); }
	.state { padding: var(--jb-space-xl, 32px); text-align: center; color: var(--jb-color-muted, #6b7280); }

	:host([jb-reorderable]) tbody tr { cursor: grab; }
	tbody tr.dragging { opacity: .4; }
	tbody tr.drop-before td { box-shadow: inset 0 2px 0 0 var(--jb-color-primary, #2563eb); }
	tbody tr.drop-after td { box-shadow: inset 0 -2px 0 0 var(--jb-color-primary, #2563eb); }
`;

/* jb-file */
const FILE_STYLES = FIELD_STYLES + `
	.drop {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--jb-space-xs, 4px);
		padding: var(--jb-space-xl, 32px) var(--jb-space-lg, 20px);
		border: 1px dashed var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
		background: var(--jb-color-surface, #fff);
		color: var(--jb-color-muted, #6b7280);
		font-size: var(--jb-font-size-body, 14px);
		cursor: pointer;
		text-align: center;
	}
	:host([jb-dragging]) .drop { border-color: var(--jb-color-primary, #2563eb); background: var(--jb-color-focus); }
	:host([jb-disabled]) .drop { opacity: .6; cursor: not-allowed; }
	:host([jb-error]:not([jb-error=""])) .drop { border-color: var(--jb-color-danger, #d92d20); }
	input { display: none; }
	.files { margin-top: var(--jb-space-sm, 8px); display: flex; flex-direction: column; gap: 4px; }
	.file { display: flex; align-items: center; justify-content: space-between; gap: var(--jb-space-sm, 8px); font-size: var(--jb-font-size-caption, 12px); }
	.remove { border: 0; background: none; color: var(--jb-color-muted, #6b7280); cursor: pointer; }
`;

/* jb-daterange */
const DATERANGE_STYLES = FIELD_STYLES + `
	.range { display: flex; align-items: center; gap: var(--jb-space-sm, 8px); }
	input {
		min-height: 36px;
		padding: 0 var(--jb-space-sm, 8px);
		border: 1px solid var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
		background: var(--jb-color-surface, #fff);
		color: var(--jb-color-text, #1b1f24);
		font-family: inherit;
		font-size: var(--jb-font-size-body, 14px);
	}
	input:focus { outline: none; border-color: var(--jb-color-primary, #2563eb); box-shadow: 0 0 0 3px var(--jb-color-focus); }
	.tilde { color: var(--jb-color-muted, #6b7280); }
`;

/* jb-breadcrumb */
const BREADCRUMB_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	.crumbs { display: flex; flex-wrap: wrap; align-items: center; gap: var(--jb-space-sm, 8px); }
	.sep { color: var(--jb-color-muted, #6b7280); font-size: var(--jb-font-size-caption, 12px); }
	button {
		border: 0;
		background: none;
		padding: 0;
		color: var(--jb-color-primary, #2563eb);
		font-family: inherit;
		font-size: var(--jb-font-size-caption, 12px);
		cursor: pointer;
	}
	button:hover { text-decoration: underline; }
	.current { color: var(--jb-color-muted, #6b7280); font-size: var(--jb-font-size-caption, 12px); }
`;

/* jb-accordion */
const ACCORDION_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	.sections {
		border: 1px solid var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
		overflow: hidden;
		background: var(--jb-color-surface, #fff);
	}
	.section + .section { border-top: 1px solid var(--jb-color-border, #d8dde3); }
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--jb-space-md, 12px);
		width: 100%;
		padding: var(--jb-space-md, 12px) var(--jb-space-lg, 20px);
		border: 0;
		background: none;
		color: var(--jb-color-text, #1b1f24);
		font-family: inherit;
		font-size: var(--jb-font-size-body, 14px);
		font-weight: 600;
		text-align: left;
		cursor: pointer;
	}
	.head:hover { background: rgba(0,0,0,.03); }
	.mark { color: var(--jb-color-muted, #6b7280); transition: transform .15s; }
	.mark.open { transform: rotate(90deg); }
	.body { padding: 0 var(--jb-space-lg, 20px) var(--jb-space-lg, 20px); }
`;

/* jb-empty */
const EMPTY_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--jb-space-sm, 8px);
		padding: var(--jb-space-xl, 32px);
		background: var(--jb-color-surface, #fff);
		border: 1px dashed var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
		text-align: center;
	}
	.icon { font-size: 32px; line-height: 1; }
	.heading { font-size: var(--jb-font-size-heading, 17px); font-weight: 600; color: var(--jb-color-text, #1b1f24); }
	.description { font-size: var(--jb-font-size-caption, 12px); color: var(--jb-color-muted, #6b7280); }
	.action { margin-top: var(--jb-space-sm, 8px); }
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
	:host { display: inline-block; position: relative; font-family: var(--jb-font, system-ui); }
	.trigger {
		display: inline-flex;
		align-items: center;
		gap: var(--jb-space-xs, 4px);
		min-height: 32px;
		padding: 0 var(--jb-space-sm, 8px);
		border: 1px solid transparent;
		border-radius: var(--jb-radius, 8px);
		background: transparent;
		color: var(--jb-color-muted, #6b7280);
		font-family: inherit;
		font-size: var(--jb-font-size-body, 14px);
		cursor: pointer;
	}
	.trigger:hover:not(:disabled) { background: rgba(0,0,0,.05); color: var(--jb-color-text, #1b1f24); }
	.trigger:disabled { opacity: .5; cursor: not-allowed; }
	.list {
		position: fixed;
		z-index: 40;
		min-width: 160px;
		padding: var(--jb-space-xs, 4px);
		border: 1px solid var(--jb-color-border, #d8dde3);
		border-radius: var(--jb-radius, 8px);
		background: var(--jb-color-surface, #fff);
		box-shadow: 0 8px 24px rgba(0,0,0,.14);
	}
	.item {
		display: flex;
		align-items: center;
		gap: var(--jb-space-sm, 8px);
		width: 100%;
		padding: var(--jb-space-sm, 8px);
		border: 0;
		border-radius: calc(var(--jb-radius, 8px) - 2px);
		background: transparent;
		color: var(--jb-color-text, #1b1f24);
		font-family: inherit;
		font-size: var(--jb-font-size-body, 14px);
		text-align: left;
		white-space: nowrap;
		cursor: pointer;
	}
	.item:hover:not(:disabled) { background: rgba(0,0,0,.05); }
	.item:disabled { opacity: .45; cursor: not-allowed; }
	.item.danger { color: var(--jb-color-danger, #d92d20); }
	.divider { height: 1px; margin: var(--jb-space-xs, 4px) 0; background: var(--jb-color-border, #d8dde3); }
`;

/* jb-nav */
const NAV_STYLES = `
	:host {
		display: flex;
		flex-direction: column;
		gap: var(--jb-space-sm, 8px);
		height: 100%;
		padding: var(--jb-space-lg, 20px) var(--jb-space-md, 12px);
		background: var(--jb-color-surface, #fff);
		border-right: 1px solid var(--jb-color-border, #d8dde3);
		font-family: var(--jb-font, system-ui);
	}
	.heading {
		padding: 0 var(--jb-space-sm, 8px) var(--jb-space-sm, 8px);
		color: var(--jb-color-text, #1b1f24);
		font-size: var(--jb-font-size-heading, 17px);
		font-weight: 700;
	}
	.items { display: flex; flex-direction: column; gap: 2px; }
	.group {
		padding: var(--jb-space-md, 12px) var(--jb-space-sm, 8px) var(--jb-space-xs, 4px);
		color: var(--jb-color-muted, #6b7280);
		font-size: var(--jb-font-size-caption, 12px);
		font-weight: 700;
		letter-spacing: .04em;
	}
	.item {
		display: flex;
		align-items: center;
		gap: var(--jb-space-sm, 8px);
		width: 100%;
		padding: var(--jb-space-sm, 8px);
		border: 0;
		border-radius: var(--jb-radius, 8px);
		background: transparent;
		color: var(--jb-color-text, #1b1f24);
		font-family: inherit;
		font-size: var(--jb-font-size-body, 14px);
		text-align: left;
		cursor: pointer;
	}
	.item:hover:not(:disabled) { background: rgba(0,0,0,.05); }
	.item:disabled { opacity: .45; cursor: not-allowed; }
	.item.current { background: var(--jb-color-primary, #2563eb); color: var(--jb-color-primary-text, #fff); }
	.label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.badge {
		padding: 1px 6px;
		border-radius: 999px;
		background: rgba(0,0,0,.08);
		font-size: var(--jb-font-size-caption, 12px);
	}
	.item.current .badge { background: rgba(255,255,255,.25); }
	.foot { margin-top: auto; padding-top: var(--jb-space-md, 12px); }
`;

/* jb-page-header */
const PAGE_HEADER_STYLES = `
	:host { display: block; font-family: var(--jb-font, system-ui); }
	.head { display: flex; align-items: flex-start; gap: var(--jb-space-md, 12px); flex-wrap: wrap; }
	.texts { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--jb-space-xs, 4px); }
	.heading { color: var(--jb-color-text, #1b1f24); font-size: var(--jb-font-size-title, 22px); font-weight: 700; }
	.description { color: var(--jb-color-muted, #6b7280); font-size: var(--jb-font-size-caption, 12px); }
	.actions { display: flex; align-items: center; gap: var(--jb-space-sm, 8px); flex-wrap: wrap; }
	::slotted([slot="breadcrumb"]) { display: block; margin-bottom: var(--jb-space-xs, 4px); }
	::slotted([slot="below"]) { display: block; margin-top: var(--jb-space-md, 12px); }
`;

export default registerTheme({

	name: 'original',

	tokens: TOKENS,

	components: {

		'jb-stack': component<JbStack>({
			styles: STACK_STYLES,
			template: (_el, html) => html`<slot></slot>`
		}),

		'jb-text': component<JbText>({
			styles: TEXT_STYLES,
			template: (el, html) => html`${el.text}`
		}),

		'jb-button': component<JbButton>({
			styles: BUTTON_STYLES,
			template: (el, html) => html`
				<button ?disabled=${el.disabled || el.loading} @click=${(e: Event) => el.handleClick(e)}>
					${el.loading ? html`<span class="spinner"></span>` : ''}
					${!el.loading && el.icon ? html`<jb-icon jb-name=${el.icon} jb-size="sm"></jb-icon>` : ''}
					${el.label ? html`<span>${el.label}</span>` : ''}
				</button>
			`
		}),

		'jb-input': component<JbInput>({
			styles: INPUT_STYLES,
			template: (el, html) => html`
				<label>
					${el.label ? html`<span class="label">${el.label}${el.required ? html`<span class="required">*</span>` : ''}</span>` : ''}
					${el.multiline
						? html`<textarea
								.value=${el.value ?? ''}
								placeholder=${el.placeholder ?? ''}
								?disabled=${el.disabled}
								@input=${(e: Event) => el.handleInput(e)}
								@change=${(e: Event) => el.handleChange(e)}></textarea>`
						: html`<input
								type=${el.type || 'text'}
								.value=${el.value ?? ''}
								placeholder=${el.placeholder ?? ''}
								?disabled=${el.disabled}
								@input=${(e: Event) => el.handleInput(e)}
								@change=${(e: Event) => el.handleChange(e)}>`}
					${el.error
						? html`<span class="message error">${el.error}</span>`
						: el.hint ? html`<span class="message">${el.hint}</span>` : ''}
				</label>
			`
		})
,

		'jb-select': component<JbSelect>({
			styles: SELECT_STYLES,
			template: (el, html) => html`
				<label>
					${el.label ? html`<span class="label">${el.label}${el.required ? html`<span class="required">*</span>` : ''}</span>` : ''}
					<select ?disabled=${el.disabled} @change=${(e: Event) => el.handleChange(e)}>
						${el.placeholder || !el.value
							? html`<option value="" ?selected=${!el.value}>${el.placeholder || '選択してください'}</option>`
							: ''}
						${el.options.map((option) => html`
							<option value=${option.value} ?disabled=${option.disabled === true} ?selected=${option.value === el.value}>${option.label}</option>
						`)}
					</select>
					${el.error
						? html`<span class="message error">${el.error}</span>`
						: el.hint ? html`<span class="message">${el.hint}</span>` : ''}
				</label>
			`
		}),

		'jb-checkbox': component<JbCheckbox>({
			styles: CHECKBOX_STYLES,
			template: (el, html) => html`
				<label class="row">
					<input type="checkbox" .checked=${el.checked} ?disabled=${el.disabled} @change=${(e: Event) => el.handleChange(e)}>
					<span class="text">${el.label}</span>
				</label>
				${el.error
					? html`<span class="message error">${el.error}</span>`
					: el.hint ? html`<span class="message">${el.hint}</span>` : ''}
			`
		}),

		'jb-switch': component<JbCheckbox>({
			styles: SWITCH_STYLES,
			template: (el, html) => html`
				<label class="row">
					<input type="checkbox" .checked=${el.checked} ?disabled=${el.disabled} @change=${(e: Event) => el.handleChange(e)}>
					<span class="track"><span class="thumb"></span></span>
					<span class="text">${el.label}</span>
				</label>
				${el.error
					? html`<span class="message error">${el.error}</span>`
					: el.hint ? html`<span class="message">${el.hint}</span>` : ''}
			`
		}),

		'jb-radio': component<JbRadio>({
			styles: RADIO_STYLES,
			template: (el, html) => html`
				${el.label ? html`<span class="label">${el.label}${el.required ? html`<span class="required">*</span>` : ''}</span>` : ''}
				<div class="options">
					${el.options.map((option) => html`
						<label class="option">
							<input
								type="radio"
								name=${el.name}
								value=${option.value}
								?checked=${option.value === el.value}
								?disabled=${el.disabled || option.disabled === true}
								@change=${(e: Event) => el.handleChange(e)}>
							<span>${option.label}</span>
						</label>
					`)}
				</div>
				${el.error
					? html`<span class="message error">${el.error}</span>`
					: el.hint ? html`<span class="message">${el.hint}</span>` : ''}
			`
		}),

		'jb-tabs': component<JbTabs>({
			styles: TABS_STYLES,
			template: (el, html) => html`
				<div class="tabs">
					${el.tabs.map((tab) => html`
						<button class=${tab.value === el.value ? 'tab active' : 'tab'} @click=${() => el.handleSelect(tab.value)}>
							${tab.label}${tab.badge == null ? '' : html`<span class="badge">${tab.badge}</span>`}
						</button>
					`)}
				</div>
			`
		}),

		'jb-table': component<JbTable>({
			styles: TABLE_STYLES,
			template: (el, html) => html`
				<div class="wrap">
					<table>
						<thead>
							<tr>
								${el.columns.map((column) => html`
									<th
										class=${'align-' + (column.align ?? 'start') + (column.sortable === true ? ' sortable' : '')}
										style=${column.width == null ? '' : 'width:' + column.width}
										@click=${() => el.handleSort(column)}>
										${column.label}
										${el.sortKey === column.key ? html`<span class="order">${el.sortOrder === 'asc' ? '▲' : '▼'}</span>` : ''}
									</th>
								`)}
							</tr>
						</thead>
						<tbody>
							${el.loading
								? html`<tr><td class="state" colspan=${el.columns.length}>読み込み中…</td></tr>`
								: el.rows.length === 0
									? html`<tr><td class="state" colspan=${el.columns.length}>${el.empty}</td></tr>`
									: el.rows.map((row, index) => html`
										<tr
											class=${(el.clickable ? 'clickable ' : '') + el.dragState(index)}
											draggable=${el.reorderable ? 'true' : 'false'}
											@click=${() => el.handleRowClick(row, index)}
											@dragstart=${(e: DragEvent) => el.handleDragStart(index, e)}
											@dragover=${(e: DragEvent) => el.handleDragOver(index, e)}
											@drop=${(e: DragEvent) => el.handleDrop(index, e)}
											@dragend=${() => el.handleDragEnd()}>
											${row.cells.map((cell, position) => html`
												<td class=${'align-' + (el.columns[position]?.align ?? 'start')}>${cell}</td>
											`)}
										</tr>
									`)}
						</tbody>
					</table>
				</div>
			`
		}),

		'jb-pagination': component<JbPagination>({
			styles: PAGINATION_STYLES,
			template: (el, html) => html`
				<div class="pager">
					<span class="summary">${el.summary || '全 ' + String(el.total) + ' 件'}</span>
					<div class="buttons">
						<button ?disabled=${el.page <= 1} @click=${() => el.handleSelect(el.page - 1)}>前へ</button>
						${el.pageNumbers().map((number) => number === 0
							? html`<span class="gap">…</span>`
							: html`<button class=${number === el.page ? 'current' : ''} @click=${() => el.handleSelect(number)}>${number}</button>`)}
						<button ?disabled=${el.page >= el.pages} @click=${() => el.handleSelect(el.page + 1)}>次へ</button>
					</div>
				</div>
			`
		}),

		'jb-dialog': component<JbDialog>({
			styles: DIALOG_STYLES,
			template: (el, html) => html`
				${el.open ? html`
					<div class="overlay" @click=${() => el.handleClose()}></div>
					<div class="panel" role="dialog" aria-modal="true">
						<div class="head">
							<span class="title">${el.title}</span>
							${el.closable ? html`<button class="close" @click=${() => el.handleClose()}>×</button>` : ''}
						</div>
						<div class="body"><slot></slot></div>
						<div class="foot"><slot name="footer"></slot></div>
					</div>
				` : ''}
			`
		}),

		'jb-toast': component<JbToast>({
			styles: TOAST_STYLES,
			template: (el, html) => html`
				<div class="toast">
					<span class="message">${el.message}</span>
					<button class="close" @click=${() => el.handleClose()}>×</button>
				</div>
			`
		}),

		'jb-form': component<JbForm>({
			styles: FORM_STYLES,
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
			styles: STAT_STYLES,
			template: (el, html) => html`
				<div class="tile">
					<span class="label">${el.label}</span>
					<span class="value">${el.value}${el.unit ? html`<span class="unit">${el.unit}</span>` : ''}</span>
					${el.delta ? html`<span class="delta">${el.trend === 'up' ? '▲' : el.trend === 'down' ? '▼' : ''} ${el.delta}</span>` : ''}
					${el.hint ? html`<span class="hint">${el.hint}</span>` : ''}
				</div>
			`
		}),

		'jb-chart': component<JbChart>({
			styles: CHART_STYLES,
			template: (el, html, svg) => html`
				<div class="frame">
					${el.title ? html`<div class="title">${el.title}</div>` : ''}
					${el.points.length === 0
						? html`<div class="state">${el.empty}</div>`
						: html`
							<svg viewBox="0 0 100 100" preserveAspectRatio="none" style=${'height:' + String(el.height) + 'px'}>
								${el.type === 'bar'
									? el.points.map((point, index) => {
										const width = 100 / el.points.length;
										const height = el.ratio(point.value) * 96;
										return svg`<rect
											class="bar"
											x=${String(index * width + width * 0.15)}
											y=${String(100 - height)}
											width=${String(width * 0.7)}
											height=${String(height)}></rect>`;
									})
									: svg`
										<polyline class="line" points=${el.polyline(100, 96)}></polyline>
										${el.points.map((point, index) => svg`<circle
											class="dot"
											cx=${String(el.points.length === 1 ? 50 : (index / (el.points.length - 1)) * 100)}
											cy=${String(96 - el.ratio(point.value) * 96)}
											r="1.2"></circle>`)}
									`}
								${svg`<line class="axis" x1="0" y1="100" x2="100" y2="100"></line>`}
							</svg>
							<div class="labels">
								${el.points.map((point) => html`<span>${point.label}</span>`)}
							</div>
						`}
				</div>
			`
		}),

		'jb-file': component<JbFile>({
			styles: FILE_STYLES,
			template: (el, html) => html`
				${el.label ? html`<span class="label">${el.label}</span>` : ''}
				<label
					class="drop"
					@dragover=${(e: DragEvent) => el.handleDragging(e, true)}
					@dragleave=${(e: DragEvent) => el.handleDragging(e, false)}
					@drop=${(e: DragEvent) => el.handleDrop(e)}>
					<span>ここに放り込むか、押して選んでください</span>
					${el.accept ? html`<span class="message">${el.accept}</span>` : ''}
					<input
						type="file"
						accept=${el.accept}
						?multiple=${el.multiple}
						?disabled=${el.disabled}
						@change=${(e: Event) => el.handleSelect(e)}>
				</label>
				${el.files.length === 0 ? '' : html`
					<div class="files">
						${el.files.map((file) => html`
							<span class="file">
								<span>${file.name}（${Math.ceil(file.size / 1024)}KB）</span>
								<button class="remove" @click=${() => el.handleClear()}>取消</button>
							</span>
						`)}
					</div>
				`}
				${el.error
					? html`<span class="message error">${el.error}</span>`
					: el.hint ? html`<span class="message">${el.hint}</span>` : ''}
			`
		}),

		'jb-daterange': component<JbDateRange>({
			styles: DATERANGE_STYLES,
			template: (el, html) => html`
				${el.label ? html`<span class="label">${el.label}${el.required ? html`<span class="required">*</span>` : ''}</span>` : ''}
				<div class="range">
					<input type="date" .value=${el.from} ?disabled=${el.disabled} @change=${(e: Event) => el.handleFrom(e)}>
					<span class="tilde">〜</span>
					<input type="date" .value=${el.to} ?disabled=${el.disabled} @change=${(e: Event) => el.handleTo(e)}>
				</div>
				${el.error
					? html`<span class="message error">${el.error}</span>`
					: el.hint ? html`<span class="message">${el.hint}</span>` : ''}
			`
		}),

		'jb-breadcrumb': component<JbBreadcrumb>({
			styles: BREADCRUMB_STYLES,
			template: (el, html) => html`
				<nav class="crumbs">
					${el.items.map((item, index) => html`
						${index === 0 ? '' : html`<span class="sep">${el.separator}</span>`}
						${item.path == null
							? html`<span class="current">${item.label}</span>`
							: html`<button @click=${() => el.handleSelect(item)}>${item.label}</button>`}
					`)}
				</nav>
			`
		}),

		'jb-accordion': component<JbAccordion>({
			styles: ACCORDION_STYLES,
			template: (el, html) => html`
				<div class="sections">
					${el.sections.map((section) => html`
						<div class="section">
							<button class="head" @click=${() => el.handleToggle(section.value)}>
								<span>${section.label}</span>
								<span class=${el.isOpen(section.value) ? 'mark open' : 'mark'}>▶</span>
							</button>
							${el.isOpen(section.value)
								? html`<div class="body"><slot name=${'section-' + section.value}></slot></div>`
								: ''}
						</div>
					`)}
				</div>
			`
		}),

		'jb-empty': component<JbEmpty>({
			styles: EMPTY_STYLES,
			template: (el, html) => html`
				<div class="empty">
					${el.icon ? html`<span class="icon">${el.icon}</span>` : ''}
					<span class="heading">${el.heading}</span>
					${el.description ? html`<span class="description">${el.description}</span>` : ''}
					<span class="action"><slot></slot></span>
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
					class="trigger"
					type="button"
					aria-haspopup="menu"
					aria-expanded=${el.open ? 'true' : 'false'}
					?disabled=${el.disabled}
					@click=${(e: Event) => el.handleToggle(e)}>
					${el.icon ? html`<jb-icon jb-name=${el.icon} jb-size="sm"></jb-icon>` : ''}
					${el.label ? html`<span>${el.label}</span>` : ''}
				</button>
				${el.open ? html`
					<div class="list" role="menu" style=${menuPosition(el)}>
						${el.items.map((item) => item.divider === true
							? html`<div class="divider"></div>`
							: html`
								<button
									class=${item.danger === true ? 'item danger' : 'item'}
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
				${el.heading ? html`<div class="heading">${el.heading}</div>` : ''}
				<slot></slot>
				<div class="items">
					${el.items.map((item, index) => html`
						${el.startsGroup(index) ? html`<div class="group">${item.group}</div>` : ''}
						<button
							class=${el.isCurrent(item) ? 'item current' : 'item'}
							type="button"
							?disabled=${item.disabled === true}
							@click=${() => el.handleSelect(item)}>
							${item.icon ? html`<jb-icon jb-name=${item.icon} jb-size="sm"></jb-icon>` : ''}
							<span class="label">${item.label}</span>
							${item.badge == null ? '' : html`<span class="badge">${item.badge}</span>`}
						</button>
					`)}
				</div>
				<div class="foot"><slot name="footer"></slot></div>
			`
		}),

		'jb-page-header': component<JbPageHeader>({
			styles: PAGE_HEADER_STYLES,
			template: (el, html) => html`
				<slot name="breadcrumb"></slot>
				<div class="head">
					<div class="texts">
						<span class="heading">${el.heading}</span>
						${el.description ? html`<span class="description">${el.description}</span>` : ''}
					</div>
					<div class="actions"><slot></slot></div>
				</div>
				<slot name="below"></slot>
			`
		})

	}

});
