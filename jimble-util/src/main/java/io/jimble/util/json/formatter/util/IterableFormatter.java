package io.jimble.util.json.formatter.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.json.formatter.Formatter;
import io.jimble.util.json.formatter.IFormatter;
import io.jimble.util.json.formatter.lang.NullFormatter;
import io.jimble.util.json.formatter.stream.OutputStreamWriterWrapper;
import io.jimble.util.data.TableNest;
import io.jimble.util.data.async.AsyncList;

import java.util.Iterator;

/**
 * Iterableフォーマットクラス.
 *
 * @author DN
 */
public class IterableFormatter implements IFormatter {

	/** インスタンス. */
	public static final IterableFormatter INSTANCE = new IterableFormatter();

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

		int hash;
		if (obj instanceof AsyncList) {
			hash = obj.hashCode();
		} else {
			hash = System.identityHashCode(obj);
		}

		if (!conf.hashSet.add(hash)) {
			writer.write("[]", 0, 2);
			return;
		}

		/** 出力処理. */

		boolean isOutput = false;
		conf.Hierarchy++;

		writer.write('[');

		IFormatter lastFormatter = null;
		Class< ? > lastClass = null;

		Iterable< ? > iterable = (Iterable< ? >) obj;

		// テーブルネストの組み直し（要件 F-A-11）
		if (obj instanceof AsyncList asyncList && conf.tableNest != TableNest.AS_IS) {
			iterable = asyncList.reshape(conf.tableNest);
		}

		Iterator< ? > it = iterable.iterator();
		while (it.hasNext()) {

			Object k = it.next();

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
