package io.jimble.util.json.formatter.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.json.formatter.Formatter;
import io.jimble.util.json.formatter.IFormatter;
import io.jimble.util.json.formatter.lang.NullFormatter;
import io.jimble.util.json.formatter.lang.StringFormatter;
import io.jimble.util.json.formatter.stream.OutputStreamWriterWrapper;
import io.jimble.util.data.TableNest;
import io.jimble.util.data.async.AsyncData;

import java.util.Map;

/**
 * Mapフォーマットクラス.
 *
 * @author DN
 */
public class MapFormatter implements IFormatter {

	/** インスタンス. */
	public static final MapFormatter INSTANCE = new MapFormatter();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void format(OutputStreamWriterWrapper writer, Configration conf, Object obj) throws Exception {

		/** 循環参照前処理. */

		if (conf.isMaxHierarchy()) {
			writer.write("{}", 0, 2);
			return;
		}

		int hash;
		if (obj instanceof AsyncData) {
			hash = obj.hashCode();
		} else {
			hash = System.identityHashCode(obj);
		}

		if (!conf.hashSet.add(hash)) {
			writer.write("{}", 0, 2);
			return;
		}

		/** 出力処理. */

		boolean isOutput = false;
		conf.Hierarchy++;

		writer.write('{');

		Map< ? , ? > map = (Map< ? , ? >) obj;

		/*
		 * テーブルネストの組み直し（要件 F-A-11）。
		 *
		 * 循環参照のハッシュは<b>組み直す前のノード</b>で取ってある。
		 * 組み直した Data は呼ばれるたびに別のインスタンスになるので、
		 * ここで取り直すと同じノードを2度書き出してしまう。
		 */
		if (obj instanceof AsyncData asyncData && conf.tableNest != TableNest.AS_IS) {
			map = asyncData.reshape(conf.tableNest);
		}

		IFormatter lastFormatter = null;
		Class< ? > lastClass = null;

		for (Map.Entry< ? , ? > entry : map.entrySet()) {

			Object k = entry.getKey();
			if (k == null) {
				continue;
			}
			Object v = entry.getValue();

			if (conf.isOutputNullValue || v != null) {

				if (isOutput) {
					writer.write(',');
				} else {
					isOutput = true;
				}

				writer.writeln(conf);
				writer.writelt(conf);

				StringFormatter.INSTANCE.format(writer, conf, k);
				writer.write(':');
				if (v == null) {
					NullFormatter.INSTANCE.format(writer, conf, null);
				} else {
					Class< ? > tClass = v.getClass();
					if (tClass.equals(lastClass)) {
						lastFormatter.format(writer, conf, v);
					} else {
						lastFormatter = Formatter.format(writer, v, conf);
						lastClass = tClass;
					}
				}

			}

		}

		conf.Hierarchy--;

		if (isOutput) {
			writer.writeln(conf);
			writer.writelt(conf);
		}
		writer.write('}');

		/** 循環参照後処理. */

		conf.hashSet.remove(hash);

	}

}
