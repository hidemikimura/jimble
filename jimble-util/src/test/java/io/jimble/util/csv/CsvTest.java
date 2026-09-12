package io.jimble.util.csv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV の読み書き（要件 F-U-05）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>ドキュメントに載っているのに、テストが1度も触っていなかった。</b>
 * 読むほうが 549 行、書くほうが 281 行ある。
 * </p>
 *
 * <p>
 * <b>CSV の壊れ方は静かである。</b>引用符の中の改行を見落とせば行がずれ、
 * BOM を落とし損ねれば<b>1列目のヘッダーだけ名前が合わなくなる</b>——
 * どちらも例外は出ず、<b>取り込んだあとのデータがおかしいだけ</b>になる。
 * </p>
 */
class CsvTest {

	/**
	 * 文字列から読む
	 *
	 * @param csv	CSV
	 * @return	読み手
	 * @throws Exception	例外
	 */
	private static CsvReader read (String csv) throws Exception {

		return new CsvReader(new StringReader(csv));

	}

	// region 読む

	@Test
	@DisplayName("1行目をヘッダーにして、名前で引ける")
	void headerIsTheFirstLine () throws Exception {

		try (CsvReader csv = read("name,age\nりんご,3\nみかん,5\n")) {

			assertEquals(List.of("name", "age"), csv.getHeader());

			assertTrue(csv.next());
			assertEquals("りんご", csv.getString("name"));
			assertEquals(3, csv.getInt("age"));

			assertTrue(csv.next());
			assertEquals("みかん", csv.getString("name"));
			assertEquals(5, csv.getInt("age"));

			assertFalse(csv.next(), "行が余っています");

		}

	}

	@Test
	@DisplayName("引用符の中の カンマ と 改行 は、1つの値のまま")
	void quotedCommaAndNewlineStayInOneField () throws Exception {

		/*
		 * <b>ここが崩れると行数が変わる。</b>
		 * 住所や備考に改行が入っている CSV は珍しくないので、
		 * <b>取り込んだ件数だけが静かに増える</b>。
		 */
		try (CsvReader csv = read("name,note\n\"山田, 太郎\",\"1行目\n2行目\"\n")) {

			assertTrue(csv.next());
			assertEquals("山田, 太郎", csv.getString("name"));
			assertEquals("1行目\n2行目", csv.getString("note"));

			assertFalse(csv.next(), "改行のところで行が割れています");

		}

	}

	@Test
	@DisplayName("引用符の中の引用符は、2つ書いて1つになる")
	void doubledQuoteIsOneQuote () throws Exception {

		try (CsvReader csv = read("v\n\"彼は\"\"はい\"\"と言った\"\n")) {

			assertTrue(csv.next());
			assertEquals("彼は\"はい\"と言った", csv.getString("v"));

		}

	}

	@Test
	@DisplayName("BOM 付きでも、1列目のヘッダー名が合う")
	void bomDoesNotStickToTheFirstHeader () throws Exception {

		/*
		 * <b>Excel が書き出す UTF-8 の CSV には BOM が付く。</b>
		 * 落とさないと1列目のヘッダーが {@code "﻿name"} になり、
		 * <b>{@code getString("name")} だけが null を返す</b>——
		 * 2列目から先は合っているので、<b>読み込みが失敗したようには見えない</b>。
		 */
		byte[] bytes = ("﻿name,age\nりんご,3\n").getBytes(StandardCharsets.UTF_8);

		try (CsvReader csv = new CsvReader(new ByteArrayInputStream(bytes), "UTF-8")) {

			assertEquals("name", csv.getHeader().getFirst(), "BOM が残っています");

			assertTrue(csv.next());
			assertEquals("りんご", csv.getString("name"));

		}

	}

