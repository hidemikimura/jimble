package io.jimble.util.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 閉じたら消える入力（要件 D-164）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>「読み終わったら消す」を呼ぶ側に任せると、まず消し忘れる。</b>
 * 一時ファイルを返すところ（ダウンロード・変換した結果）で使う道具で、
 * <b>消し忘れはディスクが埋まるまで表に出ない</b>。
 * </p>
 *
 * <p>
 * <b>逆に、閉じる前に消えても困る。</b>
 * 途中まで送ったところでファイルが無くなると、
 * <b>相手には途中で切れたファイルが届く</b>。
 * </p>
 */
class FileInputStreamWithDeleteTest {

	/**
	 * 中身を持った一時ファイルを1つ作る
	 *
	 * @param text	中身
	 * @return	ファイル
	 * @throws Exception	例外
	 */
	private static File temp (String text) throws Exception {

		Path path = Files.createTempFile("jimble-", ".txt");

		Files.writeString(path, text, StandardCharsets.UTF_8);

		return path.toFile();

	}

	@Test
	@DisplayName("閉じたときに消える")
	void closingDeletesTheFile () throws Exception {

		File file = temp("こんにちは");

		try (InputStream is = new FileInputStreamWithDelete(file)) {

			assertTrue(file.exists(), "読んでいる途中で消えました");

			assertArrayEquals("こんにちは".getBytes(StandardCharsets.UTF_8), is.readAllBytes());

			assertTrue(file.exists(), "読み終わっただけで消えました（まだ閉じていません）");

		}

		assertFalse(file.exists(), "閉じたのに残っています: " + file);

	}

	@Test
	@DisplayName("最後まで読まずに閉じても消える")
	void closingEarlyStillDeletes () throws Exception {

		/*
		 * <b>相手が途中で切ったときの道である。</b>
		 * ここで消し損ねると、<b>切られるたびにゴミが積もる</b>。
		 */
		File file = temp("0123456789");

		try (InputStream is = new FileInputStreamWithDelete(file)) {
			assertTrue(is.read() >= 0);
		}

		assertFalse(file.exists(), "途中で閉じたら残りました");

	}

	@Test
	@DisplayName("二度閉じても落ちない")
	void closingTwiceIsFine () throws Exception {

		File file = temp("あ");

		InputStream is = new FileInputStreamWithDelete(file);

		is.close();
		is.close();

		assertFalse(file.exists());

	}

	// region ここで固定していないこと

	/*
	 * - <b>閉じるところで例外が出たときに消すかどうか</b>は見ていない。
	 *   {@code super.close()} が投げると<b>そこで止まってファイルが残る</b>——
	 *   稀だが、残るほうへ倒れていることは知っておくこと
	 * - <b>消せなかったとき</b>（権限・ロック）に何が起きるかも見ていない。
	 *   {@code FileUtil.delete} に任せている
	 */

	// endregion

}
