package io.jimble.util.internal.json.formatter.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.internal.convertor.Conversions;
import io.jimble.util.internal.json.formatter.IFormatter;
import io.jimble.util.internal.json.formatter.stream.OutputStreamWriterWrapper;

/**
 * Enumフォーマットクラス.
 * 
 * @author DN
 */
public class EnumFormatter implements IFormatter {

	/** インスタンス. */
	public static final EnumFormatter INSTANCE = new EnumFormatter();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void format(OutputStreamWriterWrapper writer, Configration conf, Object obj) throws Exception {

		writer.write('"');

		writer.write(Conversions.toText(obj));

		writer.write('"');

	}

}
