import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, Path, PathValue, Resolvable } from '../core/types.js';

/**
 * チェックボックス / スイッチ ビルダー
 *
 * <pre>
 * UI.checkbox('active').label('在職中').bind('form.active');
 * UI.switch('notify').label('通知を受け取る').bind('form.notify');
 * </pre>
 */
export class CheckboxBuilder<S extends object = AppState> extends Builder<S> {

	private name: string;
	private tag: 'jb-checkbox' | 'jb-switch';
	private path: string | null = null;
	private checkedValue: Resolvable<boolean, Context<S>> | undefined;
	private labelText: Resolvable<string, Context<S>> | undefined;
	private hintText: Resolvable<string, Context<S>> | undefined;
	private errorText: Resolvable<string | undefined, Context<S>> | undefined;
	private disabledValue: Resolvable<boolean, Context<S>> | undefined;
	private changeHandler: ((checked: boolean, ctx: Context<S>) => unknown) | null = null;

	constructor (name: string, tag: 'jb-checkbox' | 'jb-switch' = 'jb-checkbox') {

		super();
		this.name = name;
		this.tag = tag;

	}

	/**
	 * ラベル
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	label (text: Resolvable<string, Context<S>>): this {

		this.labelText = text;
		return this;

	}

	/**
	 * 補足
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	hint (text: Resolvable<string, Context<S>>): this {

		this.hintText = text;
		return this;

	}

	/**
	 * エラー文言
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	error (text: Resolvable<string | undefined, Context<S>>): this {

		this.errorText = text;
		return this;

	}

	/**
	 * 使用不可
	 *
	 * @param value 使用不可か
	 * @return ビルダー
	 */
	disabled (value: Resolvable<boolean, Context<S>> = true): this {

		this.disabledValue = value;
		return this;

	}

	/**
	 * 入っているかを直接指定する
	 *
	 * @param value 入っているか
	 * @return ビルダー
	 */
	checked (value: Resolvable<boolean, Context<S>>): this {

		this.checkedValue = value;
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
	 * 切り替えられたときの処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onChange (handler: (checked: boolean, ctx: Context<S>) => unknown): this {

		this.changeHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		const current = this.checkedValue !== undefined
			? resolve(this.checkedValue, ctx) === true
			: (this.path != null && ctx.store.get(this.path as Path<S> & string) === true);

		const attributes = {
			name: this.name,
			label: resolve(this.labelText, ctx) ?? '',
			hint: resolve(this.hintText, ctx) ?? '',
			error: resolve(this.errorText, ctx) ?? '',
			checked: current,
			disabled: resolve(this.disabledValue, ctx) === true
		};

		const accept = (event: CustomEvent<{ checked: boolean }>) => this.accept(event.detail.checked, ctx);

		return this.tag === 'jb-switch'
			? html`<jb-switch
					.name=${attributes.name}
					.label=${attributes.label}
					.hint=${attributes.hint}
					.error=${attributes.error}
					.checked=${attributes.checked}
					.disabled=${attributes.disabled}
					@jb-change=${accept}
				></jb-switch>`
			: html`<jb-checkbox
					.name=${attributes.name}
					.label=${attributes.label}
					.hint=${attributes.hint}
					.error=${attributes.error}
					.checked=${attributes.checked}
					.disabled=${attributes.disabled}
					@jb-change=${accept}
				></jb-checkbox>`;

	}

	/**
	 * 値を受け取る
	 *
	 * @param checked 入っているか
	 * @param ctx コンテキスト
	 */
	protected accept (checked: boolean, ctx: Context<S>): void {

		if (this.path != null) {
			ctx.store.set(this.path as Path<S> & string, checked as PathValue<S, Path<S> & string>);
		}
		this.changeHandler?.(checked, ctx);

	}

}
