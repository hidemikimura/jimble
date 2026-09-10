package io.jimble.web.validation;

import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ValidationExecutor} のテスト（要件 F-V-04）
 *
 * <p>実際に {@link Dispatcher} を通して、後続がキャンセルされることを見る。</p>
 */
class ValidationExecutorTest {

	/* 実行ログ */
	private final List<String> log = new ArrayList<>();

	/**
	 * 失敗するバリデーション
	 */
	// docs:begin validation-executor
	private class Failing extends ValidationExecutor {

		@Override
		protected void validate (WebContext context) {

			log.add("validate");
			addError("title", "入力してください");

		}

	}
	// docs:end

	/**
	 * 通るバリデーション
	 */
	private class Passing extends ValidationExecutor {

		@Override
		protected void validate (WebContext context) {

			log.add("validate");

		}

	}

	/**
	 * 後続の処理
	 */
	private class UseCase extends io.jimble.core.executor.AbstractExecutor<WebContext> {

		@Override
		public void execute (WebContext context) {

			log.add("usecase");
			context.response().json("saved", true);

		}

	}

	/**
	 * ディスパッチする
	 *
	 * @param app	アプリケーション
	 * @return	レスポンス
	 */
	private Fakes.FakeResponseSink dispatch (JimbleApp app) {

		Dispatcher dispatcher = new Dispatcher(app);
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("POST", "/items"), sink)) {
			dispatcher.dispatch(context);
		}

		return sink;

	}

	// region テスト

	@Test
	@DisplayName("失敗すると後続の UseCase が実行されず 422 になる")
	void cancelsFollowingExecutors () {

		// docs:begin validation-executor-route
		JimbleApp app = new JimbleApp() {
			{
				post("/items", context -> {
					context.addExecutor(new Failing());
					context.addExecutor(new UseCase());
				});
			}
		};
		// docs:end

		Fakes.FakeResponseSink sink = dispatch(app);

		assertEquals(List.of("validate"), log, "後続が実行されている");
		assertEquals(ValidationExecutor.STATUS_CODE, sink.status());
		assertTrue(sink.body().contains("入力してください"), sink.body());
		assertTrue(sink.body().contains("title"), sink.body());

	}

	@Test
	@DisplayName("通れば後続の UseCase が実行される")
	void runsFollowingExecutors () {

		JimbleApp app = new JimbleApp() {
			{
				post("/items", context -> {
					context.addExecutor(new Passing());
					context.addExecutor(new UseCase());
				});
			}
		};

		Fakes.FakeResponseSink sink = dispatch(app);

		assertEquals(List.of("validate", "usecase"), log);
		assertEquals(200, sink.status());

	}

	@Test
	@DisplayName("同じ項目に複数のエラーを積める")
	void multipleErrorsPerField () {

		ValidationExecutor executor = new ValidationExecutor() {
			@Override
			protected void validate (WebContext context) {
				addError("title", "入力してください");
				addError("title", "50 文字以下で入力してください");
			}
		};

		try (WebContext context = new WebContext(
			new Fakes.FakeRequestSource("POST", "/items"), new Fakes.FakeResponseSink())) {

			assertFalse(executor.hasError());

			try {
				executor.execute(context);
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}

			assertTrue(executor.hasError());
			assertEquals(List.of("入力してください", "50 文字以下で入力してください"),
				executor.errors().get("title"));

		}

	}

	@Test
	@DisplayName("ValidationRules の結果をまとめて積める")
	void addErrorsFromRules () {

		Data validationErrors = new Data();
		validationErrors.put("name", new Data()
			.putData(ValidationMessages.KEY_TYPE, io.jimble.web.validation.error.ValidationErrorType.Empty)
			.putData(ValidationMessages.KEY_SETTING, new Data())
			.putData(ValidationMessages.KEY_INPUT, ""));

		ValidationExecutor executor = new ValidationExecutor() {
			@Override
			protected void validate (WebContext context) {
				addErrors(validationErrors);
			}
		};

		try (WebContext context = new WebContext(
			new Fakes.FakeRequestSource("POST", "/items"), new Fakes.FakeResponseSink())) {

			try {
				executor.execute(context);
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}

			assertEquals(List.of("入力してください"), executor.errors().get("name"));

		}

	}

	// endregion

	// region 入力値の返り（要件 F-W-09 / D-133）

	@Test
	@DisplayName("F-W-09 検証に落ちたら、送られてきた値が応答にも入る")
	void submittedValuesComeBack () {

		ValidationExecutor executor = new ValidationExecutor() {
			@Override
			protected void validate (WebContext context) {
				addError("title", "入力してください");
			}
		};

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("POST", "/items");
		source.form("title", "");
		source.form("body", "書きかけの本文");

		try (WebContext context = new WebContext(source, new Fakes.FakeResponseSink())) {

			try {
				executor.execute(context);
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}

			try {
				executor.onCancel(context);
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}

			/*
			 * <b>ここが無いと画面を組み直せない。</b>
			 * 前は putForm が誰も読まないフィールドに書いていただけで、
			 * <b>応答にもテンプレートにも出てこなかった</b>（D-133）。
			 * ドキュメント（errors.md）は最初から「入力値も一緒に返る」と書いてある
			 */
			assertEquals("書きかけの本文", context.response().getString("body")
				, "入力値が応答に入っていません: " + context.response());

			assertEquals(422, context.response().code());

			assertNotNull(context.response().getData(ValidationExecutor.RESPONSE_KEY)
				, "検証エラーが応答に入っていません");

		}

	}

	@Test
	@DisplayName("F-W-09 putForm に入れたものは getForm でも応答でも読める")
	void putFormIsReadableBothWays () {

		try (WebContext context = new WebContext(
			new Fakes.FakeRequestSource("POST", "/items"), new Fakes.FakeResponseSink())) {

			context.response().putForm("title", "書きかけ");

			// 記録としても残る（何を送られたかを後から見るため）
			assertEquals("書きかけ", context.response().getForm().getString("title"));
			assertTrue(context.response().hasForm());

			// 応答にも入る（テンプレートが読むのはこちら）
			assertEquals("書きかけ", context.response().getString("title"));

		}

	}

	// endregion

}
