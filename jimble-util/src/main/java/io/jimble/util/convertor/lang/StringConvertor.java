package io.jimble.util.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.ConvertorConfigKeys;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.data.Data;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * String変換クラス.
 *
 * @author DN
 */
public class StringConvertor implements IConvertor<String> {

	/**
	 * インスタンス.
	 */
	public static final StringConvertor INSTANCE = new StringConvertor();

	/* Dateフォーマット */
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String convert (Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof String) {

			return (String) obj;

		} else if (obj instanceof Data) {

			return ((Data) obj).getJsonString();

		} else if (obj instanceof Date) {

			if (conf != null) {

				Object f = conf.get(ConvertorConfigKeys.CONVERT_DATE_TO_STRING);

				if (f instanceof DateFormat) {

					return ((DateFormat) f).format((Date) obj);

				} else if (f instanceof String) {

					try {
						SimpleDateFormat sdf = new SimpleDateFormat(f.toString());
						conf.put(ConvertorConfigKeys.CONVERT_DATE_TO_STRING, sdf);
						return sdf.format((Date) obj);
					} catch (Exception e) {
						conf.remove(ConvertorConfigKeys.CONVERT_DATE_TO_STRING);
					}

				}

			}

			LocalDateTime localDateTime = ((Date) obj).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
			return DATE_TIME_FORMATTER.format(localDateTime);

//			return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format((Date) obj);

		} else if (obj instanceof Document) {

			try {

				Document doc = (Document) obj;

				String encode = doc.getXmlEncoding();
				if (encode == null || encode.isEmpty()) {
					encode = "UTF-8";
				}

				StringWriter sw = new StringWriter();
				TransformerFactory tf = TransformerFactory.newInstance();
				Transformer transformer = tf.newTransformer();
				transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
				transformer.setOutputProperty(OutputKeys.METHOD, "xml");
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				transformer.setOutputProperty(OutputKeys.ENCODING, encode);

				transformer.transform(new DOMSource(doc), new StreamResult(sw));

				return sw.toString();

			} catch (Exception ignore) {
			}

		}

		return PropertyUtil.toString(obj);
	}

}
