import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { ToastVariant } from '../core/types.js';

/**
 * 知らせ（トースト）
 *
 * <p>{@link notify} が作って body 直下に置く。画面のコードは直接触らない。</p>
 */
export class JbToast extends JbElement {

	static override tag = 'jb-toast';

	static override properties: PropertyDeclarations = {
		message: { type: String },
		variant: { type: String, reflect: true, attribute: 'jb-variant' }
	};

	/** 文言 */
	message = '';

	/** 種類 */
	variant: ToastVariant = 'info';

	/**
	 * 閉じる（テーマのテンプレートから呼ぶ）
	 */
	handleClose (): void {

		this.dispatchEvent(new CustomEvent('jb-close', { bubbles: true, composed: true }));
		this.remove();

	}

}

customElements.define('jb-toast', JbToast);
