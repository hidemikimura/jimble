package io.jimble.util.json.decoder.stream;

import io.jimble.util.convertor.Configration;
import io.jimble.util.json.decoder.stream.util.IInputStream;
import io.jimble.util.data.Data;

/**
 * オブジェクト解析クラス.
 *
 * @author DN
 */
public class ObjectStreamParser implements IStreamParser {

	/** インスタンス. */
	public static final ObjectStreamParser INSTANCE = new ObjectStreamParser();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object parse(Configration conf, IInputStream stream) {

		return parseMap(conf, stream);

	}

	/**
	 * マップにデコードする.
	 *
	 * @param conf 設定情報
	 * @param stream 入力ストリーム
	 * @return マップ
	 */
	/*
	 * switch を<b>わざと落として</b>いる。
	 * 「区切り文字はどれも同じ扱い」「: のときだけ先にキーを切り替える」
	 * という書き方で、break を入れると<b>区切り文字が値の一部として読まれる</b>。
	 */
	@SuppressWarnings("fallthrough")
	private Data parseMap(Configration conf, IInputStream stream) {

		Data res = new Data();

		String key = null;

		boolean isKey = true;

		int val = -1;
		while ((val = stream.readInt()) != -1) {

			switch (val) {
			case ':':
				isKey = !isKey;
			case '\r':
			case '\n':
			case '\t':
			case '\f':
			case '\b':
			case ' ':
			case ',':
			case 0xFEFF:
				break;
			case '}':
				return res;
			case '{':
				res.put(key, ObjectStreamParser.INSTANCE.parse(conf, stream));
				isKey = !isKey;
				break;
			case '[':
				res.put(key, ArrayStreamParser.INSTANCE.parse(conf, stream));
				isKey = !isKey;
				break;
			case '"':
				if (isKey) {
					key = String.valueOf(StringStreamParser.INSTANCE2.parse(conf, stream));
				} else {
					res.put(key, StringStreamParser.INSTANCE2.parse(conf, stream));
					isKey = !isKey;
				}
				break;
			case '\'':
				if (isKey) {
					key = String.valueOf(StringStreamParser.INSTANCE1.parse(conf, stream));
				} else {
					res.put(key, StringStreamParser.INSTANCE1.parse(conf, stream));
					isKey = !isKey;
				}
				break;
			default:
				if (isKey) {
					key = String.valueOf(NumberStreamParser.INSTANCE_KEY.parse(conf, stream));
				} else {
					res.put(key, NumberStreamParser.INSTANCE_NO_KEY.parse(conf, stream));
					isKey = !isKey;
				}
				break;
			}

		}

		return res;
	}

}
