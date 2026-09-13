package io.jimble.web.validation.validator;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

/**
 * 1つの検証（{@code ValidationRule.custom(IValidator)} でアプリが足せる）
 *
 * <p>
 * <b>実装しなければならないのは {@link #validate} だけである（D-173）。</b>
 * かつては 3 つとも abstract で、<b>{@code default} が1つも無かった</b>——
 * 枠組みがメソッドを1つ足した瞬間に、<b>アプリの実装が全部コンパイルエラーになる</b>。
 * 1.0 からは公開 API を壊す前に非推奨期間を置くと約束しているので、
 * <b>足せない interface を公開したままにはできない</b>。
 * </p>
 *
 * <p>
 * 引数を増やしたくなったときも、<b>新しい形を {@code default} で足して
 * 古い形へ流す</b>ことができる（abstract を足すのと違い、実装側は壊れない）。
 * </p>
 */
public interface IValidator {

	/**
	 * validate
	 *
	 * @param db				DB
	 * @param req				リクエスト情報
	 * @param isInsertRequest	登録リクエスト判定
	 * @param value				値
	 * @return	エラーの場合 = false
	 */
	boolean validate (DB db, Data req, boolean isInsertRequest, Object value) throws CodeException;

	/**
	 * エラー種別を取得する
	 *
	 * <p>文言（{@code ValidationMessages}）はこれで引く。</p>
	 *
	 * @return	エラー種別（既定は {@link ValidationErrorType#Custom}）
	 */
	default ValidationErrorType errorType () {

		return ValidationErrorType.Custom;

	}

	/**
	 * 設定情報を取得する
	 *
	 * <p>
	 * {@code min} / {@code max} のような、文言に埋め込む値を入れる。
	 * <b>毎回新しい入れ物を返す</b>——共有すると、あとから書き換えられる。
	 * </p>
	 *
	 * @return	設定情報（既定は空）
	 */
	default Data settings () {

		return new Data();

	}

}
