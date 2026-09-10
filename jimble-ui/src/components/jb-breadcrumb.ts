import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { BreadcrumbItem } from '../core/types.js';

/**
 * パンくず
 */
export class JbBreadcrumb extends JbElement {

	static override tag = 'jb-breadcrumb';

	static override properties: PropertyDeclarations = {
		items: { attribute: false },
		separator: { type: String }
	};

	/** 並び */
	items: BreadcrumbItem[] = [];

	/** 区切り文字 */
	separator = '/';

	/**
	 * 押されたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param item 押された項目
	 */
	handleSelect (item: BreadcrumbItem): void {

		if (item.path == null) {
			return;
		}
		this.dispatchEvent(new CustomEvent('jb-select', {
			detail: { path: item.path },
			bubbles: true,
			composed: true
		}));

	}

}

customElements.define('jb-breadcrumb', JbBreadcrumb);
