package io.jimble.db.cache;

import io.jimble.db.cache.exception.CacheNotFoundException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * キャッシュが無いときの例外（要件 D-164）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>例外は、出たときに読む人しか見ない。</b>
 * だから<b>出るまで誰も中身を確かめない</b>——
 * 「どのキーが無かったのか」が書かれていない例外は、
 * <b>出た瞬間に調査が振り出しに戻る</b>。
 * </p>
 */
class CacheNotFoundExceptionTest {

	@Test
	@DisplayName("どのキーが無かったのかが、文面に出る")
	void theMessageNamesTheKey () {

		CacheNotFoundException ex =
			new CacheNotFoundException(new CacheData("top:2026-09", "top"));

		assertTrue(ex.getMessage().contains("top:2026-09"), ex.getMessage());
		assertTrue(ex.getMessage().contains("top"), ex.getMessage());

	}

	@Test
	@DisplayName("グループが無くても文面は出る")
	void withoutAGroupItStillReads () {

		CacheNotFoundException ex =
			new CacheNotFoundException(new CacheData("top:2026-09", (String) null));

		assertTrue(ex.getMessage().contains("top:2026-09"), ex.getMessage());

	}

	@Test
	@DisplayName("捕まえなくてよい例外である")
	void itIsUnchecked () {

		/*
		 * <b>検査例外にすると、キャッシュを触る全部の口に {@code throws} が伝染する。</b>
		 * 「無ければ作る」で片付く場面がほとんどなので、
		 * <b>書かせない側に倒している</b>。
		 */
		assertInstanceOf(RuntimeException.class
			, new CacheNotFoundException(new CacheData("k", "g")));

	}

	// region ここで固定していないこと

	/*
	 * - <b>文面の書き方そのもの</b>は固定していない。見ているのは
	 *   <b>キーとグループが出ること</b>だけである
	 */

	// endregion

}
