import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { DialogSize } from '../core/types.js';

/**
 * ダイアログ
 *
 * <p>
 * 中身は既定スロット、下部のボタンは {@code footer} スロットに入る。
 * 閉じる操作は {@code jb-close} を投げるだけで、開閉の状態は画面側が持つ。
 * </p>
 */
export class JbDialog extends JbElement {

	static override tag = 'jb-dialog';

	static override properties: PropertyDeclarations = {
		open: { type: Boolean, reflect: true, attribute: 'jb-open' },
		title: { type: String },
		size: { type: String, reflect: true, attribute: 'jb-size' },
		closable: { type: Boolean, reflect: true, attribute: 'jb-closable' }
	};

	/** 開いているか */
	open = false;

	/** 見出し */
	override title = '';

	/** 大きさ */
	size: DialogSize = 'md';

	/** 閉じるボタンを出すか */
	closable = true;

	override connectedCallback (): void {

		super.connectedCallback();
		this.addEventListener('keydown', this.handleKeydown);

	}

	override disconnectedCallback (): void {

		this.removeEventListener('keydown', this.handleKeydown);
		super.disconnectedCallback();

	}

	/**
	 * 閉じる（テーマのテンプレートから呼ぶ）
	 */
	handleClose = (): void => {

		if (!this.closable) {
			return;
		}
		this.dispatchEvent(new CustomEvent('jb-close', { bubbles: true, composed: true }));

	};

	/**
	 * Esc で閉じる
	 *
	 * @param event イベント
	 */
	protected handleKeydown = (event: KeyboardEvent): void => {

		if (event.key === 'Escape') {
			this.handleClose();
		}

	};

}

customElements.define('jb-dialog', JbDialog);
