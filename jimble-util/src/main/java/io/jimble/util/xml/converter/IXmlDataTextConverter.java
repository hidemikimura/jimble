package io.jimble.util.xml.converter;

/**
 * テキストコンバーター
 */
public interface IXmlDataTextConverter {

	/**
	 * 変換
	 *
	 * @param  src テキスト
	 * @return     変換後テキスト
	 */
	public String convert(String src);

}
