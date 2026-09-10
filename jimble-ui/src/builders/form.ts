import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder } from '../core/builder.js';
import type { Node } from '../core/builder.js';
import type { Context } from '../core/context.js';
import { report } from '../core/dev.js';
import type { AppState } from '../core/types.js';

/**
 * フォームビルダー
 *
 * <p>
 * 中の入力欄で Enter が押されたら {@code onSubmit} が走る
 * （複数行入力の中の Enter は改行のまま）。
 * </p>
 *
 * <pre>
 * UI.form(
 *     UI.text('loginId').label('ログインID').bind('form.loginId'),
 *     UI.password('password').label('パスワード').bind('form.password'),
 *     UI.button('ログイン').primary().onClick(login)
 * ).onSubmit(login);
 * </pre>
 */
export class FormBuilder<S extends object = AppState> extends Builder<S> {

	private submitHandler: ((ctx: Context<S>) => unknown) | null = null;

	constructor (children: Node<S>[] = []) {

		super();
		this.add(...children);

	}

	/**
	 * 送信されたときの処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onSubmit (handler: (ctx: Context<S>) => unknown): this {

		this.submitHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-form @jb-submit=${() => this.submit(ctx)}>${this.renderChildren(ctx)}</jb-form>`;

	}

	/**
	 * 送信処理を実行する
	 *
	 * @param ctx コンテキスト
	 */
	protected submit (ctx: Context<S>): void {

		if (this.submitHandler == null) {
			return;
		}

		try {
			const result = this.submitHandler(ctx);
			if (result instanceof Promise) {
				result.catch((error: unknown) => report(error, 'onSubmit'));
			}
		} catch (error) {
			report(error, 'onSubmit');
		}

	}

}
