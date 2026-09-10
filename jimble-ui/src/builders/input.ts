import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { InputEventDetail } from '../components/jb-input.js';
import type { AppState, InputType, Path, PathValue, Resolvable } from '../core/types.js';

/**
 * 入力ビルダー
 *
 * <pre>
 * UI.text('name')
 *     .label('氏名')
 *     .required()
 *     .bind('form.name')
 *     .error((ctx) => ctx.get('errors.name'));
 * </pre>
 */
export class InputBuilder<S extends object = AppState> extends Builder<S> {

	private name: string;
	private type: InputType;
	private path: string | null = null;
	private fixedValue: Resolvable<unknown, Context<S>> | undefined = undefined;

	private labelText: Resolvable<string, Context<S>> | undefined;
	private placeholderText: Resolvable<string, Context<S>> | undefined;
	private hintText: Resolvable<string, Context<S>> | undefined;
	private errorText: Resolvable<string | undefined, Context<S>> | undefined;
	private requiredValue: Resolvable<boolean, Context<S>> | undefined;
	private disabledValue: Resolvable<boolean, Context<S>> | undefined;
	private multilineValue: Resolvable<boolean, Context<S>> | undefined;

	private inputHandler: ((value: unknown, ctx: Context<S>) => unknown) | null = null;
	private changeHandler: ((value: unknown, ctx: Context<S>) => unknown) | null = null;

	constructor (name: string, type: InputType = 'text') {

		super();
		this.name = name;
		this.type = type;

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
	 * 入力例
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
	 * 複数行にする
	 *
	 * @param value 複数行か
	 * @return ビルダー
	 */
	multiline (value: Resolvable<boolean, Context<S>> = true): this {

		this.multilineValue = value;
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
	 * 値を直接指定する
	 *
	 * @param value 値
	 * @return ビルダー
	 */
	value (value: Resolvable<unknown, Context<S>>): this {

		this.fixedValue = value;
		return this;

	}

	/**
	 * 入力中の処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onInput (handler: (value: unknown, ctx: Context<S>) => unknown): this {

		this.inputHandler = handler;
		return this;

	}

	/**
	 * 確定時の処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onChange (handler: (value: unknown, ctx: Context<S>) => unknown): this {

		this.changeHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		const current = this.fixedValue !== undefined
			? resolve(this.fixedValue, ctx)
			: (this.path == null ? '' : ctx.store.get(this.path as Path<S> & string));

		return html`<jb-input
			.name=${this.name}
			.type=${this.type}
			.label=${resolve(this.labelText, ctx) ?? ''}
			.placeholder=${resolve(this.placeholderText, ctx) ?? ''}
			.hint=${resolve(this.hintText, ctx) ?? ''}
			.error=${resolve(this.errorText, ctx) ?? ''}
			.value=${current == null ? '' : String(current)}
			.required=${resolve(this.requiredValue, ctx) === true}
			.disabled=${resolve(this.disabledValue, ctx) === true}
			.multiline=${resolve(this.multilineValue, ctx) === true}
			@jb-input=${(event: CustomEvent<InputEventDetail>) => this.accept(event, ctx, this.inputHandler)}
			@jb-change=${(event: CustomEvent<InputEventDetail>) => this.accept(event, ctx, this.changeHandler)}
		></jb-input>`;

	}

	/**
	 * 入力値を受け取る
	 *
	 * @param event イベント
	 * @param ctx コンテキスト
	 * @param handler 処理
	 */
	protected accept (
		event: CustomEvent<InputEventDetail>,
		ctx: Context<S>,
		handler: ((value: unknown, ctx: Context<S>) => unknown) | null
	): void {

		const raw = event.detail.value;
		const value: unknown = this.type === 'number' ? (raw === '' ? null : Number(raw)) : raw;

		if (this.path != null) {
			ctx.store.set(this.path as Path<S> & string, value as PathValue<S, Path<S> & string>);
		}
		handler?.(value, ctx);

	}

}
