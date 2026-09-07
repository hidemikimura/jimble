package io.jimble.docs;

import java.util.Set;

/**
 * コードに色を付ける（生成時に済ませる）
 *
 * <p>
 * <b>読む人のブラウザで JavaScript を動かさない。</b>
 * highlight.js を CDN から読むのが普通のやり方だが、
 * それは<b>ドキュメントを読むだけで外部のサーバーに繋がる</b>ということである。
 * jimble は依存を減らすことを目的に掲げているのに、
 * その説明ページが CDN に依存しているのはおかしい。
 * </p>
 *
 * <p>
 * 完全な字句解析ではない。<b>キーワード・文字列・コメント・数値</b>だけを見る。
 * 読むための色付けにはそれで足りる。
 * </p>
 */
final class Highlighter {

	/** Java と Kotlin のキーワード */
	private static final Set<String> KEYWORDS = Set.of(
		"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class"
		, "const", "continue", "default", "do", "double", "else", "enum", "extends", "final"
		, "finally", "float", "for", "goto", "if", "implements", "import", "instanceof"
		, "int", "interface", "long", "native", "new", "package", "private", "protected"
		, "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized"
		, "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
		, "var", "record", "sealed", "permits", "yield", "true", "false", "null"
		// Kotlin（Gradle のビルドスクリプト用）
		, "fun", "val", "object", "when", "is", "in", "by", "tasks", "dependencies", "plugins"
	);

	/** シェルの組み込み */
	private static final Set<String> SHELL = Set.of(
		"cd", "curl", "echo", "export", "mkdir", "rm", "cp", "mv", "cat", "grep", "open"
	);

	private Highlighter () {}

	/**
	 * 色を付ける
	 *
	 * @param code		コード
	 * @param language	言語
	 * @return	HTML
	 */
	static String highlight (String code, String language) {

		if (language == null) {
			return escape(code);
		}

		return switch (language) {
			case "java", "kotlin", "kts", "jte" -> highlightCode(code, "//", true);
			case "json" -> highlightCode(code, null, false);
			case "conf", "hocon", "properties", "toml" -> highlightCode(code, "#", false);
			case "sql" -> highlightCode(code, "--", false);
			case "bash", "sh", "shell" -> highlightShell(code);
			default -> escape(code);
		};

	}

	/**
	 * コードに色を付ける
	 *
	 * @param code			コード
	 * @param lineComment	行コメントの印。無ければ null
	 * @param blockComment	ブロックコメントがあるか
	 * @return	HTML
	 */
	private static String highlightCode (String code, String lineComment, boolean blockComment) {

		StringBuilder out = new StringBuilder();

		int i = 0;
		int length = code.length();

		while (i < length) {

			char c = code.charAt(i);

			// 行コメント
			if (lineComment != null && code.startsWith(lineComment, i)) {
				int end = code.indexOf('\n', i);
				end = end < 0 ? length : end;
				span(out, "c", code.substring(i, end));
				i = end;
				continue;
			}

			// ブロックコメント
			if (blockComment && code.startsWith("/*", i)) {
				int end = code.indexOf("*/", i + 2);
				end = end < 0 ? length : end + 2;
				span(out, "c", code.substring(i, end));
				i = end;
				continue;
			}

			// 文字列
			if (c == '"' || c == '\'') {
				int end = endOfString(code, i, c);
				span(out, "s", code.substring(i, end));
				i = end;
				continue;
			}

			// 注釈（@Override など）
			if (c == '@' && i + 1 < length && Character.isLetter(code.charAt(i + 1))) {
				int end = i + 1;
				while (end < length && Character.isLetterOrDigit(code.charAt(end))) {
					end++;
				}
				span(out, "a", code.substring(i, end));
				i = end;
				continue;
			}

			// 数値
			if (Character.isDigit(c) && (i == 0 || !isWordChar(code.charAt(i - 1)))) {
				int end = i;
				while (end < length && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '.')) {
					end++;
				}
				span(out, "n", code.substring(i, end));
				i = end;
				continue;
			}

			// 語
			if (isWordChar(c)) {

				int end = i;
				while (end < length && isWordChar(code.charAt(end))) {
					end++;
				}

				String word = code.substring(i, end);

				if (KEYWORDS.contains(word)) {
					span(out, "k", word);
				} else {
					out.append(escape(word));
				}

				i = end;
				continue;

			}

			out.append(escape(String.valueOf(c)));
			i++;

		}

		return out.toString();

	}

	/**
	 * シェルに色を付ける
	 *
	 * @param code	コード
	 * @return	HTML
	 */
	private static String highlightShell (String code) {

		StringBuilder out = new StringBuilder();

		for (String line : code.split("\n", -1)) {

			if (!out.isEmpty()) {
				out.append('\n');
			}

			String trimmed = line.stripLeading();

			if (trimmed.startsWith("#")) {
				span(out, "c", line);
				continue;
			}

			// 先頭の語だけ色を付ける
			int start = line.length() - trimmed.length();
			int end = start;
			while (end < line.length() && !Character.isWhitespace(line.charAt(end))) {
				end++;
			}

			String first = line.substring(start, end);

			out.append(escape(line.substring(0, start)));

			if (SHELL.contains(first) || first.startsWith("./") || first.equals("gradle") || first.equals("jimble")) {
				span(out, "k", first);
			} else {
				out.append(escape(first));
			}

			out.append(escape(line.substring(end)));

		}

		return out.toString();

	}

	/**
	 * 文字列の終わり
	 *
	 * @param code	コード
	 * @param start	始まり
	 * @param quote	引用符
	 * @return	終わりの次
	 */
	private static int endOfString (String code, int start, char quote) {

		// テキストブロック
		if (quote == '"' && code.startsWith("\"\"\"", start)) {

			int end = code.indexOf("\"\"\"", start + 3);

			return end < 0 ? code.length() : end + 3;

		}

		int i = start + 1;

		while (i < code.length()) {

			char c = code.charAt(i);

			if (c == '\\') {
				i += 2;
				continue;
			}

			if (c == quote) {
				return i + 1;
			}

			// 閉じないまま行が終わったら、そこで切る
			if (c == '\n') {
				return i;
			}

			i++;

		}

		return code.length();

	}

	/**
	 * 語を作る文字か
	 *
	 * @param c	文字
	 * @return	語を作る場合 = true
	 */
	private static boolean isWordChar (char c) {

		return Character.isLetterOrDigit(c) || c == '_' || c == '$';

	}

	/**
	 * 囲む
	 *
	 * @param out	出し先
	 * @param type	種別
	 * @param text	中身
	 */
	private static void span (StringBuilder out, String type, String text) {

		out.append("<span class=\"h-").append(type).append("\">")
			.append(escape(text))
			.append("</span>");

	}

	/**
	 * HTML として無害にする
	 *
	 * @param text	文字列
	 * @return	無害にしたもの
	 */
	static String escape (String text) {

		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");

	}

}
