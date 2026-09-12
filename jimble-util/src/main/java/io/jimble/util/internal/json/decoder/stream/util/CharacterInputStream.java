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

}
