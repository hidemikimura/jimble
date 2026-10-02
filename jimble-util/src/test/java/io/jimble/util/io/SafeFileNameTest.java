package io.jimble.util.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ファイル名を安全にする・消すときにリンクをたどらない（D-234）
 */
class SafeFileNameTest {

	@Test
	@DisplayName(". / .. を通さない（かつては拡張子を残すと .. がそのまま通った）")
	void dots () {

		assertEquals("_", FileUtil.safeFileName("..", true, "_"));
		assertEquals("_", FileUtil.safeFileName(".", true, "_"));
		assertEquals("_", FileUtil.safeFileName("...", true, "_"));
		assertEquals("a.txt", FileUtil.safeFileName("a.txt", true, "_"));
		assertEquals("_.._etc_passwd", FileUtil.safeFileName("/../etc/passwd", true, "_"));

	}

	@Test
	@DisplayName("制御文字と、末尾の点・空白を落とす。置き換えの字は字のとおり")
	void controlAndTrailing () {

		assertEquals("a_b", FileUtil.safeFileName("a\u0000b", true, "_"));
		assertEquals("a_b", FileUtil.safeFileName("a\nb", true, "_"));
		assertEquals("report.txt", FileUtil.safeFileName("report.txt. . ", true, "_"));
		assertEquals("a$b", FileUtil.safeFileName("a/b", true, "$"));

	}

	@Test
	@DisplayName("delete：シンボリックリンクのディレクトリは、リンクだけを消す（リンク先の中身を消さない）")
	void deleteDoesNotFollowLinks (@TempDir Path dir) throws Exception {

		Path target = Files.createDirectories(dir.resolve("target"));
		Path keep = Files.writeString(target.resolve("keep.txt"), "x");

		Path work = Files.createDirectories(dir.resolve("work"));
		Files.createSymbolicLink(work.resolve("link"), target);

		FileUtil.delete(work.toFile());

		assertTrue(Files.exists(keep), "リンク先の中身まで消しています");

	}

}
