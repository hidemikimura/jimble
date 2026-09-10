import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, Path, PathValue, Resolvable } from '../core/types.js';

/**
 * 日付範囲ビルダー
 *
 * <pre>
 * UI.dateRange('period').label('対象期間').bind('filter.from', 'filter.to');
 * </pre>
 */
export class DateRangeBuilder<S extends object = AppState> extends Builder<S> {

	private name: string;
	private fromPath: string | null = null;
	private toPath: string | null = null;
	private labelText: Resolvable<string, Context<S>> | undefined;
	private hintText: Resolvable<string, Context<S>> | undefined;
	private errorText: Resolvable<string | undefined, Context<S>> | undefined;
	private requiredValue: Resolvable<boolean, Context<S>> | undefined;
	private disabledValue: Resolvable<boolean, Context<S>> | undefined;
	private changeHandler: ((from: string, to: string, ctx: Context<S>) => unknown) | null = null;

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
	 * 開始と終了を状態に結び付ける
	 *
	 * @param from 開始のパス
	 * @param to 終了のパス
	 * @return ビルダー
	 */
	bind<F extends Path<S> & string, T extends Path<S> & string> (from: F, to: T): this {

		this.fromPath = from;
		this.toPath = to;
		return this;

	}

	/**
	 * 変わったときの処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onChange (handler: (from: string, to: string, ctx: Context<S>) => unknown): this {

		this.changeHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		const from = this.fromPath == null ? '' : ctx.store.get(this.fromPath as Path<S> & string);
		const to = this.toPath == null ? '' : ctx.store.get(this.toPath as Path<S> & string);

		return html`<jb-daterange
			.name=${this.name}
			.label=${resolve(this.labelText, ctx) ?? ''}
			.hint=${resolve(this.hintText, ctx) ?? ''}
			.error=${resolve(this.errorText, ctx) ?? ''}
			.from=${from == null ? '' : String(from)}
			.to=${to == null ? '' : String(to)}
			.required=${resolve(this.requiredValue, ctx) === true}
			.disabled=${resolve(this.disabledValue, ctx) === true}
			@jb-change=${(event: CustomEvent<{ from: string; to: string }>) => this.accept(event.detail.from, event.detail.to, ctx)}
		></jb-daterange>`;

	}

	/**
	 * 値を受け取る
	 *
	 * @param from 開始
	 * @param to 終了
	 * @param ctx コンテキスト
	 */
	protected accept (from: string, to: string, ctx: Context<S>): void {

		if (this.fromPath != null) {
			ctx.store.set(this.fromPath as Path<S> & string, from as PathValue<S, Path<S> & string>);
		}
		if (this.toPath != null) {
			ctx.store.set(this.toPath as Path<S> & string, to as PathValue<S, Path<S> & string>);
		}
		this.changeHandler?.(from, to, ctx);

	}

}
