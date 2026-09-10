/**
 * tailwind-dark テーマ用の Tailwind 設定
 *
 * テーマファイルに書かれたクラス名だけを走査して CSS を作る。
 */
module.exports = {
	content: ['./src/themes/tailwind-dark.ts'],
	theme: { extend: {} },
	plugins: []
};
