package io.jimble.util.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ファイルの種別とメタデータ（{@link FileUtil}）
 *
 * <p>
 * <b>種別も大きさも文字コードも、変わっても例外は出ずに値が違うだけ</b>なので、
 * ここで固定しておかないと気づけない。tika の版を上げるときは、まずこれを流す。
 * </p>
 *
 * <h2>取れないもの</h2>
 * <ul>
 *   <li><b>HEIC・AVIF・SVG と動画の幅と高さ</b>（0 になる。要件 D-116）</li>
 *   <li><b>中身が短い CSV の区切り文字</b>（tika が言い切れないときは空。当てずっぽうは返さない）</li>
 * </ul>
 */
class FileMetaDataTest {

	/** テスト用のファイル置き場 */
	@TempDir
	Path dir;

	// region 種別

	@Test
	@DisplayName("中身を見て種別が分かる")
	void detect () throws IOException {

		assertEquals("image/png", FileUtil.getFileContentType(image("a.png", "png", 3, 2)));
		assertEquals("image/jpeg", FileUtil.getFileContentType(image("a.jpg", "jpg", 5, 4)));
		assertEquals("application/json", FileUtil.getFileContentType(write("a.json", "{\"a\": 1}")));
		assertEquals("text/plain", FileUtil.getFileContentType(write("a.txt", "ただのテキストです\n")));
		assertEquals("text/csv", FileUtil.getFileContentType(write("a.csv", "名前,数\nりんご,1\n")));

	}

	@Test
	@DisplayName("拡張子が違っても中身で見分ける")
	void detectIgnoresExtension () throws IOException {

		// 中身は PNG だが名前は .txt
		assertEquals("image/png", FileUtil.getFileContentType(image("b.txt", "png", 3, 2)));

	}

	// endregion

	// region メタデータ

	@Test
	@DisplayName("画像は種別と大きさが取れる")
	void image () throws IOException {

		FileUtil.FileMetaData meta = FileUtil.getFileMetaData(image("a.png", "png", 3, 2), null);

		assertNotNull(meta);
		assertEquals("image/png", meta.contentType());
		assertEquals(3, meta.width());
		assertEquals(2, meta.height());

	}

	@Test
	@DisplayName("大きさが読めない形式でも種別は返す")
	void imageWithoutSize () throws IOException {

		// SVG は ImageIO でも WebP のヘッダでも読めない
		File file = write("a.svg", """
			<?xml version="1.0"?>
			<svg xmlns="http://www.w3.org/2000/svg" width="10" height="20"></svg>
			""");

		FileUtil.FileMetaData meta = FileUtil.getFileMetaData(file, null);

		assertNotNull(meta);
		assertEquals("image/svg+xml", meta.contentType());
		assertEquals(0, meta.width());
		assertEquals(0, meta.height());

	}

	@Test
	@DisplayName("CSV は種別・文字コード・区切りが取れる")
	void csv () throws IOException {

		File file = write("a.csv", rows(",", 30));

		FileUtil.FileMetaData meta = FileUtil.getFileMetaData(file, "text/csv");

		assertNotNull(meta);
		assertEquals("text/csv", meta.contentType());
		assertEquals("UTF-8", meta.charset());
		assertEquals(",", meta.delimiter());

		// ファイル名を渡す入口でも同じ
		assertEquals(meta, FileUtil.getCsvFileMetaData(file, "a.csv"));

	}

	@Test
	@DisplayName("タブ区切りは区切り文字がタブになる")
	void tsv () throws IOException {

		FileUtil.FileMetaData meta = FileUtil.getCsvFileMetaData(write("a.csv", rows("\t", 30)), "a.csv");

		assertNotNull(meta);
		assertEquals("text/tsv", meta.contentType());
		assertEquals("UTF-8", meta.charset());
		assertEquals("\t", meta.delimiter());

	}

	@Test
	@DisplayName("セミコロン区切りも読む")
	void semicolon () throws IOException {

		FileUtil.FileMetaData meta = FileUtil.getCsvFileMetaData(write("a.csv", rows(";", 30)), "a.csv");

		assertNotNull(meta);
		assertEquals(";", meta.delimiter());

	}

	@Test
	@DisplayName("Shift_JIS でも文字コードが取れる")
	void csvShiftJis () throws IOException {

		Path path = dir.resolve("sjis.csv");
		Files.write(path, rows(",", 30).getBytes("Windows-31J"));

		FileUtil.FileMetaData meta = FileUtil.getCsvFileMetaData(path.toFile(), "sjis.csv");

		assertNotNull(meta);
		assertEquals("text/csv", meta.contentType());
		assertEquals("windows-31j", meta.charset());
		assertEquals(",", meta.delimiter());

	}

	@Test
	@DisplayName("短い CSV は、種別と文字コードは取れるが区切りは空になる")
	void csvTooShort () throws IOException {

		// tika は「区切りが規則的だ」と言い切れないと text/plain を返す。
		// そのときは名前から text/csv にするが、区切りは当てずっぽうを返さない
		File file = write("a.csv", "名前,数\nりんご,1\n");

		FileUtil.FileMetaData meta = FileUtil.getCsvFileMetaData(file, "a.csv");

		assertNotNull(meta);
		assertEquals("text/csv", meta.contentType());
		assertEquals("UTF-8", meta.charset());
		assertEquals("", meta.delimiter());

	}

