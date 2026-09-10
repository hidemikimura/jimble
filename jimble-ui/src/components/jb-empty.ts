import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';

/**
 * 空状態
 *
 * <p>「まだ何もありません」の 1 枚。押せるものは既定スロットに入れる。</p>
 */
export class JbEmpty extends JbElement {

	static override tag = 'jb-empty';

	static override properties: PropertyDeclarations = {
		icon: { type: String },
		heading: { type: String },
		description: { type: String }
	};

	/** 絵文字などの飾り */
	icon = '';

	/** 見出し */
	heading = '';

	/** 説明 */
	description = '';

}

customElements.define('jb-empty', JbEmpty);
