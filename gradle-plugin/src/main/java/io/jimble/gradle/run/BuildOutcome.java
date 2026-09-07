package io.jimble.gradle.run;

import java.util.List;

/**
 * ビルドの結果（要件 F-X-02 / F-X-05）
 *
 * @param success	成功した場合 = true
 * @param output	出力（成功時は空）
 */
record BuildOutcome(boolean success, List<String> output) {

	/** 成功 */
	static final BuildOutcome OK = new BuildOutcome(true, List.of());

	/**
	 * 出力を1つの文字列にする
	 *
	 * @return	文字列
	 */
	String text () {

		return String.join("\n", output);

	}

}
