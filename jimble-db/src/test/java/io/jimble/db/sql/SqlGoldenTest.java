package io.jimble.db.sql;

import com.typesafe.config.ConfigFactory;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SQL の組み立ての字面が 2.7.4 と同じか（2.7.5）
 *
 * <h2>なぜ要るのか</h2>
 * <p>
 * 2.7.5 で、全列の SELECT 句をテーブルごとに 1 回だけ作る形にし、パラメータを木を 1 回たどって集める形にし、
 * キャッシュのキーの作り方も変えた（どれも要求ごとの CPU を減らすため）。
 * <b>字面が 1 文字でも変わると、SQL 結果キャッシュのキーが変わる</b>——
 * Redis や DB に置いたキャッシュは、上げた日に全部外れ、古い版と新しい版が並ぶ間は別々に溜まる。
 * パラメータの並びがずれれば、別の行を読み書きする。
 * </p>
 *
 * <h2>答えの作り方</h2>
 * <p>
 * {@code golden/sql-2.7.4.txt} は、{@link SqlGoldenCases} を <b>Maven Central の 2.7.4 の jar で動かして</b>書き出したもの。
 * 組み立てを足したら、同じクラスを 2.7.4 の jar で動かして作り直す
 * （{@code io.jimble:jimble-db:2.7.4} に依存する小さなプロジェクトに {@link SqlGoldenCases}・{@link TestSchema}・{@link DslSchema} を写し、
 * 下と同じ設定で {@code SqlGoldenCases.render()} をファイルに書く）。
 * <b>今のソースで作り直してはいけない</b>——今の出力を答えにすると、何も確かめていないのと同じになる。
 * </p>
 */
class SqlGoldenTest {

	@AfterEach
	void restore () {

		Conf.reload();

	}

	@Test
	@DisplayName("SQL・パラメータ・キャッシュのキーが 2.7.4 と 1 文字も変わらない（MySQL / PostgreSQL）")
	void sameAs274 () throws Exception {

		Conf.replace(ConfigFactory.parseString("sql_cache { enabled = true, namespace = golden }"));

		String expected;
		try (InputStream in = SqlGoldenTest.class.getResourceAsStream("/golden/sql-2.7.4.txt")) {
			assertNotNull(in, "golden/sql-2.7.4.txt がありません");
			expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		// 2 回目は、全列の字面をテーブルが覚えたあと（1 回目と同じでなければならない）
		assertEquals(expected, SqlGoldenCases.render());
		assertEquals(expected, SqlGoldenCases.render());

	}

}
