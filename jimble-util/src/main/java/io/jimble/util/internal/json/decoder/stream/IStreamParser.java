package io.jimble.util.internal.json.decoder.stream;

import io.jimble.util.convertor.Configration;
import io.jimble.util.internal.json.decoder.stream.util.IInputStream;

/**
 * ストリーム解析インターフェイス.
 * 
 * @author DN
 */
public interface IStreamParser {

	/**
	 * ストリームを解析する.
	 * 
	 * @param conf 設定情報
	 * @param stream ストリーム
	 * @return 解析結果
	 */
	public Object parse(Configration conf, IInputStream stream);

}
