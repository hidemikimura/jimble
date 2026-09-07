package io.jimble.util.charset;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * テキストファイルの文字コード判別
 */
public class FileCharDetecter {

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
				BufferedInputStream bis = new BufferedInputStream(is)
		) {

			UniversalDetector detector = new UniversalDetector(null);

			byte[] buff = new byte[8192];
			int len;
			while ((len = bis.read(buff, 0, 8192)) > -1 && !detector.isDone()) {
				detector.handleData(buff, 0, len);
			}
			detector.dataEnd();

			String charset = detector.getDetectedCharset();
			detector.reset();

			if (charset == null || charset.isEmpty()) {
				return defaultCharset;
			}

			return charset;

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
				FileInputStream fis = new FileInputStream(file);
				BufferedInputStream bis = new BufferedInputStream(fis)
		) {

			UniversalDetector detector = new UniversalDetector(null);

			byte[] buff = new byte[8192];
			int len;
			while ((len = bis.read(buff, 0, 8192)) > -1 && !detector.isDone()) {
				detector.handleData(buff, 0, len);
			}
			detector.dataEnd();

			String charset = detector.getDetectedCharset();
			detector.reset();

			if (charset == null || charset.isEmpty()) {
				return defaultCharset;
			}

			return charset;

		} catch (Exception ex) {

			return defaultCharset;

		}

	}

}
