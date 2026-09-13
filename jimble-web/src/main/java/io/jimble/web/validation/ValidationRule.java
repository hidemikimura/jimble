package io.jimble.web.validation;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationError;
import io.jimble.web.validation.validator.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * validation rule
 */
public class ValidationRule {

	/* validator list */
	private final List<IValidator> validatorList = new ArrayList<>();

	/* insertリクエスト時に必須かどうか */
	private boolean insertRequired = false;

	/**
	 * insertリクエスト時に必須に設定する
	 *
	 * @return	ValidationRule
	 */
	public ValidationRule insertRequired() {

		this.insertRequired = true;
		return this;

	}

	/**
	 * insertリクエスト時に必須かどうかを取得する
	 *
	 * @return	insertリクエスト時に必須かどうかを取得する
	 */
	public boolean isInsertRequired() {

		return this.insertRequired;

	}

	/**
	 * validation
	 *
	 * @param db				DB
	 * @param req				リクエスト情報
	 * @param isInsertRequest	登録リクエスト判定
	 * @param value				値
	 * @return	結果
	 */
	public ValidationResult validate (DB db, Data req, boolean isInsertRequest, Object value) throws CodeException {

		ValidationResult result = new ValidationResult();

		for (IValidator validator : validatorList) {
			if (!validator.validate(db, req, isInsertRequest, value)) {
				ValidationError error = createError(value, validator);
				result.setValidationError(error);
				return result;
			}
		}

		return result;

	}

	/**
	 * validationエラー情報を作成する
	 *
	 * @param value		値
	 * @param validator	validator
	 * @return	validationエラー情報
	 */
	private ValidationError createError (Object value, IValidator validator) {

		return new ValidationError(validator.errorType(), validator.settings(), value);

	}

	// region カスタムチェック

	/**
	 * カスタムチェック
	 *
	 * @param validator	IValidator
	 * @return	ValidationRule
	 */
	public ValidationRule custom (IValidator validator) {

		this.validatorList.add(validator);
		return this;

	}

	// endregion

	// region 空チェック

	/**
	 * 空チェック
	 *
	 * @return	ValidationRule
	 */
	public ValidationRule empty () {

		validatorList.add(new EmptyValidator());
		return this;

	}

	/**
	 * 必須チェック（{@link #empty()} の別名）
	 *
	 * <p>
	 * {@code empty()} は「空を許す」と読めてしまうが、実際は<b>空だとエラー</b>にする。
	 * 読み違えると必須チェックが外れたまま気づかないので、素直な名前を足した。
	 * 既存コードのために {@code empty()} も残す。
	 * </p>
	 *
	 * @return	ValidationRule
	 */
	public ValidationRule required () {

		return empty();

	}

	// endregion

	// region 文字列長

	/**
	 * 文字列長
	 *
	 * @param min	最小値
	 * @param max	最大値
	 * @return	ValidationRule
	 */
	public ValidationRule textLength (int min, int max) {

		validatorList.add(new TextLengthValidator()
			.min(min)
			.max(max)
		);
		return this;

	}

	/**
	 * 文字列長（最小値のみ）
	 *
	 * @param min	最小値
	 * @return	ValidationRule
	 */
	public ValidationRule textLengthMin (int min) {

		validatorList.add(new TextLengthValidator()
			.min(min)
		);
		return this;

	}

	/**
	 * 文字列長（最大値のみ）
	 *
	 * @param max	最大値
	 * @return	ValidationRule
	 */
	public ValidationRule textLengthMax (int max) {

		validatorList.add(new TextLengthValidator()
			.max(max)
		);
		return this;

	}

	// endregion

	// region 文字列Byte長

	/**
	 * 文字列Byte長
	 *
	 * @param min		最小値
	 * @param max		最大値
	 * @param charset	文字コード
	 * @return	ValidationRule
	 */
	public ValidationRule textByteLength (int min, int max, Charset charset) {

		validatorList.add(new TextByteLengthValidator()
			.min(min)
			.max(max)
			.charset(charset)
		);
		return this;

	}

	/**
	 * 文字列Byte長（UTF-8）
	 *
	 * @param min		最小値
	 * @param max		最大値
	 * @return	ValidationRule
	 */
	public ValidationRule textByteLength (int min, int max) {

		validatorList.add(new TextByteLengthValidator()
			.min(min)
			.max(max)
		);
		return this;

	}

	/**
	 * 文字列Byte長（UTF-8、最小値のみ）
	 *
	 * @param min		最小値
	 * @return	ValidationRule
	 */
	public ValidationRule textByteLengthMin (int min) {

		validatorList.add(new TextByteLengthValidator()
			.min(min)
		);
		return this;

	}

	/**
	 * 文字列Byte長（最小値のみ）
	 *
	 * @param min		最小値
	 * @param charset	文字コード
	 * @return	ValidationRule
	 */
	public ValidationRule textByteLengthMin (int min, Charset charset) {

		validatorList.add(new TextByteLengthValidator()
			.min(min)
			.charset(charset)
		);
		return this;

	}

	/**
	 * 文字列Byte長（UTF-8、最大値のみ）
	 *
	 * @param max		最大値
	 * @return	ValidationRule
	 */
	public ValidationRule textByteLengthMax (int max) {

		validatorList.add(new TextByteLengthValidator()
			.max(max)
		);
		return this;

	}

