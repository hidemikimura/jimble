package io.jimble.util.json.formatter;

import io.jimble.util.convertor.Configration;
import io.jimble.util.json.formatter.stream.OutputStreamWriterWrapper;

/**
 * フォーマットインターフェイス.
 * 
 * @author DN
 */
public interface IFormatter {

	/**
	 * オブジェクトをJSON文字列に変換する.
	 * 
	 * @param writer 出力ラッパ
	 * @param conf 設定情報
	 * @param obj オブジェクト
	 * @throws Exception 例外
	 */
	public void format(OutputStreamWriterWrapper writer, Configration conf, Object obj) throws Exception;

}
