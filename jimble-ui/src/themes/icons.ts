import { isDev, suggest, warn } from '../core/dev.js';

/**
 * 飾り（アイコン）の図形
 *
 * <p>
 * テーマ層の持ち物。コンポーネントは名前しか知らない。
 * 値は SVG の path の {@code d} 属性の並びで、24×24 の枠に線だけで描く前提。
 * 独自のアイコンを足したいときは、テーマ側で {@code ICONS} に足せばよい。
 * </p>
 */
export const ICONS: Record<string, string[]> = {

	/* 操作 */
	'more': ['M5 12h.01', 'M12 12h.01', 'M19 12h.01'],
	'more-vertical': ['M12 5v.01', 'M12 12v.01', 'M12 19v.01'],
	'menu': ['M3 6h18', 'M3 12h18', 'M3 18h18'],
	'close': ['M6 6l12 12', 'M18 6L6 18'],
	'plus': ['M12 5v14', 'M5 12h14'],
	'minus': ['M5 12h14'],
	'check': ['M4 12l5 5L20 6'],
	'search': ['M17 11a6 6 0 11-12 0 6 6 0 0112 0z', 'M15.5 15.5L20 20'],
	'edit': ['M4 20h4l10-10-4-4L4 16v4z', 'M14 6l4 4'],
	'trash': ['M4 7h16', 'M9 7V4h6v3', 'M6 7l1 13h10l1-13'],
	'copy': ['M9 9h11v11H9z', 'M5 15H4V4h11v1'],
	'save': ['M5 4h11l3 3v13H5z', 'M8 4v5h7', 'M8 13h8v7'],
	'filter': ['M4 5h16l-6 7v6l-4 2v-8L4 5z'],
	'refresh': ['M20 12a8 8 0 11-2.3-5.6', 'M20 4v5h-5'],
	'download': ['M12 4v11', 'M7 11l5 5 5-5', 'M4 20h16'],
	'upload': ['M12 20V9', 'M7 13l5-5 5 5', 'M4 4h16'],
	'drag': ['M9 6h.01', 'M9 12h.01', 'M9 18h.01', 'M15 6h.01', 'M15 12h.01', 'M15 18h.01'],
	'external': ['M14 4h6v6', 'M20 4l-9 9', 'M18 14v5a1 1 0 01-1 1H5a1 1 0 01-1-1V7a1 1 0 011-1h5'],

	/* 向き */
	'chevron-up': ['M6 15l6-6 6 6'],
	'chevron-down': ['M6 9l6 6 6-6'],
	'chevron-left': ['M15 6l-6 6 6 6'],
	'chevron-right': ['M9 6l6 6-6 6'],
	'arrow-up': ['M12 19V5', 'M6 11l6-6 6 6'],
	'arrow-down': ['M12 5v14', 'M6 13l6 6 6-6'],
	'arrow-left': ['M19 12H5', 'M11 6l-6 6 6 6'],
	'arrow-right': ['M5 12h14', 'M13 6l6 6-6 6'],

	/* 画面・区分 */
	'home': ['M4 11l8-7 8 7', 'M6 10v10h12V10'],
	'list': ['M8 6h13', 'M8 12h13', 'M8 18h13', 'M3 6h.01', 'M3 12h.01', 'M3 18h.01'],
	'grid': ['M4 4h7v7H4z', 'M13 4h7v7h-7z', 'M4 13h7v7H4z', 'M13 13h7v7h-7z'],
	'box': ['M12 3l8 4.5v9L12 21l-8-4.5v-9L12 3z', 'M4 7.5l8 4.5 8-4.5', 'M12 12v9'],
	'cart': ['M3 5h2l2.5 10h10L20 8H6', 'M9 20h.01', 'M17 20h.01'],
	'chart': ['M4 20V10', 'M10 20V4', 'M16 20v-7', 'M22 20H2'],
	'calendar': ['M4 6h16v14H4z', 'M4 10h16', 'M8 3v4', 'M16 3v4'],
	'settings': [
		'M12 9a3 3 0 100 6 3 3 0 000-6z',
		'M4 12h2', 'M18 12h2', 'M12 4v2', 'M12 18v2',
		'M6.5 6.5L8 8', 'M16 16l1.5 1.5', 'M17.5 6.5L16 8', 'M8 16l-1.5 1.5'
	],

	/* 人 */
	'user': ['M12 4a4 4 0 100 8 4 4 0 000-8z', 'M4 21c0-4 4-6 8-6s8 2 8 6'],
	'users': ['M9 4a4 4 0 100 8 4 4 0 000-8z', 'M2 21c0-4 3-6 7-6s7 2 7 6', 'M17 5a3.5 3.5 0 010 7', 'M18 15c2 1 3 2.5 3 6'],
	'login': ['M9 12h11', 'M16 8l4 4-4 4', 'M11 4H6a2 2 0 00-2 2v12a2 2 0 002 2h5'],
	'logout': ['M15 12H4', 'M8 8l-4 4 4 4', 'M13 4h5a2 2 0 012 2v12a2 2 0 01-2 2h-5'],
	'lock': ['M5 11h14v9H5z', 'M8 11V8a4 4 0 018 0v3'],

	/* 知らせ */
	'info': ['M12 4a8 8 0 100 16 8 8 0 000-16z', 'M12 11v5', 'M12 8h.01'],
	'warning': ['M12 4l9 16H3l9-16z', 'M12 10v4', 'M12 17h.01'],
	'error': ['M12 4a8 8 0 100 16 8 8 0 000-16z', 'M12 8v5', 'M12 16h.01'],
	'success': ['M12 4a8 8 0 100 16 8 8 0 000-16z', 'M8 12l3 3 5-6'],
	'star': ['M12 4l2.4 5 5.6.8-4 3.9 1 5.5-5-2.7-5 2.7 1-5.5-4-3.9 5.6-.8L12 4z'],
	'eye': ['M2 12s4-6 10-6 10 6 10 6-4 6-10 6-10-6-10-6z', 'M12 9.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5z'],
	'help': ['M12 4a8 8 0 100 16 8 8 0 000-16z', 'M9.5 9.5a2.5 2.5 0 114 2c-.9.6-1.5 1.2-1.5 2.5', 'M12 17h.01']

};

/** 使える名前 */
export const ICON_NAMES: string[] = Object.keys(ICONS);

/* もう知らせた名前（毎回の描画で騒がないため） */
const REPORTED = new Set<string>();

/**
 * 名前から図形を引く
 *
 * <p>知らない名前のときは「?」を返し、開発モードでは似た名前を教える。</p>
 *
 * @param name 名前
 * @return path の d 属性の並び
 */
export function iconPaths (name: string): string[] {

	const found = ICONS[name];
	if (found != null) {
		return found;
	}

	if (isDev() && name !== '' && !REPORTED.has(name)) {
		REPORTED.add(name);
		warn('アイコン "' + name + '" はありません', suggest(name, ICON_NAMES));
	}

	return ICONS['help'] as string[];

}
