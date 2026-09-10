import type { ReorderPosition } from './types.js';

/**
 * 並べ替えの内容
 *
 * <p>「どれを」「どこの前／後ろに」落としたか。</p>
 */
export interface ReorderDetail<T> {
	/** 動かした行 */
	item: T;
	/** 動かす前の位置 */
	from: number;
	/** 動かした後の位置 */
	to: number;
	/** 落とした先の行 */
	reference: T;
	/** 落とした先の行の前か後ろか */
	position: ReorderPosition;
}

/**
 * 並べ替えを配列に適用する
 *
 * <p>
 * 元の配列は書き換えず、並べ替えた新しい配列を返す。
 * サーバへ順番を送る前の「画面だけ先に動かす」用。
 * </p>
 *
 * <pre>
 * UI.table((ctx) =&gt; ctx.get('list'))
 *     .reorderable()
 *     .onReorder(async (detail, ctx) =&gt; {
 *         ctx.set('list', reorder(ctx.get('list'), detail));
 *         await moveTo(detail.item.id, detail.to);
 *     });
 * </pre>
 *
 * @param items 元の並び
 * @param detail 並べ替えの内容
 * @return 並べ替えた新しい配列
 */
export function reorder<T> (items: readonly T[], detail: ReorderDetail<T>): T[] {

	const result = [...items];
	const [moved] = result.splice(detail.from, 1);
	if (moved === undefined) {
		return result;
	}
	result.splice(detail.to, 0, moved);
	return result;

}

/**
 * 落とした先から、動かした後の位置を求める
 *
 * @param from 動かす前の位置
 * @param to 落とした先の行の位置
 * @param position 前か後ろか
 * @return 動かした後の位置
 */
export function reorderIndex (from: number, to: number, position: ReorderPosition): number {

	const inserted = position === 'before' ? to : to + 1;
	return from < inserted ? inserted - 1 : inserted;

}
