package io.jimble.util.data.async;

import io.jimble.util.data.Data;
import io.jimble.util.data.TableNest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * テーブルネストの組み直しのテスト（要件 F-A-11）
 *
 * <p>
 * <b>同じクラスから、ネストありとネストなしの両方が出ること</b>を確かめる。
 * 管理画面 API はネスト、ショップ API はフラット、という使い分けのためにある。
 * </p>
 */
class TableNestTest {

	// region テスト用の実装

	/** SELECT の結果を模す（テーブル名でネストしている。要件 F-D-02） */
	private static Data row (long id, String title) {

		Data post = new Data();
		post.put("id", id);
		post.put("title", title);

		Data data = new Data();
		data.put("post", post);

		return data;

	}

	/** setData を flattenTable で書いた実装 */
	static final class FlatPost extends AsyncData {

		private final long id;

		FlatPost (long id) { this.id = id; }

		@Override
		protected Data load () {

			return row(id, "こんにちは");

		}

		@Override
		protected void setData (Data data) {

			putAll(data.flattenTable("post"));

		}

		@Override
		protected void setRelationData (Data data) {

			// 子は最上位に置く
			put("comments", new NestComments(id));

		}

		@Override
		protected String hashKey () { return "FlatPost:" + id; }

	}

	/** setData を extractTableData で書いた実装 */
	static final class NestedPost extends AsyncData {

		private final long id;

		NestedPost (long id) { this.id = id; }

		@Override
		protected Data load () {

			return row(id, "こんにちは");

		}

		@Override
		protected void setData (Data data) {

			putAll(data.extractTableData(new TestTable("post")));

		}

		@Override
		protected void setRelationData (Data data) {

			put("comments", new NestComments(id));

		}

		@Override
		protected String hashKey () { return "NestedPost:" + id; }

	}

	/** 要素をテーブルネスト形で持つリスト（examples/blog の CommentList と同じ書き方） */
	static final class NestComments extends AsyncList {

		private final long postId;

		NestComments (long postId) { this.postId = postId; }

		@Override
		protected List<Data> load () {

			List<Data> rows = new ArrayList<>();

			for (int i = 1; i <= 2; i++) {
				Data comment = new Data();
				comment.put("id", (long) i);
				comment.put("body", "コメント" + i);

				Data data = new Data();
				data.put("comment", comment);

				rows.add(data);
			}

			return rows;

		}

		@Override
		protected void setData (Data data) {

			add(data.extractTableData(new TestTable("comment")));

		}

		@Override
		protected String hashKey () { return "NestComments:" + postId; }

	}

	/** 要素をフラットで持つリスト */
	static final class FlatComments extends AsyncList {

		@Override
		protected List<Data> load () {

			Data comment = new Data();
			comment.put("id", 1L);
			comment.put("body", "コメント1");

			Data data = new Data();
			data.put("comment", comment);

			return List.of(data);

		}

		@Override
		protected void setData (Data data) {

			add(data.flattenTable("comment"));

		}

		@Override
		protected String hashKey () { return "FlatComments"; }

	}

	/** テーブルネストしていない結果（自由 SQL・集約） */
	static final class Aggregate extends AsyncData {

		@Override
		protected Data load () {

			Data data = new Data();
			data.put("total", 12L);

			return data;

		}

		@Override
		protected void setData (Data data) { putAll(data); }

		@Override
		protected String hashKey () { return "Aggregate"; }

	}

	/** テスト用のテーブル定義 */
	record TestTable(String tableName) implements io.jimble.util.data.definition.ITable {

		@Override
		public io.jimble.util.data.definition.ISchema schema () { return null; }

		@Override
		public String name () { return tableName; }

	}

	// endregion

	// region フラットで持っているものを両方の形で出す

	@Test
	@DisplayName("flattenTable で書いた実装から、テーブルネストありの JSON が出る")
	void flatToNested () {

		String json = new FlatPost(1).getJsonString(TableNest.ON);

		assertTrue(json.contains("\"post\":{"), json);
		assertTrue(json.contains("\"title\":\"こんにちは\""), json);

		// 子は最上位に残る
		assertTrue(json.contains("\"comments\":["), json);

	}

	@Test
	@DisplayName("flattenTable で書いた実装は、ネストなしなら今までどおり")
	void flatToFlat () {

		String json = new FlatPost(1).getJsonString(TableNest.OFF);

		assertFalse(json.contains("\"post\":{"), json);
		assertTrue(json.contains("\"title\":\"こんにちは\""), json);
		assertTrue(json.contains("\"comments\":["), json);

	}

	// endregion

	// region ネストで持っているものを両方の形で出す

	@Test
	@DisplayName("extractTableData で書いた実装から、フラットな JSON が出る")
	void nestedToFlat () {

		String json = new NestedPost(1).getJsonString(TableNest.OFF);

		assertFalse(json.contains("\"post\":{"), json);
		assertTrue(json.contains("\"title\":\"こんにちは\""), json);
		assertTrue(json.contains("\"comments\":["), json);

	}

