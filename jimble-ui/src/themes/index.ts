/**
 * テーマ一式
 *
 * <p>
 * 読み込むだけで登録される。CSS 本体は遅延読み込みなので、
 * ここで全部 import しても、選ばなかったテーマの CSS は取りに行かない。
 * </p>
 */
import original from './original.js';
import bootstrap5 from './bootstrap5.js';
import tailwindDark from './tailwind-dark.js';

export { original, bootstrap5, tailwindDark };

/** 既定テーマ名 */
export const DEFAULT_THEME = 'original';
