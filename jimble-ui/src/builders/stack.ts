import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Node } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { Align, AppState, Justify, Resolvable, Size, Width } from '../core/types.js';

/**
 * レイアウトビルダー
 *
 * <pre>
 * UI.column(
 *     UI.title('社員登録'),
 *     UI.text('name').label('氏名')
 * ).gap('lg').width('md');
 * </pre>
 */
export class StackBuilder<S extends object = AppState> extends Builder<S> {

	private direction: 'row' | 'column';
	private gapSize: Resolvable<Size, Context<S>> | undefined;
	private padSize: Resolvable<Size, Context<S>> | undefined;
	private alignValue: Resolvable<Align, Context<S>> | undefined;
	private justifyValue: Resolvable<Justify, Context<S>> | undefined;
	private widthValue: Resolvable<Width, Context<S>> | undefined;
	private wrapValue: Resolvable<boolean, Context<S>> | undefined;
	private surfaceValue: Resolvable<boolean, Context<S>> | undefined;

	constructor (direction: 'row' | 'column' = 'column', children: Node<S>[] = []) {

		super();
		this.direction = direction;
		this.add(...children);

	}

	/**
	 * 間隔
	 *
	 * @param size none | xs | sm | md | lg | xl
	 * @return ビルダー
	 */
	gap (size: Resolvable<Size, Context<S>>): this {

		this.gapSize = size;
		return this;

	}

	/**
	 * 内側の余白
	 *
	 * @param size none | xs | sm | md | lg | xl
	 * @return ビルダー
	 */
	pad (size: Resolvable<Size, Context<S>>): this {

		this.padSize = size;
		return this;

	}

	/**
	 * 交差軸の揃え
	 *
	 * @param value start | center | end | stretch
	 * @return ビルダー
	 */
	align (value: Resolvable<Align, Context<S>>): this {

		this.alignValue = value;
		return this;

	}

	/**
	 * 主軸の揃え
	 *
	 * @param value start | center | end | between
	 * @return ビルダー
	 */
	justify (value: Resolvable<Justify, Context<S>>): this {

		this.justifyValue = value;
		return this;

	}

	/**
	 * 折り返し
	 *
	 * @param value 折り返すか
	 * @return ビルダー
	 */
	wrap (value: Resolvable<boolean, Context<S>> = true): this {

		this.wrapValue = value;
		return this;

	}

	/**
	 * 幅
	 *
	 * @param value sm | md | lg | full
	 * @return ビルダー
	 */
	width (value: Resolvable<Width, Context<S>>): this {

		this.widthValue = value;
		return this;

	}

	/**
	 * カード状の面にする
	 *
	 * @param value 面にするか
	 * @return ビルダー
	 */
	surface (value: Resolvable<boolean, Context<S>> = true): this {

		this.surfaceValue = value;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-stack
			.direction=${this.direction}
			.gap=${resolve(this.gapSize, ctx) ?? 'md'}
			.pad=${resolve(this.padSize, ctx)}
			.align=${resolve(this.alignValue, ctx)}
			.justify=${resolve(this.justifyValue, ctx)}
			.width=${resolve(this.widthValue, ctx)}
			.wrap=${resolve(this.wrapValue, ctx) === true}
			.surface=${resolve(this.surfaceValue, ctx) === true}
		>${this.renderChildren(ctx)}</jb-stack>`;

	}

}
