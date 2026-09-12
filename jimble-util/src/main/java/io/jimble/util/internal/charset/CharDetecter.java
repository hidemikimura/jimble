package io.jimble.util.internal.charset;

import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 文字列の文字コード判定ユーティリティ
 */
public class CharDetecter {

	/* 文字コード推測一覧 */
	private static final List<Charset> URL_ENCODE_CHARSET_LIST = new ArrayList<>();
	static {
		URL_ENCODE_CHARSET_LIST.add(Charset.forName("ISO-2022-JP"));
		URL_ENCODE_CHARSET_LIST.add(Charset.forName("EUC-JP"));
		URL_ENCODE_CHARSET_LIST.add(Charset.forName("UTF-8"));
		URL_ENCODE_CHARSET_LIST.add(Charset.forName("Shift-JIS"));
	}

	/**
	 * URLエンコードされた文字列の文字コードを取得する
	 *
	 * @param value	URLエンコードされた文字列
	 * @return	文字コード
	 */
	public static String detectorUrlEncodeString (String value) {

		return detectorUrlEncodeString(value, null);

	}

	/**
	 * URLエンコードされた文字列の文字コードを取得する
	 *
	 * @param value				URLエンコードされた文字列
	 * @param defaultCharset	デフォルト文字コード
	 * @return	文字コード
	 */
	public static String detectorUrlEncodeString (String value, String defaultCharset) {

		if (value == null || value.isEmpty()) {
			return value;
		}

		try {

			byte[] b = URLDecoder.decode(value, "ISO-8859-1").getBytes("ISO-8859-1");
			for (Charset charset : URL_ENCODE_CHARSET_LIST) {

				CharsetDecoder decoder = charset.newDecoder();

				try {

					String _v = decoder.decode(ByteBuffer.wrap(b)).toString();
					return charset.name();

				} catch (CharacterCodingException e) {}

			}

			return defaultCharset;

		} catch (Exception ex) {

			return defaultCharset;

		}

	}

}
