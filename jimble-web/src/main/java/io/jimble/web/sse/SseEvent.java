package io.jimble.web.sse;

import io.jimble.util.data.Data;

/**
 * SSE の1件（要件 F-W-21）
 *
 * <p>
 * 送られる形はこうなる。
 * </p>
 *
 * <pre>
 * id: 42
 * event: progress
 * data: {"percent":10}
 *
 * </pre>
 *
 * <p>
 * <b>空行で1件の終わり</b>である。
 * 本文に改行が含まれていたら、行ごとに {@code data:} を付ける
 * （付けないと、そこで1件が切れたことになる）。
 * </p>
 *
 * @param name		種別（{@code event:}）。null なら付けない
 * @param data		本文（{@code data:}）
 * @param id		識別子（{@code id:}）。null なら付けない
 * @param retry		再接続までの待ち（{@code retry:} ミリ秒）。0 以下なら付けない
 * @param comment	コメント（{@code :}）。キープアライブに使う
 */
public record SseEvent(String name, String data, String id, long retry, String comment) {

	/** キープアライブ用のコメントだけの1件 */
	public static final SseEvent KEEP_ALIVE = new SseEvent(null, null, null, 0, "");

	/**
	 * 本文だけ
	 *
	 * @param data	本文
	 * @return	1件
	 */
	public static SseEvent of (String data) {

		return new SseEvent(null, data, null, 0, null);

	}

	/**
	 * 種別と本文
	 *
	 * @param name	種別
	 * @param data	本文
	 * @return	1件
	 */
	public static SseEvent of (String name, String data) {

		return new SseEvent(name, data, null, 0, null);

	}

	/**
	 * 種別と JSON
	 *
	 * @param name	種別
	 * @param data	本文
	 * @return	1件
	 */
	public static SseEvent json (String name, Data data) {

		return new SseEvent(name, data == null ? "{}" : data.getJsonString(), null, 0, null);

	}

	/**
	 * 送る形に組み立てる
	 *
	 * @return	組み立てたもの
	 */
	public String format () {

		StringBuilder builder = new StringBuilder();

		if (comment != null) {
			// ":" だけの行はコメント。受け取り側は読み飛ばす
			appendLines(builder, ":", comment);
		}

		if (id != null && !id.isEmpty()) {
			builder.append("id: ").append(oneLine(id)).append('\n');
		}

		if (name != null && !name.isEmpty()) {
			builder.append("event: ").append(oneLine(name)).append('\n');
		}

		if (retry > 0) {
			builder.append("retry: ").append(retry).append('\n');
		}

		if (data != null) {
			appendLines(builder, "data:", data);
		}

		// 空行で1件の終わり
		builder.append('\n');

		return builder.toString();

	}

	/**
	 * 複数行を1行ずつ書く
	 *
	 * <p>
	 * <b>ここを手を抜くと、本文に改行があるだけで壊れる。</b>
	 * 改行は「次のフィールド」の合図なので、
	 * そのまま流すと 2件目・3件目として読まれる。
	 * </p>
	 *
	 * @param builder	組み立て先
	 * @param prefix	行の頭（{@code data:} など）
	 * @param text		中身
	 */
	private static void appendLines (StringBuilder builder, String prefix, String text) {

		if (text.isEmpty()) {
			builder.append(prefix).append('\n');
			return;
		}

		// \r\n と \r も1つの改行として扱う
		for (String line : text.split("\r\n|\r|\n", -1)) {
			builder.append(prefix).append(' ').append(line).append('\n');
		}

	}

	/**
	 * 改行を落とす
	 *
	 * @param text	文字列
	 * @return	落としたもの
	 */
	private static String oneLine (String text) {

		return text.replaceAll("[\r\n]", " ");

	}

}
