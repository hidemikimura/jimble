import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { TabItem } from '../core/types.js';

/**
 * タブ
 *
 * <p>選ばれたら {@code jb-change} を投げるだけ。中身の出し分けは画面側で行う。</p>
 */
export class JbTabs extends JbElement {

	static override tag = 'jb-tabs';

	static override properties: PropertyDeclarations = {
		tabs: { attribute: false },
		value: { type: String }
	};

	/** タブ一覧 */
	tabs: TabItem[] = [];

	/** 選ばれている値 */
	value = '';

	/**
	 * 選ばれたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param value 値
	 */
	handleSelect (value: string): void {

		if (this.value === value) {
			return;
		}
		this.value = value;
		this.dispatchEvent(new CustomEvent('jb-change', {
			detail: { value },
			bubbles: true,
			composed: true
		}));

	}

}

customElements.define('jb-tabs', JbTabs);
