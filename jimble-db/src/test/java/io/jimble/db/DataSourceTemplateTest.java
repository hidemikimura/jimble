package io.jimble.db;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 同じ作りのデータソースを、ひな形から書く（HOCON の {@code ${}}）
 *
 * <p>
 * 並べる店の DB はどれもプールとサブの組が同じなので、<b>ひな形を {@code db { }} の外に置き、
 * {@code ${db_templates.tenant} { url = ... }} で写して上書きする</b>。jimble に専用のキーは無い（HOCON で足りる）。
 * ここでは、書き写したときと同じ設定になることを確かめる。
 * </p>
 */
class DataSourceTemplateTest {

	private static final Config TEMPLATED = ConfigFactory.parseString("""
		db_templates {
			tenant {
				driver             = "org.mariadb.jdbc.Driver"
				maximum_pool_size  = 6
				minimum_idle       = 0
				connection_timeout = 3s
				migration_dir      = "shop"
				codegen_name       = "shop"
				required           = false
				framework_tables   = ["db_lock"]
				subs {
					shop  { maximum_pool_size = 4 }
					admin { maximum_pool_size = 2 }
				}
			}
		}
		db {
			main   { main = true, url = "jdbc:x/main" }
			shop_a = ${db_templates.tenant} { url = "jdbc:x/shop_a" }
			shop_b = ${db_templates.tenant} { url = "jdbc:x/shop_b", maximum_pool_size = 8 }
		}
		""").resolve();

	private static final Config WRITTEN_OUT = ConfigFactory.parseString("""
		db {
			main   { main = true, url = "jdbc:x/main" }
			shop_a {
				driver             = "org.mariadb.jdbc.Driver"
				url                = "jdbc:x/shop_a"
				maximum_pool_size  = 6
				minimum_idle       = 0
				connection_timeout = 3s
				migration_dir      = "shop"
				codegen_name       = "shop"
				required           = false
				framework_tables   = ["db_lock"]
				subs {
					shop  { maximum_pool_size = 4 }
					admin { maximum_pool_size = 2 }
				}
			}
			shop_b {
				driver             = "org.mariadb.jdbc.Driver"
				url                = "jdbc:x/shop_b"
				maximum_pool_size  = 8
				minimum_idle       = 0
				connection_timeout = 3s
				migration_dir      = "shop"
				codegen_name       = "shop"
				required           = false
				framework_tables   = ["db_lock"]
				subs {
					shop  { maximum_pool_size = 4 }
					admin { maximum_pool_size = 2 }
				}
			}
		}
		""");

	@Test
	@DisplayName("ひな形から写したデータソースの設定は、書き写したときと同じになる（上書きしたところだけ違う）")
	void templateEqualsWrittenOut () {

		Map<String, DBConf> templated = DBUtil.readDataSourceConfs(TEMPLATED);
		Map<String, DBConf> writtenOut = DBUtil.readDataSourceConfs(WRITTEN_OUT);

		assertEquals(writtenOut.keySet(), templated.keySet(), "ひな形がデータソースとして読まれた");

		for (String name : writtenOut.keySet()) {
			assertEquals(describe(writtenOut.get(name)), describe(templated.get(name)), name);
		}

		assertEquals(8, templated.get("shop_b").maximumPoolSize(), "上書きが効いていない");

	}

	@Test
	@DisplayName("サブの組もひな形から写る")
	void subsAreCopied () {

		for (String name : new String[] { "shop_a", "shop_b" }) {
			assertEquals(WRITTEN_OUT.getConfig("db." + name + ".subs").root().unwrapped()
				, TEMPLATED.getConfig("db." + name + ".subs").root().unwrapped(), name);
		}

	}

	/**
	 * 比べる設定を1行にする
	 *
	 * @param conf	設定
	 * @return	1行
	 */
	private static String describe (DBConf conf) {

		return String.join(" | "
			, conf.driver(), conf.url(), String.valueOf(conf.maximumPoolSize()), String.valueOf(conf.minimumIdle())
			, String.valueOf(conf.connectionTimeout()), conf.migrationDir(), conf.codegenName()
			, String.valueOf(conf.required()), String.valueOf(conf.frameworkTables()), String.valueOf(conf.mq()));

	}

}
