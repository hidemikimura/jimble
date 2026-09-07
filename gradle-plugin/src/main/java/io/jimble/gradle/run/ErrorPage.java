package io.jimble.gradle.run;

/**
 * うまくいかなかったことをブラウザに出す（要件 F-X-05）
 *
 * <p>
 * <b>移送元はコンソールにしか出していなかった。</b>
 * 画面が変わらないので、まずリロードし、それでも変わらないので
 * ターミナルを見に行く、という順番になる。
 * <b>直したい人が見ている場所に出す</b>のが要点である。
 * </p>
 */
final class ErrorPage {

	private ErrorPage () {}

	/**
	 * 作る
	 *
	 * @param title	見出し
	 * @param detail	中身（そのまま出す）
	 * @return	HTML
	 */
	static String html (String title, String detail) {

		return """
			<!doctype html>
			<html lang="ja">
			<head>
			<meta charset="utf-8">
			<title>%1$s - jimbleRun</title>
			<style>
			:root { color-scheme: light dark; }
			body { margin: 0; padding: 2rem; font: 14px/1.7 ui-monospace, SFMono-Regular, Menlo, monospace; }
			h1 { font-size: 1.1rem; margin: 0 0 .25rem; }
			p.hint { margin: 0 0 1.5rem; opacity: .7; }
			pre { padding: 1rem; overflow-x: auto; border-radius: 6px;
			      background: rgba(127,127,127,.12); white-space: pre; }
			</style>
			</head>
			<body>
			<h1>%1$s</h1>
			<p class="hint">直したらこのページをリロードしてください。作り直してから応えます。</p>
			<pre>%2$s</pre>
			</body>
			</html>
			""".formatted(escape(title), escape(detail));

	}

	/**
	 * HTML として無害にする
	 *
	 * @param text	文字列
	 * @return	無害にしたもの
	 */
	private static String escape (String text) {

		if (text == null) {
			return "";
		}

		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");

	}

}
