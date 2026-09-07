package io.jimble.util.function;

/**
 * 例外ありRunnable
 */
@FunctionalInterface
public interface ExceptionRunnable {

	/**
	 * 実行
	 *
	 * @throws Exception	エラー
	 */
	void run() throws Exception;

}
