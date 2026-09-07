package io.jimble.util.xml;

import io.jimble.util.log.Log;
import org.w3c.dom.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Stack;

/**
 * XML解析クラス
 */
public class XmlParser {

	/**
	 * XMLを解析する
	 *
	 * @param  xml XMLファイル
	 * @return     結果
	 */
	public static XmlData parse(File xml) {

		try (FileInputStream fileInputStream = new FileInputStream(xml);) {
			return parseSax(fileInputStream);
		} catch (Exception ex) {
			Log.error(ex);
			return null;
		}

	}

	/**
	 * XMLを解析する
	 *
	 * @param  xml XML文字列
	 * @return     結果
	 */
	public static XmlData parse(String xml) {

		try (
		  ByteArrayInputStream byteArrayInputStream
		    = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
		) {
			return parseSax(byteArrayInputStream);
		} catch (Exception ex) {
			Log.error(ex);
			return null;
		}

	}

	/**
	 * XMLを解析する
	 *
	 * @param  inputStream 入力ソース
	 * @return             結果
	 */
	private static XmlData parseDom(InputStream inputStream) {

		try (BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document document = documentBuilder.parse(bufferedInputStream);
			document.getDocumentElement().normalize();
			return parseDom(document.getDocumentElement());
		} catch (Exception ex) {
			Log.error(ex);
			return null;
		}

	}

	/**
	 * 要素を解析する
	 *
	 * @param  element 要素
	 * @return         結果
	 */
	private static XmlData parseDom(Element element) {

		XmlData xmlData = new XmlData();

		// タグ名
		xmlData.setTagName(element.getTagName());
		// 属性
		NamedNodeMap namedNodeMap = element.getAttributes();

		for (int i = 0; i < namedNodeMap.getLength(); i++) {
			Node node = namedNodeMap.item(i);
			xmlData.addAttribute(node.getNodeName(), node.getNodeValue());
		}

		// 子要素
		StringBuilder textContentBuilder = new StringBuilder();
		NodeList childList = element.getChildNodes();

		for (int i = 0; i < childList.getLength(); i++) {
			Node node = childList.item(i);

			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element childElement = (Element) node;
				XmlData childXmlData = parseDom(childElement);
				xmlData.addChild(childXmlData);
			} else if (
			  node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE
			) {
				textContentBuilder.append(node.getTextContent());
			}

		}

		// 値
		xmlData.setTextContent(textContentBuilder.toString());

		return xmlData;

	}

	/**
	 * XMLを解析する
	 *
	 * @param  inputStream 入力ソース
	 * @return             結果
	 */
	private static XmlData parseSax(InputStream inputStream) {

		SAXParserFactory factory = SAXParserFactory.newInstance();

		try (BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
			XmlSaxParser xmlSaxParser = new XmlSaxParser();

			SAXParser parser = factory.newSAXParser();
			parser.parse(bufferedInputStream, xmlSaxParser);

			return xmlSaxParser.getRootElement();
		} catch (Exception ex) {
			Log.error(ex);
			return null;
		}

	}

	/**
	 * SAX parser
	 */
	private static class XmlSaxParser extends DefaultHandler {

		/* ルート要素 */
		private XmlData rootElement = null;

		/**
		 * ルート要素を取得する
		 *
		 * @return ルート要素
		 */
		public XmlData getRootElement() {

			return rootElement;

		}

		/* 要素スタック */
		private final Stack<XmlData> stack = new Stack<>();

		/* 現在要素 */
		private XmlData nowElement = null;

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
		  throws SAXException {

			XmlData newElement = new XmlData().setTagName(qName);

			if (nowElement != null) {
				stack.push(nowElement);
				nowElement.addChild(newElement);
			}

			if (rootElement == null) {
				rootElement = newElement;
			}

			if (attributes != null) {

				for (int i = 0; i < attributes.getLength(); i++) {
					newElement.addAttribute(attributes.getQName(i), attributes.getValue(i));
				}

			}

			nowElement = newElement;

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {

			if (stack.isEmpty()) {
				nowElement = null;
			} else {
				nowElement = stack.pop();
			}

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {

			if (nowElement != null) {
				nowElement.addTextContent(new String(ch, start, length));
			}

		}

	}

}
