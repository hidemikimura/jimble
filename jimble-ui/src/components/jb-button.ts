import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { ButtonVariant } from '../core/types.js';

/**
 * ボタン
 *
 * <p>押されたら {@code jb-click} を投げる。処理はアプリ側の Builder が受け取る。</p>
 */
export class JbButton extends JbElement {

	static override tag = 'jb-button';

	static override properties: PropertyDeclarations = {
		label: { type: String },
		variant: { type: String, reflect: true, attribute: 'jb-variant' },
		disabled: { type: Boolean, reflect: true, attribute: 'jb-disabled' },
		loading: { type: Boolean, reflect: true, attribute: 'jb-loading' },
		icon: { type: String, reflect: true, attribute: 'jb-icon' }
	};

	/** 文言 */
	label = '';

	/** 種類 */
	variant: ButtonVariant = 'default';

	/** 使用不可 */
	disabled = false;

	/** 処理中 */
	loading = false;

	/** 飾り（jb-icon の名前）。空なら出さない */
	icon = '';

	/**
	 * 押されたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 */
	handleClick (event: Event): void {

		event.stopPropagation();
		if (this.disabled || this.loading) {
			return;
		}
		this.dispatchEvent(new CustomEvent('jb-click', { bubbles: true, composed: true }));

	}

}

customElements.define('jb-button', JbButton);
