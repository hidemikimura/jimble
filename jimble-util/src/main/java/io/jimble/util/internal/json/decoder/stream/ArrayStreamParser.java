package io.jimble.util.internal.json.decoder.stream;

import io.jimble.util.convertor.Configration;
import io.jimble.util.internal.json.decoder.stream.util.IInputStream;

import java.util.ArrayList;
import java.util.List;

/**
 * 配列解析クラス.
 * 
 * @author DN
 */
public class ArrayStreamParser implements IStreamParser {

	/** インスタンス. */
	public static final ArrayStreamParser INSTANCE = new ArrayStreamParser();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object parse(Configration conf, IInputStream stream) {

		List<Object> res = new ArrayList<Object>();

		int val = -1;
		while ((val = stream.readInt()) != -1) {

			switch (val) {
			case ':':
			case ',':
			case '\r':
			case '\n':
			case '\t':
			case '\f':
			case '\b':
			case ' ':
			case 0xFEFF:
				break;
			case ']': // 閉めタグ
				return res;
			case '{': // オブジェクト
				res.add(ObjectStreamParser.INSTANCE.parse(conf, stream));
				break;
			case '[': // 配列
				res.add(ArrayStreamParser.INSTANCE.parse(conf, stream));
				break;
			case '"': // 文字列
				res.add(StringStreamParser.INSTANCE2.parse(conf, stream));
				break;
			case '\'': // 文字列
				res.add(StringStreamParser.INSTANCE1.parse(conf, stream));
				break;
			default: // 数値 or 文字列
				res.add(NumberStreamParser.INSTANCE_NO_KEY.parse(conf, stream));
				break;
			}

		}

		return res;

	}

}
