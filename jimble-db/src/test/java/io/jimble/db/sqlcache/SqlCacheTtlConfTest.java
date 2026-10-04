package io.jimble.db.sqlcache;

import com.typesafe.config.ConfigFactory;
import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code sql_cache.ttl} の単位の書き忘れを、起動時に落とすか（D-267）
 *
 * <p>
 * <b>読むのは入れるときなので、{@code ttl = 300} は入れるたびに失敗してログに出るだけだった。</b>
 * {@code selectCached} は DB から引いた結果を返すので、アプリは動く。<b>キャッシュが黙って効かない</b>。
 * </p>
 */
class SqlCacheTtlConfTest {

	@AfterEach
	void reset () {

		Conf.reload();

	}

	@Test
	@DisplayName("D-267 単位の無い ttl は DBUtil.load で落ちる（繋ぎにいく前に。切っていても見る）")
	void bareNumberFailsAtLoad () {

		for (String enabled : new String[] {"true", "false"}) {

			replace("""
				sql_cache { enabled = %s, ttl = 300 }
				db { main { url = "jdbc:mysql://127.0.0.1:1/nothing" } }
				""".formatted(enabled));

			IllegalStateException thrown = assertThrows(IllegalStateException.class
				, () -> DBUtil.load(Conf.conf().config(), SqlCacheTtlConfTest.class)
				, "enabled = " + enabled + " で、単位の無い ttl を通しています");

			// どのキーか、どう書けばよいかまで言う
			assertTrue(thrown.getMessage().contains("sql_cache.ttl"), thrown.getMessage());
			assertTrue(thrown.getMessage().contains("30m"), thrown.getMessage());

		}

	}

	@Test
	@DisplayName("単位つきなら読める。0s は無期限、書かなければ 5 分")
	void withUnit () {

		replace("sql_cache.ttl = 90s");
		assertEquals(Duration.ofSeconds(90), SqlCacheConf.ttl());

		replace("sql_cache.ttl = 0s");
		assertEquals(Duration.ZERO, SqlCacheConf.ttl());

		replace("");
		assertEquals(Duration.ofMinutes(5), SqlCacheConf.ttl());

	}

	private static void replace (String hocon) {

		Conf.reload();
		Conf.replace(ConfigFactory.parseString(hocon).withFallback(Conf.conf().config()));

	}

}
