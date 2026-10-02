package io.jimble.db.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 大きな中身を置くファイルの名前に、キーをそのまま使わない（D-235）
 */
class CacheFileNameTest {

	@Test
	@DisplayName("../ を含むキーでも、一時ディレクトリの外を指さない")
	void noTraversal () {

		String name = AbstractCache.cacheFileName("../../etc/cron.d/x", 1700000000000L);

		assertFalse(name.contains("/") || name.contains("\\\\") || name.contains(".."), name);
		assertTrue(name.matches("[0-9a-f]+"), name);
		assertNotEquals(name, AbstractCache.cacheFileName("../../etc/cron.d/x", 1700000000001L));

	}

}
