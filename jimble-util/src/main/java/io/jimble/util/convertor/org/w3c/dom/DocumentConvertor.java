package io.jimble.util.convertor.org.w3c.dom;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.convertor.lang.BeanConvertor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;
import java.util.Map;

/**
 * Document変換クラス.
 * 
 * @author DN
 */
public class DocumentConvertor implements IConvertor<Document> {

	/** インスタンス. */
	public static final DocumentConvertor INSTANCE = new DocumentConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Document convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (!PropertyUtil.isAssignableFrom(List.class, obj.getClass())) {
			return (Document) BeanConvertor.INSTANCE.convert(conf, obj, destClasses);
		}

		return createDocumentFromJsonML(conf, obj, destClasses);

	}

	/**
	 * JsonMLからDocumentを作成する.
	 * 
	 * @param conf 設定情報
	 * @param obj オブジェクト
	 * @param destClasses 変換希望クラス
	 * @return Document
	 * @throws Exception 例外
	 */
	private Document createDocumentFromJsonML(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		List< ? > list = (List< ? >) obj;

		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

		Element root = createElement(doc, list);
		if (root != null) {
			doc.appendChild(root);
		}

		return doc;

	}

	/**
	 * Element作成.
	 * 
	 * @param doc Document
	 * @param list List
	 * @return Element
	 */
	private Element createElement(Document doc, List< ? > list) {

		if (list.size() == 0) {
			return null;
		}

		String tagName = PropertyUtil.toString(list.get(0));

		Element element = doc.createElement(tagName);

		for (int i = 1; i < list.size(); i++) {

			Object o = list.get(i);

			if (o == null) {
				continue;
			}

			Class< ? > c = o.getClass();

			if (o instanceof String) {
				Text text = doc.createTextNode((String) o);
				if (text != null) {
					element.appendChild(text);
				}
			} else if (PropertyUtil.isAssignableFrom(Map.class, c)) {
				for (Map.Entry< ? , ? > entry : ((Map< ? , ? >) o).entrySet()) {

					Object k = entry.getKey();
					Object v = entry.getValue();

					if (k == null || v == null) {
						continue;
					}

					String sk = PropertyUtil.toString(k);
					String sv = PropertyUtil.toString(v);

					if (sk.length() == 0 || sv.length() == 0) {
						continue;
					}

					element.setAttribute(sk, sv);

				}
			} else if (PropertyUtil.isAssignableFrom(List.class, c)) {
				Element child = createElement(doc, (List< ? >) o);
				if (child != null) {
					element.appendChild(child);
				}
			}
		}

		return element;

	}
}
