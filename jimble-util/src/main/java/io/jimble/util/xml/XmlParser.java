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
			DocumentBuilderFactory documentBuilderFactory = secureDocumentBuilderFactory();
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

		SAXParserFactory factory = secureSaxParserFactory();

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
	 * 外部のものを読まない SAX の factory（D-206）
	 *
	 * <p>
	 * <b>DOCTYPE を断る。</b>JDK の既定のままだと外部実体を読むので、
	 * {@code <!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><r>&x;</r>} で
	 * <b>サーバーのファイルが読め</b>、{@code http://} の実体で<b>内側のネットワークへ届いた</b>（XXE / SSRF）。
	 * DOCTYPE を含む XML は解析できず、{@code parse} は null を返す。
	 * </p>
	 *
	 * @return	factory
	 */
	static SAXParserFactory secureSaxParserFactory () {

		SAXParserFactory factory = SAXParserFactory.newInstance();

		try {
			factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		} catch (Exception ex) {
			// 守れない factory では解析しない
			throw new IllegalStateException("XML の解析器を安全に設定できませんでした", ex);
		}

		factory.setXIncludeAware(false);

		return factory;

	}

	/**
	 * 外部のものを読まない DOM の factory（D-206）
	 *
	 * @return	factory
	 */
	static DocumentBuilderFactory secureDocumentBuilderFactory () {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		try {
			factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		} catch (Exception ex) {
			throw new IllegalStateException("XML の解析器を安全に設定できませんでした", ex);
		}

		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);

		return factory;

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
