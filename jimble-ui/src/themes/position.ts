import type { JbMenu } from '../components/jb-menu.js';

/**
 * ドロップダウンを画面のどこに置くかを決める
 *
 * <p>
 * 表の中で開いても枠に切り取られないよう、一覧は {@code position: fixed} で置く。
 * その {@code top / bottom / left / right} をここで組み立てる。
 * </p>
 *
 * @param menu ドロップダウン
 * @param gap ボタンとの隙間
 * @return style 属性に入れる文字列
 */
export function menuPosition (menu: JbMenu, gap = 4): string {

	const anchor = menu.anchor;

	const vertical = menu.placement === 'up'
		? 'bottom:' + String(Math.round(window.innerHeight - anchor.top + gap)) + 'px'
		: 'top:' + String(Math.round(anchor.bottom + gap)) + 'px';

	const horizontal = menu.align === 'start'
		? 'left:' + String(Math.round(anchor.left)) + 'px'
		: 'right:' + String(Math.round(window.innerWidth - anchor.right)) + 'px';

	return vertical + ';' + horizontal;

}
