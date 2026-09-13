package io.jimble.web.validation;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.util.data.Data;
import io.jimble.util.data.definition.ISchema;
import io.jimble.web.validation.error.ValidationErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * バリデーションのテスト（M4 ステップ3）
 *
 * <p>DB を使うバリデータは無いので通常の {@code build} で走る。</p>
 */
class ValidationTest {

	// region テスト用のテーブル定義

	/** スキーマ */
	public static class TestSchema extends AbstractSchema {

		@Override
		public String name () {

			return "test";

		}

	}

	/** テーブル */
	public static class Item extends Table {

		/** 名前 */
		public static final Column name = new Column(instance(), "name", String.class, true, null, false);

		/** 年齢 */
		public static final Column age = new Column(instance(), "age", int.class, true, null, false);

		/** メール */
		public static final Column email = new Column(instance(), "email", String.class, true, null, false);

		private static final List<Column> COLUMNS = List.of(name, age, email);

		@Override
		protected List<Column> declareColumns () {

			return COLUMNS;

		}

		public Item (ISchema schema, String tableName) {

			super(schema, tableName);

		}

		/**
		 * インスタンス
		 *
		 * @return	テーブル
		 */
		public static Item instance () {

			return new Item(new TestSchema(), "item");

		}

	}

	// endregion

	// region ValidationRule

	@Test
	@DisplayName("empty() は「空だとエラー」（＝必須）")
	void empty () throws Exception {

		ValidationRule rule = new ValidationRule().empty();

		assertFalse(rule.validate(null, new Data(), false, "あ").error());
		assertTrue(rule.validate(null, new Data(), false, "").error());
		assertTrue(rule.validate(null, new Data(), false, null).error());

	}

	@Test
	@DisplayName("required() は empty() の別名")
	void required () throws Exception {

		ValidationRule rule = new ValidationRule().required();

		assertFalse(rule.validate(null, new Data(), false, "あ").error());
		assertTrue(rule.validate(null, new Data(), false, "").error());

	}

	@Test
	@DisplayName("整数の範囲チェックが効く")
	void integerRange () throws Exception {

		ValidationRule rule = new ValidationRule().integer(1, 120);

		assertFalse(rule.validate(null, new Data(), false, "42").error());
		assertTrue(rule.validate(null, new Data(), false, "0").error());
		assertTrue(rule.validate(null, new Data(), false, "121").error());
		assertTrue(rule.validate(null, new Data(), false, "abc").error());
		// 空は範囲チェックの対象外（必須は empty() で表す）
		assertFalse(rule.validate(null, new Data(), false, "").error());

	}

	@Test
	@DisplayName("文字列長チェックが効く")
	void textLength () throws Exception {

		ValidationRule rule = new ValidationRule().textLength(2, 4);

		assertFalse(rule.validate(null, new Data(), false, "あいう").error());
		assertTrue(rule.validate(null, new Data(), false, "あ").error());
		assertTrue(rule.validate(null, new Data(), false, "あいうえお").error());

	}

	@Test
	@DisplayName("メールアドレスのチェックが効く")
	void email () throws Exception {

		ValidationRule rule = new ValidationRule().email();

		assertFalse(rule.validate(null, new Data(), false, "kimura@example.com").error());
		assertTrue(rule.validate(null, new Data(), false, "not-an-email").error());

	}

	@Test
	@DisplayName("最初に失敗したチェックで止まる")
	void stopsAtFirstFailure () throws Exception {

		ValidationRule rule = new ValidationRule().empty().integer(1, 10);

		ValidationResult result = rule.validate(null, new Data(), false, null);

		assertTrue(result.error());
		assertEquals(ValidationErrorType.Empty, result.validationError().errorType());

	}

	// endregion

	// region ValidationRules

