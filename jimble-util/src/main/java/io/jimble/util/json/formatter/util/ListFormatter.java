package io.jimble.util.json.formatter.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.json.formatter.Formatter;
import io.jimble.util.json.formatter.IFormatter;
import io.jimble.util.json.formatter.lang.NullFormatter;
import io.jimble.util.json.formatter.stream.OutputStreamWriterWrapper;
import io.jimble.util.data.TableNest;
import io.jimble.util.data.async.AsyncList;

import java.util.List;

/**
 * Listフォーマットクラス.
 *
 * @author DN
 */
public class ListFormatter implements IFormatter {

	/** インスタンス. */
	public static final ListFormatter INSTANCE = new ListFormatter();

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

		conf.Hierarchy++;

		writer.write('[');

		IFormatter lastFormatter = null;
		Class< ? > lastClass = null;

		List< ? > list = (List< ? >) obj;

		/*
		 * テーブルネストの組み直し（要件 F-A-11）。
		 *
		 * 循環参照のハッシュは<b>組み直す前のノード</b>で取ってある。
		 */
		if (obj instanceof AsyncList asyncList && conf.tableNest != TableNest.AS_IS) {
			list = asyncList.reshape(conf.tableNest);
		}

		int length = list.size();
		int i = 0;
		boolean isOutput = false;
		while (i < length) {

			Object k = list.get(i);

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
