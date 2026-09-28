package io.jimble.util.internal;

import io.jimble.util.exception.CodeException;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 検査例外を非検査例外に包む（内部。要件 D-197）
 *
 * <p>
 * 2.0 で、利用者が呼ぶメソッドから {@code throws Exception} を外した。
 * 中で起きた検査例外は、元の例外を cause に残したまま、ここで非検査例外に包む。
 * </p>
 */
public final class Unchecked {

	private Unchecked () {
	}

	/**
	 * 非検査例外にする
	 *
	 * <ul>
	 *   <li>非検査例外 … そのまま</li>
	 *   <li>{@link IOException} … {@link UncheckedIOException}</li>
	 *   <li>{@link InterruptedException} … 割り込みの印を戻してから {@link CodeException}</li>
	 *   <li>それ以外 … {@link CodeException}（コード {@code code}）</li>
	 * </ul>
	 *
	 * @param code	コード
	 * @param what	何をしていたか（メッセージの頭に付ける）
	 * @param ex	元の例外
	 * @return	投げる例外
	 */
	public static RuntimeException of (String code, String what, Exception ex) {

		if (ex instanceof RuntimeException runtime) {
			return runtime;
		}

		if (ex instanceof IOException io) {
			return new UncheckedIOException(what + ": " + io.getMessage(), io);
		}

		if (ex instanceof InterruptedException) {
			Thread.currentThread().interrupt();
		}

		return new CodeException(code, what + ": " + ex.getMessage(), ex);

	}

}
