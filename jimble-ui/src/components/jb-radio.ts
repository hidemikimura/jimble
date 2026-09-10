import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { SelectOption } from '../core/types.js';

/**
 * ラジオ（1 組をまとめて扱う）
 */
export class JbRadio extends JbElement {

	static override tag = 'jb-radio';

	static override properties: PropertyDeclarations = {
		name: { type: String },
		label: { type: String },
		value: { type: String },
		options: { attribute: false },
		hint: { type: String },
		error: { type: String, reflect: true, attribute: 'jb-error' },
		required: { type: Boolean, reflect: true, attribute: 'jb-required' },
		disabled: { type: Boolean, reflect: true, attribute: 'jb-disabled' },
		inline: { type: Boolean, reflect: true, attribute: 'jb-inline' }
	};

	/** 項目名 */
	name = '';

	/** ラベル */
	label = '';

	/** 選ばれている値 */
	value = '';

	/** 選択肢 */
	options: SelectOption[] = [];

	/** 補足 */
	hint = '';

	/** エラー文言 */
	error = '';

	/** 必須 */
	required = false;

	/** 使用不可 */
	disabled = false;

	/** 横に並べるか */
	inline = false;

	/**
	 * 選ばれたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 */
	handleChange (event: Event): void {

		this.value = (event.target as HTMLInputElement).value;
		this.dispatchEvent(new CustomEvent('jb-change', {
			detail: { name: this.name, value: this.value },
			bubbles: true,
			composed: true
		}));

	}

}

customElements.define('jb-radio', JbRadio);
