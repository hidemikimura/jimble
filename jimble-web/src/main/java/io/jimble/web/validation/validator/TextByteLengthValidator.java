package io.jimble.web.validation.validator;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 文字列バイト長
 */
public class TextByteLengthValidator implements IValidator {

	/* 設定 */
	private final Data settings = new Data();

	/**
	 * 最小値設定
	 *
	 * @param min	最小値
	 * @return	TextByteLengthValidator
	 */
	public TextByteLengthValidator min (int min) {

		this.settings.put("min", min);
		return this;

	}

	/**
	 * 最大値設定
	 *
	 * @param max	最大値
	 * @return	TextByteLengthValidator
	 */
	public TextByteLengthValidator max (int max) {

		this.settings.put("max", max);
		return this;

	}

	/**
	 * 文字コード設定
	 *
	 * @param charset	文字コード
	 * @return	TextByteLengthValidator
	 */
	public TextByteLengthValidator charset (Charset charset) {

		this.settings.put("charset", charset);
		return this;

	}

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

		if (!this.settings.containsKey("charset")) {
			this.settings.put("charset", StandardCharsets.UTF_8);
		}

		Charset charset = this.settings.getObject("charset");
		int length = str.getBytes(charset).length;

		if (this.settings.containsKey("min")) {
			if (length < this.settings.getInt("min")) {
				return false;
			}
		}

		if (this.settings.containsKey("max")) {
			if (this.settings.getInt("max") < length) {
				return false;
			}
		}

		return true;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ValidationErrorType errorType() {

		return ValidationErrorType.TextByteLength;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return this.settings;

	}

}
