package io.jimble.db.data;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.internal.JsonArrayList;
import io.jimble.util.json.Dson;
import io.jimble.util.log.Log;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON の列の読み方（要件 D-189）
 *
 * <p>
 * JSON の列は読んだ時点で Data / List になる。<b>配列の列を getString すると先頭の要素だけが返る</b>——
 * JSON の文字が返ると思って書くと、黙って違う値になる（アプリ側の AI が実際に踏んだ）。
 * 返り値は変えずに、<b>そうしたときに1度だけ言う</b>ことを固める。
 * </p>
 */
@Tag("db")
class JsonColumnIntegrationTest {

	@BeforeAll
	static void setUp () {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), JsonColumnIntegrationTest.class), "DB に接続できませんでした");

		DB db = DBUtil.getMainDB();
		String json = MySqlDialect.NAME.equals(db.dialect().name()) ? "json" : "jsonb";

		db.execute("DROP TABLE IF EXISTS json_column_item");
		db.execute("CREATE TABLE json_column_item (id bigint primary key, tags %s, options %s)".formatted(json, json));
		db.execute("INSERT INTO json_column_item VALUES (1, '[\"a\",\"b\"]', '{\"k\":\"v\"}')");

	}

	@AfterAll
	static void tearDown () {

		DBUtil.getMainDB().execute("DROP TABLE IF EXISTS json_column_item");
		DBUtil.stop();

	}

	@Test
	@DisplayName("D-189 配列の列は List（印つき）、getString は先頭の要素を返し、そのとき1度だけ言う")
	void arrayColumn () {

		List<String> warns = new CopyOnWriteArrayList<>();
		Log.sink((loggerName, level, message, data, throwable) -> {
			if (level.toInt() >= org.slf4j.event.Level.WARN.toInt()) {
				warns.add(message);
			}
		});

		JsonArrayList.resetWarning();

		try {

			Data row = DBUtil.getMainDB().select("SELECT * FROM json_column_item WHERE id = ?", 1);

			assertInstanceOf(JsonArrayList.class, row.get("tags"));
			assertEquals(List.of("a", "b"), row.getStringList("tags"));
			assertEquals("v", row.getData("options").getString("k"));

			// 返り値は変えない（1.4 までと同じ）
			assertEquals("a", row.getString("tags"));
			assertEquals("a", row.getString("tags"));

			assertEquals(1, warns.stream().filter(message -> message.contains("JSON の配列の列 tags")).count(), warns.toString());
			assertTrue(warns.stream().anyMatch(message -> message.contains("db.md")), warns.toString());

			// JSON に戻しても配列のまま
			assertTrue(Dson.encodes(row).contains("\"tags\":[\"a\",\"b\"]"), Dson.encodes(row));

		} finally {
			Log.resetSink();
		}

	}

}
