package io.jimble.util.crypto;

import java.util.ArrayList;
import java.util.List;

/**
 * 入れ替え中の鍵の並び（要件 NF-S-09 / D-128）
 *
 * <p>
 * <b>先頭が「いま書くのに使う鍵」で、残りは「読むときだけ試す鍵」である。</b>
 * この順番が全部である——並べ方を場所ごとに決めると、
 * <b>どこかで古い鍵が書き込みに使われる</b>。
 * </p>
 *
 * <h2>空は落とす</h2>
 * <p>
 * 設定は環境変数から入ることが多く（{@code secret = ${?COOKIE_SECRET}}）、
 * <b>渡し忘れると空文字が並びに混ざる</b>。空の鍵で署名や暗号化はできないので、
 * ここで落としておかないと<b>使うところで初めて落ちる</b>。
 * </p>
 */
public final class Secrets {

	private Secrets () {}

	/**
	 * 並びを作る
	 *
	 * @param current	いま書くのに使う鍵。空なら鍵なし
	 * @param previous	古い鍵（読むときだけ試す）
	 * @return	並び（先頭が新しいもの）。鍵が1つも無ければ空
	 */
	public static List<String> of (String current, List<String> previous) {

		List<String> result = new ArrayList<>();

		add(result, current);

		if (previous != null) {
			for (String secret : previous) {
				add(result, secret);
			}
		}

		return List.copyOf(result);

	}

	/**
	 * 足す（空と重複は落とす）
	 *
	 * @param result	並び
	 * @param secret	鍵
	 */
	private static void add (List<String> result, String secret) {

		if (secret == null || secret.isEmpty()) {
			return;
		}

		/*
		 * <b>同じ鍵を2度試さない。</b>入れ替えが終わったあと
		 * previous_secrets を消し忘れて secret と同じ値が残る、はよくある。
		 * 落とさないと<b>古い鍵で読めた</b>と数えてしまい、
		 * 「まだ終わっていない」と永久に言い続ける
		 */
		if (result.contains(secret)) {
			return;
		}

		result.add(secret);

	}

}
