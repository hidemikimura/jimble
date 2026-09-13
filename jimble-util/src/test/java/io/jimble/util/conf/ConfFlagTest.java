package io.jimble.util.conf;

import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * リクエストごとに読み直さない設定値（要件 D-167）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>覚えるものは、忘れ方のほうが難しい。</b>
 * 設定を差し替えたのに<b>古い値のまま固まる</b>と、
 * 「設定を変えたのに効かない」——<b>いちばん原因を探しにくい壊れ方</b>になる。
 * </p>
 *
 * <p>
 * ここが守っているのは<b>「入れ替えたら読み直す」</b>ことだけである。
 * 速さは {@code Bench} が見ている。
 * </p>
 */
class ConfFlagTest {

	@AfterEach
	void reset () {

		Conf.reload();

	}

	@Test
	@DisplayName("書いていなければ既定")
	void theDefaultWhenAbsent () {

		Conf.replace(ConfigFactory.parseString(""));

		assertTrue(ConfFlag.of("nothing.here", true).get());
		assertFalse(ConfFlag.of("nothing.here", false).get());

	}

	@Test
	@DisplayName("書いてあればその値")
	void theWrittenValue () {

		Conf.replace(ConfigFactory.parseString("a { b = false }"));

		assertFalse(ConfFlag.of("a.b", true).get());

	}

	@Test
	@DisplayName("D-167 設定を差し替えたら読み直す")
	void replacingTheConfigIsPickedUp () {

		ConfFlag flag = ConfFlag.of("a.b", true);

		Conf.replace(ConfigFactory.parseString("a { b = true }"));
		assertTrue(flag.get());

		/*
		 * <b>ここが効かないと「設定を変えたのに効かない」になる。</b>
		 * 覚えたまま返し続けるのがいちばんまずい壊れ方で、
		 * <b>例外もログも出ない</b>。
		 */
		Conf.replace(ConfigFactory.parseString("a { b = false }"));
		assertFalse(flag.get(), "差し替えたのに古い値を返しています");

		Conf.replace(ConfigFactory.parseString("a { b = true }"));
		assertTrue(flag.get(), "戻したのに古い値を返しています");

	}

	@Test
	@DisplayName("D-167 reload しても読み直す")
	void reloadIsPickedUp () {

		ConfFlag flag = ConfFlag.of("a.b", true);

		Conf.replace(ConfigFactory.parseString("a { b = false }"));
		assertFalse(flag.get());

		// reload は設定を捨てて読み直すので、書いていない状態＝既定に戻る
		Conf.reload();

		assertTrue(flag.get(), "reload のあとも古い値を返しています");

	}

	@Test
	@DisplayName("何度読んでも同じ値")
	void repeatedReadsAgree () {

		Conf.replace(ConfigFactory.parseString("a { b = false }"));

		ConfFlag flag = ConfFlag.of("a.b", true);

		for (int i = 0; i < 1000; i++) {
			assertFalse(flag.get());
		}

	}

	@Test
	@DisplayName("キーは名乗る")
	void itKnowsItsKey () {

		assertEquals("a.b", ConfFlag.of("a.b", true).key());

	}

	// region ここで固定していないこと

	/*
	 * - <b>速さ</b>は見ていない（{@code Bench} の仕事）。
	 *   ここで測ると<b>共用ランナーのぶれで理由なく落ちる</b>
	 * - <b>複数のスレッドから同時に読んだとき</b>も見ていない。
	 *   覚えているものは<b>「読んだときの Conf」と「値」を1つの record にまとめて
	 *   volatile で持っている</b>ので、<b>片方だけ新しい組み合わせは見えない</b>——
	 *   最悪でも<b>同じ値を2回読む</b>だけである
	 */

	// endregion

}
