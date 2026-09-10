import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { Size } from '../core/types.js';

/**
 * カード並べ（グリッド）
 *
 * <p>列数を指定するか、最小幅を指定して自動で折り返す。</p>
 */
export class JbGrid extends JbElement {

	static override tag = 'jb-grid';

	static override properties: PropertyDeclarations = {
		columns: { type: Number, reflect: true, attribute: 'jb-columns' },
		gap: { type: String, reflect: true, attribute: 'jb-gap' },
		min: { type: String }
	};

	/** 列数（1〜6）。min を指定したときは無視される */
	columns = 3;

	/** 間隔 */
	gap: Size | undefined = undefined;

	/** 1 枚あたりの最小幅（例: '240px'）。指定すると自動で折り返す */
	min = '';

}

customElements.define('jb-grid', JbGrid);
