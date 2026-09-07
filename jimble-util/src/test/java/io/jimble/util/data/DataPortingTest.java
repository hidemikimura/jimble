package io.jimble.util.data;

import io.jimble.util.data.definition.IColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 移送した Data が動くことの確認
 *
 * <p>
 * 「事故になりやすい仕様」（`jooby-base-db` スキル）を移送後も保っているかを固定する。
 * </p>
 */
class DataPortingTest {

	/* site.id */
	private static final IColumn SITE_ID = TestTable.SITE.column("id", long.class);

	/* site.name */
	private static final IColumn SITE_NAME = TestTable.SITE.column("name", String.class);

	/* feed.title */
	private static final IColumn FEED_TITLE = TestTable.FEED.column("title", String.class);

	@Test
	@DisplayName("putData(IColumn, 値) は常にテーブル名の下にネストする")
	void putDataNests () {

		Data data = new Data();
		data.putData(SITE_ID, 1L);
		data.putData(SITE_NAME, "俺的まとめ");

		Data site = data.getData("site");

		assertEquals(1L, site.getLong("id"));
		assertEquals("俺的まとめ", site.getString("name"));

	}

	@Test
	@DisplayName("IColumn 版の取得はテーブル名を辿る / 文字列キー版は静かに null を返す")
	void columnAccessorVsStringKey () {

		Data data = new Data();
		data.putData(SITE_NAME, "俺的まとめ");

		assertEquals("俺的まとめ", data.getString(SITE_NAME), "Column 版は取れる");
		assertNull(data.getString("name"), "文字列キー版は null（例外にはならない）");

	}

	@Test
	@DisplayName("複数テーブルの値がテーブル単位で分かれる")
	void multipleTables () {

		Data data = new Data();
		data.putData(SITE_NAME, "サイト名");
		data.putData(FEED_TITLE, "記事タイトル");

		assertEquals("サイト名", data.getString(SITE_NAME));
		assertEquals("記事タイトル", data.getString(FEED_TITLE));
		assertEquals(2, data.size());

	}

	@Test
	@DisplayName("flattenTable は該当テーブルが無いと null を返す")
	void flattenTableReturnsNullWhenAbsent () {

		Data data = new Data();
		data.putData(SITE_NAME, "サイト名");

		assertEquals("サイト名", data.flattenTable(TestTable.SITE).getString("name"));
		assertNull(data.flattenTable(TestTable.FEED), "無いテーブルは null。putAll に渡すと NPE になる");

	}

	@Test
	@DisplayName("挿入順が保たれる")
	void insertionOrderIsKept () {

		Data data = new Data();
		data.put("c", 3);
		data.put("a", 1);
		data.put("b", 2);

		assertEquals(List.of("c", "a", "b"), List.copyOf(data.keySet()));

	}

	@Test
	@DisplayName("型付き getter が動く")
	void typedGetters () {

		Data data = new Data();
		data.put("num", "42");
		data.put("flag", "true");
		data.put("dec", "1.5");

		assertEquals(42, data.getInt("num"));
		assertEquals(42L, data.getLong("num"));
		assertTrue(data.getBoolean("flag"));
		assertEquals(1.5d, data.getDouble("dec"));

	}

	@Test
	@DisplayName("JSON のラウンドトリップ（日本語・ネスト・配列）")
	void jsonRoundTrip () {

		Data nested = new Data();
		nested.put("title", "記事タイトル");

		Data data = new Data();
		data.put("name", "俺的まとめ");
		data.put("count", 3);
		data.put("feed", nested);
		data.put("tags", List.of("あ", "い"));

		String json = data.getJsonString();
		Data restored = Data.fromJsonString(json);

		assertEquals("俺的まとめ", restored.getString("name"));
		assertEquals(3, restored.getInt("count"));
		assertEquals("記事タイトル", restored.getData("feed").getString("title"));
		assertEquals(List.of("あ", "い"), restored.getStringList("tags"));

	}

	@Test
	@DisplayName("Optional 版は無ければ空を返す（null を返さない）")
	void optionalAccessors () {

		Data data = new Data();

		assertTrue(data.getDataOptional("none").isEmpty());
		assertTrue(data.getStringListOptional("none").isEmpty());

	}

}
