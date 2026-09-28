package io.jimble.util.io;

import io.jimble.util.log.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * IOユーティリティ.
 */
public class IOUtil {

	/**
	 * Closableリソースを閉じる.
	 *
	 * @param closeables	対象
	 */
	public static void close (AutoCloseable... closeables) {

		if (closeables == null) {
			return;
		}

		try {

			for (AutoCloseable c : closeables) {

				if (c != null) {
					c.close();
				}

			}

		} catch (Exception ex) {

			Log.debug(ex.getMessage(), ex);

		}

	}

	/**
	 * ストリームを文字列として読み込む.
	 *
	 * @param in	ストリーム
	 * @return	文字列
	 */
	public static String read (InputStream in) {

		BufferedReader streamReader = null;
		try {

			streamReader = new BufferedReader(new InputStreamReader(in, "UTF-8"));

			StringBuilder response = new StringBuilder();

			int len;
			char[] buff = new char[4096];
			while ((len = streamReader.read(buff, 0, 4096)) > -1) {
				response.append(buff, 0, len);
			}

			return response.toString();

		} catch (Exception ex) {

			Log.debug(ex.getMessage(), ex);
			return "";

		} finally {

			close(streamReader);

		}


	}

	/**
	 * 入力ストリームから出力ストリームにコピーする
	 *
	 * @param is	入力ストリーム
	 * @param os	出力ストリーム
	 * @throws java.io.UncheckedIOException	読み書きできなかったとき（2.0。1.x は検査例外の Exception）
	 */
	public static void copy (InputStream is, OutputStream os){

		try {
			byte[] buff = new byte[4096];
			int len;
			while ((len = is.read(buff, 0, buff.length)) > -1) {
				os.write(buff, 0, len);
			}

	
		} catch (Exception ex) {
			throw io.jimble.util.internal.Unchecked.of("IO_001", "コピーできませんでした", ex);
		}

	}

	/**
	 * 入力ストリームから行ごとの文字列を読み込む
	 *
	 * @param is		入力ストリーム
	 * @param charset	文字コード
	 * @return	文字一覧
	 * @throws java.io.UncheckedIOException	読めなかったとき（2.0。1.x は検査例外の Exception）
	 */
	public static List<String> readLines (InputStream is, String charset){

		try {
			List<String> lines = new ArrayList<>();

			InputStreamReader isr = new InputStreamReader(is, charset);
			BufferedReader br = new BufferedReader(isr);

			String line;
			while ((line = br.readLine()) != null) {
				lines.add(line);
			}

			return lines;

	
		} catch (Exception ex) {
			throw io.jimble.util.internal.Unchecked.of("IO_001", "読み込めませんでした", ex);
		}

	}

}
