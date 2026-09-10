import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, renderNode, resolve } from '../core/builder.js';
import type { Node } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, ReorderPosition, Resolvable, SortOrder, TableColumn, TableRow } from '../core/types.js';
import { reorderIndex } from '../core/reorder.js';
import type { ReorderDetail } from '../core/reorder.js';

/** 表の列の定義 */
export interface ColumnDefinition<T, S extends object> {
	/** 列のキー（並べ替えに使う） */
	key: string;
	/** 見出し */
	label: string;
	/** セルの中身。省略すると row[key] をそのまま出す */
	cell?: (row: T, ctx: Context<S>) => Node<S>;
	/** 並べ替えできるか */
	sortable?: boolean;
	/** 揃え */
	align?: 'start' | 'center' | 'end';
	/** 幅（CSS の値） */
	width?: string;
}

/**
 * 表ビルダー
 *
 * <pre>
 * UI.table((ctx) => ctx.get('list'))
 *     .rowKey((staff) => String(staff.id))
 *     .column({ key: 'name', label: '氏名', sortable: true })
 *     .column({ key: 'mail', label: 'メール' })
 *     .column({ key: 'actions', label: '', cell: (staff) => UI.button('詳細').quiet().go('/staff/' + staff.id) })
 *     .sort((ctx) => ctx.get('sortKey'), (ctx) => ctx.get('sortOrder'))
 *     .onSort((key, order, ctx) => ctx.store.patch({ sortKey: key, sortOrder: order }));
 * </pre>
 */
export class TableBuilder<T, S extends object = AppState> extends Builder<S> {

	private items: Resolvable<readonly T[], Context<S>>;
	private definitions: ColumnDefinition<T, S>[] = [];
	private keyOf: ((row: T, index: number) => string) | null = null;
	private sortKeyValue: Resolvable<string, Context<S>> | undefined;
	private sortOrderValue: Resolvable<SortOrder, Context<S>> | undefined;
	private emptyText: Resolvable<string, Context<S>> | undefined;
	private loadingValue: Resolvable<boolean, Context<S>> | undefined;
	private sortHandler: ((key: string, order: SortOrder, ctx: Context<S>) => unknown) | null = null;
	private rowClickHandler: ((row: T, ctx: Context<S>) => unknown) | null = null;
	private reorderValue: Resolvable<boolean, Context<S>> | undefined;
	private reorderHandler: ((detail: ReorderDetail<T>, ctx: Context<S>) => unknown) | null = null;

	constructor (items: Resolvable<readonly T[], Context<S>>) {

		super();
		this.items = items;

	}

	/**
	 * 列を足す
	 *
	 * @param definition 列の定義
	 * @return ビルダー
	 */
	column (definition: ColumnDefinition<T, S>): this {

		this.definitions.push(definition);
		return this;

	}

	/**
	 * 行の識別子の作り方
	 *
	 * @param keyOf 識別子を作る関数
	 * @return ビルダー
	 */
	rowKey (keyOf: (row: T, index: number) => string): this {

		this.keyOf = keyOf;
		return this;

	}

	/**
	 * 並べ替えの状態
	 *
	 * @param key 並べ替えの列
	 * @param order 並び順
	 * @return ビルダー
	 */
	sort (key: Resolvable<string, Context<S>>, order: Resolvable<SortOrder, Context<S>>): this {

		this.sortKeyValue = key;
		this.sortOrderValue = order;
		return this;

	}

	/**
	 * 0 件のときの文言
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	empty (text: Resolvable<string, Context<S>>): this {

		this.emptyText = text;
		return this;

	}

	/**
	 * 読み込み中
	 *
	 * @param value 読み込み中か
	 * @return ビルダー
	 */
	loading (value: Resolvable<boolean, Context<S>> = true): this {

		this.loadingValue = value;
		return this;

	}

	/**
	 * 並べ替えが押されたときの処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onSort (handler: (key: string, order: SortOrder, ctx: Context<S>) => unknown): this {

		this.sortHandler = handler;
		return this;

	}

	/**
	 * 行が押されたときの処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onRowClick (handler: (row: T, ctx: Context<S>) => unknown): this {

		this.rowClickHandler = handler;
		return this;

	}

	/**
	 * つかんで並べ替えられるようにする
	 *
	 * <p>{@link TableBuilder#onReorder} と一緒に使う。</p>
	 *
	 * @param value 並べ替えられるか
	 * @return ビルダー
	 */
	reorderable (value: Resolvable<boolean, Context<S>> = true): this {

		this.reorderValue = value;
		return this;

	}

	/**
	 * 並べ替えられたときの処理
	 *
	 * <p>
	 * 受け取るのは「どれを」「どこへ」動かしたか。
	 * 画面の並びを先に直すなら {@code reorder(list, detail)} が使える。
	 * </p>
	 *
	 * <pre>
	 * .reorderable()
	 * .onReorder(async (detail, ctx) =&gt; {
	 *     ctx.set('list', reorder(ctx.get('list'), detail));
	 *     await moveTo(detail.item.id, detail.to);
	 * })
	 * </pre>
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onReorder (handler: (detail: ReorderDetail<T>, ctx: Context<S>) => unknown): this {

		this.reorderHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		const items = resolve(this.items, ctx) ?? [];

		const columns: TableColumn[] = this.definitions.map((definition) => ({
			key: definition.key,
			label: definition.label,
			...(definition.sortable == null ? {} : { sortable: definition.sortable }),
			...(definition.align == null ? {} : { align: definition.align }),
			...(definition.width == null ? {} : { width: definition.width })
		}));

		const rows: TableRow[] = items.map((item, index) => ({
			key: this.keyOf == null ? String(index) : this.keyOf(item, index),
			cells: this.definitions.map((definition) => definition.cell == null
				? String((item as Record<string, unknown>)[definition.key] ?? '')
				: renderNode(definition.cell(item, ctx), ctx))
		}));

		return html`<jb-table
			.columns=${columns}
			.rows=${rows}
			.sortKey=${resolve(this.sortKeyValue, ctx) ?? ''}
			.sortOrder=${resolve(this.sortOrderValue, ctx) ?? 'asc'}
			.empty=${resolve(this.emptyText, ctx) ?? '該当するデータがありません'}
			.loading=${resolve(this.loadingValue, ctx) === true}
			.clickable=${this.rowClickHandler != null}
			.reorderable=${this.reorderHandler != null && resolve(this.reorderValue, ctx) !== false}
			@jb-sort=${(event: CustomEvent<{ key: string; order: SortOrder }>) =>
				this.sortHandler?.(event.detail.key, event.detail.order, ctx)}
			@jb-row-click=${(event: CustomEvent<{ index: number }>) => {
				const row = items[event.detail.index];
				if (row !== undefined) {
					this.rowClickHandler?.(row, ctx);
				}
			}}
			@jb-reorder=${(event: CustomEvent<{ fromIndex: number; toIndex: number; position: ReorderPosition }>) => {
				const { fromIndex, toIndex, position } = event.detail;
				const item = items[fromIndex];
				const reference = items[toIndex];
				if (item === undefined || reference === undefined) {
					return;
				}
				this.reorderHandler?.({
					item,
					from: fromIndex,
					to: reorderIndex(fromIndex, toIndex, position),
					reference,
					position
				}, ctx);
			}}
		></jb-table>`;

	}

}
