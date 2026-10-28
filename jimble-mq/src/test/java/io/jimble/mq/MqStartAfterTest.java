package io.jimble.mq;

import io.jimble.db.DBSource;
import io.jimble.db.TestDataSources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 取り出し役がデータソースを見る順（直前に行を取ったデータソースの次から）
 */
class MqStartAfterTest {

	private static final List<DBSource> SOURCES = List.of(
		TestDataSources.named("a"), TestDataSources.named("b"), TestDataSources.named("c"));

	@Test
	@DisplayName("直前に行を取ったデータソースの次から一回りする")
	void startsAfterLastClaimed () {

		assertEquals(List.of("b", "c", "a"), names(MqQueue.startAfter(SOURCES, "a")));
		assertEquals(List.of("c", "a", "b"), names(MqQueue.startAfter(SOURCES, "b")));
		assertEquals(List.of("a", "b", "c"), names(MqQueue.startAfter(SOURCES, "c")));

	}

	@Test
	@DisplayName("まだ取っていない・もう無いデータソースなら、名前の順のまま")
	void keepsOrderWhenUnknown () {

		assertSame(SOURCES, MqQueue.startAfter(SOURCES, null));
		assertSame(SOURCES, MqQueue.startAfter(SOURCES, "x"));

	}

	@Test
	@DisplayName("データソースが1つなら、これまでと同じ（並べ直さない）")
	void singleSourceIsUnchanged () {

		List<DBSource> single = List.of(TestDataSources.named("a"));

		assertSame(single, MqQueue.startAfter(single, "a"));

	}

	private static List<String> names (List<DBSource> sources) {

		return sources.stream().map(DBSource::name).toList();

	}

}
