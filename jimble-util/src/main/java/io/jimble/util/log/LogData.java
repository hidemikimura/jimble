package io.jimble.util.log;

import io.jimble.util.data.Data;

/**
 * ログに載せるデータ
 *
 * <p>
 * {@link Data#toString()} は要約表示にした（要件 F-D-26 / O-3）。
 * ログに1行出しただけで中身が全部出るのを避けるためである。
 * </p>
 *
 * <p>
 * <b>ログのデータだけは別扱いにする。</b>これは「出すために作ったもの」であって、
 * うっかり出るものではない。テキスト形式のログ（logback の pattern レイアウト）は
 * 引数を {@code toString()} で描くので、ここを要約にすると
 * <b>開発中のログから情報が消える。</b>
 * </p>
 *
 * <p>
 * JSON 形式のログ（{@code LogbackJsonEncoder}。要件 F-U-06）は
 * {@code Data} として中身を取り出すので、こちらは影響を受けない。
 * </p>
 */
public final class LogData extends Data {

	private static final long serialVersionUID = 1L;

	/**
	 * {@inheritDoc}
	 *
	 * <p>ログ用なので JSON をそのまま出す。</p>
	 */
	@Override
	public String toString () {

		return getJsonString();

	}

}
