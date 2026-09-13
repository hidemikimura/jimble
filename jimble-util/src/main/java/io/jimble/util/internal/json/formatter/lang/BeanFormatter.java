package io.jimble.util.internal.json.formatter.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.internal.json.formatter.Formatter;
import io.jimble.util.internal.json.formatter.IFormatter;
import io.jimble.util.internal.json.formatter.array.ArrayFormatter;
import io.jimble.util.internal.json.formatter.stream.OutputStreamWriterWrapper;
import io.jimble.util.internal.json.formatter.util.EnumerationFormatter;
import io.jimble.util.internal.json.formatter.util.IterableFormatter;
import io.jimble.util.internal.json.formatter.util.ListFormatter;
import io.jimble.util.internal.json.formatter.util.MapFormatter;

import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * Beanフォーマットクラス.
 *
 * @author DN
 */
public class BeanFormatter implements IFormatter {

	/** インスタンス. */
	public static final BeanFormatter INSTANCE = new BeanFormatter();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void format(OutputStreamWriterWrapper writer, Configration conf, Object obj) throws Exception {

		if (obj instanceof List< ? >) {
			ListFormatter.INSTANCE.format(writer, conf, obj);
			return;
		}

		if (obj instanceof Iterable< ? >) {
			IterableFormatter.INSTANCE.format(writer, conf, obj);
			return;
		}

		if (obj instanceof Enumeration< ? >) {
			EnumerationFormatter.INSTANCE.format(writer, conf, obj);
			return;
		}

		if (obj.getClass().isArray()) {
			ArrayFormatter.INSTANCE.format(writer, conf, obj);
			return;
		}

		if (obj instanceof Map< ? , ? >) {
			MapFormatter.INSTANCE.format(writer, conf, obj);
			return;
		}

		/** 循環参照前処理. */

		if (conf.isMaxHierarchy()) {
			writer.write("{}", 0, 2);
			return;
		}

		int hash = System.identityHashCode(obj);

		if (!conf.hashSet().add(hash)) {
			writer.write("{}", 0, 2);
			return;
		}

		int h = conf.hierarchy();
		conf.hierarchy(conf.hierarchy() + 1);
		boolean isOutput = false;

		/** 出力処理. */

		writer.write('{');

		List<PropertyUtil.MethodFieldInfo> names = PropertyUtil.getFieldNames(obj.getClass());

		int length = names.size();
		int i = 0;
		while (i < length) {

			PropertyUtil.MethodFieldInfo info = names.get(i);

			Object v = info.getProperty(obj);

			if (conf.isOutputNullValue() || v != null) {

				if (isOutput) {
					writer.write(',');
				} else {
					isOutput = true;
				}

				StringFormatter.INSTANCE.format(writer, conf, info.getFieldName());
				writer.write(':');
				Formatter.format(writer, v, conf);

			}

			i++;

		}

		if (isOutput) {
			writer.writeln(conf);
			writer.writelt(conf, h);
		}
		writer.write('}');

		/** 循環参照後処理. */

		conf.hierarchy(conf.hierarchy() - 1);
		conf.hashSet().remove(hash);
	}

}
