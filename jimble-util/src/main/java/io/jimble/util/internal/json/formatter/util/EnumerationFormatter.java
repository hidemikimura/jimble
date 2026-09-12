package io.jimble.util.internal.json.formatter.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.internal.json.formatter.Formatter;
import io.jimble.util.internal.json.formatter.IFormatter;
import io.jimble.util.internal.json.formatter.lang.NullFormatter;
import io.jimble.util.internal.json.formatter.stream.OutputStreamWriterWrapper;

import java.util.Enumeration;

/**
 * Enumerationフォーマットクラス.
 *
 * @author DN
 */
public class EnumerationFormatter implements IFormatter {

	/** インスタンス. */
	public static final EnumerationFormatter INSTANCE = new EnumerationFormatter();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void format(OutputStreamWriterWrapper writer, Configration conf, Object obj) throws Exception {

		/** 循環参照前処理. */

		if (conf.isMaxHierarchy()) {
			writer.write("[]", 0, 2);
			return;
		}

		int hash = System.identityHashCode(obj);

		if (!conf.hashSet.add(hash)) {
			writer.write("[]", 0, 2);
			return;
		}

		/** 出力処理. */

		conf.Hierarchy++;

		writer.write('[');

		IFormatter lastFormatter = null;
		Class< ? > lastClass = null;

		Enumeration< ? > list = (Enumeration< ? >) obj;
		boolean isOutput = false;
		while (list.hasMoreElements()) {

			Object k = list.nextElement();

			if (isOutput) {
				writer.write(',');
			} else {
				isOutput = true;
			}

			writer.writeln(conf);
			writer.writelt(conf);

			if (k == null) {
				NullFormatter.INSTANCE.format(writer, conf, null);
			} else {
				Class< ? > tClass = k.getClass();
				if (tClass.equals(lastClass)) {
					lastFormatter.format(writer, conf, k);
				} else {
					lastFormatter = Formatter.format(writer, k, conf);
					lastClass = tClass;
				}
			}

		}

		conf.Hierarchy--;

		if (isOutput) {
			writer.writeln(conf);
			writer.writelt(conf);
		}

		writer.write(']');

		/** 循環参照後処理. */

		conf.hashSet.remove(hash);

	}

}
