import { Builder, resolve, renderNode } from '../core/builder.js';
import type { Node, Renderable } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, Resolvable } from '../core/types.js';

/**
 * 繰り返しビルダー
 *
 * <pre>
 * UI.each((ctx) => ctx.get('list'), (staff) => UI.label(staff.name));
 * </pre>
 */
export class EachBuilder<T, S extends object = AppState> extends Builder<S> {

	private items: Resolvable<readonly T[], Context<S>>;
	private itemView: (item: T, index: number, ctx: Context<S>) => Node<S>;
	private emptyNode: Node<S> | null = null;

	constructor (
		items: Resolvable<readonly T[], Context<S>>,
		itemView: (item: T, index: number, ctx: Context<S>) => Node<S>
	) {

		super();
		this.items = items;
		this.itemView = itemView;

	}

	/**
	 * 0 件のときの表示
	 *
	 * @param node ノード
	 * @return ビルダー
	 */
	empty (node: Node<S>): this {

		this.emptyNode = node;
		return this;

	}

	protected override template (ctx: Context<S>): Renderable | Renderable[] {

		const items = resolve(this.items, ctx) ?? [];

		if (items.length === 0 && this.emptyNode != null) {
			return renderNode(this.emptyNode, ctx);
		}

		return items.flatMap((item, index) => renderNode(this.itemView(item, index, ctx), ctx));

	}

}
