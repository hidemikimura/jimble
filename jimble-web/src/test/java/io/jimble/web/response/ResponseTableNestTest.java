package io.jimble.web.response;

import io.jimble.util.data.Data;
import io.jimble.util.data.TableNest;
import io.jimble.util.data.async.AsyncData;
import io.jimble.util.data.async.AsyncList;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * レスポンス単位のテーブルネスト切り替え（要件 F-A-11）
 *
 * <p>
 * <b>同じ {@code AsyncData} を、管理画面 API はネスト、ショップ API はフラットで返す。</b>
 * これがこの機能の目的である。形ごとにクラスを2つ書くと、
 * <b>片方だけ直したときに気づけない。</b>
 * </p>
 */
class ResponseTableNestTest {

	// region テスト用の実装

	/** テスト用のテーブル定義 */
	record Tbl(String tableName) implements io.jimble.util.data.definition.ITable {

		@Override
		public io.jimble.util.data.definition.ISchema schema () { return null; }

		@Override
		public String name () { return tableName; }

	}

	/** コメント（テーブルネストしたまま持つ） */
	static final class Comments extends AsyncList {

		private static final long serialVersionUID = 1L;

		private final long postId;

		Comments (long postId) { this.postId = postId; }

		@Override
		protected List<Data> load () {

			Data comment = new Data();
			comment.put("id", 10L);
			comment.put("body", "コメント");

			Data row = new Data();
			row.put("comment", comment);

			List<Data> rows = new ArrayList<>();
			rows.add(row);

			return rows;

		}

		@Override
		protected void setData (Data data) { add(data.extractTableData(new Tbl("comment"))); }

		@Override
		protected String hashKey () { return "Comments:" + postId; }

	}

	/** 記事（フラットで持つ）。<b>面ごとに別クラスを作らない</b> */
	static final class Post extends AsyncData {

		private static final long serialVersionUID = 1L;

		private final long id;

		Post (long id) { this.id = id; }

		@Override
		protected Data load () {

			Data post = new Data();
			post.put("id", id);
			post.put("title", "こんにちは");

			Data row = new Data();
			row.put("post", post);

			return row;

		}

		@Override
		protected void setData (Data data) { putAll(data.flattenTable("post")); }

		@Override
		protected void setRelationData (Data data) { put("comments", new Comments(id)); }

		@Override
		protected String hashKey () { return "Post:" + id; }

	}

	/** 管理画面はネスト、ショップはフラット。<b>返しているものは同じクラス</b> */
	static final class TestApp extends JimbleApp {

		{
			path("/admin", () -> {
				before(context -> context.response().tableNest(TableNest.ON));
				get("/posts/1", context -> context.response().json("post", new Post(1)));
			});

			path("/shop", () -> {
				before(context -> context.response().tableNest(TableNest.OFF));
				get("/posts/1", context -> context.response().json("post", new Post(1)));
			});

			get("/raw/posts/1", context -> context.response().json("post", new Post(1)));
		}

	}

	// endregion

	/**
	 * 1本流して本文を返す
	 *
	 * @param rawPath	パス
	 * @return	本文
	 */
	private String body (String rawPath) {

		Dispatcher dispatcher = new Dispatcher(new TestApp());
		Fakes.FakeRequestSource source = new Fakes.FakeRequestSource("GET", rawPath);
		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(source, sink)) {
			dispatcher.dispatch(context);
			return sink.body();
		}

	}

	@Test
	@DisplayName("管理画面はテーブルネストで返る")
	void adminIsNested () {

		String json = body("/admin/posts/1");

		assertTrue(json.contains("\"post\":{\"post\":{"), json);
		assertTrue(json.contains("\"comment\":{"), json);

	}

	@Test
	@DisplayName("ショップはフラットで返る")
	void shopIsFlat () {

		String json = body("/shop/posts/1");

		assertFalse(json.contains("\"post\":{\"post\":{"), json);
		assertFalse(json.contains("\"comment\":{"), json);
		assertTrue(json.contains("\"title\":\"こんにちは\""), json);
		assertTrue(json.contains("\"body\":\"コメント\""), json);

	}

	@Test
	@DisplayName("指定しなければ今までどおり（setData が作った形）")
	void defaultIsUnchanged () {

		String json = body("/raw/posts/1");

		// 記事は flattenTable なのでフラット、コメントは extractTableData なのでネスト
		assertFalse(json.contains("\"post\":{\"post\":{"), json);
		assertTrue(json.contains("\"comment\":{"), json);

	}

	@Test
	@DisplayName("同じクラスから面ごとに違う形が出る")
	void sameClassBothShapes () {

		String admin = body("/admin/posts/1");
		String shop = body("/shop/posts/1");

		assertTrue(admin.contains("\"comment\":{"), admin);
		assertFalse(shop.contains("\"comment\":{"), shop);

	}

}
