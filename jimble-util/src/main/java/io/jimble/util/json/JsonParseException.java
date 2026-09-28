package io.jimble.util.json;

/**
 * JSON を読めなかった（要件 D-195）
 *
 * <p>
 * {@link Dson#decodes} と {@code Data.fromJsonString} が投げる。
 * 1.x は壊れた JSON で<b>黙って {@code null}</b>（エラーは捨てた {@code Dson} の中）を返していたので、
 * 「送った値が全部無かった」ことになり、原因が見えなかった。元の例外は cause に残す。
 * </p>
 *
 * @since 2.0.0
 */
public class JsonParseException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 * @param cause		元の例外（無ければ null）
	 */
	public JsonParseException (String message, Throwable cause) {

		super(message, cause);

	}

}
