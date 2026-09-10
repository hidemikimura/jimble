import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { ChartPoint, ChartType } from '../core/types.js';

/**
 * 簡易グラフ
 *
 * <p>
 * 棒と折れ線だけ。描画（SVG）はテーマが持つので、
 * ここは点の並びと目盛りの計算だけを提供する。
 * </p>
 */
export class JbChart extends JbElement {

	static override tag = 'jb-chart';

	static override properties: PropertyDeclarations = {
		points: { attribute: false },
		type: { type: String, reflect: true, attribute: 'jb-type' },
		height: { type: Number },
		title: { type: String },
		empty: { type: String }
	};

	/** 点の並び */
	points: ChartPoint[] = [];

	/** 種類 */
	type: ChartType = 'bar';

	/** 高さ（px） */
	height = 160;

	/** 見出し */
	override title = '';

	/** 0 件のときの文言 */
	empty = 'データがありません';

	/**
	 * 目盛りの最大値を求める（テーマから呼ぶ）
	 *
	 * @return 最大値（0 件なら 1）
	 */
	max (): number {

		return Math.max(1, ...this.points.map((point) => point.value));

	}

	/**
	 * 値を 0〜1 の比率にする（テーマから呼ぶ）
	 *
	 * @param value 値
	 * @return 比率
	 */
	ratio (value: number): number {

		return Math.max(0, Math.min(1, value / this.max()));

	}

	/**
	 * 折れ線の座標を作る（テーマから呼ぶ）
	 *
	 * @param width 幅（100 を基準にした割合座標）
	 * @param height 高さ
	 * @return points 属性の文字列
	 */
	polyline (width = 100, height = 100): string {

		if (this.points.length === 0) {
			return '';
		}
		if (this.points.length === 1) {
			const only = height - this.ratio(this.points[0]!.value) * height;
			return '0,' + String(only) + ' ' + String(width) + ',' + String(only);
		}

		return this.points
			.map((point, index) => {
				const x = (index / (this.points.length - 1)) * width;
				const y = height - this.ratio(point.value) * height;
				return String(x) + ',' + String(y);
			})
			.join(' ');

	}

}

customElements.define('jb-chart', JbChart);
