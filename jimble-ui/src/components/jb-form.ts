import { JbElement } from './base.js';

/**
 * フォーム
 *
 * <p>
 * 入力欄はそれぞれ別の Shadow DOM の中にいるため、
 * ブラウザ既定の「Enter で送信」は効かない。ここで拾って {@code jb-submit} を投げる。
 * 複数行入力の中の Enter は改行なので送信しない。
 * </p>
 */
export class JbForm extends JbElement {

	static override tag = 'jb-form';

	override connectedCallback (): void {

		super.connectedCallback();
		this.addEventListener('keydown', this.handleKeydown);

	}

	override disconnectedCallback (): void {

		this.removeEventListener('keydown', this.handleKeydown);
		super.disconnectedCallback();

	}

	/**
	 * 送信する（テーマのテンプレートから呼ぶこともできる）
	 */
	submit (): void {

		this.dispatchEvent(new CustomEvent('jb-submit', { bubbles: true, composed: true }));

	}

	/**
	 * Enter を拾う
	 *
	 * @param event イベント
	 */
	protected handleKeydown = (event: KeyboardEvent): void => {

		if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
			return;
		}

		const target = event.composedPath()[0];
		if (target instanceof HTMLTextAreaElement || target instanceof HTMLButtonElement) {
			return;
		}

		event.preventDefault();
		this.submit();

	};

}

customElements.define('jb-form', JbForm);
