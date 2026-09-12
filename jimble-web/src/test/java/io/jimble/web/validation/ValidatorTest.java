package io.jimble.web.validation;

import io.jimble.util.data.Data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 規則の束をまとめて走らせる（要件 D-164）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>公開しているのに、どこからも呼ばれておらず、テストも無かった。</b>
 * そして中を見たら、<b>戻り値の約束が壊れていた</b>。
 * </p>
 *
 * <p>
 * {@code Data result = null} で始めて「エラーが無ければ {@code null}」の顔をしていたが、
 * 呼んでいる {@link ValidationRules#validate} は
 * <b>エラーが無くても空の {@code Data} を返す</b>。
 * つまり <b>{@code null} 判定は常に「エラーあり」に倒れ、
 * 全部のリクエストが弾かれる</b>——例外もログも出ない。
 * </p>
 */
class ValidatorTest {

	/** 名前 */
	private static final io.jimble.db.sql.definition.column.Column NAME
		= ValidationTest.Item.name;

	/** 年齢 */
	private static final io.jimble.db.sql.definition.column.Column AGE
		= ValidationTest.Item.age;

	@Test
	@DisplayName("D-164 エラーが無ければ、空の Data（null ではない）")
	void cleanInputGivesAnEmptyData () {

		ValidationRules rules = new ValidationRules()
			.put(NAME, new ValidationRule().required());

		Data errors = Validator.validate(null, new Data().putData(NAME, "たろう"), rules);

		assertNotNull(errors, "null が返っています（呼ぶ側が null 判定を書けてしまいます）");
		assertTrue(errors.isEmpty(), "エラーが無いのに中身があります: " + errors);

	}

	@Test
	@DisplayName("エラーは列ごとに入る")
	void errorsAreKeyedByColumn () {

		ValidationRules rules = new ValidationRules()
			.put(NAME, new ValidationRule().required())
			.put(AGE, new ValidationRule().integer(0, 150));

		Data errors = Validator.validate(null
			, new Data().putData(NAME, "").putData(AGE, "999"), rules);

		assertFalse(errors.isEmpty());

		/*
		 * <b>エラーはテーブル名の下にまとまる</b>（{@code Data.putData(Column, ...)} の決まり）。
		 */
		Data item = errors.getData("item");

		assertEquals(2, item.size(), "両方の列が出ていません: " + errors);

		assertTrue(item.containsKey(NAME.name()), errors.toString());
		assertTrue(item.containsKey(AGE.name()), errors.toString());

	}

	@Test
	@DisplayName("D-164 束を複数渡すと、どちらのエラーも残る")
	void severalRuleSetsAreMerged () {

		/*
		 * <b>これがこのクラスの存在理由である。</b>
		 * 「共通の規則」と「この画面だけの規則」を別々に持って、
		 * <b>まとめて1回で走らせる</b>。
		 *
		 * <b>以前は、あとから重ねた束が前の束のエラーを丸ごと消していた。</b>
		 * エラーはテーブル名の下にまとまるので、素直に上書きすると
		 * <b>{@code item} の階層ごと入れ替わる</b>——
		 * <b>共通側のエラーが1件も出ない</b>のに、例外もログも出ない。
		 */
		ValidationRules common = new ValidationRules()
			.put(NAME, new ValidationRule().required());

		ValidationRules screen = new ValidationRules()
			.put(AGE, new ValidationRule().integer(0, 150));

		Data errors = Validator.validate(null
			, new Data().putData(NAME, "").putData(AGE, "999"), common, screen);

		assertEquals(2, errors.getData("item").size(), "束をまたいで集められていません: " + errors);

	}

	@Test
	@DisplayName("束を1つも渡さなくても落ちない")
	void noRulesIsNotAnError () {

		Data errors = Validator.validate(null, new Data());

		assertNotNull(errors);
		assertTrue(errors.isEmpty());

	}

	// region ここで固定していないこと

	/*
	 * - <b>同じ列に対して束が2つエラーを出したとき、どちらが残るか</b>は固定していない。
	 *   （<b>違う列なら両方残る</b>ことは上で見ている）
	 *   あとから重ねたほうで上書きされるが、<b>どちらも「その列が不正」であること</b>に
	 *   変わりはないので、順番までは約束しない
	 * - <b>エラー情報の中身</b>（{@code validation_type} など）は
	 *   {@code ValidationTest} が見ている
	 */

	// endregion

}
