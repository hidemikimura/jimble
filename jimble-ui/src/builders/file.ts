import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, Resolvable } from '../core/types.js';

/**
 * ファイル添付ビルダー
 *
 * <pre>
 * UI.file('attachment')
 *     .label('添付ファイル')
 *     .accept('.pdf,image/*')
 *     .multiple()
 *     .onSelect((files, ctx) => ctx.set('files', files.map((f) => f.name)));
 * </pre>
 */
export class FileBuilder<S extends object = AppState> extends Builder<S> {

	private name: string;
	private labelText: Resolvable<string, Context<S>> | undefined;
	private acceptText: Resolvable<string, Context<S>> | undefined;
	private hintText: Resolvable<string, Context<S>> | undefined;
	private errorText: Resolvable<string | undefined, Context<S>> | undefined;
	private multipleValue: Resolvable<boolean, Context<S>> | undefined;
	private disabledValue: Resolvable<boolean, Context<S>> | undefined;
	private selectHandler: ((files: File[], ctx: Context<S>) => unknown) | null = null;

	constructor (name: string) {

		super();
		this.name = name;

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
	 * 受け付ける種類
	 *
	 * @param text 例: '.pdf,image/*'
	 * @return ビルダー
	 */
	accept (text: Resolvable<string, Context<S>>): this {

		this.acceptText = text;
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
	 * 複数選べるようにする
	 *
	 * @param value 複数可か
	 * @return ビルダー
	 */
	multiple (value: Resolvable<boolean, Context<S>> = true): this {

		this.multipleValue = value;
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
	 * 選ばれたときの処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onSelect (handler: (files: File[], ctx: Context<S>) => unknown): this {

		this.selectHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-file
			.name=${this.name}
			.label=${resolve(this.labelText, ctx) ?? ''}
			.accept=${resolve(this.acceptText, ctx) ?? ''}
			.hint=${resolve(this.hintText, ctx) ?? ''}
			.error=${resolve(this.errorText, ctx) ?? ''}
			.multiple=${resolve(this.multipleValue, ctx) === true}
			.disabled=${resolve(this.disabledValue, ctx) === true}
			@jb-select=${(event: CustomEvent<{ files: File[] }>) => this.selectHandler?.(event.detail.files, ctx)}
		></jb-file>`;

	}

}
