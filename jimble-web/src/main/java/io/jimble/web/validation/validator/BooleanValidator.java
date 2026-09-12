package io.jimble.web.validation.validator;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

import java.util.Locale;
import java.util.Set;

/**
 * 真偽値
 *
 * <p>
 * <b>{@code true} / {@code false} / {@code 1} / {@code 0} だけを通す（要件 D-161）。</b>
 * 大文字小文字は問わない。
 * </p>
 *
 * <h2>以前は何も検証していなかった</h2>
 * <p>
 * 判定は {@code getBooleanObject()} が {@code null} を返すかどうかで見ていたが、
 * <b>この関数は文字列に対して {@code null} を返さない</b>——
 * {@code "true"} と {@code "1"} なら {@code TRUE}、
 * <b>それ以外はすべて {@code FALSE}</b> である。
 * つまり {@code yes} も {@code はい} も {@code -1} も<b>全部通っていた</b>。
 * </p>
 *
 * <p>
 * <b>しかも読み出し側が同じ規則である。</b>
 * {@code yes} は検証を通ったうえで {@code false} として保存される——
 * <b>「はい」と答えた人が「いいえ」になる</b>。例外もログも出ない。
 * </p>
 */
public class BooleanValidator implements IValidator {

	/**
	 * 通す綴り
	 *
	 * <p>
	 * <b>ここを増やすときは、読み出し側（{@code Data.getBooleanObject}）も一緒に見ること。</b>
	 * 片方だけ増やすと、<b>検証を通った値が反対の意味で保存される</b>。
	 * </p>
	 */
	private static final Set<String> SPELLINGS = Set.of("true", "false", "1", "0");

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean validate(DB db, Data req, boolean isInsertRequest, Object value) throws CodeException {

		if (value == null) {
			return true;
		}

		String str = String.valueOf(value);
		if (str.isEmpty()) {
			return true;
		}

		return SPELLINGS.contains(str.toLowerCase(Locale.ROOT));

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ValidationErrorType errorType() {

		return ValidationErrorType.Boolean;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return new Data();

	}

}
