package io.jimble.web.router;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Column からパスパラメータを作る（要件 F-R-18）
 *
 * <p>
 * ルートに書く名前とカラム名がずれると、
 * <b>「{@code {siteId}} で登録して {@code site_id} で読む」</b>のような取り違えが起きる。
 * どちらも文字列なので、コンパイルでは分からず、実行しても null が返るだけになる。
 * {@code Column.urlPathPlaceholder()} を通せば両者が同じ場所から出る。
 * </p>
 *
 * <p>
 * 文字列の形は {@code jimble-db} の {@code SqlDslTest} で見ている。
 * ここでは<b>その文字列で実際にルートが引けて、値が読めるところまで</b>を見る。
 * </p>
 */
class ColumnPathParameterTest {

	/* テスト用のスキーマ */
	private static final class Schema extends AbstractSchema {

		static final Schema INSTANCE = new Schema();

		@Override
		public String name () {

			return "jimble_test";

		}

	}

	/* site テーブル */
	private static final class Site extends Table {

		static final Column id = new Column(instance(), "id", long.class, false, null, true);

		private Site () {

			super(Schema.INSTANCE, "site");

		}

		static Site instance () {

			return new Site();

		}

	}

	/* 何もしないハンドラ */
	private static final Handler NOOP = context -> { };

	@Test
	@DisplayName("Column から作ったパスパラメータでルートが引ける")
	void matchByColumnPlaceholder () {

		Router router = new Router();

		// get("/sites/{site.id}", ...)
		Route route = router.get("/sites/" + Site.id.urlPathPlaceholder(), NOOP);

		RouteMatch match = router.match("GET", "/sites/42");

		assertSame(route, match.route());
		assertEquals("42", match.variables().get("site.id"), "カラム名で読めない");

	}

	@Test
	@DisplayName("パスパラメータはリクエストのパラメータとしても読める")
	void readableFromRequest () {

		Router router = new Router();
		router.get("/sites/" + Site.id.urlPathPlaceholder(), NOOP);

		try (WebContext context = Fakes.context("GET", "/sites/42")) {

			context.route(router.match("GET", "/sites/42"));

			/*
			 * パスパラメータはクエリ・フォームと同じ入れ物に入る（要件 F-W-01）。
			 *
			 * ここでキーは "site.id" のままではなく、"." で分けて
			 * {"site": {"id": "42"}} になる（要件 F-W-03）。
			 * SELECT の結果がテーブル名でネストするのと同じ形なので（要件 F-D-02）、
			 * <b>Column で読めば行と同じ書き方になる</b>（要件 F-D-22）。
			 */
			Data all = context.request().bodyAll();

			assertTrue(all.containsKey("site"), all.keySet().toString());

			// 登録も取得も Column から出るので、名前がずれない（要件 F-R-18）
			assertEquals(42L, all.getLong(Site.id));

			// 素の書き方でも読める
			assertEquals("42", all.getData("site").getString("id"));

		}

	}

}
