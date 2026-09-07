package io.jimble.web.template;

import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * テンプレート描画のテスト（要件 F-W-08 / F-W-10 / F-W-11）
 *
 * <p>
 * テンプレートは <b>Gradle の {@code generateTestJte} で Java に変換され、
 * テストと一緒にコンパイルされている</b>（{@code src/test/jte}）。
 * 実行時コンパイルは使っていない。
 * </p>
 */
class TemplateTest {

	@AfterEach
	void resetEngine () {

		Templates.use(null);

	}

	// region 描画

	@Test
	@DisplayName("事前コンパイル済みのテンプレートを描画する")
	void render () {

		Data model = new Data().putData("title", "一覧");

		assertEquals("<h1>一覧</h1>\n", Templates.render("templates/hello.jte", model));

	}

	@Test
	@DisplayName("HTML を自動でエスケープする")
	void escapes () {

		// 要件 NF-S-02。既定でエスケープされること
		Data model = new Data().putData("title", "<script>alert(1)</script>");

		String html = Templates.render("templates/hello.jte", model);

		assertFalse(html.contains("<script>"), html);
		assertTrue(html.contains("&lt;script&gt;"), html);

	}

	@Test
	@DisplayName("エスケープを外すには明示が要る")
	void unsafeIsExplicit () {

		// $unsafe{...} と書いたときだけ素通し
		Data model = new Data().putData("html", "<b>強調</b>");

		assertTrue(Templates.render("templates/unsafe.jte", model).contains("<b>強調</b>"));

	}

	@Test
	@DisplayName("ループが書ける")
	void loop () {

		Data model = new Data().putData("items", List.of(
			new Data().putData("name", "A")
			, new Data().putData("name", "B")
		));

		String html = Templates.render("templates/list.jte", model);

		assertTrue(html.contains("<li>A</li>"), html);
		assertTrue(html.contains("<li>B</li>"), html);

	}

	@Test
	@DisplayName("Writer に書き出せる")
	void renderToWriter () {

		Writer out = new StringWriter();

		Templates.render("templates/hello.jte", new Data().putData("title", "x"), out);

		assertEquals("<h1>x</h1>\n", out.toString());

	}

	@Test
	@DisplayName("テンプレートの有無が分かる")
	void has () {

		assertTrue(Templates.engine().has("templates/hello.jte"));
		assertFalse(Templates.engine().has("templates/nothing.jte"));

	}

	// endregion

	// region 失敗したとき

	@Test
	@DisplayName("無いテンプレートは直し方の分かる例外にする")
	void notFound () {

		TemplateException ex = assertThrows(TemplateException.class
			, () -> Templates.render("templates/nothing.jte", new Data()));

		// 要件 F-X-05。「何が起きたか」と「どう直すか」
		assertTrue(ex.getMessage().contains("templates/nothing.jte"), ex.getMessage());
		assertTrue(ex.getMessage().contains("generateJte"), ex.getMessage());

	}

	@Test
	@DisplayName("テンプレートの中で落ちたら原因を残す")
	void renderFailure () {

		TemplateException ex = assertThrows(TemplateException.class
			, () -> Templates.render("templates/boom.jte", new Data()));

		assertTrue(ex.getMessage().contains("templates/boom.jte"), ex.getMessage());
		assertNotNull(ex.getCause());

	}

	// endregion

	// region 差し替え（要件 F-W-11）

	@Test
	@DisplayName("エンジンを差し替えられる")
	void replaceable () {

		Templates.use(new TemplateEngine() {

			@Override
			public void render (String name, Data model, Writer out) {
				try {
					out.write("fake:" + name);
				} catch (Exception ex) {
					throw new TemplateException("だめ", ex);
				}
			}

			@Override
			public boolean has (String name) {
				return true;
			}

		});

		assertEquals("fake:anything", Templates.render("anything", new Data()));

	}

	// endregion

	// region レスポンスとの結線

	/**
	 * ディスパッチする
	 *
	 * @param app		アプリケーション
	 * @param accept	Accept ヘッダ（不要なら null）
	 * @return	レスポンス
	 */
	private Fakes.FakeResponseSink request (JimbleApp app, String accept) {

		Dispatcher dispatcher = new Dispatcher(app);
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/page");
		if (accept != null) {
			source.header("Accept", accept);
		}

		try (WebContext context = new WebContext(source, sink)) {
			dispatcher.dispatch(context);
		}

		return sink;

	}

	/**
	 * テンプレートを返すアプリ
	 *
	 * @return	アプリケーション
	 */
	private JimbleApp viewApp () {

		return new JimbleApp() {
			{
				get("/page", context -> {
					context.response().putData("title", "見出し");
					context.response().view("templates/hello.jte");
				});
			}
		};

	}

	@Test
	@DisplayName("view() で HTML を返す")
	void responseView () {

		Fakes.FakeResponseSink sink = request(viewApp(), null);

		assertEquals(200, sink.status());
		assertEquals("<h1>見出し</h1>\n", sink.body());
		// 文字コードまで載せる。載せないと受け取る側が推測することになる
		assertEquals("text/html; charset=UTF-8", sink.headers().get("Content-Type"));

	}

	@Test
	@DisplayName("JSON を求められたら同じ口が JSON を返す")
	void responseViewAsJson () {

		// 移送元の挙動をそのまま採った。1つの口が HTML と JSON の両方を返せる
		Fakes.FakeResponseSink sink = request(viewApp(), "application/json");

		assertEquals(200, sink.status());
		assertTrue(sink.body().contains("\"title\""), sink.body());
		assertTrue(sink.body().contains("見出し"), sink.body());
		assertFalse(sink.body().contains("<h1>"), sink.body());

	}

	@Test
	@DisplayName("テンプレートが落ちたら 500。本文は出さない")
	void responseViewFailure () {

		JimbleApp app = new JimbleApp() {
			{
				get("/page", context -> context.response().view("templates/nothing.jte"));
			}
		};

		Fakes.FakeResponseSink sink = request(app, null);

		/*
		 * いったん文字列に組み立ててから送っているので、
		 * 途中で落ちてもヘッダを差し替えて 500 を返せる。
		 * 描画しながら流していると、ここは 200 + 途中まで出た HTML になる。
		 */
		assertEquals(500, sink.status());
		assertNull(sink.body(), "途中まで描画した本文が出ている");

	}

	@Test
	@DisplayName("モデルを別に渡せる")
	void responseViewWithModel () {

		JimbleApp app = new JimbleApp() {
			{
				get("/page", context -> context.response()
					.view("templates/hello.jte", new Data().putData("title", "別のモデル")));
			}
		};

		assertEquals("<h1>別のモデル</h1>\n", request(app, null).body());

	}

	// endregion

}
