package io.jimble.util.internal.json.decoder;

import io.jimble.util.convertor.Configration;
import io.jimble.util.internal.json.decoder.stream.ArrayStreamParser;
import io.jimble.util.internal.json.decoder.stream.ObjectStreamParser;
import io.jimble.util.internal.json.decoder.stream.util.CharacterInputStream;
import io.jimble.util.internal.json.decoder.stream.util.FileSeekInputStream;
import io.jimble.util.internal.json.decoder.stream.util.IInputStream;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * JSONデコーダクラス.<br>
 * 
 * @author DN
 */
public class StreamDecoder extends AbstractDecoder {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object decode(Configration conf, String value) {
		return decode(conf, new CharacterInputStream(value));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object decode(Configration conf, Reader reader) {
		FileSeekInputStream stream = new FileSeekInputStream(reader);
		Object res = decode(conf, stream);
		stream.close();
		return res;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object decode(Configration conf, InputStream iStream, Charset charset) throws Exception {
		FileSeekInputStream stream = new FileSeekInputStream(iStream, charset);
		Object res = decode(conf, stream);
		stream.close();
		return res;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object decode(Configration conf, File file, Charset charset) throws Exception {
		FileSeekInputStream stream = new FileSeekInputStream(file, charset);
		Object res = decode(conf, stream);
		stream.close();
		return res;
	}

	/**
	 * ストリームを解析する.
	 * 
	 * @param stream ストリーム
	 * @return 解析結果
	 */
	private static Object decode(Configration conf, IInputStream stream) {

		int val = -1;
		while ((val = stream.readInt()) != -1) {

			switch (val) {
			case '\r':
			case '\n':
			case '\t':
			case '\f':
			case '\b':
			case ' ':
			case 0xFEFF:
				break;
			case '{': // オブジェクト
				return ObjectStreamParser.INSTANCE.parse(conf, stream);
			case '[': // 配列
				return ArrayStreamParser.INSTANCE.parse(conf, stream);
			case '"': // 文字列
				// return StringStreamParser.INSTANCE2.parse(conf, stream);
				return null;
			case '\'': // 文字列
				// return StringStreamParser.INSTANCE1.parse(conf, stream);
				return null;
			default: // その他
				// return NumberStreamParser.INSTANCE.parse(conf, stream);
				return null;
			}

		}

		return null;

	}

}
