import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Node } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, Resolvable, Size } from '../core/types.js';

/**
 * カード並べビルダー
 *
 * <pre>
 * UI.grid(...cards).columns(3).gap('lg');
 * UI.grid(...cards).min('240px');   // 幅に応じて自動で折り返す
 * </pre>
 */
export class GridBuilder<S extends object = AppState> extends Builder<S> {

	private columnCount: Resolvable<number, Context<S>> | undefined;
	private gapSize: Resolvable<Size, Context<S>> | undefined;
	private minWidth: Resolvable<string, Context<S>> | undefined;

	constructor (children: Node<S>[] = []) {

		super();
		this.add(...children);

	}

	/**
	 * 列数（1〜6）
	 *
	 * @param count 列数
	 * @return ビルダー
	 */
	columns (count: Resolvable<number, Context<S>>): this {

		this.columnCount = count;
		return this;

	}

	/**
	 * 間隔
	 *
	 * @param size none | xs | sm | md | lg | xl
	 * @return ビルダー
	 */
	gap (size: Resolvable<Size, Context<S>>): this {

		this.gapSize = size;
		return this;

	}

	/**
	 * 1 枚あたりの最小幅（指定すると自動で折り返す）
	 *
	 * @param width 幅（例: '240px'）
	 * @return ビルダー
	 */
	min (width: Resolvable<string, Context<S>>): this {

		this.minWidth = width;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-grid
			.columns=${resolve(this.columnCount, ctx) ?? 3}
			.gap=${resolve(this.gapSize, ctx) ?? 'md'}
			.min=${resolve(this.minWidth, ctx) ?? ''}
		>${this.renderChildren(ctx)}</jb-grid>`;

	}

}
