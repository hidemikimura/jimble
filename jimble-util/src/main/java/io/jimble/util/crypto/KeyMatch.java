package io.jimble.util.crypto;

/**
 * どの鍵で読めたか（鍵の入れ替え用）
 *
 * <p>
 * 鍵を入れ替えている最中は、<b>新しい鍵で書き、古い鍵でも読める</b>ようにする。
 * このとき「読めた」だけでは足りない——<b>古い鍵で読めたのなら、
 * その場で新しい鍵に書き直さないと入れ替えが終わらない</b>。
 * </p>
 *
 * <p>
 * <b>そして、終わったことが分からないと古い鍵を捨てられない。</b>
 * 「もう誰も古い鍵を使っていない」を数えるために、
 * 読めたかどうかとは別に<b>どちらの鍵だったか</b>を返す。
 * </p>
 *
 * @param value		読めた値
 * @param current	いま書くのに使っている鍵で読めた場合 = true
 */
public record KeyMatch (String value, boolean current) {

	/**
	 * 古い鍵で読めたか
	 *
	 * @return	古い鍵なら true（書き直しが要る）
	 */
	public boolean isStale () {

		return !current;

	}

}
