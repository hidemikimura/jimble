import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { MenuItem } from '../core/types.js';

/**
 * ドロップダウン（行の「…」メニュー）
 *
 * <p>
 * 開閉と「どれが選ばれたか」だけを持つ。選ばれたら {@code jb-select} を投げて閉じる。
 * 外側を押したときと Escape でも閉じる。
 * </p>
 */
export class JbMenu extends JbElement {

	static override tag = 'jb-menu';

	static override properties: PropertyDeclarations = {
		items: { attribute: false },
		label: { type: String },
		icon: { type: String },
		align: { type: String, reflect: true, attribute: 'jb-align' },
		disabled: { type: Boolean, reflect: true, attribute: 'jb-disabled' },
		open: { type: Boolean, reflect: true, attribute: 'jb-open' },
		anchor: { state: true },
		placement: { state: true }
	};

	/** 項目 */
	items: MenuItem[] = [];

	/** ボタンの文言 */
	label = '';

	/** ボタンの飾り（jb-icon の名前） */
	icon = 'more';

	/** どちら側に開くか */
	align: 'start' | 'end' = 'end';

	/** 使用不可 */
	disabled = false;

	/** 開いているか */
	open = false;

	/**
	 * 開いたときのボタンの位置（画面の左上からの位置）
	 *
	 * <p>
	 * 一覧の中で開くと、表の枠に切り取られてしまう。
	 * それを避けるため、テーマは一覧を <b>position: fixed</b> で置き、この位置を使う。
	 * </p>
	 */
	anchor = { top: 0, bottom: 0, left: 0, right: 0 };

	/** 下と上のどちらに開くか */
	placement: 'down' | 'up' = 'down';

	/* 外側を押したときの処理 */
	#onOutside = (event: Event): void => {

		if (!this.open) {
			return;
		}
		if (event.composedPath().includes(this)) {
			return;
		}
		this.close();

	};

	/* 画面が動いたら閉じる（位置がずれるため） */
	#onScroll = (): void => {

		if (this.open) {
			this.close();
		}

	};

	/* Escape が押されたときの処理 */
	#onKey = (event: KeyboardEvent): void => {

		if (this.open && event.key === 'Escape') {
			this.close();
		}

	};

	override connectedCallback (): void {

		super.connectedCallback();
		document.addEventListener('pointerdown', this.#onOutside, true);
		document.addEventListener('keydown', this.#onKey);
		window.addEventListener('scroll', this.#onScroll, true);
		window.addEventListener('resize', this.#onScroll);

	}

	override disconnectedCallback (): void {

		document.removeEventListener('pointerdown', this.#onOutside, true);
		document.removeEventListener('keydown', this.#onKey);
		window.removeEventListener('scroll', this.#onScroll, true);
		window.removeEventListener('resize', this.#onScroll);
		super.disconnectedCallback();

	}

	/**
	 * 開け閉めする（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 */
	handleToggle (event: Event): void {

		event.stopPropagation();
		if (this.disabled) {
			return;
		}
		if (this.open) {
			this.close();
			return;
		}

		/* 同時に 2 つ開かない */
		for (const other of document.querySelectorAll('jb-menu')) {
			if (other !== this) {
				(other as JbMenu).close();
			}
		}
		this.measure();
		this.open = true;

	}

	/**
	 * ボタンの位置と、上下どちらに開くかを測る
	 */
	measure (): void {

		const rect = this.getBoundingClientRect();
		this.anchor = { top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right };

		/* 1 項目およそ 40px、余白 16px として、下に入らなければ上に開く */
		const height = this.items.filter((item) => item.divider !== true).length * 40 + 16;
		this.placement = rect.bottom + height > window.innerHeight && rect.top > height ? 'up' : 'down';

	}

	/**
	 * 項目が選ばれたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param item 項目
	 * @param event イベント
	 */
	handleSelect (item: MenuItem, event: Event): void {

		event.stopPropagation();
		if (item.disabled === true || item.divider === true) {
			return;
		}

		this.close();
		this.dispatchEvent(new CustomEvent('jb-select', {
			detail: { value: item.value },
			bubbles: true,
			composed: true
		}));

	}

	/** 閉じる */
	close (): void {

		if (!this.open) {
			return;
		}
		this.open = false;
		this.dispatchEvent(new CustomEvent('jb-close', { bubbles: true, composed: true }));

	}

}

customElements.define('jb-menu', JbMenu);
