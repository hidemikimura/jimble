import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AppState, ChartPoint, ChartType, Resolvable } from '../core/types.js';

/**
 * グラフビルダー
 *
 * <p>棒と折れ線だけの簡易グラフ。描画はテーマが SVG で行う。</p>
 *
 * <pre>
 * UI.chart((ctx) => ctx.get('daily')).line().title('日別の売上').height(180);
 * </pre>
 */
export class ChartBuilder<S extends object = AppState> extends Builder<S> {

	private points: Resolvable<ChartPoint[], Context<S>>;
	private chartType: ChartType = 'bar';
	private heightValue: Resolvable<number, Context<S>> | undefined;
	private titleText: Resolvable<string, Context<S>> | undefined;
	private emptyText: Resolvable<string, Context<S>> | undefined;

	constructor (points: Resolvable<ChartPoint[], Context<S>>) {

		super();
		this.points = points;

	}

	/** 棒グラフにする */
	bar (): this {

		this.chartType = 'bar';
		return this;

	}

	/** 折れ線グラフにする */
	line (): this {

		this.chartType = 'line';
		return this;

	}

	/**
	 * 高さ
	 *
	 * @param height 高さ（px）
	 * @return ビルダー
	 */
	height (height: Resolvable<number, Context<S>>): this {

		this.heightValue = height;
		return this;

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
	 * 0 件のときの文言
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	empty (text: Resolvable<string, Context<S>>): this {

		this.emptyText = text;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-chart
			.points=${resolve(this.points, ctx) ?? []}
			.type=${this.chartType}
			.height=${resolve(this.heightValue, ctx) ?? 160}
			.title=${resolve(this.titleText, ctx) ?? ''}
			.empty=${resolve(this.emptyText, ctx) ?? 'データがありません'}
		></jb-chart>`;

	}

}
