import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, BreadcrumbItem, Resolvable } from '../core/types.js';

/**
 * パンくずビルダー
 *
 * <pre>
 * UI.breadcrumb([
 *     { label: 'ホーム', path: '/' },
 *     { label: '社員一覧', path: '/staff' },
 *     { label: '山田 太郎' }
 * ]);
 * </pre>
 *
 * <p>{@code path} のある項目を押すとその画面へ移る。</p>
 */
export class BreadcrumbBuilder<S extends object = AppState> extends Builder<S> {

	private items: Resolvable<BreadcrumbItem[], Context<S>>;
	private separatorText: Resolvable<string, Context<S>> | undefined;
	private selectHandler: ((path: string, ctx: Context<S>) => unknown) | null = null;

	constructor (items: Resolvable<BreadcrumbItem[], Context<S>>) {

		super();
		this.items = items;

	}

	/**
	 * 区切り文字
	 *
	 * @param text 区切り
	 * @return ビルダー
	 */
	separator (text: Resolvable<string, Context<S>>): this {

		this.separatorText = text;
		return this;

	}

	/**
	 * 押されたときの処理（省略すると path へ移る）
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onSelect (handler: (path: string, ctx: Context<S>) => unknown): this {

		this.selectHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-breadcrumb
			.items=${resolve(this.items, ctx) ?? []}
			.separator=${resolve(this.separatorText, ctx) ?? '/'}
			@jb-select=${(event: CustomEvent<{ path: string }>) => {
				if (this.selectHandler == null) {
					ctx.go(event.detail.path);
				} else {
					this.selectHandler(event.detail.path, ctx);
				}
			}}
		></jb-breadcrumb>`;

	}

}
