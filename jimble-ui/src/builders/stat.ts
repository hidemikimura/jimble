import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, Resolvable, Trend } from '../core/types.js';

/**
 * 統計タイルビルダー
 *
 * <pre>
 * UI.stat('今月の売上', (ctx) => format(ctx.get('sales')))
 *     .unit('円')
 *     .delta('+12%', 'up');
 * </pre>
 */
export class StatBuilder<S extends object = AppState> extends Builder<S> {

	private labelText: Resolvable<string, Context<S>>;
	private valueText: Resolvable<string | number, Context<S>>;
	private unitText: Resolvable<string, Context<S>> | undefined;
	private deltaText: Resolvable<string, Context<S>> | undefined;
	private trendValue: Resolvable<Trend, Context<S>> = 'flat';
	private hintText: Resolvable<string, Context<S>> | undefined;

	constructor (label: Resolvable<string, Context<S>>, value: Resolvable<string | number, Context<S>>) {

		super();
		this.labelText = label;
		this.valueText = value;

	}

	/**
	 * 単位
	 *
	 * @param text 単位
	 * @return ビルダー
	 */
	unit (text: Resolvable<string, Context<S>>): this {

		this.unitText = text;
		return this;

	}

	/**
	 * 増減
	 *
	 * @param text 増減（例: '+12%'）
	 * @param trend 向き
	 * @return ビルダー
	 */
	delta (text: Resolvable<string, Context<S>>, trend: Resolvable<Trend, Context<S>> = 'flat'): this {

		this.deltaText = text;
		this.trendValue = trend;
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

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-stat
			.label=${resolve(this.labelText, ctx) ?? ''}
			.value=${String(resolve(this.valueText, ctx) ?? '')}
			.unit=${resolve(this.unitText, ctx) ?? ''}
			.delta=${resolve(this.deltaText, ctx) ?? ''}
			.trend=${resolve(this.trendValue, ctx) ?? 'flat'}
			.hint=${resolve(this.hintText, ctx) ?? ''}
		></jb-stat>`;

	}

}