	@Test
	@DisplayName("パラメータ付きの種別でも CSV として扱う")
	void csvWithParameters () throws IOException {

		File file = write("a.csv", rows(",", 30));

		// ブラウザから来るのはこの形
		FileUtil.FileMetaData meta = FileUtil.getFileMetaData(file, "text/csv; charset=UTF-8");

		assertNotNull(meta);
		assertEquals("text/csv", meta.contentType());
		assertEquals(",", meta.delimiter());

	}

	@Test
	@DisplayName("拡張子の無いファイルでも、CSV だと言われていれば CSV として扱う")
	void csvWithoutExtension () throws IOException {

		// アップロードを一時ファイルに置くと、名前に拡張子が付かない
		File file = write("temp_1234567890_abcdef", rows(",", 30));

		FileUtil.FileMetaData meta = FileUtil.getFileMetaData(file, "text/csv");

		assertNotNull(meta);
		assertEquals("text/csv", meta.contentType());
		assertEquals("UTF-8", meta.charset());
		assertEquals(",", meta.delimiter());

	}

	@Test
	@DisplayName("短くて名前も手がかりにならないときは、言われた種別のまま返す")
	void csvKeepsDeclaredType () throws IOException {

		// 中身が短いので tika は text/plain としか言えない。名前にも拡張子が無い
		File file = write("temp_9999", "名前\t数\nりんご\t1\n");

		FileUtil.FileMetaData meta = FileUtil.getFileMetaData(file, "text/tsv");

		assertNotNull(meta);
		assertEquals("text/tsv", meta.contentType(), "言われた種別を捨てている");

	}

	@Test
	@DisplayName("名前だけ csv の画像は、CSV だと言い張らない")
	void csvThatIsNotCsv () throws IOException {

		File file = image("fake.csv", "png", 3, 2);

		FileUtil.FileMetaData meta = FileUtil.getCsvFileMetaData(file, "fake.csv");

		assertNotNull(meta);
		assertEquals("image/png", meta.contentType());

	}

	@Test
	@DisplayName("引用符が壊れた行があっても、種別・文字コード・区切りは返す")
	void csvBroken () throws IOException {

		// 壊れた行は後ろのほうに置く（tika が区切りを決めるのは先頭 20000 文字だけ）
		File file = write("a.csv", rows(",", 1500) + "9999,\"閉じ\"ない\",100\n");

		FileUtil.FileMetaData meta = FileUtil.getCsvFileMetaData(file, "a.csv");

		assertNotNull(meta);
		assertEquals("text/csv", meta.contentType());
		assertEquals("UTF-8", meta.charset());
		assertEquals(",", meta.delimiter());

	}

	@Test
	@DisplayName("種別のパラメータは落として返す")
	void baseTypeOnly () throws IOException {

		FileUtil.FileMetaData meta = FileUtil.getFileMetaData(
			write("a.json", "{\"a\": 1}"), "APPLICATION/JSON; charset=UTF-8");

		assertNotNull(meta);
		assertEquals("application/json", meta.contentType());

	}

	@Test
	@DisplayName("文字コードと区切りは、無いときも null ではなく空")
	void neverNull () throws IOException {

		FileUtil.FileMetaData image = FileUtil.getFileMetaData(image("a.png", "png", 3, 2), null);
		assertNotNull(image);
		assertEquals("", image.charset());
		assertEquals("", image.delimiter());

		FileUtil.FileMetaData other = FileUtil.getFileMetaData(write("a.json", "{\"a\": 1}"), null);
		assertNotNull(other);
		assertEquals("", other.charset());
		assertEquals("", other.delimiter());

	}

	@Test
	@DisplayName("画像でも CSV でもないものは種別だけ返す")
	void other () throws IOException {

		FileUtil.FileMetaData meta = FileUtil.getFileMetaData(write("a.json", "{\"a\": 1}"), null);

		assertNotNull(meta);
		assertEquals("application/json", meta.contentType());
		assertEquals(0, meta.width());
		assertEquals(0, meta.height());

	}

	// endregion

	// region ヘルパー

	/**
	 * 画像を作る
	 *
	 * @param name		ファイル名
	 * @param format	画像の形式
	 * @param width		幅
	 * @param height	高さ
	 * @return	ファイル
	 * @throws IOException	書けなかった場合
	 */
	private File image (String name, String format, int width, int height) throws IOException {

		File file = dir.resolve(name).toFile();

		BufferedImage image = new BufferedImage(width, height
			, "jpg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);

		ImageIO.write(image, format, file);

		return file;

	}

	/**
	 * 区切りのある行を作る
	 *
	 * <p>tika は中身が短いと区切りを言い切れないので、行数をそろえて作る。</p>
	 *
	 * @param delimiter	区切り文字
	 * @param count		行数
	 * @return	中身
	 */
	private String rows (String delimiter, int count) {

		StringBuilder sb = new StringBuilder();

		sb.append("id").append(delimiter).append("名前").append(delimiter).append("値段\n");

		for (int i = 0; i < count; i++) {
			sb.append(i).append(delimiter).append("商品").append(i).append(delimiter).append(i * 100).append("\n");
		}

		return sb.toString();

	}

	/**
	 * テキストファイルを作る（UTF-8）
	 *
	 * @param name	ファイル名
	 * @param text	中身
	 * @return	ファイル
	 * @throws IOException	書けなかった場合
	 */
	private File write (String name, String text) throws IOException {

		Path path = dir.resolve(name);

		Files.writeString(path, text, StandardCharsets.UTF_8);

		return path.toFile();

	}

	// endregion

}
