package io.jimble.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB が読み込めていないときの落ち方（要件 F-X-05 / D-130）
 *
 * <p>
 * <b>{@link DBUtil#load} は繋がらなければ原因をログに出して {@code false} を返す。</b>
 * ところが戻り値を見ずに先へ進む書き方ができてしまい、そのときは
 * <b>ずっと後ろで {@code NullPointerException}</b> になっていた——
 * <b>「Cannot read field "conf" because "dbSource" is null」は DB のことを何も言わない</b>。
 * 本当の原因（接続できなかったこと）は何十行も上にある。
 * </p>
 *
 * <p>
 * ここでは<b>取り出したその場で、DB の話として落ちる</b>ことを固定する。
 * DB は要らない（繋ぐ前に落ちるのが要点なので）。
 * </p>
 */
class DbSourceMissingTest {

	@Test
	@DisplayName("D-130 データソースが無い DB は作らせない")
	void nullDataSourceIsRejected () {

		IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> new DB(null));

		assertTrue(thrown.getMessage().contains("DBUtil.load")
			, "何を直せばいいかが書かれていません: " + thrown.getMessage());

	}

	@Test
	@DisplayName("D-130 読み込んでいない名前の DB を取ると、その場で理由付きで落ちる")
	void unknownNameFailsAtGet () {

		/*
		 * <b>ありえない名前を使う。</b>他のテストが何を読み込んでいても、
		 * この名前だけは無い——読み込み済みかどうかに結果が左右されない
		 */
		String name = "d130_存在しないDB";

		IllegalStateException thrown =
			assertThrows(IllegalStateException.class, () -> DBUtil.getDB(name));

		assertTrue(thrown.getMessage().contains(name)
			, "どの DB の話か分かりません: " + thrown.getMessage());

		assertTrue(thrown.getMessage().contains("DBUtil.load")
			, "何を直せばいいかが書かれていません: " + thrown.getMessage());

	}

}
