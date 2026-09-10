import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';

/**
 * ページ送り
 */
export class JbPagination extends JbElement {

	static override tag = 'jb-pagination';

	static override properties: PropertyDeclarations = {
		page: { type: Number },
		pages: { type: Number },
		total: { type: Number },
		summary: { type: String }
	};

	/** 現在のページ（1 始まり） */
	page = 1;

	/** 総ページ数 */
	pages = 1;

	/** 総件数 */
	total = 0;

	/** 件数の表示文言（省略時はテーマが作る） */
	summary = '';

	/**
	 * ページが選ばれたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param page ページ
	 */
	handleSelect (page: number): void {

		const next = Math.min(Math.max(1, page), Math.max(1, this.pages));
		if (next === this.page) {
			return;
		}
		this.page = next;
		this.dispatchEvent(new CustomEvent('jb-change', {
			detail: { page: next },
			bubbles: true,
			composed: true
		}));

	}

	/**
	 * 表示するページ番号を作る
	 *
	 * <p>前後 2 ページ分だけを出す。省略箇所は 0 で表す。</p>
	 *
	 * @return ページ番号（0 は省略記号）
	 */
	pageNumbers (): number[] {

		const numbers: number[] = [];
		const last = Math.max(1, this.pages);

		for (let page = 1; page <= last; page++) {
			if (page === 1 || page === last || Math.abs(page - this.page) <= 2) {
				numbers.push(page);
			} else if (numbers[numbers.length - 1] !== 0) {
				numbers.push(0);
			}
		}

		return numbers;

	}

}

customElements.define('jb-pagination', JbPagination);
