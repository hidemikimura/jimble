import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, IconSize, Resolvable } from '../core/types.js';

/**
 * 飾り（アイコン）ビルダー
 *
 * <pre>
 * UI.icon('trash').lg().alt('削除');
 * </pre>
 *
 * <p>使える名前は {@code ICON_NAMES} を参照。知らない名前を書くと開発モードで警告が出る。</p>
 */
export class IconBuilder<S extends object = AppState> extends Builder<S> {

	private iconName: Resolvable<string, Context<S>>;
	private iconSize: IconSize = 'md';
	private altText: Resolvable<string, Context<S>> | undefined;

	constructor (name: Resolvable<string, Context<S>>) {

		super();
		this.iconName = name;

	}

	/** 小さくする */
	sm (): this {

		this.iconSize = 'sm';
		return this;

	}

	/** 大きくする */
	lg (): this {

		this.iconSize = 'lg';
		return this;

	}

	/**
	 * 大きさ
	 *
	 * @param size 大きさ
	 * @return ビルダー
	 */
	size (size: IconSize): this {

		this.iconSize = size;
		return this;

	}

	/**
	 * 読み上げ用の説明
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	alt (text: Resolvable<string, Context<S>>): this {

		this.altText = text;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-icon
			.name=${String(resolve(this.iconName, ctx) ?? '')}
			.size=${this.iconSize}
			.alt=${resolve(this.altText, ctx) ?? ''}
		></jb-icon>`;

	}

}
