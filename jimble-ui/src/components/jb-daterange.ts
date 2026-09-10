import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';

/**
 * 日付範囲
 *
 * <p>開始と終了の 2 つ。変わったら {@code jb-change} に両方を載せて投げる。</p>
 */
export class JbDateRange extends JbElement {

	static override tag = 'jb-daterange';

	static override properties: PropertyDeclarations = {
		name: { type: String },
		label: { type: String },
		from: { type: String },
		to: { type: String },
		hint: { type: String },
		error: { type: String, reflect: true, attribute: 'jb-error' },
		required: { type: Boolean, reflect: true, attribute: 'jb-required' },
		disabled: { type: Boolean, reflect: true, attribute: 'jb-disabled' }
	};

	/** 項目名 */
	name = '';

	/** ラベル */
	label = '';

	/** 開始（YYYY-MM-DD） */
	from = '';

	/** 終了（YYYY-MM-DD） */
	to = '';

	/** 補足 */
	hint = '';

	/** エラー文言 */
	error = '';

	/** 必須 */
	required = false;

	/** 使用不可 */
	disabled = false;

	/**
	 * 開始が変わったときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 */
	handleFrom (event: Event): void {

		this.from = (event.target as HTMLInputElement).value;
		this.emit();

	}

	/**
	 * 終了が変わったときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 */
	handleTo (event: Event): void {

		this.to = (event.target as HTMLInputElement).value;
		this.emit();

	}

	/**
	 * イベントを投げる
	 */
	protected emit (): void {

		this.dispatchEvent(new CustomEvent('jb-change', {
			detail: { name: this.name, from: this.from, to: this.to },
			bubbles: true,
			composed: true
		}));

	}

}

customElements.define('jb-daterange', JbDateRange);
