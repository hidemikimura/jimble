package io.jimble.db.async;

import io.jimble.core.context.BatchContext;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.TestSchema;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.data.async.AsyncList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 遅延読み込みが実 DB に対して動くことの確認（要件 F-A-01〜05 / F-A-10）
 *
 * <p>
 * <b>開発用 DB が必要</b>（要件 D-16）。
 * </p>
 *
 * <pre>
 * ./gradlew :jimble-db:dbTest
 * </pre>
 *
 * <p>
 * ここでは<b>「何本 SQL が飛んだか」を Context の集計で数える</b>（要件 F-D-17 / NF-O-02）。
 * 遅延読み込みは「いつ SQL が飛ぶか」が仕様なので、そこを固定しないと意味がない。
 * </p>
 */
@Tag("db")
class AsyncIntegrationTest {

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), AsyncIntegrationTest.class)
			, "DB に接続できませんでした");

		DB db = DBUtil.getMainDB();

		db.execute("DROP TABLE IF EXISTS site");
		db.execute("""
			CREATE TABLE site (
				id          bigint unsigned auto_increment comment 'ID' primary key,
				group_id    bigint unsigned not null comment 'グループID',
				name        varchar(250)    null comment 'サイト名',
				feed_count  bigint unsigned default 0 not null comment 'フィード数',
				deleted_at  datetime        null comment '削除日時'
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment 'サイト'
			""");

		db.execute("DROP TABLE IF EXISTS feed");
		db.execute("""
			CREATE TABLE feed (
				id       bigint unsigned auto_increment comment 'ID' primary key,
				site_id  bigint unsigned not null comment 'サイトID',
				title    varchar(250)    null comment 'タイトル'
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment 'フィード'
			""");

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		DB db = DBUtil.getMainDB();

		db.execute("TRUNCATE TABLE site");
		db.execute("TRUNCATE TABLE feed");

		for (int i = 1; i <= 3; i++) {

			long siteId = db.insert(
				SQL.insert(TestSchema.Site.instance())
					.value(TestSchema.Site.group_id, 1L)
					.value(TestSchema.Site.name, "サイト" + i));

			for (int j = 1; j <= 2; j++) {
				db.insert(
					SQL.insert(TestSchema.Feed.instance())
						.value(TestSchema.Feed.site_id, siteId)
						.value(TestSchema.Feed.title, "記事%d-%d".formatted(i, j)));
			}

		}

	}

	// region 遅延読み込みの木

	/**
	 * 1サイト分のフィード（サイトごとに1本引く = N+1 になる形）
	 */
	static class FeedList extends AsyncList {

		private final long siteId;

		FeedList (long siteId) {

			this.siteId = siteId;

		}

		@Override
		protected List<Data> load () {

			return DBUtil.getMainDB().selectList(
				SQL.select()
					.from(TestSchema.Feed.instance())
					.where(TestSchema.Feed.site_id.eq(siteId))
					.orderBy(TestSchema.Feed.id));

		}

		@Override
		protected void setData (Data data) {

			add(data.getString(TestSchema.Feed.title));

		}

		@Override
		protected String hashKey () {

			return "FeedList:" + siteId;

		}

	}

	/**
	 * グループのサイト一覧
	 */
	static class SiteList extends AsyncList {

		private final long groupId;

		private final boolean batched;

		SiteList (long groupId, boolean batched) {

			this.groupId = groupId;
			this.batched = batched;

		}

		@Override
		protected List<Data> load () {

			return DBUtil.getMainDB().selectList(
				SQL.select()
					.from(TestSchema.Site.instance())
					.where(TestSchema.Site.group_id.eq(groupId))
					.orderBy(TestSchema.Site.id));

		}

		@Override
		protected void setData (Data data) {

			Data site = new Data()
				.putData("id", data.getLong(TestSchema.Site.id))
				.putData("name", data.getString(TestSchema.Site.name));

			if (!batched) {
				// サイトごとに1本引く枝をぶら下げる
				site.putData("feeds", new FeedList(data.getLong(TestSchema.Site.id)));
			}

			add(site);

		}

		/**
		 * {@inheritDoc}
		 *
		 * <p>
		 * 全件の {@link #setData(Data)} が終わってから1回だけ呼ばれる（要件 F-A-03）。
		 * <b>ここで IN 句 1本にまとめる。</b>
		 * </p>
		 */
		@Override
		protected void setRelationData (List<Data> dataList) {

			if (!batched) {
				return;
			}

			List<Object> siteIds = new ArrayList<>();
			for (Data data : dataList) {
				siteIds.add(data.getLong(TestSchema.Site.id));
			}

			List<Data> feeds = DBUtil.getMainDB().selectList(
				SQL.select()
					.from(TestSchema.Feed.instance())
					.where(TestSchema.Feed.site_id.in(siteIds))
					.orderBy(TestSchema.Feed.id));

			for (Object element : loadedValues()) {

				Data site = (Data) element;
				List<Object> titles = new ArrayList<>();

				for (Data feed : feeds) {
					if (feed.getLong(TestSchema.Feed.site_id) == site.getLong("id")) {
						titles.add(feed.getString(TestSchema.Feed.title));
					}
				}

				site.putData("feeds", titles);

			}

		}

		@Override
		protected String hashKey () {

			return "SiteList:" + groupId;

		}

	}

	// endregion

	/**
	 * SQL を何本投げたかを数える
	 *
	 * @param name	名前
	 * @param body	処理
	 * @return	本数
	 */
	private long countSql (String name, Runnable body) {

		try (BatchContext context = new BatchContext(name)) {
			context.run(body);
			return context.sqlExecuteCount();
		}

	}

	@Test
	@DisplayName("木を組み立てただけでは SQL が飛ばない")
	void buildingTreeIssuesNoSql () {

		long sql = countSql("組み立て", () -> {

			SiteList sites = new SiteList(1L, false);

			Data page = new Data()
				.putData("group_id", 1L)
				.putData("sites", sites);

			// 触らずに状態だけ見る（要件 F-A-04）
			assertFalse(sites.isLoaded());
			assertEquals(0, sites.loadedSize());
			assertTrue(page.toString().contains("SiteList(未読み込み)"), page.toString());

		});

		assertEquals(0, sql, "組み立てただけで SQL が飛んでいる");

	}

	@Test
	@DisplayName("触った枝だけ読む")
	void loadsOnlyTouchedBranch () {

		long sql = countSql("サイト名だけ", () -> {

			SiteList sites = new SiteList(1L, false);

			for (Object element : sites) {
				// フィードには触らない
				((Data) element).getString("name");
			}

		});

		assertEquals(1, sql, "触っていない枝まで読んでいる: " + sql);

	}

	@Test
	@DisplayName("枝を全部たどると N+1 になる（先読みが Phase 2 の理由）")
	void fullTraversalIsNPlusOne () {

		/*
		 * これは仕様である。要件 F-A-06（先読み）は Phase 2 なので、
		 * <b>いまは N+1 が起きる</b>。起きることをテストで固定しておかないと、
		 * Phase 2 で先読みを入れたときに「効いたのかどうか」が分からない。
		 */
		long sql = countSql("全部たどる", () -> {

			SiteList sites = new SiteList(1L, false);

			for (Object element : sites) {
				// ここで枝ごとに1本ずつ飛ぶ
				((List<?>) ((Data) element).get("feeds")).size();
			}

		});

		// サイト1本 + サイトごとに1本
		assertEquals(4, sql, "本数が想定と違う: " + sql);

	}

	@Test
	@DisplayName("setRelationData でまとめれば 2 本で済む（要件 F-A-03）")
	void relationDataBatchesQueries () {

		long sql = countSql("まとめて引く", () -> {

			SiteList sites = new SiteList(1L, true);

			for (Object element : sites) {
				((List<?>) ((Data) element).get("feeds")).size();
			}

		});

		assertEquals(2, sql, "本数が想定と違う: " + sql);

	}

	@Test
	@DisplayName("まとめても結果は同じ")
	void sameResultEitherWay () {

		try (BatchContext context = new BatchContext("突き合わせ")) {
			context.run(() -> {

				Data lazy = new Data().putData("sites", new SiteList(1L, false));
				Data batched = new Data().putData("sites", new SiteList(1L, true));

				assertEquals(batched.getJsonString(), lazy.getJsonString());

			});
		}

	}

	@Test
	@DisplayName("JSON にすると木が読まれる")
	void jsonLoadsTree () {

		try (BatchContext context = new BatchContext("JSON")) {
			context.run(() -> {

				SiteList sites = new SiteList(1L, false);

				assertFalse(sites.isLoaded());
				assertEquals("SiteList(未読み込み)", sites.toString(), "toString で読んでいる");

				String json = new Data().putData("sites", sites).getJsonString();

				assertTrue(json.contains("記事1-1"), json);
				assertTrue(json.contains("記事3-2"), json);
				assertTrue(sites.isLoaded());

			});
		}

	}

	@Test
	@DisplayName("読み込まずに入れれば SQL は飛ばない（要件 F-A-10）")
	void putDataIssuesNoSql () {

		long sql = countSql("外から入れる", () -> {

			FeedList feeds = new FeedList(1L);
			feeds.putData(List.of(new Data().putData("feed", new Data().putData("title", "外から"))));

			assertEquals(1, feeds.size());

		});

		assertEquals(0, sql, "外から入れたのに SQL が飛んでいる");

	}

}