	@Test
	@DisplayName("extractTableData で書いた実装は、ネストありなら今までどおり")
	void nestedToNested () {

		String json = new NestedPost(1).getJsonString(TableNest.ON);

		assertTrue(json.contains("\"post\":{"), json);
		assertTrue(json.contains("\"comments\":["), json);

	}

	// endregion

	// region 同じクラスで両方の形が出る（この機能の目的）

	@Test
	@DisplayName("同じインスタンスから、ネストありとなしの両方が出る")
	void bothShapesFromOneInstance () {

		FlatPost post = new FlatPost(1);

		String nested = post.getJsonString(TableNest.ON);
		String flat = post.getJsonString(TableNest.OFF);

		assertTrue(nested.contains("\"post\":{"), nested);
		assertFalse(flat.contains("\"post\":{"), flat);

		// 組み直しは元を壊さない
		assertEquals("こんにちは", post.getString("title"));

	}

	// endregion

	// region リスト

	@Test
	@DisplayName("リストの要素もテーブルネストありで出せる")
	void listToNested () {

		Data root = new Data();
		root.put("comments", new NestComments(1));

		String json = root.getJsonString(TableNest.ON);

		assertTrue(json.contains("\"comment\":{"), json);
		assertTrue(json.contains("コメント2"), json);

	}

	@Test
	@DisplayName("リストの要素をフラットにできる")
	void listToFlat () {

		Data root = new Data();
		root.put("comments", new NestComments(1));

		String json = root.getJsonString(TableNest.OFF);

		assertFalse(json.contains("\"comment\":{"), json);
		assertTrue(json.contains("\"body\":\"コメント1\""), json);

	}

	@Test
	@DisplayName("フラットで持っているリストもネストありにできる")
	void flatListToNested () {

		Data root = new Data();
		root.put("comments", new FlatComments());

		String json = root.getJsonString(TableNest.ON);

		assertTrue(json.contains("\"comment\":{"), json);

	}

	@Test
	@DisplayName("入れ子のリストも組み直される")
	void nestedTree () {

		String json = new FlatPost(1).getJsonString(TableNest.ON);

		// 記事はテーブルネスト、その下のコメントもテーブルネスト
		assertTrue(json.contains("\"post\":{"), json);
		assertTrue(json.contains("\"comment\":{"), json);

	}

	// endregion

	// region 触らない場合

	@Test
	@DisplayName("既定（指定なし）は今までとまったく同じ")
	void defaultIsUnchanged () {

		assertEquals(new FlatPost(1).getJsonString(), new FlatPost(1).getJsonString(TableNest.AS_IS));

		// flattenTable で書いてあるので、指定なしはフラットのまま
		assertFalse(new FlatPost(1).getJsonString().contains("\"post\":{"));

		// extractTableData で書いてあるので、指定なしはネストのまま
		assertTrue(new NestedPost(1).getJsonString().contains("\"post\":{"));

	}

	@Test
	@DisplayName("AS_IS は自分自身を返す（作り直さない）")
	void asIsReturnsSelf () {

		FlatPost post = new FlatPost(1);

		assertSame(post, post.reshape(TableNest.AS_IS));
		assertSame(post, post.reshape(null));

	}

	@Test
	@DisplayName("テーブルネストしていない結果は組み直さない")
	void aggregateIsUntouched () {

		Aggregate aggregate = new Aggregate();

		assertEquals("{\"total\":12}", aggregate.getJsonString(TableNest.ON));
		assertEquals("{\"total\":12}", aggregate.getJsonString(TableNest.OFF));

	}

	@Test
	@DisplayName("普通の Data は組み直さない（対象は Async だけ）")
	void plainDataIsUntouched () {

		Data data = new Data();
		Data post = new Data();
		post.put("id", 1L);
		data.put("post", post);

		assertEquals(data.getJsonString(), data.getJsonString(TableNest.OFF));

	}

	// endregion

	// region 潰さない・壊さない

	/** JOIN していて、片方をネストのまま、片方を平らにして持つ実装 */
	static final class JoinedPost extends AsyncData {

		@Override
		protected Data load () {

			Data post = new Data();
			post.put("id", 1L);
			post.put("title", "こんにちは");

			Data tag = new Data();
			tag.put("id", 9L);
			tag.put("name", "日記");

			Data data = new Data();
			data.put("post", post);
			data.put("tag", tag);

			return data;

		}

		@Override
		protected void setData (Data data) {

			// post はネストのまま、tag は平らにして持つ
			putAll(data.extractTableData(new TestTable("post")));
			putAll(data.flattenTable("tag"));

		}

		@Override
		protected String hashKey () { return "JoinedPost"; }

	}

	@Test
	@DisplayName("組み直しても生の読み込みデータを書き換えない")
	void doesNotMutateLoadedData () {

		JoinedPost post = new JoinedPost();

		String before = post.getJsonString();

		post.getJsonString(TableNest.ON);
		post.getJsonString(TableNest.OFF);

		/*
		 * extractTableData は生データの入れ子を同じインスタンスのまま返す。
		 * そこへ組み立てると、出力しただけで元のノードが変わる。
		 */
		assertEquals(before, post.getJsonString());
		assertEquals(1L, post.getData("post").getLong("id"));

	}

