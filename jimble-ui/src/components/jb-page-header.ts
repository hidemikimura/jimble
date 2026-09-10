import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';

/**
 * 画面の見出し（題名・説明・右側のボタン）
 *
 * <p>持っているのは文言だけ。差し込み口は 3 つ。</p>
 *
 * <ul>
 *   <li>{@code breadcrumb} … 題名の上</li>
 *   <li>既定 … 右側（ボタンなど）</li>
 *   <li>{@code below} … 一番下（検索欄やタブなど）</li>
 * </ul>
 */
export class JbPageHeader extends JbElement {

	static override tag = 'jb-page-header';

	static override properties: PropertyDeclarations = {
		heading: { type: String },
		description: { type: String }
	};

	/** 題名 */
	heading = '';

	/** 説明 */
	description = '';

}

customElements.define('jb-page-header', JbPageHeader);
