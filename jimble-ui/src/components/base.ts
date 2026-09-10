import { LitElement, html, svg, nothing } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { currentTheme, subscribeTheme } from '../core/theme.js';
import { isDev, report, JimbleError } from '../core/dev.js';

/**
 * コンポーネント基底
 *
 * <p>
 * 見た目は一切持たない。<b>状態（プロパティ）と振る舞い（イベント）だけ</b>を持ち、
 * HTML と CSS は現在のテーマから受け取る。
 * </p>
 */
export abstract class JbElement extends LitElement {

	/** タグ名（サブクラスで指定する） */
	static tag = '';

	/* テーマ購読の解除関数 */
	#unsubscribe: (() => void) | null = null;

	override createRenderRoot (): HTMLElement | DocumentFragment {

		const root = super.createRenderRoot();
		this.#applyStyles(root);
		return root;

	}

	override connectedCallback (): void {

		super.connectedCallback();
		this.#unsubscribe = subscribeTheme(() => {
			this.#applyStyles(this.renderRoot);
			this.requestUpdate();
		});

	}

	override disconnectedCallback (): void {

		this.#unsubscribe?.();
		this.#unsubscribe = null;
		super.disconnectedCallback();

	}

	override render (): unknown {

		const tag = (this.constructor as typeof JbElement).tag;
		const template = currentTheme().template(tag);

		if (template == null) {
			return this.#missing(tag);
		}

		try {
			return template(this, html, svg);
		} catch (error) {
			report(error, tag + ' のテンプレート');
			return isDev() ? errorBox(tag + ' のテンプレートで例外が発生しました') : nothing;
		}

	}

	/**
	 * テーマにテンプレートが無いことを知らせる
	 *
	 * <p>黙って空白にしない。これが一番見つけにくい壊れ方なので、必ず目に見える形で出す。</p>
	 *
	 * @param tag タグ名
	 * @return テンプレート
	 */
	#missing (tag: string): unknown {

		const theme = currentTheme().name;
		const message = 'テーマ "' + theme + '" に ' + tag + ' のテンプレートがありません';

		report(new JimbleError(message, 'そのテーマの components に "' + tag + '" を足すか、テンプレートを持つテーマを継承してください'));

		return isDev() ? errorBox(message) : nothing;

	}

	/**
	 * テーマのスタイルを適用する
	 *
	 * @param root シャドウルート
	 */
	#applyStyles (root: HTMLElement | DocumentFragment): void {

		const shadow = root as ShadowRoot;
		if (shadow?.adoptedStyleSheets == null) {
			return;
		}
		shadow.adoptedStyleSheets = currentTheme().styleSheets((this.constructor as typeof JbElement).tag);

	}

}

/**
 * 画面に出す赤いエラー表示
 *
 * @param message 文言
 * @return テンプレート
 */
function errorBox (message: string): TemplateResult {

	return html`<span style="display:inline-block;padding:2px 6px;border:1px solid #f43f5e;border-radius:4px;background:#fff1f2;color:#9f1239;font:11px/1.5 ui-monospace,monospace">${message}</span>`;

}
