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
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 画像の幅と高さ（{@link ImageSize}）
 *
 * <p>
 * WebP は JDK の ImageIO に無いのでヘッダを自前で読む（要件 D-116）。
 * <b>3種類とも大きさの置き場所が違う</b>ので、3つとも見る。
 * </p>
 */
class ImageSizeTest {

	/** 非可逆（VP8 ）。7 x 5 */
	private static final String WEBP_LOSSY =
		"UklGRjoAAABXRUJQVlA4IC4AAACwAQCdASoHAAUAAUAmJaACdLoABDAAAP7x3I/4DdfFtMv/vYL/3YL/3YL/WwAA";

	/** 可逆（VP8L）。7 x 5 */
	private static final String WEBP_LOSSLESS =
		"UklGRh4AAABXRUJQVlA4TBEAAAAvBgABAAdQjyLXo/+BiOh/AAA=";

	/** 拡張（VP8X。透過つき）。9 x 6 */
	private static final String WEBP_ALPHA =
		"UklGRl4AAABXRUJQVlA4WAoAAAAQAAAACAAABQAAQUxQSAoAAAABB1DAiAhERP8DVlA4IC4AAACwAQCdASoJ"
		+ "AAYAAUAmJaACdLoABDAAAP7x3I/4DdfFtMv/vYL/3YL/3YL/WwAA";

	/** テスト用のファイル置き場 */
	@TempDir
	Path dir;

	@Test
	@DisplayName("ImageIO が持っている形式は ImageIO で読む")
	void imageIo () throws IOException {

		assertEquals(new ImageSize(3, 2), ImageSize.read(image("a.png", "png", 3, 2)));
		assertEquals(new ImageSize(5, 4), ImageSize.read(image("a.jpg", "jpg", 5, 4)));
		assertEquals(new ImageSize(8, 7), ImageSize.read(image("a.gif", "gif", 8, 7)));
		assertEquals(new ImageSize(6, 9), ImageSize.read(image("a.bmp", "bmp", 6, 9)));
		assertEquals(new ImageSize(11, 12), ImageSize.read(image("a.tif", "tif", 11, 12)));

	}

	@Test
	@DisplayName("WebP は非可逆・可逆・拡張の3種類とも読む")
	void webp () throws IOException {

		assertEquals(new ImageSize(7, 5), ImageSize.read(base64("lossy.webp", WEBP_LOSSY)));
		assertEquals(new ImageSize(7, 5), ImageSize.read(base64("lossless.webp", WEBP_LOSSLESS)));
		assertEquals(new ImageSize(9, 6), ImageSize.read(base64("alpha.webp", WEBP_ALPHA)));

	}

	@Test
	@DisplayName("読めないものは 0 を返す（落ちない）")
	void unknown () throws IOException {

		assertEquals(ImageSize.UNKNOWN, ImageSize.read(text("a.txt", "ただのテキスト")));
		assertEquals(ImageSize.UNKNOWN, ImageSize.read(text("empty.png", "")));
		assertEquals(ImageSize.UNKNOWN, ImageSize.read(text("short.webp", "RIFF")));
		assertEquals(ImageSize.UNKNOWN, ImageSize.read(dir.resolve("none.png").toFile()));
		assertEquals(ImageSize.UNKNOWN, ImageSize.read(null));
		assertEquals(ImageSize.UNKNOWN, ImageSize.read(dir.toFile()));

	}

	@Test
	@DisplayName("WebP を名乗っていても中身が違えば 0")
	void brokenWebp () throws IOException {

		// RIFF ではあるが WEBP ではない（wav のような形）
		byte[] bytes = Base64.getDecoder().decode(WEBP_LOSSY);
		bytes[8] = 'W';
		bytes[9] = 'A';
		bytes[10] = 'V';
		bytes[11] = 'E';

		Path path = dir.resolve("b.webp");
		Files.write(path, bytes);

		assertEquals(ImageSize.UNKNOWN, ImageSize.read(path.toFile()));

	}

	@Test
	@DisplayName("VP8 は、キーフレームで同期コードがあるものだけ読む")
	void webpNotKeyFrame () throws IOException {

		byte[] bytes = Base64.getDecoder().decode(WEBP_LOSSY);

		// フレームタグの1ビット目を立てる（中間フレーム）。大きさは書かれていない
		byte[] interFrame = bytes.clone();
		interFrame[20] |= 0x01;
		assertEquals(ImageSize.UNKNOWN, ImageSize.read(bytes("inter.webp", interFrame)));

		// 同期コードを壊す
		byte[] noSync = bytes.clone();
		noSync[24] = 0x00;
		assertEquals(ImageSize.UNKNOWN, ImageSize.read(bytes("nosync.webp", noSync)));

		// 目印を壊した可逆も読まない
		byte[] noSignature = Base64.getDecoder().decode(WEBP_LOSSLESS);
		noSignature[20] = 0x00;
		assertEquals(ImageSize.UNKNOWN, ImageSize.read(bytes("nosig.webp", noSignature)));

	}

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

		ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, file);

		return file;

	}

	/**
	 * バイト列からファイルを作る
	 *
	 * @param name	ファイル名
	 * @param bytes	中身
	 * @return	ファイル
	 * @throws IOException	書けなかった場合
	 */
	private File bytes (String name, byte[] bytes) throws IOException {

		Path path = dir.resolve(name);

		Files.write(path, bytes);

		return path.toFile();

	}

	/**
	 * base64 からファイルを作る
	 *
	 * @param name		ファイル名
	 * @param base64	中身
	 * @return	ファイル
	 * @throws IOException	書けなかった場合
	 */
	private File base64 (String name, String base64) throws IOException {

		Path path = dir.resolve(name);

		Files.write(path, Base64.getDecoder().decode(base64));

		return path.toFile();

	}

	/**
	 * テキストファイルを作る
	 *
	 * @param name	ファイル名
	 * @param text	中身
	 * @return	ファイル
	 * @throws IOException	書けなかった場合
	 */
	private File text (String name, String text) throws IOException {

		Path path = dir.resolve(name);

		Files.writeString(path, text, StandardCharsets.UTF_8);

		return path.toFile();

	}

	// endregion

}
