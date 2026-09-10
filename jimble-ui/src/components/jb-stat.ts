import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { Trend } from '../core/types.js';

/**
 * 統計タイル
 *
 * <p>「今月の売上 1,240,000 円 前月比 +12%」のような 1 枚。</p>
 */
export class JbStat extends JbElement {

	static override tag = 'jb-stat';

	static override properties: PropertyDeclarations = {
		label: { type: String },
		value: { type: String },
		unit: { type: String },
		delta: { type: String },
		trend: { type: String, reflect: true, attribute: 'jb-trend' },
		hint: { type: String }
	};

	/** 見出し */
	label = '';

	/** 値（整形済みの文字列で渡す） */
	value = '';

	/** 単位 */
	unit = '';

	/** 増減（例: '+12%'） */
	delta = '';

	/** 増減の向き */
	trend: Trend = 'flat';

	/** 補足 */
	hint = '';

}

customElements.define('jb-stat', JbStat);
