import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { SelectOption } from '../core/types.js';

/**
 * 選択
 *
 * <p>選択肢を持つだけで、どう見せるかはテーマが決める。</p>
 */
export class JbSelect extends JbElement {

	static override tag = 'jb-select';

	static override properties: PropertyDeclarations = {
		name: { type: String },
		label: { type: String },
		value: { type: String },
		options: { attribute: false },
		placeholder: { type: String },
		hint: { type: String },
		error: { type: String, reflect: true, attribute: 'jb-error' },
		required: { type: Boolean, reflect: true, attribute: 'jb-required' },
		disabled: { type: Boolean, reflect: true, attribute: 'jb-disabled' }
	};

	/** 項目名 */
	name = '';

	/** ラベル */
	label = '';

	/** 選ばれている値 */
	value = '';

	/** 選択肢 */
	options: SelectOption[] = [];

	/** 未選択時の文言 */
	placeholder = '';

	/** 補足 */
	hint = '';

	/** エラー文言 */
	error = '';

	/** 必須 */
	required = false;

	/** 使用不可 */
	disabled = false;

	/**
	 * 選ばれたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 */
	handleChange (event: Event): void {

		this.value = (event.target as HTMLSelectElement).value;
		this.dispatchEvent(new CustomEvent('jb-change', {
			detail: { name: this.name, value: this.value },
			bubbles: true,
			composed: true
		}));

	}

}

customElements.define('jb-select', JbSelect);
