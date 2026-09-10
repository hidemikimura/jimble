package io.jimble.load;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表の行の作り方（{@link Result}／要件 NF-P-08）
 *
 * <p>
 * <b>見張っているのは「印が出る行と出ない行」だけである。</b>
 * 印はどの行を読み飛ばしてよいかの目印なので、
 * <b>いつも出る／一度も出ない</b>のどちらになっても、目印としては死ぬ。
 * </p>
 */
class ResultTest {

	/**
	 * 適当な結果を1つ作る
	 *
	 * @param connections	接続数
	 * @return	結果
	 */
	private static Result result (int connections) {

		return new Result(
			URI.create("http://127.0.0.1:9010/")
			, connections
			, 10.0, 1000, 0, 100.0, 1.0, 2.0, 3.0, 4.0);

	}

	@Test
	@DisplayName("接続数がコア数を超えたら印を付ける")
	void marksWhenOverCores () {

		assertEquals("", Result.overCoresNote(8, 10), "10 コアに 8 接続は超えていない");
		assertEquals("", Result.overCoresNote(10, 10), "ちょうどは超えていない");

		assertTrue(Result.overCoresNote(64, 10).contains(Result.OVER_CORES));
		assertTrue(Result.overCoresNote(64, 10).contains("10"), "いくつを超えたのかを書くこと");

	}

	@Test
	@DisplayName("コア数が分からないときは印を付けない")
	void noMarkWithoutCores () {

		/*
		 * <b>0 や負の数で「全部の行に印」になるほうが害が大きい。</b>
		 * 全部に付いた印は、付いていないのと同じである
		 */
		assertEquals("", Result.overCoresNote(256, 0));
		assertEquals("", Result.overCoresNote(256, -1));

	}

	@Test
	@DisplayName("印は行の末尾に出る")
	void markAppearsInRow () {

		assertTrue(result(64).toRow("x", 10).endsWith(Result.overCoresNote(64, 10))
			, result(64).toRow("x", 10));

		assertFalse(result(8).toRow("x", 10).contains(Result.OVER_CORES)
			, result(8).toRow("x", 10));

	}

	@Test
	@DisplayName("失敗の有無は印とは別に必ず出る")
	void errorsAreShownRegardless () {

		Result failed = new Result(
			URI.create("http://127.0.0.1:9010/"), 64, 10.0, 1000, 3, 100.0, 1.0, 2.0, 3.0, 4.0);

		String row = failed.toRow("x", 10);

		assertTrue(row.contains("失敗 3 ★"), row);
		assertTrue(row.contains(Result.OVER_CORES), "印のせいで失敗が消えてはいけない: " + row);

	}

}
