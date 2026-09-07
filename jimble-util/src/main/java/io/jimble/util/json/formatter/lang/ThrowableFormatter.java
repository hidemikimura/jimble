package io.jimble.util.json.formatter.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.json.formatter.IFormatter;
import io.jimble.util.json.formatter.stream.OutputStreamWriterWrapper;

/**
 * Throwableフォーマットクラス
 */
public class ThrowableFormatter implements IFormatter {

	/* インスタンス */
	public static ThrowableFormatter INSTANCE = new ThrowableFormatter();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void format(OutputStreamWriterWrapper writer, Configration conf, Object obj) throws Exception {

		writer.write('[');

		Throwable throwable = (Throwable) obj;

		boolean isOutput = false;

		if (throwable.getMessage() != null && !throwable.getMessage().isEmpty()) {
			writer.write('"');
			writer.write(StringFormatter.escape(throwable.getMessage()));
			writer.write('"');
			isOutput = true;
		}

		for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {

			if (isOutput) {
				writer.write(',');
			} else {
				isOutput = true;
			}

			writer.write('"');
			writer.write(StringFormatter.escape(stackTraceElement.toString()));
			writer.write('"');

		}

		outputCaused(writer, throwable, isOutput);

		writer.write(']');

	}

	private void outputCaused (OutputStreamWriterWrapper writer, Throwable throwable, boolean isOutput) throws Exception {

		if (throwable.getCause() == null) {
			return;
		}

		if (isOutput) {
			writer.write(',');
		}

		writer.write("\"\",");

		Throwable throwableCase = throwable.getCause();
		writer.write("\"Caused by: ");
		writer.write(throwableCase.getClass().getCanonicalName());
		writer.write(": ");
		writer.write(StringFormatter.escape(throwableCase.getMessage()));
		writer.write('"');

		for (StackTraceElement stackTraceElement : throwableCase.getStackTrace()) {

			writer.write(',');

			writer.write('"');
			writer.write(StringFormatter.escape(stackTraceElement.toString()));
			writer.write('"');

		}

		outputCaused(writer, throwableCase, true);

	}

}
