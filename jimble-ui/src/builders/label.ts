import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, Resolvable, TextVariant } from '../core/types.js';

/**
 * テキスト表示ビルダー
 */
export class LabelBuilder<S extends object = AppState> extends Builder<S> {

	private text: Resolvable<string | number | null | undefined, Context<S>>;
	private variant: TextVariant;

	constructor (text: Resolvable<string | number | null | undefined, Context<S>>, variant: TextVariant = 'body') {

		super();
		this.text = text;
		this.variant = variant;

	}

	/** 大見出しにする */
	title (): this {

		this.variant = 'title';
		return this;

	}

	/** 見出しにする */
	heading (): this {

		this.variant = 'heading';
		return this;

	}

	/** 注釈にする */
	caption (): this {

		this.variant = 'caption';
		return this;

	}

	/** 警告色にする */
	danger (): this {

		this.variant = 'danger';
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		const text = resolve(this.text, ctx);
		return html`<jb-text .text=${text == null ? '' : String(text)} .variant=${this.variant}></jb-text>`;

	}

}