	@Test
	@DisplayName("ヘッダー無しの CSV でも、BOM は1列目に残らない")
	void bomIsRemovedWithoutAHeaderToo () throws Exception {

		/*
		 * <b>ヘッダーを読まない設定だと、BOM を落とす仕事は最初の {@code next()} に回る。</b>
		 * ここが抜けると<b>1件目の1列目だけ</b>先頭に見えない文字が付き、
		 * <b>数として読めば 0、突き合わせれば不一致</b>になる。
		 */
		byte[] bytes = "\ufeff100,200\n300,400\n".getBytes(StandardCharsets.UTF_8);

		try (CsvReader csv = new CsvReader(
			new java.io.InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8), 0, 1)) {

			assertTrue(csv.next());
			assertEquals("100", csv.getString(0), "BOM が残っています");
			assertEquals(100, csv.getInt(0));

			assertTrue(csv.next());
			assertEquals("300", csv.getString(0));

		}

	}

	@Test
	@DisplayName("ヘッダーに無い名前は null（落ちない）")
	void unknownHeaderIsNull () throws Exception {

		try (CsvReader csv = read("name\nりんご\n")) {

			assertTrue(csv.next());

			assertEquals(-1, csv.getKeyIndex("いない"));
			assertNull(csv.getString("いない"));

			/*
			 * <b>数値は 0 が返る。</b>「読めなかった」と「本当に 0」は<b>見分けが付かない</b>——
			 * 金額や件数を読むところでは {@code getString} で受けて自分で見ること。
			 */
			assertEquals(0, csv.getInt("いない"));
			assertEquals(0L, csv.getLong("いない"));
			assertFalse(csv.getBoolean("いない"));
			assertNull(csv.getDate("いない"));

		}

	}

	@Test
	@DisplayName("数として読めない値も 0 になる")
	void unreadableNumberIsZero () throws Exception {

		try (CsvReader csv = read("age\nりんご\n")) {

			assertTrue(csv.next());

			assertEquals("りんご", csv.getString("age"));
			assertEquals(0, csv.getInt("age"), "例外にはしない約束です");

		}

	}

	@Test
	@DisplayName("ヘッダー行と本文の開始行を指定できる")
	void headerAndBodyRowCanBeMoved () throws Exception {

		/*
		 * 上に説明書きが1行、ヘッダーが2行目、本文が4行目から、という形。
		 */
		try (CsvReader csv = new CsvReader(
			new StringReader("説明,の行\nname,age\n----,---\nりんご,3\n"), 2, 4)) {

			assertEquals(List.of("name", "age"), csv.getHeader());

			assertTrue(csv.next());
			assertEquals("りんご", csv.getString("name"));

		}

	}

	@Test
	@DisplayName("ヘッダー無しでも、位置で引ける")
	void withoutAHeaderUseTheIndex () throws Exception {

		try (CsvReader csv = new CsvReader(new StringReader("りんご,3\nみかん,5\n"), 0, 1)) {

			assertNull(csv.getHeader());

			assertTrue(csv.next());
			assertEquals("りんご", csv.getString(0));
			assertEquals(3, csv.getInt(1));

			assertNull(csv.getString("name"), "ヘッダーが無いので名前では引けません");

		}

	}

	@Test
	@DisplayName("列の数が揃っていない CSV は、その行で落ちる")
	void aRaggedRowThrows () throws Exception {

		/*
		 * <b>黙って読み進めない。</b>1行目より列が少ない（多い）行に来たところで
		 * 例外になる——<b>列がずれたまま取り込むより、止まったほうがよい</b>。
		 * 出るのは {@code CsvReader.next()} からで、値を取りに行く前である。
		 */
		try (CsvReader csv = read("a,b,c\n1,2\n")) {

			Exception ex = assertThrows(Exception.class, csv::next);

			assertTrue(ex.getMessage().contains("fields"), ex.getMessage());

		}

	}

	@Test
	@DisplayName("空の値と、空の行")
	void emptyFieldsAndEmptyLines () throws Exception {

		try (CsvReader csv = read("a,b\n,2\n")) {

			assertTrue(csv.next());
			assertEquals("", csv.getString("a"), "空の値は空文字です（null ではありません）");
			assertEquals("2", csv.getString("b"));

		}

	}

	// endregion

	// region 書く

	/**
	 * 書いた中身
	 *
	 * @param out	書き出し先
	 * @return	中身
	 */
	private static String written (ByteArrayOutputStream out) {

		return out.toString(StandardCharsets.UTF_8);

	}

	@Test
	@DisplayName("既定は Shift_JIS / CRLF / 全部引用符")
	void defaultsAreShiftJisCrlfAndAlwaysQuoted () throws Exception {

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (CsvWriter csv = new CsvWriter(out)) {
			csv.writeLine("name", "age");
			csv.writeLine("りんご", 3);
		}

		/*
		 * <b>既定が Shift_JIS なのは、Excel でそのまま開けるようにするためである。</b>
		 * UTF-8 で欲しいときは<b>文字コードを明示すること</b>——
		 * 既定のまま UTF-8 だと思って書くと、<b>日本語が化けたファイルが出来上がる</b>。
		 */
		String sjis = out.toString(java.nio.charset.Charset.forName("SHIFT-JIS"));

		assertEquals("\"name\",\"age\"\r\n\"りんご\",\"3\"\r\n", sjis);

	}

	@Test
	@DisplayName("値の中の 引用符 / カンマ / 改行 は、読み直せる形で書く")
	void specialCharactersSurviveARoundTrip () throws Exception {

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (CsvWriter csv = new CsvWriter(out, "UTF-8")) {
			csv.writeLine("name", "note");
			csv.writeLine("山田, 太郎", "彼は\"はい\"と言った\n2行目");
		}

		try (CsvReader csv = new CsvReader(
			new ByteArrayInputStream(out.toByteArray()), "UTF-8")) {

			assertTrue(csv.next());
			assertEquals("山田, 太郎", csv.getString("name"));
			assertEquals("彼は\"はい\"と言った\n2行目", csv.getString("note"));

		}

	}

	@Test
	@DisplayName("null は空文字として書く")
	void nullBecomesAnEmptyField () throws Exception {

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (CsvWriter csv = new CsvWriter(out, "UTF-8")) {
			csv.writeLine("a", null, "c");
		}

		assertEquals("\"a\",\"\",\"c\"\r\n", written(out));

	}

	@Test
	@DisplayName("配列やリストを渡すと、1列ずつに開く")
	void collectionsAreFlattened () throws Exception {

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (CsvWriter csv = new CsvWriter(out, "UTF-8")) {
			csv.writeLine(List.of("a", "b"));
			csv.writeLine("x", List.of("y", "z"));
		}

		assertEquals("\"a\",\"b\"\r\n\"x\",\"y\",\"z\"\r\n", written(out));

	}

	@Test
	@DisplayName("区切りと改行と引用符は変えられる")
	void separatorsCanBeChanged () throws Exception {

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (CsvWriter csv = new CsvWriter(out, "UTF-8")) {
			csv.setRecordSeparator('\t').setQuoteCharacter('\'').setLineSeparatorLF();
			csv.writeLine("a", "b");
		}

		assertEquals("'a'\t'b'\n", written(out));

	}

	@Test
	@DisplayName("D-161 書き始めたあとに設定を変えようとすると落ちる")
	void changingSettingsAfterWritingFails () throws Exception {

		/*
		 * <b>以前は黙って無視されていた。</b>組み立ては最初の {@code writeLine} で
		 * 1回だけ行われるので、そのあとに区切りを変えても<b>何も起きない</b>——
		 * <b>出来上がった CSV は指定したはずの形になっていない</b>のに、
		 * 例外もログも出なかった。
		 */
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (CsvWriter csv = new CsvWriter(out, "UTF-8")) {

			assertFalse(csv.isBodyWritten());

			csv.writeLine("a", "b");

			assertTrue(csv.isBodyWritten());

			assertThrows(IllegalStateException.class, () -> csv.setRecordSeparator('\t'));
			assertThrows(IllegalStateException.class, () -> csv.setQuoteCharacter('\''));
			assertThrows(IllegalStateException.class, csv::setLineSeparatorLF);

		}

		assertEquals("\"a\",\"b\"\r\n", written(out));

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>文字コードの自動判別</b>は見ていない（{@code FileCharDetecterTest} が見ている）。
	 *   {@code new CsvReader(File)} はそれを通るので、ここでは
	 *   <b>文字コードを明示する口だけ</b>を使っている
	 * - <b>巨大なファイルの速さやメモリ</b>も見ていない。1行ずつ流す作りなので
	 *   全部載せにはならないが、測ってはいない
	 * - <b>列がずれたときに飛ぶ例外の型</b>は固定していない。いまは fastcsv の
	 *   {@code CsvParseException} が<b>そのまま外へ出ている</b>——
	 *   <b>枠組みの外の型が jimble の口から飛ぶ</b>形なので、
	 *   ここで型を書くと「そういう約束」になってしまう。見ているのは<b>落ちること</b>だけである
	 * - <b>{@code getByte} / {@code getShort} / {@code getFloat} / {@code getDouble}</b> は
	 *   {@code getInt} と<b>同じ形（Convertor に投げて、駄目なら 0）</b>なので、
	 *   代表として {@code getInt} だけを見ている
	 */

	// endregion

}
