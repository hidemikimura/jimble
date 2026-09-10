import { JbElement } from './base.js';
import type { PropertyDeclarations, PropertyValues } from '../../vendor/lit.js';
import type { Align, Justify, Size, Width } from '../core/types.js';

/**
 * 積み上げレイアウト
 *
 * <p>
 * 縦積み・横並び・間隔・余白をトークン名で受け取る。
 * それを CSS にするか utility クラスにするかはテーマが決める。
 * </p>
 */
export class JbStack extends JbElement {

	static override tag = 'jb-stack';

	/*
	 * 属性名にはすべて jb- を付ける。
	 * align / width などは HTML の既定スタイルシートが解釈してしまう予約語のため。
	 */
	static override properties: PropertyDeclarations = {
		direction: { type: String, reflect: true, attribute: 'jb-direction' },
		gap: { type: String, reflect: true, attribute: 'jb-gap' },
		pad: { type: String, reflect: true, attribute: 'jb-pad' },
		align: { type: String, reflect: true, attribute: 'jb-align' },
		justify: { type: String, reflect: true, attribute: 'jb-justify' },
		wrap: { type: Boolean, reflect: true, attribute: 'jb-wrap' },
		width: { type: String, reflect: true, attribute: 'jb-width' },
		surface: { type: Boolean, reflect: true, attribute: 'jb-surface' }
	};

	/** 並べる向き */
	direction: 'row' | 'column' = 'column';

	/** 間隔 */
	gap: Size | undefined = undefined;

	/** 内側の余白 */
	pad: Size | undefined = undefined;

	/** 交差軸の揃え */
	align: Align | undefined = undefined;

	/** 主軸の揃え */
	justify: Justify | undefined = undefined;

	/** 折り返し */
	wrap = false;

	/** 幅 */
	width: Width | undefined = undefined;

	/** カード状の面にするか */
	surface = false;

	override updated (changed: PropertyValues): void {

		super.updated(changed);

		if (!changed.has('width')) {
			return;
		}

		/*
		 * トークン（sm / md / lg / full）はテーマの CSS が受け持つ。
		 * '220px' のような長さが来たときだけ、ここで max-width として当てる。
		 */
		const custom = this.width != null && !['sm', 'md', 'lg', 'full'].includes(this.width);

		this.style.width = custom ? '100%' : '';
		this.style.maxWidth = custom ? (this.width as string) : '';

	}

}

customElements.define('jb-stack', JbStack);
