import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { NavItem } from '../core/types.js';

/**
 * サイドバー（画面の左に置く案内）
 *
 * <p>
 * 項目と「今どこにいるか」だけを持つ。押されたら {@code jb-select} を投げるだけで、
 * 実際に遷移するかどうかは画面側（ビルダー）が決める。
 * </p>
 *
 * <p>差し込み口: 既定（見出しの下）／{@code footer}（一番下）。</p>
 */
export class JbNav extends JbElement {

	static override tag = 'jb-nav';

	static override properties: PropertyDeclarations = {
		items: { attribute: false },
		heading: { type: String },
		current: { type: String },
		collapsed: { type: Boolean, reflect: true, attribute: 'jb-collapsed' }
	};

	/** 項目 */
	items: NavItem[] = [];

	/** 一番上の見出し（アプリ名など） */
	heading = '';

	/** 今いる場所（項目の value と突き合わせる） */
	current = '';

	/** 畳んでいるか */
	collapsed = false;

	/**
	 * その項目が今いる場所かどうか
	 *
	 * <p>完全一致のほか、「/makers/edit にいるとき /makers を光らせる」も見る。</p>
	 *
	 * @param item 項目
	 * @return 今いる場所なら true
	 */
	isCurrent (item: NavItem): boolean {

		if (this.current === '') {
			return false;
		}
		if (this.current === item.value) {
			return true;
		}
		return item.value !== '/' && this.current.startsWith(item.value + '/');

	}

	/**
	 * まとまりの見出しを出すべきかどうか
	 *
	 * @param index 位置
	 * @return 出すなら true
	 */
	startsGroup (index: number): boolean {

		const group = this.items[index]?.group;
		if (group == null || group === '') {
			return false;
		}
		return this.items[index - 1]?.group !== group;

	}

	/**
	 * 選ばれたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param item 項目
	 */
	handleSelect (item: NavItem): void {

		if (item.disabled === true) {
			return;
		}
		this.dispatchEvent(new CustomEvent('jb-select', {
			detail: { value: item.value },
			bubbles: true,
			composed: true
		}));

	}

}

customElements.define('jb-nav', JbNav);
