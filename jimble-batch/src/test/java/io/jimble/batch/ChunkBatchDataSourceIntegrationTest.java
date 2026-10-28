package io.jimble.batch;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * チャンクバッチの読み書きを、主以外のデータソースにする（{@code AbstractChunkBatch#db(BatchArgs)}）
 *
 * <p>
 * 同じ表の作りの DB を並べたとき、店の DB ごとにチャンク処理を回すため。
 * 引数でデータソースを受け取り、{@code db(args)} で返す。バッチの管理の表（実行の記録）は主のまま。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class ChunkBatchDataSourceIntegrationTest {

	/** 主 */
	private static final String MAIN = "jimble_test";

	/** 店の DB */
	private static final String SHOP = "jimble_test_shop";

	/**
	 * 引数 data_source のデータソースで、chunk_src を chunk_dst に写すバッチ
	 */
	public static class ShopCopyBatch extends AbstractChunkBatch<Data> {

		@Override public String batchName () { return "テスト（店の DB ごとのチャンク）"; }

		@Override public int chunkSize () { return 2; }

		@Override
		protected DB db (BatchArgs args) {

			return DBUtil.getDB(args.cliArgs().getString("data_source"));

		}

		@Override
		protected Iterator<Data> reader (BatchArgs args, DB db) {

			return KeyPagingReader.of("id", 0L, chunkSize(), (lastKey, limit) -> db.selectList("""
					SELECT id, name FROM chunk_src WHERE id > ? ORDER BY id ASC LIMIT ?
				""", lastKey, limit));

		}

		@Override
		protected void write (List<Data> items, DB db) {

			for (Data item : items) {
				db.insert("INSERT INTO chunk_dst (id, name) VALUES (?, ?)", item.getLong("id"), item.getString("name"));
			}

		}

	}

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		Config base = Conf.conf().config();
		Config main = base.getConfig("db." + MAIN);
		Config shop = main
			.withValue("main", ConfigValueFactory.fromAnyRef(false))
			.withValue("url", ConfigValueFactory.fromAnyRef(main.getString("url").replaceFirst("/" + MAIN + "(\\?|$)", "/" + SHOP + "$1")));
		if (main.hasPath("create_database_sql")) {
			shop = shop.withValue("create_database_sql"
				, ConfigValueFactory.fromAnyRef(main.getString("create_database_sql").replace(MAIN, SHOP)));
		}

		Conf.replace(base.withValue("db." + SHOP, shop.root()));
		DBUtil.load(Conf.conf().config(), ChunkBatchDataSourceIntegrationTest.class);

		BatchTables.install(DBUtil.getMainDB());

		for (String name : new String[] { MAIN, SHOP }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("CREATE TABLE IF NOT EXISTS chunk_src (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(40) NOT NULL)");
				db.execute("CREATE TABLE IF NOT EXISTS chunk_dst (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(40) NOT NULL)");
			}
		}

	}

	@AfterAll
	static void stopDataSource () {

		for (String name : new String[] { MAIN, SHOP }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("DROP TABLE IF EXISTS chunk_src");
				db.execute("DROP TABLE IF EXISTS chunk_dst");
			}
		}

		BatchRegistry.clear();
		DBUtil.stop();
		Conf.reload();

	}

	@BeforeEach
	void clean () {

		try (DB db = DBUtil.getMainDB()) {
			db.execute("TRUNCATE TABLE batch_master");
			db.execute("TRUNCATE TABLE batch_history");
			db.execute("TRUNCATE TABLE batch_execute_info");
		}

		for (String name : new String[] { MAIN, SHOP }) {
			try (DB db = DBUtil.getDB(name)) {
				db.execute("TRUNCATE TABLE chunk_src");
				db.execute("TRUNCATE TABLE chunk_dst");
			}
		}

		BatchExecutor.releaseAll();
		BatchRegistry.clear();
		BatchRegistry.add(ShopCopyBatch::new);
		BatchRegistry.sync(DBUtil.getMainDB());

	}

	@Test
	@DisplayName("db(args) を上書きしたチャンク処理は、そのデータソースの表を読み書きし、実行の記録は主に残る")
	void readsAndWritesTheChosenDataSource () {

		try (DB shop = DBUtil.getDB(SHOP); DB main = DBUtil.getMainDB()) {
			for (long id = 1; id <= 5; id++) {
				shop.insert("INSERT INTO chunk_src (id, name) VALUES (?, ?)", id, "shop" + id);
			}
			main.insert("INSERT INTO chunk_src (id, name) VALUES (?, ?)", 1L, "main1");
		}

		BatchArgs args = BatchExecutor.parseArgs(new String[] { "class=" + ShopCopyBatch.class.getName(), "data_source=" + SHOP });

		assertEquals(BatchResult.completed, BatchExecutor.execute(args));

		try (DB shop = DBUtil.getDB(SHOP); DB main = DBUtil.getMainDB()) {

			assertEquals(5, shop.select("SELECT count(*) AS n FROM chunk_dst").orElseThrow().getLong("n"), "店の DB に写っていない");
			assertEquals(0, main.select("SELECT count(*) AS n FROM chunk_dst").orElseThrow().getLong("n"), "主に写した");

			Data history = main.select("SELECT * FROM batch_history WHERE class_name = ? ORDER BY id DESC LIMIT 1"
				, ShopCopyBatch.class.getName()).orElse(null);
			assertNotNull(history, "実行の記録が主に無い");

		}

	}

}
