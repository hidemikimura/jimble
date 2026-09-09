package io.jimble.util.io;

import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * テキストファイルの文字コード判別
 *
 * <p>
 * 判定は tika に任せている（要件 D-119）。tika は CSV のために元から入っていて、
 * その依存が文字コードの判定器（{@code tika-encoding-detector-mojibuster}）を
 * 連れてくるので、<b>使っても増えるものは無い</b>。
 * </p>
 *
 * <h2>返す名前</h2>
 * <p>
 * {@link java.nio.charset.Charset#name()} の正式な名前を返す（{@code Shift_JIS} /
 * {@code windows-1252}）。{@code Charset.forName(...)} に通せばよい。
 * </p>
 *
 * <h2>言い切れないときは既定に倒す</h2>
 * <p>
 * <b>当てずっぽうを返さない。</b>実測では、当たっているときの確信度は最低でも 0.95 で、
 * 純 ASCII や空のファイルには 0.10 しか付かない。{@link #MIN_CONFIDENCE} 未満は
 * 「分からなかった」として既定の文字コードを返す。
 * </p>
 * <p>
 * ただし<b>これで誤りを弾けるわけではない</b>。外すときにも 1.00 が付くことがある
 * （ASCII が大半で日本語がまばらな EUC-JP を GB18030 と答える、など）。
 * 弾けるのは「材料が無いときの当てずっぽう」だけである。
 * </p>
 *
 * <h2>テキスト以外を渡さないこと</h2>
 * <p>
 * 画像や圧縮ファイルを渡すと、<b>それらしい名前を自信をもって返す</b>。
 * 種別を見分けたいときは {@link FileUtil#getFileContentType(File)} を使うこと。
 * </p>
 */
public class FileCharDetecter {

	/**
	 * 文字コードの判定器
	 *
	 * <p>
	 * <b>使い回す。</b>作るたびに {@code META-INF/services} を走査するためである。
	 * 共有して差し支えないことは、8 スレッド × 1,880 回で答えが1つも食い違わないことで確かめてある。
	 * </p>
	 */
	private static final EncodingDetector DETECTOR = new DefaultEncodingDetector();

	/** この確信度に満たなければ「分からなかった」とみなす */
	private static final float MIN_CONFIDENCE = 0.9f;

	/**
	 * テキストファイルの文字コードを判定する
	 *
	 * @param is	入力ストリーム
	 * @return	文字コード
	 */
	public static String detector (InputStream is) {

		return detector(is, null);

	}

	/**
	 * テキストファイルの文字コードを判定する
	 *
	 * @param is				入力ストリーム
	 * @param defaultCharset	デフォルト文字コード
	 * @return	文字コード
	 */
	public static String detector (InputStream is, String defaultCharset) {

		try (
				TikaInputStream in = TikaInputStream.get(is)
		) {

			return detect(in, defaultCharset);

		} catch (Exception ex) {

			return defaultCharset;

		}

	}

	/**
	 * テキストファイルの文字コードを判定する
	 *
	 * @param file	テキストファイル
	 * @return	文字コード
	 */
	public static String detector (File file) {

		return detector(file, "UTF-8");

	}

	/**
	 * テキストファイルの文字コードを判定する
	 *
	 * @param file				テキストファイル
	 * @param defaultCharset	デフォルト文字コード
	 * @return	文字コード
	 */
	public static String detector (File file, String defaultCharset) {

		try (
				TikaInputStream in = TikaInputStream.get(file.toPath())
		) {

			return detect(in, defaultCharset);

		} catch (Exception ex) {

			return defaultCharset;

		}

	}

	/**
	 * 判定する
	 *
	 * @param in				入力
	 * @param defaultCharset	デフォルト文字コード
	 * @return	文字コード
	 * @throws Exception 例外
	 */
	private static String detect (TikaInputStream in, String defaultCharset) throws Exception {

		/*
		 * <b>BOM は自分で見る。</b>tika にも BOM を見る判定器はあるが、
		 * どの判定器から先に聞くかが<b>クラスパスに jar が並ぶ順で決まる</b>ため、
		 * 統計で当てる判定器が先に来ると<b>BOM が無視される</b>
		 * （手元では UTF-16LE、CI では GB18030 になった。要件 D-122）
		 */
		String bom = bomCharset(in);

		if (bom != null) {
			return bom;
		}

		List<EncodingResult> results = DETECTOR.detect(in, new Metadata(), new ParseContext());

		if (results == null || results.isEmpty()) {
			return defaultCharset;
		}

		// 先頭がいちばん確からしいもの
		EncodingResult top = results.get(0);

		if (top.getCharset() == null || top.getConfidence() < MIN_CONFIDENCE) {
			return defaultCharset;
		}

		return top.getCharset().name();

	}

	/**
	 * 先頭の BOM を読む
	 *
	 * <p>
	 * 読んだぶんは巻き戻すので、呼んだあとも先頭から読める。
	 * </p>
	 *
	 * @param in 入力
	 * @return BOM が指す文字コード。BOM が無ければ null
	 * @throws Exception 例外
	 */
	private static String bomCharset (TikaInputStream in) throws Exception {

		in.mark(4);

		byte[] head = in.readNBytes(4);

		in.reset();

		// <b>UTF-32LE は UTF-16LE と頭2 byte が同じ</b>ので、先に見る
		if (starts(head, 0xFF, 0xFE, 0x00, 0x00)) {
			return "UTF-32LE";
		}

		if (starts(head, 0x00, 0x00, 0xFE, 0xFF)) {
			return "UTF-32BE";
		}

		if (starts(head, 0xEF, 0xBB, 0xBF)) {
			return "UTF-8";
		}

		if (starts(head, 0xFF, 0xFE)) {
			return "UTF-16LE";
		}

		if (starts(head, 0xFE, 0xFF)) {
			return "UTF-16BE";
		}

		return null;

	}

	/**
	 * 先頭のバイト列が一致するか
	 *
	 * @param head	読んだ先頭
	 * @param bytes	見比べる値
	 * @return 一致すれば true
	 */
	private static boolean starts (byte[] head, int... bytes) {

		if (head.length < bytes.length) {
			return false;
		}

		for (int i = 0; i < bytes.length; i++) {
			if ((head[i] & 0xFF) != bytes[i]) {
				return false;
			}
		}

		return true;

	}

}
