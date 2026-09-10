import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { TextVariant } from '../core/types.js';

/**
 * テキスト表示
 *
 * <p>見出し・本文・注釈を variant で切り替える。表示の中身はテーマが決める。</p>
 */
export class JbText extends JbElement {

	static override tag = 'jb-text';

	static override properties: PropertyDeclarations = {
		text: { type: String },
		variant: { type: String, reflect: true, attribute: 'jb-variant' }
	};

	/** 表示する文言 */
	text = '';

	/** 種類 */
	variant: TextVariant = 'body';

}

customElements.define('jb-text', JbText);
