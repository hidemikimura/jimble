package io.jimble.web.validation;

import io.jimble.util.data.Data;
import io.jimble.web.http.HttpException;

/**
 * 入力の検査に通らなかった（422。要件 D-196）
 *
 * <p>
 * {@link ValidationRules#validate(io.jimble.db.DB, Data)} と {@link Validator#validate} が投げる。
 * 何もしなければ枠組みが 422 で返し、本文は {@code {"validation": {項目: [メッセージ]}}}
 * （{@link ValidationExecutor} と同じ形）。一覧を自分で扱いたいときは {@code errors(...)} を使う。
 * </p>
 *
 * @since 2.0.0
 */
public class ValidationException extends HttpException {

	private static final long serialVersionUID = 1L;

	/* エラーの一覧（errors(...) と同じ形） */
	private final transient Data errors;

	/**
	 * コンストラクタ
	 *
	 * @param errors	エラーの一覧
	 */
	public ValidationException (Data errors) {

		super(ValidationExecutor.STATUS_CODE, "入力に誤りがあります: " + errors.keySet());
		this.errors = errors;

	}

	/**
	 * エラーの一覧
	 *
	 * @return	{@code errors(...)} と同じ形
	 */
	public Data errors () {

		return errors;

	}

}
