import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import { report } from '../core/dev.js';
import type { AppState, ButtonVariant, Resolvable } from '../core/types.js';

/**
 * ボタンビルダー
 *
 * <pre>
 * UI.button('登録').primary().onClick(async (ctx) => {
 *     await save(ctx.get('form'));
 *     ctx.go('/');
 * });
 * </pre>
 */
export class ButtonBuilder<S extends object = AppState> extends Builder<S> {

	private label: Resolvable<string, Context<S>>;
	private base: 'default' | 'primary' | 'danger' = 'default';
	private isQuiet = false;
	private iconName: Resolvable<string, Context<S>> | undefined;
	private disabledValue: Resolvable<boolean, Context<S>> | undefined;
	private loadingValue: Resolvable<boolean, Context<S>> | undefined;
	private clickHandler: ((ctx: Context<S>) => unknown) | null = null;

	constructor (label: Resolvable<string, Context<S>>) {

		super();
		this.label = label;

	}

	/** 主ボタンにする */
	primary (): this {

		this.base = 'primary';
		return this;

	}

	/**
	 * 危険操作ボタンにする
	 *
	 * <p>{@code .danger().quiet()} と続けると「枠なしの赤い文字」になる（一覧の行の削除など）。</p>
	 *
	 * @return ビルダー
	 */
	danger (): this {

		this.base = 'danger';
		return this;

	}

	/** 枠なしボタンにする */
	quiet (): this {

		this.isQuiet = true;
		return this;

	}

	/**
	 * 文言の前に飾り（アイコン）を付ける
	 *
	 * @param name jb-icon の名前
	 * @return ビルダー
	 */
	icon (name: Resolvable<string, Context<S>>): this {

		this.iconName = name;
		return this;

	}

	/* 種類を決める */
	private variantOf (): ButtonVariant {

		if (!this.isQuiet) {
			return this.base;
		}
		return this.base === 'danger' ? 'quiet-danger' : 'quiet';

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
	 * 処理中表示
	 *
	 * @param value 処理中か
	 * @return ビルダー
	 */
	loading (value: Resolvable<boolean, Context<S>> = true): this {

		this.loadingValue = value;
		return this;

	}

	/**
	 * 押されたときの処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onClick (handler: (ctx: Context<S>) => unknown): this {

		this.clickHandler = handler;
		return this;

	}

	/**
	 * 押されたら画面遷移する
	 *
	 * @param path パス
	 * @return ビルダー
	 */
	go (path: Resolvable<string, Context<S>>): this {

		this.clickHandler = (ctx: Context<S>) => {
			ctx.go(resolve(path, ctx) as string);
		};
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-button
			.label=${String(resolve(this.label, ctx) ?? '')}
			.variant=${this.variantOf()}
			.icon=${resolve(this.iconName, ctx) ?? ''}
			.disabled=${resolve(this.disabledValue, ctx) === true}
			.loading=${resolve(this.loadingValue, ctx) === true}
			@jb-click=${() => this.click(ctx)}
		></jb-button>`;

	}

	/**
	 * 押されたときの処理を実行する
	 *
	 * @param ctx コンテキスト
	 */
	protected click (ctx: Context<S>): void {

		if (this.clickHandler == null) {
			return;
		}

		try {
			const result = this.clickHandler(ctx);
			if (result instanceof Promise) {
				result.catch((error: unknown) => report(error, 'onClick'));
			}
		} catch (error) {
			report(error, 'onClick');
		}

	}

}
