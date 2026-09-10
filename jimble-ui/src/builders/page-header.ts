import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, renderNode, resolve } from '../core/builder.js';
import type { Node } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, BreadcrumbItem, Resolvable } from '../core/types.js';
import { BreadcrumbBuilder } from './breadcrumb.js';

/**
 * 画面見出しビルダー
 *
 * <pre>
 * UI.pageHeader('メーカー')
 *     .description('商品のメーカーを管理します')
 *     .breadcrumb([{ label: 'ホーム', path: '/' }, { label: 'メーカー' }])
 *     .actions(UI.button('新規登録').primary().icon('plus').go('/makers/edit'))
 *     .below(UI.text('keyword').placeholder('名前で検索'));
 * </pre>
 */
export class PageHeaderBuilder<S extends object = AppState> extends Builder<S> {

	private headingText: Resolvable<string, Context<S>>;
	private descriptionText: Resolvable<string, Context<S>> | undefined;
	private crumbs: Resolvable<BreadcrumbItem[], Context<S>> | undefined;
	private actionNodes: Node<S>[] = [];
	private belowNodes: Node<S>[] = [];

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
	 * パンくず（題名の上に出る）
	 *
	 * @param items 並び
	 * @return ビルダー
	 */
	breadcrumb (items: Resolvable<BreadcrumbItem[], Context<S>>): this {

		this.crumbs = items;
		return this;

	}

	/**
	 * 右側に置くもの（ボタンなど）
	 *
	 * @param nodes ノード
	 * @return ビルダー
	 */
	actions (...nodes: Node<S>[]): this {

		this.actionNodes = nodes;
		return this;

	}

	/**
	 * 見出しの下に置くもの（検索欄・タブなど）
	 *
	 * @param nodes ノード
	 * @return ビルダー
	 */
	below (...nodes: Node<S>[]): this {

		this.belowNodes = nodes;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-page-header
			.heading=${String(resolve(this.headingText, ctx) ?? '')}
			.description=${resolve(this.descriptionText, ctx) ?? ''}
		>
			${this.crumbs === undefined
				? ''
				: html`<span slot="breadcrumb">${renderNode(new BreadcrumbBuilder<S>(this.crumbs), ctx)}</span>`}
			${this.actionNodes.map((node) => renderNode(node, ctx))}
			${this.belowNodes.length === 0
				? ''
				: html`<span slot="below">${this.belowNodes.map((node) => renderNode(node, ctx))}</span>`}
		</jb-page-header>`;

	}

}
