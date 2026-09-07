package io.jimble.util.json.decoder.stream.util;

/**
 * 入力インターフェイス.
 * 
 * @author DN
 */
public interface IInputStream {

	/**
	 * 読み込み.
	 * 
	 * @return int
	 */
	public int readInt();

	/**
	 * 位置を一つ戻す.
	 */
	public void returnPos();

}
