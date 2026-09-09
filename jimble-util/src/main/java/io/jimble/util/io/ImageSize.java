package io.jimble.util.io;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Iterator;

/**
 * 画像の幅と高さ
 *
 * <p>
 * <b>ヘッダだけを読む。</b>画素は展開しない（アップロードされた大きな画像で
 * ヒープを食わないため）。
 * </p>
 *
 * <p>
 * 読めるのは JDK の {@code ImageIO} が持っている形式（PNG / JPEG / GIF / BMP /
 * TIFF / WBMP）と <b>WebP</b> である。WebP は ImageIO に無いので、
 * ここでヘッダを読む。40 行で済むものに依存を1つ増やさない
 * （tika の画像 parser を足すと <b>+9 jar / 約 5.4MB</b> になる。要件 D-116）。
 * </p>
 *
 * <p>
 * <b>HEIC / AVIF / SVG と動画は読めない。</b>そのときは {@link #UNKNOWN}（0 x 0）になる。
 * </p>
 *
 * @param width		幅
 * @param height	高さ
 */
record ImageSize(int width, int height) {

	/** 分からなかったとき */
	static final ImageSize UNKNOWN = new ImageSize(0, 0);

	/** WebP のヘッダを見分けるのに要るバイト数 */
	private static final int WEBP_HEADER_SIZE = 30;

	/** VP8 の同期コード */
	private static final int[] VP8_SYNC = {0x9D, 0x01, 0x2A};

	/**
	 * 幅と高さを読む
	 *
	 * @param file	ファイル
	 * @return	大きさ。読めなければ {@link #UNKNOWN}
	 */
	static ImageSize read (File file) {

		if (file == null || !file.isFile()) {
			return UNKNOWN;
		}

		ImageSize webp = readWebp(file);
		if (webp != null) {
			return webp;
		}

		return readWithImageIo(file);

	}

	/**
	 * ImageIO で読む
	 *
	 * <p>
	 * 読めそうだと言った reader を<b>順に試す</b>。1つ目で決め打ちにすると、
	 * アプリが足した reader（TwelveMonkeys など）が名乗り出て失敗したときに、
	 * <b>JDK の reader なら読めたものが 0 になる</b>。
	 * </p>
	 *
	 * @param file	ファイル
	 * @return	大きさ。読めなければ {@link #UNKNOWN}
	 */
	private static ImageSize readWithImageIo (File file) {

		try (ImageInputStream in = ImageIO.createImageInputStream(file)) {

			if (in == null) {
				return UNKNOWN;
			}

			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);

			while (readers.hasNext()) {

				ImageReader reader = readers.next();

				try {
					// 入力を渡すだけ。getWidth / getHeight はヘッダしか読まない
					reader.setInput(in, true, true);
					return new ImageSize(reader.getWidth(0), reader.getHeight(0));
				} catch (Exception ignore) {
					// この reader では読めなかった。次を試す
				} finally {
					reader.dispose();
				}

			}

			return UNKNOWN;

		} catch (Exception ex) {
			return UNKNOWN;
		}

	}

	/**
	 * WebP のヘッダから読む
	 *
	 * <p>
	 * 3種類ある。{@code VP8 }（非可逆）/ {@code VP8L}（可逆）/ {@code VP8X}（拡張。
	 * 透過やアニメーション）。<b>どれも大きさの置き場所が違う。</b>
	 * </p>
	 *
	 * <p>
	 * <b>形が合わないものは読まない。</b>WebP のつもりで読み進めると、
	 * 別のファイルから<b>それらしい数が出てしまう</b>（0 になるより悪い）。
	 * </p>
	 *
	 * @param file	ファイル
	 * @return	大きさ。WebP として読めなければ null
	 */
	private static ImageSize readWebp (File file) {

		byte[] head = head(file);

		if (head == null
			|| !isText(head, 0, "RIFF")
			|| !isText(head, 8, "WEBP")) {
			return null;
		}

		if (isText(head, 12, "VP8 ")) {
			return readVp8(head);
		}

		if (isText(head, 12, "VP8L")) {
			return readVp8l(head);
		}

		if (isText(head, 12, "VP8X")) {
			// 1:フラグ + 3:予備 のあと、3 バイトずつ「幅 - 1」「高さ - 1」（画面の大きさ）
			return new ImageSize(le(head, 24, 3) + 1, le(head, 27, 3) + 1);
		}

		return null;

	}

	/**
	 * 非可逆（{@code VP8 }）から読む
	 *
	 * <p>
	 * 大きさが書いてあるのは<b>キーフレームだけ</b>である。
	 * フレームタグの1ビット目が立っていると中間フレームで、
	 * 同期コードも大きさも<b>入っていない</b>（そこを読むと圧縮データを数として読む）。
	 * </p>
	 *
	 * @param head	先頭のバイト列
	 * @return	大きさ。読めなければ null
	 */
	private static ImageSize readVp8 (byte[] head) {

		// 12:chunk名 + 4:chunkの大きさ のあとが 3 バイトのフレームタグ
		if ((head[20] & 0x01) != 0) {
			return null;
		}

		for (int i = 0; i < VP8_SYNC.length; i++) {
			if ((head[23 + i] & 0xFF) != VP8_SYNC[i]) {
				return null;
			}
		}

		return new ImageSize(le(head, 26, 2) & 0x3FFF, le(head, 28, 2) & 0x3FFF);

	}

	/**
	 * 可逆（{@code VP8L}）から読む
	 *
	 * @param head	先頭のバイト列
	 * @return	大きさ。読めなければ null
	 */
	private static ImageSize readVp8l (byte[] head) {

		// 20 バイト目は目印（0x2F）。そのあと 14 ビットずつ「幅 - 1」「高さ - 1」
		if ((head[20] & 0xFF) != 0x2F) {
			return null;
		}

		int bits = le(head, 21, 4);

		return new ImageSize((bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1);

	}

	/**
	 * 先頭を読む
	 *
	 * @param file	ファイル
	 * @return	先頭のバイト列。足りなければ null
	 */
	private static byte[] head (File file) {

		byte[] buffer = new byte[WEBP_HEADER_SIZE];

		try (InputStream in = Files.newInputStream(file.toPath())) {

			int read = in.readNBytes(buffer, 0, buffer.length);

			return read == buffer.length ? buffer : null;

		} catch (IOException ex) {
			return null;
		}

	}

	/**
	 * その位置がこの文字列か（ASCII）
	 *
	 * @param bytes		バイト列
	 * @param offset	位置
	 * @param text		文字列
	 * @return	同じなら true
	 */
	private static boolean isText (byte[] bytes, int offset, String text) {

		for (int i = 0; i < text.length(); i++) {
			if ((bytes[offset + i] & 0xFF) != text.charAt(i)) {
				return false;
			}
		}

		return true;

	}

	/**
	 * リトルエンディアンの数を読む
	 *
	 * @param bytes		バイト列
	 * @param offset	位置
	 * @param length	バイト数
	 * @return	数
	 */
	private static int le (byte[] bytes, int offset, int length) {

		int value = 0;

		for (int i = 0; i < length; i++) {
			value |= (bytes[offset + i] & 0xFF) << (8 * i);
		}

		return value;

	}

}
