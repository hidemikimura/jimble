package io.jimble.util.json.formatter.array;

import io.jimble.util.convertor.Configration;
import io.jimble.util.json.formatter.Formatter;
import io.jimble.util.json.formatter.IFormatter;
import io.jimble.util.json.formatter.lang.NullFormatter;
import io.jimble.util.json.formatter.stream.OutputStreamWriterWrapper;

import java.lang.reflect.Array;

/**
 * 配列フォーマットクラス.
 *
 * @author DN
 */
public class ArrayFormatter implements IFormatter {

	/** インスタンス. */
	public static final ArrayFormatter INSTANCE = new ArrayFormatter();

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

		int length = Array.getLength(obj);
		int i = 0;
		boolean isOutput = false;
		while (i < length) {

			Object k = Array.get(obj, i);

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

			i++;

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
