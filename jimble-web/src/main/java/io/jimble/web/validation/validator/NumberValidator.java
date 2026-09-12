package io.jimble.web.validation.validator;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

import java.util.regex.Pattern;

/**
 * 数値
 *
 * <h2>{@code Double.parseDouble} をそのまま信じない（要件 D-161）</h2>
 * <p>
 * あれは Java のソースに書ける綴りを全部受け付けるので、
 * <b>{@code NaN} {@code Infinity} {@code 1d} {@code 1f} {@code 0x1p3} が通り、
 * 前後の空白も黙って落とす</b>。
 * </p>
 *
 * <p>
 * <b>{@code NaN} がいちばん悪い。</b>{@code NaN} との比較は
 * {@code <} も {@code >} も必ず false になるので、
 * <b>{@code min} も {@code max} もすり抜ける</b>——
 * {@code number(0, 10)} と書いてあるのに、範囲の検査ごと無効になる。
 * </p>
 *
 * <p>
 * <b>整数のほうと揃える意味もある。</b>{@code integer()} は
 * {@code Long.parseLong} なので前後の空白を落とさない。
 * 同じ {@code " 1 "} が<b>片方だけ通る</b>のは説明が付かない。
 * </p>
 */
public class NumberValidator implements IValidator {

	/* 設定 */
	private final Data settings = new Data();

	/**
	 * 数値の綴り
	 *
	 * <p>
	 * {@code 1} {@code -1.5} {@code +.5} {@code 1e3} は通り、
	 * {@code NaN} {@code Infinity} {@code 1d} {@code 0x10} {@code " 1 "} は通らない。
	 * </p>
	 */
	private static final Pattern NUMBER = Pattern.compile(
		"[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");

	/**
	 * 最小値設定
	 *
	 * @param min	最小値
	 * @return	NumberValidator
	 */
	public NumberValidator min (double min) {

		this.settings.put("min", min);
		return this;

	}

	/**
	 * 最大値設定
	 *
	 * @param max	最大値
	 * @return	NumberValidator
	 */
	public NumberValidator max (double max) {

		this.settings.put("max", max);
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

		if (!NUMBER.matcher(str).matches()) {
			return false;
		}

		double v;
		try {
			v = Double.parseDouble(str);
		} catch (Exception ex) {
			return false;
		}

		/*
		 * <b>綴りが正しくても届かないことがある。</b>
		 * {@code 1e400} は書き方としては数値だが、double では {@code Infinity} になる——
		 * <b>そのまま通すと、また min/max をすり抜ける</b>。
		 */
		if (!Double.isFinite(v)) {
			return false;
		}

		if (this.settings.containsKey("min")) {
			if (v < this.settings.getDouble("min")) {
				return false;
			}
		}

		if (this.settings.containsKey("max")) {
			if (this.settings.getDouble("max") < v) {
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

		return ValidationErrorType.Number;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return this.settings;

	}

}
