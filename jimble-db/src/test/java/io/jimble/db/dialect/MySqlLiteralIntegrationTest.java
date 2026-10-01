package io.jimble.db.dialect;

import com.typesafe.config.Config;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * MySQL の文字列リテラルで抜けられないことを、実際の MySQL で確かめる（D-205）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。PostgreSQL では飛ばす。</p>
 */
@Tag("db")
class MySqlLiteralIntegrationTest {

	private Config originalConf;

	@BeforeEach
	void load () {

		Conf.reload();
		originalConf = Conf.conf().config();
		DBUtil.load(originalConf, MySqlLiteralIntegrationTest.class);

		// 書いていなければ mysql（方言の既定）
		String product = Dialects.productNameOrNull(DBUtil.getMainDataSource().conf().product());
		Assumptions.assumeTrue(product == null || MySqlDialect.NAME.equals(product), "MySQL だけ");

	}

	@AfterEach
	void stop () {

		DBUtil.stop();
		Conf.replace(originalConf);

	}

	private static String one (String expression) {

		DB db = DBUtil.getMainDB();
		Optional<Data> row = db.select("SELECT " + expression + " AS v");
		return row.map(r -> r.getString("v")).orElse(null);

	}

	@Test
	@DisplayName("dateFormat：\\' のあとに SQL を書いても、書式の文字として返るだけ")
	void dateFormat () {

		String payload = "\\' , 'pwned') -- ";

		StringBuilder sb = new StringBuilder();
		MySqlDialect.INSTANCE.dateFormat(sb, () -> sb.append("NOW()"), payload);

		assertEquals(payload, one(sb.toString()));

	}

	@Test
	@DisplayName("groupConcat の区切り：\\' でリテラルから抜けられない")
	void groupConcat () {

		StringBuilder sb = new StringBuilder();
		MySqlDialect.INSTANCE.groupConcat(sb, () -> sb.append("v"), "\\'", false);

		assertEquals("a\\'b", one("(SELECT " + sb + " FROM (SELECT 'a' AS v UNION ALL SELECT 'b') t)"));

	}

	@Test
	@DisplayName("jsonExtract：パスに SQL を書いても実行されない（パスとして読まれるか、パスの誤りになる）")
	void jsonExtract () {

		StringBuilder sb = new StringBuilder();
		MySqlDialect.INSTANCE.jsonExtract(sb, () -> sb.append("'{\"a\":1}'"), "$.a') , ('pwned') -- ", true);

		String value;
		try {
			value = one(sb.toString());
		} catch (RuntimeException ex) {
			// パスの誤り（Invalid JSON path expression）。SQL として抜けてはいない
			return;
		}

		assertNotEquals("pwned", value);

	}

}
