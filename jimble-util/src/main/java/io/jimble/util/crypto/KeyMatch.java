package io.jimble.util.crypto;

import java.util.Objects;

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
 * <p>
 * <b>record ではない（D-173）。</b>record にすると項目を足す道が無くなる——
 * 正準コンストラクタが変わるので、<b>「何番目の古い鍵で読めたか」を
 * あとから足せない</b>。いまは 2 項目だが、鍵を3本以上並べる運用に
 * なったときに要る。作るのはこのパッケージの中だけなので、
 * <b>コンストラクタは公開していない</b>。
 * </p>
 */
public final class KeyMatch {

	/** 読めた値 */
	private final String value;

	/** いま書くのに使っている鍵で読めたか */
	private final boolean current;

	/**
	 * @param value		読めた値
	 * @param current	いま書くのに使っている鍵で読めた場合 = true
	 */
	KeyMatch (String value, boolean current) {

		this.value = value;
		this.current = current;

	}

	/**
	 * 読めた値
	 *
	 * @return	読めた値
	 */
	public String value () {

		return value;

	}

	/**
	 * いま書くのに使っている鍵で読めたか
	 *
	 * @return	いまの鍵なら true
	 */
	public boolean current () {

		return current;

	}

	/**
	 * 古い鍵で読めたか
	 *
	 * @return	古い鍵なら true（書き直しが要る）
	 */
	public boolean isStale () {

		return !current;

	}

	@Override
	public boolean equals (Object other) {

		if (this == other) {
			return true;
		}

		if (!(other instanceof KeyMatch that)) {
			return false;
		}

		return current == that.current && Objects.equals(value, that.value);

	}

	@Override
	public int hashCode () {

		return Objects.hash(value, current);

	}

	/**
	 * 文字列にする
	 *
	 * <p>
	 * <b>読めた値そのものは出さない。</b>ここに入っているのは
	 * セッションや署名付き Cookie の中身で、<b>ログに出れば漏れる</b>。
	 * </p>
	 *
	 * @return	鍵がどちらだったかだけ
	 */
	@Override
	public String toString () {

		return "KeyMatch(" + (current ? "current" : "stale") + ")";

	}

}
