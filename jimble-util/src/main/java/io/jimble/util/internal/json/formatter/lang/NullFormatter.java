package io.jimble.util.internal.json.formatter.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.internal.json.formatter.IFormatter;
import io.jimble.util.internal.json.formatter.stream.OutputStreamWriterWrapper;

/**
 * Nullフォーマットクラス.
 * 
 * @author DN
 */
public class NullFormatter implements IFormatter {

	/** インスタンス. */
	public static final NullFormatter INSTANCE = new NullFormatter();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void format(OutputStreamWriterWrapper writer, Configration conf, Object obj) throws Exception {

		writer.write("null", 0, 4);

	}

}
