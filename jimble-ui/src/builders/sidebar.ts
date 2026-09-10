import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, renderNode, resolve } from '../core/builder.js';
import type { Node } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, NavItem, Resolvable } from '../core/types.js';

/** 項目の見た目の指定 */
export interface NavItemOptions {
	/** 飾り（jb-icon の名前） */
	icon?: string;
	/** 右端の数字など */
	badge?: string | number;
	/** 選べないか */
	disabled?: boolean;
}

/**
 * サイドバービルダー
 *
 * <p>
 * 既定では、項目の値をそのまま遷移先として扱い、今いる場所を自動で光らせる。
 * {@link SidebarBuilder#onSelect} を書けば遷移を自分で決められる。
 * </p>
 *
 * <pre>
 * UI.sidebar()
 *     .heading('aqSell 管理')
 *     .group('商品')
 *     .item('/makers', 'メーカー', { icon: 'list' })
 *     .item('/products', '商品', { icon: 'box' })
 *     .group('設定')
 *     .item('/staff', '担当者', { icon: 'user' })
 *     .footer(UI.button('ログアウト').quiet().icon('logout').onClick(logout));
 * </pre>
 */
export class SidebarBuilder<S extends object = AppState> extends Builder<S> {

	private navItems: NavItem[] = [];
	private groupName: string | undefined;
	private headingText: Resolvable<string, Context<S>> | undefined;
	private currentValue: Resolvable<string, Context<S>> | undefined;
	private footerNodes: Node<S>[] = [];
	private selectHandler: ((value: string, ctx: Context<S>) => unknown) | null = null;
	private widthValue: Resolvable<string, Context<S>> | undefined;
	private fullHeightValue = false;

	/**
	 * 一番上の見出し（アプリ名など）
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	heading (text: Resolvable<string, Context<S>>): this {

		this.headingText = text;
		return this;

	}

	/**
	 * ここから先の項目をまとめる見出し
	 *
	 * @param label 文言
	 * @return ビルダー
	 */
	group (label: string): this {

		this.groupName = label;
		return this;

	}

	/**
	 * 項目を足す
	 *
	 * @param path 遷移先（そのまま値になる）
	 * @param label 文言
	 * @param options 見た目
	 * @return ビルダー
	 */
	item (path: string, label: string, options: NavItemOptions = {}): this {

		this.navItems.push({
			value: path,
			label,
			...(options.icon == null ? {} : { icon: options.icon }),
			...(options.badge == null ? {} : { badge: options.badge }),
			...(options.disabled == null ? {} : { disabled: options.disabled }),
			...(this.groupName == null ? {} : { group: this.groupName })
		});
		return this;

	}

	/**
	 * 今いる場所を自分で決める（既定はブラウザのパス）
	 *
	 * @param value 値
	 * @return ビルダー
	 */
	current (value: Resolvable<string, Context<S>>): this {

		this.currentValue = value;
		return this;

	}

	/**
	 * 一番下に置くもの（ログアウトボタンなど）
	 *
	 * @param nodes ノード
	 * @return ビルダー
	 */
	footer (...nodes: Node<S>[]): this {

		this.footerNodes = nodes;
		return this;

	}

	/**
	 * 幅（CSS の値。既定は 240px）
	 *
	 * @param value 幅
	 * @return ビルダー
	 */
	width (value: Resolvable<string, Context<S>>): this {

		this.widthValue = value;
		return this;

	}

	/**
	 * 画面の高さいっぱいにする（本文が短くてもサイドバーを下まで伸ばす）
	 *
	 * @return ビルダー
	 */
	fullHeight (): this {

		this.fullHeightValue = true;
		return this;

	}

	/**
	 * 選ばれたときの処理（書かなければその値へ遷移する）
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onSelect (handler: (value: string, ctx: Context<S>) => unknown): this {

		this.selectHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		const current = this.currentValue !== undefined
			? String(resolve(this.currentValue, ctx) ?? '')
			: (ctx.router.path().split('?')[0] ?? '');

		const width = resolve(this.widthValue, ctx) ?? '240px';

		return html`<jb-nav
			style=${'width:' + width + ';flex:0 0 auto' + (this.fullHeightValue ? ';min-height:100vh' : '')}
			.items=${this.navItems}
			.heading=${resolve(this.headingText, ctx) ?? ''}
			.current=${current}
			@jb-select=${(event: CustomEvent<{ value: string }>) => this.select(event.detail.value, ctx)}
		>${this.footerNodes.length === 0
			? ''
			: html`<span slot="footer">${this.footerNodes.map((node) => renderNode(node, ctx))}</span>`}</jb-nav>`;

	}

	/* 選ばれたときの処理 */
	private select (value: string, ctx: Context<S>): void {

		if (this.selectHandler != null) {
			this.selectHandler(value, ctx);
			return;
		}
		ctx.go(value);

	}

}
