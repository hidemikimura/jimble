import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { InputType } from '../core/types.js';

/** 入力イベントの内容 */
export interface InputEventDetail {
	name: string;
	value: string;
}

/**
 * 入力欄
 *
 * <p>
 * ラベル・必須・エラーなどの「状態」を持つだけで、どう見せるかはテーマが決める。
 * 入力のたびに {@code jb-input}、確定時に {@code jb-change} を投げる。
 * </p>
 */
export class JbInput extends JbElement {

	static override tag = 'jb-input';

	static override properties: PropertyDeclarations = {
		name: { type: String },
		type: { type: String },
		label: { type: String },
		placeholder: { type: String },
		hint: { type: String },
		error: { type: String, reflect: true, attribute: 'jb-error' },
		value: { type: String },
		required: { type: Boolean, reflect: true, attribute: 'jb-required' },
		disabled: { type: Boolean, reflect: true, attribute: 'jb-disabled' },
		multiline: { type: Boolean, reflect: true, attribute: 'jb-multiline' }
	};

	/** 項目名 */
	name = '';

	/** 入力の種類 */
	type: InputType = 'text';

	/** ラベル */
	label = '';

	/** 入力例 */
	placeholder = '';

	/** 補足 */
	hint = '';

	/** エラー文言 */
	error = '';

	/** 値 */
	value = '';

	/** 必須 */
	required = false;

	/** 使用不可 */
	disabled = false;

	/** 複数行 */
	multiline = false;

	/**
	 * 入力中の処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 */
	handleInput (event: Event): void {

		this.value = (event.target as HTMLInputElement).value;
		this.emit('jb-input');

	}

	/**
	 * 確定時の処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 */
	handleChange (event: Event): void {

		this.value = (event.target as HTMLInputElement).value;
		this.emit('jb-change');

	}

	/**
	 * イベントを投げる
	 *
	 * @param type 種別
	 */
	protected emit (type: string): void {

		this.dispatchEvent(new CustomEvent<InputEventDetail>(type, {
			detail: { name: this.name, value: this.value },
			bubbles: true,
			composed: true
		}));

	}

}

customElements.define('jb-input', JbInput);