	@Test
	@DisplayName("列ごとのルールが動き、エラーは全部集まる")
	void rules () {

		// docs:begin validation
		ValidationRules rules = new ValidationRules()
			.put(Item.name, new ValidationRule().empty())
			.put(Item.age, new ValidationRule().integer(1, 120));

		Data request = new Data();
		request.putData(Item.name, "");
		request.putData(Item.age, "999");

		// エラーは最初の1件で止めず、全部集める（要件 F-V-03）
		Data errors = rules.validate(null, request);

		Data messages = ValidationMessages.toMessages(errors);
		// docs:end

		assertEquals(2, messages.size(), messages.toString());
		assertTrue(messages.containsKey("name"));
		assertTrue(messages.containsKey("age"));

	}

	@Test
	@DisplayName("送られていない項目は検証しない")
	void skipsAbsentColumn () {

		ValidationRules rules = new ValidationRules()
			.put(Item.name, new ValidationRule().empty());

		assertTrue(rules.validate(null, new Data()).isEmpty());

	}

	@Test
	@DisplayName("insertRequired は登録リクエストのときだけ必須になる（F-V-02）")
	void insertRequired () {

		// docs:begin validation-insert-required
		ValidationRules rules = new ValidationRules()
			.put(Item.name, new ValidationRule().insertRequired().empty())
			.insertRequestChecker(req -> req.getBoolean("is_insert"));

		Data update = new Data();
		update.put("is_insert", false);
		assertTrue(rules.validate(null, update).isEmpty(), "更新なのに必須になっている");

		Data insert = new Data();
		insert.put("is_insert", true);
		assertFalse(rules.validate(null, insert).isEmpty(), "登録なのに必須になっていない");
		// docs:end

	}

	@Test
	@DisplayName("エラーの並び順は登録順で決まる")
	void deterministicOrder () {

		// 移送元は HashMap だったので実行ごとに順序が変わっていた
		ValidationRules rules = new ValidationRules()
			.put(Item.name, new ValidationRule().empty())
			.put(Item.age, new ValidationRule().empty())
			.put(Item.email, new ValidationRule().empty());

		Data request = new Data();
		request.putData(Item.name, "");
		request.putData(Item.age, "");
		request.putData(Item.email, "");

		for (int i = 0; i < 20; i++) {
			Data messages = ValidationMessages.toMessages(rules.validate(null, request));
			assertEquals(List.of("name", "age", "email"), List.copyOf(messages.keySet()));
		}

	}

	@Test
	@DisplayName("複数件の検証はエラーのある行だけ返す")
	void validateList () {

		// docs:begin validation-list
		ValidationRules rules = new ValidationRules()
			.put(Item.name, new ValidationRule().empty());

		Data ok = new Data();
		ok.putData(Item.name, "あ");

		Data ng = new Data();
		ng.putData(Item.name, "");

		List<Data> errors = rules.validate(null, List.of(ok, ng, ok));

		assertEquals(1, errors.size());
		assertEquals(2, errors.getFirst().getInt("index"), "行番号が違う");
		// docs:end

	}

	// endregion

	// region メッセージ（F-V-03）

	@Test
	@DisplayName("種別と設定値から日本語のメッセージになる")
	void messages () {

		ValidationRules rules = new ValidationRules()
			.put(Item.name, new ValidationRule().empty())
			.put(Item.age, new ValidationRule().integer(1, 120));

		Data request = new Data();
		request.putData(Item.name, "");
		request.putData(Item.age, "999");

		Data messages = ValidationMessages.toMessages(rules.validate(null, request));

		assertEquals(List.of("入力してください"), messages.get("name"));
		assertEquals(List.of("1 以上 120 以下の整数で入力してください"), messages.get("age"));

	}

	@Test
	@DisplayName("メッセージは差し替えられる")
	void overrideMessage () {

		try {

			// docs:begin validation-message
			ValidationMessages.put(ValidationErrorType.Empty, (type, settings) -> "required");

			ValidationRules rules = new ValidationRules()
				.put(Item.name, new ValidationRule().empty());

			Data request = new Data();
			request.putData(Item.name, "");

			assertEquals(List.of("required"),
				ValidationMessages.toMessages(rules.validate(null, request)).get("name"));
			// docs:end

		} finally {
			ValidationMessages.reset();
		}

	}

	// endregion

}
