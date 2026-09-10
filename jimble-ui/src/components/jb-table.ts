import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { ReorderPosition, SortOrder, TableColumn, TableRow } from '../core/types.js';

/**
 * 表
 *
 * <p>
 * 列と行を受け取るだけ。セルの中身は画面側（ビルダー）が作ったものをそのまま置く。
 * 並べ替えの実際の処理は画面側が行い、ここは押されたことを知らせるだけ。
 * </p>
 */
export class JbTable extends JbElement {

	static override tag = 'jb-table';

	static override properties: PropertyDeclarations = {
		columns: { attribute: false },
		rows: { attribute: false },
		sortKey: { type: String },
		sortOrder: { type: String },
		empty: { type: String },
		loading: { type: Boolean, reflect: true, attribute: 'jb-loading' },
		clickable: { type: Boolean, reflect: true, attribute: 'jb-clickable' },
		reorderable: { type: Boolean, reflect: true, attribute: 'jb-reorderable' },
		dragIndex: { state: true },
		overIndex: { state: true },
		dropPosition: { state: true }
	};

	/** 列 */
	columns: TableColumn[] = [];

	/** 行 */
	rows: TableRow[] = [];

	/** 並べ替えの列 */
	sortKey = '';

	/** 並び順 */
	sortOrder: SortOrder = 'asc';

	/** 0 件のときの文言 */
	empty = '該当するデータがありません';

	/** 読み込み中 */
	loading = false;

	/** 行を押せるか */
	clickable = false;

	/** つかんで並べ替えられるか */
	reorderable = false;

	/** つかんでいる行の位置（つかんでいなければ -1） */
	dragIndex = -1;

	/** 今どの行の上にいるか（無ければ -1） */
	overIndex = -1;

	/** その行の前と後ろのどちらに落ちるか */
	dropPosition: ReorderPosition = 'before';

	/**
	 * 見出しが押されたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param column 列
	 */
	handleSort (column: TableColumn): void {

		if (column.sortable !== true) {
			return;
		}

		const order: SortOrder = this.sortKey === column.key && this.sortOrder === 'asc' ? 'desc' : 'asc';
		this.sortKey = column.key;
		this.sortOrder = order;

		this.dispatchEvent(new CustomEvent('jb-sort', {
			detail: { key: column.key, order },
			bubbles: true,
			composed: true
		}));

	}

	/**
	 * 行が押されたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param row 行
	 * @param index 位置
	 */
	handleRowClick (row: TableRow, index: number): void {

		if (!this.clickable) {
			return;
		}
		this.dispatchEvent(new CustomEvent('jb-row-click', {
			detail: { key: row.key, index },
			bubbles: true,
			composed: true
		}));

	}


	/**
	 * つかみ始めたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param index 位置
	 * @param event イベント
	 */
	handleDragStart (index: number, event: DragEvent): void {

		if (!this.reorderable) {
			return;
		}
		this.dragIndex = index;
		if (event.dataTransfer != null) {
			event.dataTransfer.effectAllowed = 'move';
			/* Firefox はデータを入れないと drag が始まらない */
			event.dataTransfer.setData('text/plain', String(index));
		}

	}

	/**
	 * 行の上を通ったときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param index 位置
	 * @param event イベント
	 */
	handleDragOver (index: number, event: DragEvent): void {

		if (!this.reorderable || this.dragIndex < 0) {
			return;
		}

		/* 既定の動作を止めないと drop が起きない */
		event.preventDefault();
		if (event.dataTransfer != null) {
			event.dataTransfer.dropEffect = 'move';
		}

		const target = event.currentTarget as HTMLElement | null;
		const rect = target?.getBoundingClientRect();
		const position: ReorderPosition = rect == null || event.clientY < rect.top + rect.height / 2
			? 'before'
			: 'after';

		if (this.overIndex !== index || this.dropPosition !== position) {
			this.overIndex = index;
			this.dropPosition = position;
		}

	}

	/**
	 * 落としたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param index 位置
	 * @param event イベント
	 */
	handleDrop (index: number, event: DragEvent): void {

		if (!this.reorderable || this.dragIndex < 0) {
			return;
		}
		event.preventDefault();

		const from = this.dragIndex;
		const position = this.dropPosition;
		this.handleDragEnd();

		if (from === index) {
			return;
		}

		this.dispatchEvent(new CustomEvent('jb-reorder', {
			detail: {
				fromIndex: from,
				toIndex: index,
				fromKey: this.rows[from]?.key ?? '',
				toKey: this.rows[index]?.key ?? '',
				position
			},
			bubbles: true,
			composed: true
		}));

	}

	/** つかむのをやめたときの処理（テーマのテンプレートから呼ぶ） */
	handleDragEnd (): void {

		this.dragIndex = -1;
		this.overIndex = -1;
		this.dropPosition = 'before';

	}

	/**
	 * その行が今どういう状態か（テーマが class を決めるのに使う）
	 *
	 * @param index 位置
	 * @return 'dragging' / 'drop-before' / 'drop-after' / ''
	 */
	dragState (index: number): string {

		if (!this.reorderable || this.dragIndex < 0) {
			return '';
		}
		if (this.dragIndex === index) {
			return 'dragging';
		}
		if (this.overIndex === index) {
			return 'drop-' + this.dropPosition;
		}
		return '';

	}

}

customElements.define('jb-table', JbTable);
