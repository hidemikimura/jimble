package io.jimble.util.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文字コードの判別（{@link FileCharDetecter}）
 *
 * <p>
 * <b>外しても例外は出ず、文字化けした文字列が返るだけ</b>なので、
 * ここで固定しておかないと気づけない。判定器を差し替えるときは、まずこれを流す。
 * </p>
 *
 * <h2>ここで固定していないこと</h2>
 * <ul>
 *   <li><b>14〜44 byte の断片</b>（材料が足りず、判定器によって答えが変わる。
 *       実測では 45 件中 4 件を外した。実寸のファイルでは起きない）</li>
 *   <li><b>ASCII が大半で日本語がまばらな EUC-JP</b>（GB18030 と答える。
 *       juniversalchardet も ICU4J も同じように外したので、判定器を変えても直らない）</li>
 *   <li><b>候補が空・文字コードが null のときの逃げ道</b>（いまの判定器は空のファイルにも
 *       壊れたファイルにも必ず1つ返すので、そこを通す入力を作れない。
 *       判定器を差し替えたときのために残してある）</li>
 * </ul>
 */
class FileCharDetecterTest {

	/** テスト用のファイル置き場 */
	@TempDir
	Path dir;

	/** 実寸の CSV（155 byte 以上になる） */
	private static final String CSV = """
		商品コード,商品名,単価,数量,備考
		A-001,鉛筆（HB）,120,10,在庫あり
		A-002,消しゴム,80,5,
		B-010,ノート A4 横罫,340,3,取り寄せ
		C-100,ボールペン 黒,180,24,特価
		""";

	/** 実寸の文章 */
	private static final String PROSE = """
		弊社のサービスをご利用いただきありがとうございます。
		本日はご案内をお送りいたします。ご不明な点がございましたら、
		担当者までお問い合わせください。営業時間は平日9時から18時までです。
		""";

	// region 日本語

	@Test
	@DisplayName("日本語の文字コードを見分ける")
	void japanese () throws IOException {

		assertEquals("UTF-8", detect(CSV, "UTF-8"));
		assertEquals("Shift_JIS", detect(CSV, "Shift_JIS"));
		assertEquals("EUC-JP", detect(CSV, "EUC-JP"));
		assertEquals("ISO-2022-JP", detect(CSV, "ISO-2022-JP"));

		assertEquals("UTF-8", detect(PROSE, "UTF-8"));
		assertEquals("Shift_JIS", detect(PROSE, "Shift_JIS"));
		assertEquals("EUC-JP", detect(PROSE, "EUC-JP"));
		assertEquals("ISO-2022-JP", detect(PROSE, "ISO-2022-JP"));

	}

	@Test
	@DisplayName("候補が複数あがっても、いちばん確からしいものを返す")
	void mostConfidentWins () throws IOException {

		// 行数の多い EUC-JP の CSV は EUC-JP / GB18030 / Big5-HKSCS の3つが候補にあがる。
		// <b>並び順に頼っている</b>ので、ここで固定しておく
		StringBuilder big = new StringBuilder("商品コード,商品名,単価,数量,備考\n");
		for (int i = 0; i < 200; i++) {
			big.append("A-%03d,鉛筆（HB）,120,10,在庫あり%n".formatted(i));
		}

		assertEquals("EUC-JP", detect(big.toString(), "EUC-JP"));

	}

	@Test
	@DisplayName("BOM があればそれに従う")
	void bom () throws IOException {

		assertEquals("UTF-8", FileCharDetecter.detector(
			bytes("bom8.txt", concat(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, PROSE.getBytes(StandardCharsets.UTF_8)))));

		assertEquals("UTF-16LE", FileCharDetecter.detector(
			bytes("bom16le.txt", concat(new byte[]{(byte) 0xFF, (byte) 0xFE}, PROSE.getBytes(StandardCharsets.UTF_16LE)))));

		assertEquals("UTF-16BE", FileCharDetecter.detector(
			bytes("bom16be.txt", concat(new byte[]{(byte) 0xFE, (byte) 0xFF}, PROSE.getBytes(StandardCharsets.UTF_16BE)))));

	}

	@Test
	@DisplayName("返すのは Charset.forName に通る名前")
	void nameIsUsable () throws IOException {

		String name = detect(CSV, "Shift_JIS");

		assertTrue(Charset.isSupported(name), name);
		assertEquals(Charset.forName("Shift_JIS"), Charset.forName(name));

	}

	// endregion

	// region 言い切れないとき

	@Test
	@DisplayName("材料が無ければ既定に倒す（当てずっぽうを返さない）")
	void fallsBackToDefault () throws IOException {

		// 純 ASCII。どの文字コードで読んでも同じなので、判定器は言い切らない
		File ascii = bytes("ascii.txt", "id,name,note\n1,foo,ok\n2,bar,ok\n".getBytes(StandardCharsets.US_ASCII));

		assertEquals("SHIFT-JIS", FileCharDetecter.detector(ascii, "SHIFT-JIS"));
		assertEquals("UTF-8", FileCharDetecter.detector(ascii));

		// 空
		assertEquals("SHIFT-JIS", FileCharDetecter.detector(bytes("empty.txt", new byte[0]), "SHIFT-JIS"));

	}

	@Test
	@DisplayName("読めなければ既定に倒す")
	void unreadable () {

		assertEquals("UTF-8", FileCharDetecter.detector(new File(dir.toFile(), "ここには無い.txt")));
		assertEquals("EUC-JP", FileCharDetecter.detector(new File(dir.toFile(), "ここには無い.txt"), "EUC-JP"));

	}

	@Test
	@DisplayName("既定を渡さなければ null が返る")
	void noDefault () {

		assertNull(FileCharDetecter.detector(new ByteArrayInputStream(new byte[0])));

	}

	// endregion

	// region 入口

	@Test
	@DisplayName("ファイルからでもストリームからでも同じ答え")
	void sameFromStream () throws IOException {

		for (String charset : new String[]{"UTF-8", "Shift_JIS", "EUC-JP", "ISO-2022-JP"}) {

			byte[] raw = CSV.getBytes(Charset.forName(charset));

			assertEquals(
				FileCharDetecter.detector(bytes("s.txt", raw), "UTF-8")
				, FileCharDetecter.detector(new ByteArrayInputStream(raw), "UTF-8")
				, charset
			);

		}

	}

	// endregion

	// region 道具

	/**
	 * 書いてから判定する
	 *
	 * @param text		中身
	 * @param charset	書くときの文字コード
	 * @return	判定した文字コード
	 * @throws IOException 例外
	 */
	private String detect (String text, String charset) throws IOException {

		return FileCharDetecter.detector(
			bytes(charset + ".txt", text.getBytes(Charset.forName(charset)))
			, "UTF-8"
		);

	}

	/**
	 * ファイルを書く
	 *
	 * @param name	ファイル名
	 * @param raw	中身
	 * @return	ファイル
	 * @throws IOException 例外
	 */
	private File bytes (String name, byte[] raw) throws IOException {

		Path path = dir.resolve(name);
		Files.write(path, raw);

		return path.toFile();

	}

	/**
	 * つなぐ
	 *
	 * @param a	前
	 * @param b	後ろ
	 * @return	つないだもの
	 */
	private static byte[] concat (byte[] a, byte[] b) {

		byte[] out = new byte[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);

		return out;

	}

	// endregion

}
