import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, renderNode, resolve } from '../core/builder.js';
import type { Node } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, Resolvable } from '../core/types.js';

/**
 * 空状態ビルダー
 *
 * <pre>
 * UI.empty('まだ社員がいません')
 *     .icon('📭')
 *     .description('「新規登録」から追加できます')
 *     .action(UI.button('新規登録').primary().go('/staff/new'));
 * </pre>
 */
export class EmptyBuilder<S extends object = AppState> extends Builder<S> {

	private headingText: Resolvable<string, Context<S>>;
	private descriptionText: Resolvable<string, Context<S>> | undefined;
	private iconText: Resolvable<string, Context<S>> | undefined;
	private actionNodes: Node<S>[] = [];

	constructor (heading: Resolvable<string, Context<S>>) {

		super();
		this.headingText = heading;

	}

	/**
	 * 説明
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	description (text: Resolvable<string, Context<S>>): this {

		this.descriptionText = text;
		return this;

	}

	/**
	 * 飾り（絵文字など）
	 *
	 * @param text 文字
	 * @return ビルダー
	 */
	icon (text: Resolvable<string, Context<S>>): this {

		this.iconText = text;
		return this;

	}

	/**
	 * 押せるもの（ボタンなど）
	 *
	 * @param nodes ノード
	 * @return ビルダー
	 */
	action (...nodes: Node<S>[]): this {

		this.actionNodes = nodes;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-empty
			.heading=${resolve(this.headingText, ctx) ?? ''}
			.description=${resolve(this.descriptionText, ctx) ?? ''}
			.icon=${resolve(this.iconText, ctx) ?? ''}
		>${this.actionNodes.map((node) => renderNode(node, ctx))}</jb-empty>`;

	}

}