	/** テーブルと同じ名前で子を置いている実装 */
	static final class CollidingPost extends AsyncData {

		@Override
		protected Data load () {

			Data post = new Data();
			post.put("id", 1L);
			post.put("title", "こんにちは");

			Data comment = new Data();
			comment.put("id", 5L);

			Data data = new Data();
			data.put("post", post);
			data.put("comment", comment);

			return data;

		}

		@Override
		protected void setData (Data data) {

			putAll(data.flattenTable("post"));

		}

		@Override
		protected void setRelationData (Data data) {

			// テーブル名と同じキーで子を置く
			put("comment", new NestComments(1));

		}

		@Override
		protected String hashKey () { return "CollidingPost"; }

	}

	@Test
	@DisplayName("テーブル名と同じキーで子を置いていても、子が消えない")
	void doesNotEatChildOnTableName () {

		String json = new CollidingPost().getJsonString(TableNest.ON);

		// 子は配列のまま残る（列をここへまとめない）
		assertTrue(json.contains("\"comment\":["), json);

		// post のほうは普通にまとまる
		assertTrue(json.contains("\"post\":{"), json);

	}

	/** テーブルと同じ名前で子を置き、そのテーブルの列も平らに持つ実装 */
	static final class CollidingColumns extends AsyncData {

		@Override
		protected Data load () {

			Data post = new Data();
			post.put("id", 1L);
			post.put("title", "こんにちは");

			Data comment = new Data();
			comment.put("comment_id", 5L);
			comment.put("body", "本文");

			Data data = new Data();
			data.put("post", post);
			data.put("comment", comment);

			return data;

		}

		@Override
		protected void setData (Data data) {

			putAll(data.flattenTable("post"));
			putAll(data.flattenTable("comment"));

		}

		@Override
		protected void setRelationData (Data data) {

			// テーブル名と同じキーで子を置く
			put("comment", new NestComments(1));

		}

		@Override
		protected String hashKey () { return "CollidingColumns"; }

	}

	@Test
	@DisplayName("テーブル名を子が使っていたら、その列はまとめずに最上位へ残す")
	void keepsColumnsWhenTableNameIsTaken () {

		String json = new CollidingColumns().getJsonString(TableNest.ON);

		// 子は配列のまま
		assertTrue(json.contains("\"comment\":["), json);

		/*
		 * comment テーブルの列をそこへまとめようとすると、
		 * 作った入れ物を子が上書きして<b>列が消える</b>。まとめずに残す。
		 */
		assertTrue(json.contains("\"comment_id\":5"), json);
		assertTrue(json.contains("\"body\":\"本文\""), json);

		// post のほうは普通にまとまる
		assertTrue(json.contains("\"post\":{"), json);

	}

	/** 子を先に置いてから、同じ名前の列を持つテーブルを足す実装 */
	static final class ChildBeforeTable extends AsyncData {

		@Override
		protected Data load () {

			Data post = new Data();
			post.put("id", 1L);
			post.put("comments", 3L);          // 件数の列

			Data data = new Data();
			data.put("post", post);

			return data;

		}

		@Override
		protected void setData (Data data) {

			// 子が先、テーブルが後
			put("comments", new NestComments(1));
			putAll(data.extractTableData(new TestTable("post")));

		}

		@Override
		protected String hashKey () { return "ChildBeforeTable"; }

	}

	@Test
	@DisplayName("並び順に関わらず、列でないキーはテーブルの中身で潰されない")
	void reservedKeysSurviveRegardlessOfOrder () {

		String json = new ChildBeforeTable().getJsonString(TableNest.OFF);

		assertTrue(json.contains("\"comments\":["), json);
		assertFalse(json.contains("\"comments\":3"), json);

	}

	/** 列と同じ名前で子を置いている実装 */
	static final class ShadowedChild extends AsyncData {

		@Override
		protected Data load () {

			Data post = new Data();
			post.put("id", 1L);
			post.put("comments", 3L);          // 件数の列

			Data data = new Data();
			data.put("post", post);

			return data;

		}

		@Override
		protected void setData (Data data) {

			putAll(data.extractTableData(new TestTable("post")));

		}

		@Override
		protected void setRelationData (Data data) {

			// 列と同じ名前で子を置く
			put("comments", new NestComments(1));

		}

		@Override
		protected String hashKey () { return "ShadowedChild"; }

	}

	@Test
	@DisplayName("列と同じ名前の子は、フラットにしても数値で上書きされない")
	void doesNotOverwriteChildWithColumn () {

		String json = new ShadowedChild().getJsonString(TableNest.OFF);

		// 子（配列）が残る。テーブルの中の comments（件数）で潰さない
		assertTrue(json.contains("\"comments\":["), json);
		assertFalse(json.contains("\"comments\":3"), json);

	}

	// endregion

	// region 読み込み

	@Test
	@DisplayName("組み直しは読み込みを起こす")
	void reshapeLoads () {

		FlatPost post = new FlatPost(1);

		assertFalse(post.isLoaded());

		post.reshape(TableNest.ON);

		assertTrue(post.isLoaded());

	}

	// endregion

}
