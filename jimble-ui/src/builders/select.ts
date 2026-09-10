import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, Path, PathValue, Resolvable, SelectOption } from '../core/types.js';

/**
 * 選択ビルダー
 *
 * <pre>
 * UI.select('department')
 *     .label('部署')
 *     .options([{ value: 'sales', label: '営業部' }])
 *     .bind('form.department');
 * </pre>
 */
export class SelectBuilder<S extends object = AppState> extends Builder<S> {

	private name: string;
	private path: string | null = null;
	private fixedValue: Resolvable<string, Context<S>> | undefined = undefined;
	private optionList: Resolvable<SelectOption[], Context<S>> = [];
	private labelText: Resolvable<string, Context<S>> | undefined;
	private placeholderText: Resolvable<string, Context<S>> | undefined;
	private hintText: Resolvable<string, Context<S>> | undefined;
	private errorText: Resolvable<string | undefined, Context<S>> | undefined;
	private requiredValue: Resolvable<boolean, Context<S>> | undefined;
	private disabledValue: Resolvable<boolean, Context<S>> | undefined;
	private changeHandler: ((value: string, ctx: Context<S>) => unknown) | null = null;

	constructor (name: string) {

		super();
		this.name = name;

	}

	/**
	 * 選択肢
	 *
	 * @param options 選択肢
	 * @return ビルダー
	 */
	options (options: Resolvable<SelectOption[], Context<S>>): this {

		this.optionList = options;
		return this;

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
	 * 未選択時の文言
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	placeholder (text: Resolvable<string, Context<S>>): this {

		this.placeholderText = text;
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
	 * 必須
	 *
	 * @param value 必須か
	 * @return ビルダー
	 */
	required (value: Resolvable<boolean, Context<S>> = true): this {

		this.requiredValue = value;
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
	 * 選ばれている値を直接指定する（状態に結び付けない場合）
	 *
	 * @param value 値
	 * @return ビルダー
	 */
	value (value: Resolvable<string, Context<S>>): this {

		this.fixedValue = value;
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

		const current = this.fixedValue !== undefined
			? resolve(this.fixedValue, ctx)
			: (this.path == null ? '' : ctx.store.get(this.path as Path<S> & string));

		return html`<jb-select
			.name=${this.name}
			.label=${resolve(this.labelText, ctx) ?? ''}
			.placeholder=${resolve(this.placeholderText, ctx) ?? ''}
			.hint=${resolve(this.hintText, ctx) ?? ''}
			.error=${resolve(this.errorText, ctx) ?? ''}
			.options=${resolve(this.optionList, ctx) ?? []}
			.value=${current == null ? '' : String(current)}
			.required=${resolve(this.requiredValue, ctx) === true}
			.disabled=${resolve(this.disabledValue, ctx) === true}
			@jb-change=${(event: CustomEvent<{ value: string }>) => this.accept(event.detail.value, ctx)}
		></jb-select>`;

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
