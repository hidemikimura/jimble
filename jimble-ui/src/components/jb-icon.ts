import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { IconSize } from '../core/types.js';

/**
 * 飾り（アイコン）
 *
 * <p>
 * 名前だけを持つ。どんな図形になるかはテーマが持つ（src/themes/icons.ts）。
 * 名前が分からないときは、テーマ側が「?」を出して開発モードで警告する。
 * </p>
 */
export class JbIcon extends JbElement {

	static override tag = 'jb-icon';

	static override properties: PropertyDeclarations = {
		name: { type: String, reflect: true, attribute: 'jb-name' },
		size: { type: String, reflect: true, attribute: 'jb-size' },
		alt: { type: String }
	};

	/** 名前 */
	name = '';

	/** 大きさ */
	size: IconSize = 'md';

	/** 読み上げ用の説明（空なら装飾扱い） */
	alt = '';

}

customElements.define('jb-icon', JbIcon);
