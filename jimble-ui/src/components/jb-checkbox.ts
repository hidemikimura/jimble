import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';

/**
 * チェックボックス
 *
 * <p>入切の状態を持つだけ。見た目（四角か、スイッチか）はテーマが決める。</p>
 */
export class JbCheckbox extends JbElement {

	static override tag = 'jb-checkbox';

	static override properties: PropertyDeclarations = {
		name: { type: String },
		label: { type: String },
		checked: { type: Boolean, reflect: true, attribute: 'jb-checked' },
		hint: { type: String },
		error: { type: String, reflect: true, attribute: 'jb-error' },
		disabled: { type: Boolean, reflect: true, attribute: 'jb-disabled' }
	};

	/** 項目名 */
	name = '';

	/** ラベル */
	label = '';

	/** 入っているか */
	checked = false;

	/** 補足 */
	hint = '';

	/** エラー文言 */
	error = '';

	/** 使用不可 */
	disabled = false;

	/**
	 * 切り替えられたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 */
	handleChange (event: Event): void {

		this.checked = (event.target as HTMLInputElement).checked;
		this.dispatchEvent(new CustomEvent('jb-change', {
			detail: { name: this.name, checked: this.checked },
			bubbles: true,
			composed: true
		}));

	}

}

customElements.define('jb-checkbox', JbCheckbox);

/**
 * スイッチ
 *
 * <p>状態はチェックボックスと同じ。テーマが別の見た目を与える。</p>
 */
export class JbSwitch extends JbCheckbox {

	static override tag = 'jb-switch';

}

customElements.define('jb-switch', JbSwitch);
