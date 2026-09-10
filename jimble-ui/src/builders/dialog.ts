import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, renderNode, resolve } from '../core/builder.js';
import type { Node } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, DialogSize, Resolvable } from '../core/types.js';

/**
 * ダイアログビルダー
 *
 * <pre>
 * UI.dialog(UI.label('削除します。よろしいですか？'))
 *     .title('確認')
 *     .open((ctx) => ctx.get('confirming'))
 *     .onClose((ctx) => ctx.set('confirming', false))
 *     .footer(
 *         UI.button('やめる').onClick((ctx) => ctx.set('confirming', false)),
 *         UI.button('削除する').danger().onClick(remove)
 *     );
 * </pre>
 */
export class DialogBuilder<S extends object = AppState> extends Builder<S> {

	private titleText: Resolvable<string, Context<S>> | undefined;
	private openValue: Resolvable<boolean, Context<S>> | undefined;
	private sizeValue: Resolvable<DialogSize, Context<S>> | undefined;
	private closableValue: Resolvable<boolean, Context<S>> | undefined;
	private footerNodes: Node<S>[] = [];
	private closeHandler: ((ctx: Context<S>) => unknown) | null = null;

	constructor (children: Node<S>[] = []) {

		super();
		this.add(...children);

	}

	/**
	 * 見出し
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	title (text: Resolvable<string, Context<S>>): this {

		this.titleText = text;
		return this;

	}

	/**
	 * 開いているか
	 *
	 * @param value 開いているか
	 * @return ビルダー
	 */
	open (value: Resolvable<boolean, Context<S>>): this {

		this.openValue = value;
		return this;

	}

	/**
	 * 大きさ
	 *
	 * @param value sm | md | lg
	 * @return ビルダー
	 */
	size (value: Resolvable<DialogSize, Context<S>>): this {

		this.sizeValue = value;
		return this;

	}

	/**
	 * 閉じるボタンを出すか
	 *
	 * @param value 出すか
	 * @return ビルダー
	 */
	closable (value: Resolvable<boolean, Context<S>> = true): this {

		this.closableValue = value;
		return this;

	}

	/**
	 * 下部に置くもの（ボタンなど）
	 *
	 * @param nodes ノード
	 * @return ビルダー
	 */
	footer (...nodes: Node<S>[]): this {

		this.footerNodes = nodes;
		return this;

	}

	/**
	 * 閉じられたときの処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onClose (handler: (ctx: Context<S>) => unknown): this {

		this.closeHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-dialog
			.open=${resolve(this.openValue, ctx) === true}
			.title=${resolve(this.titleText, ctx) ?? ''}
			.size=${resolve(this.sizeValue, ctx) ?? 'md'}
			.closable=${resolve(this.closableValue, ctx) !== false}
			@jb-close=${() => this.closeHandler?.(ctx)}
		>${this.renderChildren(ctx)}${this.footerNodes.length === 0
			? ''
			: html`<div slot="footer">${this.footerNodes.map((node) => renderNode(node, ctx))}</div>`}</jb-dialog>`;

	}

}
