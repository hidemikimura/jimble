/**
 * CSS ファイルを JavaScript モジュールに変換する
 *
 * <p>
 * import() で遅延読み込みできるようにするため、CSS を文字列 export のモジュールにする。
 * fetch を使わないので file:// でも動く。
 * </p>
 *
 * 使い方: node tools/css-to-module.mjs <入力.css> <出力.js> "<説明>"
 */
import { readFileSync, writeFileSync } from 'node:fs';

const [input, output, description = 'CSS'] = process.argv.slice(2);

if (input == null || output == null) {
	console.error('使い方: node tools/css-to-module.mjs <入力.css> <出力.js> "<説明>"');
	process.exit(1);
}

const css = readFileSync(input, 'utf8')
	.replace(/^@charset[^;]*;/, '')
	.replace(/\/\*# sourceMappingURL=.*?\*\//g, '')
	.trim();

const escaped = css.replace(/\\/g, '\\\\').replace(/`/g, '\\`').replace(/\$\{/g, '\\${');

writeFileSync(output, `/**\n * ${description}\n *\n * <p>tools/ のスクリプトで生成している。手で編集しない。</p>\n */\nexport default \`${escaped}\`;\n`);

console.log(output + ' を生成しました (' + Math.round(css.length / 1024) + 'KB)');
