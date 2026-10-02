package io.jimble.util.internal.json.decoder.stream.util;

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

	/** 入れ子の深さの上限（D-229） */
	int MAX_DEPTH = 512;

	/**
	 * オブジェクトか配列に1段入る（D-229）
	 *
	 * <p>
	 * <b>深さに上限を置く。</b>かつては上限が無く、{@code [[[[...]]]]}（400KB ほど）で
	 * {@code StackOverflowError} になった。{@code Request.body()} は JsonParseException しか受けないので、
	 * 500 と大きなスタックトレースのログを、要求のたびに出させられた。
	 * </p>
	 *
	 * @throws io.jimble.util.json.JsonParseException	上限を超えたとき
	 */
	void enterNesting ();

	/**
	 * オブジェクトか配列から1段出る
	 */
	void exitNesting ();

}
