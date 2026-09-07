package io.jimble.util.xml;

import org.apache.commons.text.StringEscapeUtils;

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
	 * @throws Exception     例外
	 */
	public String build(XmlData xmlData) throws Exception {

		try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
			build(xmlData, byteArrayOutputStream);
			return byteArrayOutputStream.toString(charset);
		} catch (Exception ex) {
			throw ex;
		}

	}

	/**
	 * XMLを作成する
	 *
	 * @param  xmlData XML
	 * @param  outputFile    出力ファイル
	 * @throws Exception     例外
	 */
	public void build(XmlData xmlData, File outputFile) throws Exception {

		try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
			build(xmlData, fileOutputStream);
		} catch (Exception ex) {
			throw ex;
		}

	}

	/**
	 * XMLを作成する
	 *
	 * @param  xmlData XML
	 * @param  outputStream  出力ストリーム
	 * @throws Exception     例外
	 */
	public void build(XmlData xmlData, OutputStream outputStream) throws Exception {

		build(xmlData, new OutputStreamWriter(outputStream, charset));

	}

	/**
	 * XMLを作成する
	 *
	 * @param  xmlData      XML
	 * @param  outputStreamWriter 出力ライター
	 * @throws Exception          例外
	 */
	public void build(XmlData xmlData, Writer outputStreamWriter) throws Exception {

		BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);

		bufferedWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

		write(xmlData, bufferedWriter);

		bufferedWriter.flush();

	}

	/**
	 * XMLを出力する
	 *
	 * @param  xmlData      XML
	 * @param  outputStreamWriter ライター
	 * @throws Exception          例外
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
			outputStreamWriter.write(StringEscapeUtils.escapeXml10(attribute.getValue()));
			outputStreamWriter.write("\"");
		}

		outputStreamWriter.write(">");

		// テキスト
		{
			String text = xmlData.getConvertedText();

			if (text != null && !text.isEmpty()) {
				outputStreamWriter.write(StringEscapeUtils.escapeXml10(text));
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
