package io.jimble.web.validation.error;

import io.jimble.util.data.Data;

/**
 * 検証で落ちた1件
 *
 * <p>
 * <b>どの検証が</b>（{@link #errorType()}）、<b>どの設定で</b>（{@link #settings()}）、
 * <b>どの値を</b>（{@link #value()}）落としたかを持つ。
 * 文言はここに入れない——{@link io.jimble.web.validation.ValidationMessages} の仕事である。
 * </p>
 *
 * <p>
 * <b>public フィールドではない（D-173）。</b>フィールドはアクセサに置き換えられないので、
 * 1.0 のあとは<b>検証も、遅延計算も、防御的コピーも入れられなくなる</b>。
 * 作るのは枠組みだけなので、コンストラクタは公開していない。
 * </p>
 */
public final class ValidationError {

	/** どの検証で落ちたか */
	private final ValidationErrorType errorType;

	/** その検証の設定（min / max など） */
	private final Data settings;

	/** 落ちた値 */
	private final Object value;

	/**
	 * 作る
	 *
	 * @param errorType	どの検証で落ちたか（null なら {@link ValidationErrorType#Unknown}）
	 * @param settings	その検証の設定（null なら空）
	 * @param value		落ちた値
	 */
	public ValidationError (ValidationErrorType errorType, Data settings, Object value) {

		this.errorType = errorType == null ? ValidationErrorType.Unknown : errorType;
		this.settings = settings == null ? new Data() : settings;
		this.value = value;

	}

	/**
	 * どの検証で落ちたか
	 *
	 * @return	種別（null にはならない）
	 */
	public ValidationErrorType errorType () {

		return errorType;

	}

	/**
	 * その検証の設定
	 *
	 * @return	設定（null にはならない）
	 */
	public Data settings () {

		return settings;

	}

	/**
	 * 落ちた値
	 *
	 * @return	値
	 */
	public Object value () {

		return value;

	}

	@Override
	public String toString () {

		return "ValidationError(" + errorType + ", " + settings + ")";

	}

}
