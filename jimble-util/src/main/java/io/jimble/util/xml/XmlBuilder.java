package io.jimble.util.xml;


import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * XML作成
 */
public class XmlBuilder {

	/* 文字コード */
	private static final Charset charset = StandardCharsets.UTF_8;

	/**
	 * XMLを作成する
	 *
	 * @param  xmlData XML
	 * @return               XML文字列
	 */
	public String build(XmlData xmlData){

		try {
			try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
				build(xmlData, byteArrayOutputStream);
				return byteArrayOutputStream.toString(charset);
			} catch (Exception ex) {
				throw ex;
			}

	
		} catch (Exception ex) {
			throw io.jimble.util.internal.Unchecked.of("XML_001", "XML を書き出せませんでした", ex);
		}

	}

	/**
	 * XMLを作成する
	 *
	 * @param  xmlData XML
	 * @param  outputFile    出力ファイル
	 */
	public void build(XmlData xmlData, File outputFile){

		try {
			try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
				build(xmlData, fileOutputStream);
			} catch (Exception ex) {
				throw ex;
			}

	
		} catch (Exception ex) {
			throw io.jimble.util.internal.Unchecked.of("XML_001", "XML を書き出せませんでした", ex);
		}

	}

	/**
	 * XMLを作成する
	 *
	 * @param  xmlData XML
	 * @param  outputStream  出力ストリーム
	 */
	public void build(XmlData xmlData, OutputStream outputStream) {

		build(xmlData, new OutputStreamWriter(outputStream, charset));

	}

	/**
	 * XMLを作成する
	 *
	 * @param  xmlData      XML
	 * @param  outputStreamWriter 出力ライター
	 */
	public void build(XmlData xmlData, Writer outputStreamWriter){

		try {
			BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);

			bufferedWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

			write(xmlData, bufferedWriter);

			bufferedWriter.flush();

	
		} catch (Exception ex) {
			throw io.jimble.util.internal.Unchecked.of("XML_001", "XML を書き出せませんでした", ex);
		}

	}

	/**
	 * XMLを出力する
	 *
	 * @param  xmlData      XML
	 * @param  outputStreamWriter ライター
	 */
	private void write(XmlData xmlData, Writer outputStreamWriter) throws Exception {

		// 開始タグ
		outputStreamWriter.write("<");
		outputStreamWriter.write(xmlData.getTagName());

		// 属性
		for (Map.Entry<String, String> attribute : xmlData.getAttributes()) {
			outputStreamWriter.write(" ");
			outputStreamWriter.write(attribute.getKey());
			outputStreamWriter.write("=\"");
			outputStreamWriter.write(XmlEscape.escape(attribute.getValue()));
			outputStreamWriter.write("\"");
		}

		outputStreamWriter.write(">");

		// テキスト
		{
			String text = xmlData.getConvertedText();

			if (text != null && !text.isEmpty()) {
				outputStreamWriter.write(XmlEscape.escape(text));
			}

		}

		// 子要素
		for (String key : xmlData.getChildKeys()) {
			List<XmlData> childList = xmlData.getListChildOptional(key);

			for (XmlData child : childList) {
				write(child, outputStreamWriter);
			}

		}

		// 終了タグ
		outputStreamWriter.write("</");
		outputStreamWriter.write(xmlData.getTagName());
		outputStreamWriter.write(">");

	}

}
