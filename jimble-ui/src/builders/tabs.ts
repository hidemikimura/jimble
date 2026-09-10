import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, Path, PathValue, Resolvable, TabItem } from '../core/types.js';

/**
 * タブビルダー
 *
 * <pre>
 * UI.tabs([{ value: 'all', label: 'すべて' }, { value: 'active', label: '在職' }])
 *     .bind('filter');
 * </pre>
 */
export class TabsBuilder<S extends object = AppState> extends Builder<S> {

	private tabList: Resolvable<TabItem[], Context<S>>;
	private path: string | null = null;
	private currentValue: Resolvable<string, Context<S>> | undefined;
	private changeHandler: ((value: string, ctx: Context<S>) => unknown) | null = null;

	constructor (tabs: Resolvable<TabItem[], Context<S>>) {

		super();
		this.tabList = tabs;

	}

	/**
	 * 選ばれている値を直接指定する
	 *
	 * @param value 値
	 * @return ビルダー
	 */
	value (value: Resolvable<string, Context<S>>): this {

		this.currentValue = value;
		return this;

	}

	/**
	 * 状態と双方向に結び付ける
	 *
	 * @param path 状態のパス
	 * @return ビルダー
	 */
	bind<P extends Path<S> & string> (path: P): this {

		this.path = path;
		return this;

	}

	/**
	 * 選ばれたときの処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onChange (handler: (value: string, ctx: Context<S>) => unknown): this {

		this.changeHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		const current = this.currentValue !== undefined
			? resolve(this.currentValue, ctx)
			: (this.path == null ? '' : ctx.store.get(this.path as Path<S> & string));

		return html`<jb-tabs
			.tabs=${resolve(this.tabList, ctx) ?? []}
			.value=${current == null ? '' : String(current)}
			@jb-change=${(event: CustomEvent<{ value: string }>) => this.accept(event.detail.value, ctx)}
		></jb-tabs>`;

	}

	/**
	 * 選ばれた値を受け取る
	 *
	 * @param value 値
	 * @param ctx コンテキスト
	 */
	protected accept (value: string, ctx: Context<S>): void {

		if (this.path != null) {
			ctx.store.set(this.path as Path<S> & string, value as PathValue<S, Path<S> & string>);
		}
		this.changeHandler?.(value, ctx);

	}

}
