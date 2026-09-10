import { Theme, component } from '../../dist/index.js';
import type { JbInput } from '../../dist/index.js';

/**
 * ecx テーマ（社内デザイン）
 *
 * <p>
 * original を継承し、<b>差分だけ</b>を書いている。
 * コンポーネントの実装も、書いていないテンプレートも、すべて original のまま。
 * </p>
 *
 * <ol>
 *   <li>トークンの上書き … 色と角丸を変えるだけ。マークアップには一切触らない</li>
 *   <li>CSS の追加 … 親の CSS の後ろに足される（後勝ち）</li>
 *   <li>テンプレートの差し替え … 必須の印を「※」ではなくバッジにする社内ルール</li>
 * </ol>
 */
export default Theme.extend('original', {

	name: 'ecx',

	/* 1. トークンの上書き */
	tokens: {
		'color-bg': '#f1f6f5',
		'color-primary': '#0f766e',
		'color-focus': 'rgba(15, 118, 110, .22)',
		'color-border': '#cbdad7',
		'radius': '4px',
		'font-size-title': '24px'
	},

	components: {

		/* 2. CSS の追加（親のスタイルはそのまま残る） */
		'jb-stack': {
			styles: `
				:host([jb-surface]) {
					border-left: 3px solid var(--jb-color-primary);
					box-shadow: 0 1px 2px rgba(15, 23, 42, .06);
				}
			`
		},

		'jb-button': {
			styles: `
				button { font-weight: 700; letter-spacing: .02em; }
			`
		},

		/* 3. テンプレートの差し替え（必須の印をバッジにする） */
		'jb-input': component<JbInput>({
			styles: `
				.badge {
					margin-left: 6px;
					padding: 1px 6px;
					border-radius: 2px;
					background: var(--jb-color-primary);
					color: #fff;
					font-size: 10px;
					font-weight: 700;
					letter-spacing: .04em;
				}
			`,
			template: (el, html) => html`
				<label>
					${el.label
						? html`<span class="label">${el.label}${el.required ? html`<span class="badge">必須</span>` : ''}</span>`
						: ''}
					${el.multiline
						? html`<textarea
								.value=${el.value ?? ''}
								placeholder=${el.placeholder ?? ''}
								?disabled=${el.disabled}
								@input=${(e: Event) => el.handleInput(e)}
								@change=${(e: Event) => el.handleChange(e)}></textarea>`
						: html`<input
								type=${el.type || 'text'}
								.value=${el.value ?? ''}
								placeholder=${el.placeholder ?? ''}
								?disabled=${el.disabled}
								@input=${(e: Event) => el.handleInput(e)}
								@change=${(e: Event) => el.handleChange(e)}>`}
					${el.error
						? html`<span class="message error">${el.error}</span>`
						: el.hint ? html`<span class="message">${el.hint}</span>` : ''}
				</label>
			`
		})

	}

});
