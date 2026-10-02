package io.jimble.util.string;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * 改行続きを削除する（D-231）
 *
 * <p>かつての正規表現は、空白が長く続くと2乗の時間がかかった（空白 10 万個で 47 秒）。</p>
 */
class TrimBlankLineTest {

	@Test
	@DisplayName("空の行と、空白だけの行を落とす")
	void dropsBlankLines () {

		assertEquals("a\nb", StringUtil.trimBlankLine("a\n\n\nb"));
		assertEquals("a\nb", StringUtil.trimBlankLine("a\r\n   \t\r\nb\n"));
		assertEquals("a  \nb", StringUtil.trimBlankLine("a  \nb"));
		assertEquals("", StringUtil.trimBlankLine(""));
		assertNull(StringUtil.trimBlankLine(null));

	}

	@Test
	@DisplayName("空白が長く続いても、すぐ終わる")
	void linear () {

		String spaces = "x" + " ".repeat(200_000) + "y";

		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(spaces, StringUtil.trimBlankLine(spaces)));

	}

}
