import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { AccordionSection } from '../core/types.js';

/**
 * アコーディオン
 *
 * <p>
 * 各節の中身は名前付きスロット（{@code section-<value>}）に入る。
 * 開閉は自分で持ち、変わったら {@code jb-toggle} を投げる。
 * </p>
 */
export class JbAccordion extends JbElement {

	static override tag = 'jb-accordion';

	static override properties: PropertyDeclarations = {
		sections: { attribute: false },
		open: { attribute: false },
		single: { type: Boolean, reflect: true, attribute: 'jb-single' }
	};

	/** 節の一覧 */
	sections: AccordionSection[] = [];

	/** 開いている節 */
	open: string[] = [];

	/** 一度に 1 つだけ開くか */
	single = false;

	/**
	 * 開いているか（テーマから呼ぶ）
	 *
	 * @param value 節の値
	 * @return 開いていれば true
	 */
	isOpen (value: string): boolean {

		return this.open.includes(value);

	}

	/**
	 * 開閉する（テーマのテンプレートから呼ぶ）
	 *
	 * @param value 節の値
	 */
	handleToggle (value: string): void {

		const opened = this.isOpen(value);

		if (this.single) {
			this.open = opened ? [] : [value];
		} else {
			this.open = opened ? this.open.filter((entry) => entry !== value) : [...this.open, value];
		}

		this.dispatchEvent(new CustomEvent('jb-toggle', {
			detail: { value, open: !opened },
			bubbles: true,
			composed: true
		}));

	}

}

customElements.define('jb-accordion', JbAccordion);
