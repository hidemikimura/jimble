package io.jimble.util.internal;

/**
 * JSON の形だけを見る（内部。要件 D-195）
 */
public final class JsonShape {

	private JsonShape () {
	}

	/**
	 * 括弧が閉じていて、後ろに余分なものが無いか（要件 D-195）
	 *
	 * <p>
	 * 読み手は寛容で、閉じ括弧の無い {"a":1 も {"a":1} として読んでしまう——途中で切れた本文が
	 * 「途中までの値」として通る。文字列の中を飛ばしながら括弧の深さを数えて、
	 * 最初の値が閉じたところで終わっているかだけを見る（中身の文法は読み手に任せる）。
	 * </p>
	 *
	 * @param text	JSON
	 * @return	閉じていて余分なものが無ければ true（オブジェクトでも配列でもないものも true）
	 */
	public static boolean isComplete (String text) {

		int depth = 0;
		boolean inString = false;
		boolean started = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (inString) {
				if (c == '\\') {
					i++;
				} else if (c == '"') {
					inString = false;
				}
				continue;
			}
			if (started && depth == 0) {
				if (!Character.isWhitespace(c)) {
					return false;       // 値のあとに余分なもの
				}
				continue;
			}
			switch (c) {
				case '"' -> inString = true;
				case '{', '[' -> { depth++; started = true; }
				case '}', ']' -> {
					depth--;
					if (depth < 0) {
						return false;
					}
				}
				default -> {
					if (!started && !Character.isWhitespace(c)) {
						return true;    // オブジェクトでも配列でもない（null など）。読み手に任せる
					}
				}
			}
		}
		return !inString && depth == 0;

	}

}
