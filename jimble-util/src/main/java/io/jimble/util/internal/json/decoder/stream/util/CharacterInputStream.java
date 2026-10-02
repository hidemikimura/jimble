package io.jimble.util.internal.json.decoder.stream.util;

/**
 * 文字列入力ストリームクラス.
 * 
 * @author DN
 */
public class CharacterInputStream implements IInputStream {

	/** バッファ. */
	private final char[] buffer;

	/* バッファ長 */
	private final int length;

	/** バッファ位置. */
	private int pos = 0;

	/**
	 * コンストラクタ.
	 * 
	 * @param src ソース
	 */
	public CharacterInputStream(String src) {
		buffer = src.toCharArray();
		length = buffer.length;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int readInt() {
		return (pos < 0 || pos >= length) ? -1 : buffer[pos++];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void returnPos() {
		this.pos--;
	}


	/* 入れ子の深さ（D-229） */
	private int nesting = 0;

	@Override
	public void enterNesting () {

		if (++nesting > MAX_DEPTH) {
			throw new io.jimble.util.json.JsonParseException("JSON の入れ子が深すぎます（%d 段まで）".formatted(MAX_DEPTH), null);
		}

	}

	@Override
	public void exitNesting () {

		nesting--;

	}

}
