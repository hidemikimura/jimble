package io.jimble.web.request;

import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * リクエストパラメータ（要件 F-W-01〜03）
 *
 * <p>
 * <b>ネストしたパラメータ</b>（{@code a[b][c]=1}）が要件 F-W-03。
 * </p>
 */
class NestedParameterTest {

	/**
	 * リクエストを作る
	 *
	 * @param source	入力口
	 * @return	コンテキスト
	 */
	private WebContext context (Fakes.FakeRequestSource source) {

		return new WebContext(source, new Fakes.FakeResponseSink());

	}

	@Test
	@DisplayName("素のパラメータが取れる")
	void flat () {

		try (WebContext context = context(new Fakes.FakeRequestSource("GET", "/x")
			.query("name", "俺的")
			.query("page", "2"))) {

			Data all = context.request().bodyAll();

			assertEquals("俺的", all.getString("name"));
			assertEquals(2L, all.getLong("page"), "型付きで取れる");

		}

	}

	@Test
	@DisplayName("a[b][c] がネストして入る（要件 F-W-03）")
	void nested () {

		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x")
			.form("user[name]", "きむら")
			.form("user[address][city]", "京都"))) {

			Data all = context.request().bodyAll();

			assertEquals("きむら", all.getDataOptional("user").getString("name"));
			assertEquals("京都", all.getDataOptional("user").getDataOptional("address").getString("city"));

		}

	}

	@Test
	@DisplayName("添字つきは配列になる")
	void indexed () {

		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x")
			.form("tags[0]", "a")
			.form("tags[1]", "b"))) {

			List<Object> tags = context.request().bodyAll().getObjectListOptional("tags", Object.class);

			assertEquals(List.of("a", "b"), tags);

		}

	}

	@Test
	@DisplayName("配列の中のネストも入る")
	void nestedInArray () {

		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x")
			.form("items[0][name]", "A")
			.form("items[0][price]", "100")
			.form("items[1][name]", "B")
			.form("items[1][price]", "200"))) {

			List<Data> items = context.request().bodyAll().getDataList("items");

			assertEquals(2, items.size());
			assertEquals("A", items.get(0).getString("name"));
			assertEquals(200L, items.get(1).getLong("price"));

		}

	}

	@Test
	@DisplayName("ドットでもネストする")
	void dotted () {

		try (WebContext context = context(new Fakes.FakeRequestSource("GET", "/x")
			.query("paging.page", "3")
			.query("paging.per", "50"))) {

			Data paging = context.request().bodyAll().getDataOptional("paging");

			assertEquals(3L, paging.getLong("page"));
			assertEquals(50L, paging.getLong("per"));

		}

	}

	@Test
	@DisplayName("パスパラメータもクエリも同じところから取れる（要件 F-W-01）")
	void pathAndQuery () {

		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", "/sites/42")
			.query("page", "2");

		try (WebContext context = context(source)) {

			context.request().bodyPath().putData("id", "42");

			Data all = context.request().bodyAll();

			assertEquals(42L, all.getLong("id"));
			assertEquals(2L, all.getLong("page"));

		}

	}

	@Test
	@DisplayName("JSON の本文もネストしたまま取れる")
	void jsonBody () {

		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x")
			.body("application/json", "{\"user\":{\"name\":\"きむら\",\"tags\":[\"a\",\"b\"]}}"))) {

			Data user = context.request().bodyAll().getDataOptional("user");

			assertEquals("きむら", user.getString("name"));
			assertEquals(2, user.getObjectListOptional("tags", Object.class).size());

		}

	}

	@Test
	@DisplayName("ヘッダが取れる（要件 F-W-02）")
	void headers () {

		try (WebContext context = context(new Fakes.FakeRequestSource("GET", "/x")
			.header("User-Agent", "test-agent")
			.header("X-Request-Id", "abc"))) {

			assertEquals("abc", context.request().header().getString("x-request-id"));
			assertEquals("GET", context.request().method());
			assertEquals("/x", context.request().path());
			assertEquals("http", context.request().scheme());
			assertEquals("localhost", context.request().host());
			assertTrue(context.request().url().endsWith("/x"));

		}

	}

	@Test
	@DisplayName("添字なしの [] は末尾に足す")
	void append () {

		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x")
			.form("tags[]", "a", "b"))) {

			assertEquals(List.of("a", "b")
				, context.request().bodyAll().getObjectListOptional("tags", Object.class));

		}

	}

	@Test
	@DisplayName("同じキーが複数あればリストになる")
	void multiValue () {

		try (WebContext context = context(new Fakes.FakeRequestSource("GET", "/x")
			.query("status", "a", "b"))) {

			assertEquals(List.of("a", "b")
				, context.request().bodyAll().getObjectListOptional("status", Object.class));

		}

	}

	@Test
	@DisplayName("クエリと JSON に同じキーがあっても配列に化けない")
	void jsonOverridesQuery () {

		/*
		 * 移送元は MapUtil.mergeData()（深いマージ）で重ねていたので、
		 * 同じキーの値が1つのリストにまとめられていた。
		 */
		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x")
			.query("name", "クエリ")
			.body("application/json", "{\"name\":\"JSON\"}"))) {

			assertEquals("JSON", context.request().bodyAll().getString("name"));

		}

	}

	@Test
	@DisplayName("JSON の入れ子はクエリの入れ子と重なる")
	void jsonMergesNested () {

		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x")
			.query("user[age]", "20")
			.body("application/json", "{\"user\":{\"name\":\"きむら\"}}"))) {

			Data user = context.request().bodyAll().getDataOptional("user");

			assertEquals("きむら", user.getString("name"));
			assertEquals(20L, user.getLong("age"), "クエリ側が消えている");

		}

	}

	@Test
	@DisplayName("キーの分解")
	void segments () {

		assertEquals(List.of("user", "name"), names(NestedParameterParser.segments("user[name]")));
		assertEquals(List.of("items", "#0", "name"), names(NestedParameterParser.segments("items[0][name]")));
		assertEquals(List.of("tags", "#-1"), names(NestedParameterParser.segments("tags[]")));
		assertEquals(List.of("a", "b", "#2", "c"), names(NestedParameterParser.segments("a.b[2].c")));
		assertEquals(List.of(), names(NestedParameterParser.segments("")));

	}

	/**
	 * 分解した位置を読みやすくする
	 *
	 * @param segments	位置
	 * @return	文字列
	 */
	private List<String> names (List<NestedParameterParser.Segment> segments) {

		return segments.stream()
			.map(segment -> segment.isIndex() ? "#" + segment.index() : segment.name())
			.toList();

	}

	// region 空で送られたもの（D-133）

	@Test
	@DisplayName("値なしで送られた欄は空文字になる（required が素通りしない）")
	void emptyValueBecomesEmptyString () {

		/*
		 * <b>ここが空の List のままだと、必須の検証が抜ける。</b>
		 * EmptyValidator が見るのは「null か、空の文字列か」なので、
		 * <b>空の List はどちらでもない</b>——
		 * 必須の欄を空のまま送れば通ってしまっていた
		 */
		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x")
			.form("name"))) {

			Data all = context.request().bodyAll();

			assertTrue(all.containsKey("name"), "送ったキーが消えています: " + all);
			assertEquals("", all.getString("name"), "空文字になっていません: " + all.get("name"));

		}

	}

	@Test
	@DisplayName("入れ子の中でも、値なしは空文字になる")
	void emptyValueInNested () {

		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x")
			.form("items[0][name]")
			.form("items[0][amount]", "100"))) {

			List<Data> items = context.request().bodyAll().getDataList("items");

			assertEquals(1, items.size());
			assertEquals("", items.getFirst().getString("name"));
			assertEquals(100L, items.getFirst().getLong("amount"));

		}

	}

	@Test
	@DisplayName("そもそも送られていないものは、キーごと無い（PATCH の見分けが壊れない）")
	void absentIsNotEmpty () {

		/*
		 * <b>「空で送った」と「送っていない」は別である。</b>
		 * examples/blog の PATCH は containsKey でこれを見分けているので、
		 * 空文字を勝手に補うと<b>送っていない項目まで書き換わる</b>
		 */
		try (WebContext context = context(new Fakes.FakeRequestSource("POST", "/x")
			.form("name", "きむら"))) {

			Data all = context.request().bodyAll();

			assertTrue(all.containsKey("name"));
			assertFalse(all.containsKey("note"), "送っていないキーが増えています: " + all);

		}

	}

	// endregion

}