	/**
	 * 文字列Byte長（最大値のみ）
	 *
	 * @param max		最大値
	 * @param charset	文字コード
	 * @return	ValidationRule
	 */
	public ValidationRule textByteLengthMax (int max, Charset charset) {

		validatorList.add(new TextByteLengthValidator()
			.max(max)
			.charset(charset)
		);
		return this;

	}

	// endregion

	// region 真偽値

	/**
	 * 真偽値
	 *
	 * @return	ValidationRule
	 */
	public ValidationRule bool () {

		validatorList.add(new BooleanValidator());
		return this;

	}

	// endregion

	// region 整数

	/**
	 * 整数
	 *
	 * @return	ValidationRule
	 */
	public ValidationRule integer () {

		validatorList.add(new IntegerValidator());
		return this;

	}

	/**
	 * 整数
	 *
	 * @param min	最小値
	 * @param max	最大値
	 * @return	ValidationRule
	 */
	public ValidationRule integer (long min, long max) {

		validatorList.add(new IntegerValidator()
			.min(min)
			.max(max)
		);
		return this;

	}

	/**
	 * 整数（最小値のみ）
	 *
	 * @param min	最小値
	 * @return	ValidationRule
	 */
	public ValidationRule integerMin (long min) {

		validatorList.add(new IntegerValidator()
			.min(min)
		);
		return this;

	}

	/**
	 * 整数（最大値のみ）
	 *
	 * @param max	最大値
	 * @return	ValidationRule
	 */
	public ValidationRule integerMax (long max) {

		validatorList.add(new IntegerValidator()
			.max(max)
		);
		return this;

	}

	// endregion

	// region 数値

	/**
	 * 数値
	 *
	 * @return	ValidationRule
	 */
	public ValidationRule number () {

		validatorList.add(new NumberValidator());
		return this;

	}

	/**
	 * 数値
	 *
	 * @param min	最小値
	 * @param max	最大値
	 * @return	ValidationRule
	 */
	public ValidationRule number (double min, double max) {

		validatorList.add(new NumberValidator()
			.min(min)
			.max(max)
		);
		return this;

	}

	/**
	 * 数値（最小値のみ）
	 *
	 * @param min	最小値
	 * @return	ValidationRule
	 */
	public ValidationRule numberMin (double min) {

		validatorList.add(new NumberValidator()
			.min(min)
		);
		return this;

	}

	/**
	 * 数値（最大値のみ）
	 *
	 * @param max	最大値
	 * @return	ValidationRule
	 */
	public ValidationRule numberMax (double max) {

		validatorList.add(new NumberValidator()
			.max(max)
		);
		return this;

	}

	// endregion

	// region メールアドレス

	/**
	 * メールアドレス
	 *
	 * @return	ValidationRule
	 */
	public ValidationRule email () {

		validatorList.add(new EmailValidator());
		return this;

	}

	// endregion

	// region URL

	/**
	 * URL
	 *
	 * @return	ValidationRule
	 */
	public ValidationRule url () {

		validatorList.add(new UrlValidator());
		return this;

	}

	// endregion

	// region ドメイン

	/**
	 * ドメイン
	 *
	 * @return	ValidationRule
	 */
	public ValidationRule domain () {

		validatorList.add(new DomainValidator());
		return this;

	}

	// endregion

	// region 文字種別

	/**
	 * 文字種別
	 *
	 * @param types			文字種別
	 * @param characters	カスタム文字
	 * @return	ValidationRule
	 */
	public ValidationRule characterType (CharacterTypeValidator.CharacterType[] types, Character[] characters) {

		validatorList.add(new CharacterTypeValidator()
			.characterType(types)
			.character(characters)
		);
		return this;

	}

	/**
	 * 文字種別
	 *
	 * @param types			文字種別
	 * @return	ValidationRule
	 */
	public ValidationRule characterType (CharacterTypeValidator.CharacterType[] types) {

		validatorList.add(new CharacterTypeValidator()
			.characterType(types)
		);
		return this;

	}

	/**
	 * 文字種別
	 *
	 * @param characters	カスタム文字
	 * @return	ValidationRule
	 */
	public ValidationRule characterType (Character[] characters) {

		validatorList.add(new CharacterTypeValidator()
			.character(characters)
		);
		return this;

	}

	// endregion

	// region 日時

	/**
	 * 日時
	 *
	 * @param format	フォーマット
	 * @return	ValidationRule
	 */
	public ValidationRule date (String format) {

		validatorList.add(new DateValidator()
			.format(format)
		);
		return this;

	}

	/**
	 * 日時
	 *
	 * @return	ValidationRule
	 */
	public ValidationRule date () {

		validatorList.add(new DateValidator());
		return this;

	}

	// endregion

	// region 正規表現

	/**
	 * 正規表現
	 *
	 * @param regex	正規表現
	 * @return	ValidationRule
	 */
	public ValidationRule regex (String regex) {

		validatorList.add(new RegexValidator()
			.regex(regex)
		);
		return this;

	}

	// endregion

	// region enum

	/**
	 * enum
	 *
	 * @param enumType	enum
	 * @return	ValidationRule
	 */
	public ValidationRule enumType (Class<? extends Enum<?>> enumType) {

		validatorList.add(new EnumValidator()
			.enumType(enumType)
		);
		return this;

	}

	// endregion

}
