package io.jimble.util.json.formatter.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.json.formatter.IFormatter;
import io.jimble.util.json.formatter.stream.OutputStreamWriterWrapper;

/**
 * 数値フォーマットクラス.
 * 
 * @author DN
 */
public class NumberFormatter implements IFormatter {

	/** インスタンス. */
	public static final NumberFormatter INSTANCE = new NumberFormatter();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void format(OutputStreamWriterWrapper writer, Configration conf, Object obj) throws Exception {

		writer.write(PropertyUtil.toString(obj));

	}

}
