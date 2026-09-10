import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, Resolvable } from '../core/types.js';

/**
 * ページ送りビルダー
 *
 * <pre>
 * UI.pagination()
 *     .page((ctx) => ctx.get('page'))
 *     .pages((ctx) => ctx.get('pages'))
 *     .total((ctx) => ctx.get('total'))
 *     .onChange((page, ctx) => { ctx.set('page', page); reload(ctx); });
 * </pre>
 */
export class PaginationBuilder<S extends object = AppState> extends Builder<S> {

	private pageValue: Resolvable<number, Context<S>> = 1;
	private pagesValue: Resolvable<number, Context<S>> = 1;
	private totalValue: Resolvable<number, Context<S>> | undefined;
	private summaryText: Resolvable<string, Context<S>> | undefined;
	private changeHandler: ((page: number, ctx: Context<S>) => unknown) | null = null;

	/**
	 * 現在のページ
	 *
	 * @param value ページ
	 * @return ビルダー
	 */
	page (value: Resolvable<number, Context<S>>): this {

		this.pageValue = value;
		return this;

	}

	/**
	 * 総ページ数
	 *
	 * @param value ページ数
	 * @return ビルダー
	 */
	pages (value: Resolvable<number, Context<S>>): this {

		this.pagesValue = value;
		return this;

	}

	/**
	 * 総件数
	 *
	 * @param value 件数
	 * @return ビルダー
	 */
	total (value: Resolvable<number, Context<S>>): this {

		this.totalValue = value;
		return this;

	}

	/**
	 * 件数の表示文言
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	summary (text: Resolvable<string, Context<S>>): this {

		this.summaryText = text;
		return this;

	}

	/**
	 * ページが選ばれたときの処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onChange (handler: (page: number, ctx: Context<S>) => unknown): this {

		this.changeHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-pagination
			.page=${resolve(this.pageValue, ctx) ?? 1}
			.pages=${resolve(this.pagesValue, ctx) ?? 1}
			.total=${resolve(this.totalValue, ctx) ?? 0}
			.summary=${resolve(this.summaryText, ctx) ?? ''}
			@jb-change=${(event: CustomEvent<{ page: number }>) => this.changeHandler?.(event.detail.page, ctx)}
		></jb-pagination>`;

	}

}
