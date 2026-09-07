package io.jimble.util.json.formatter.time;

import io.jimble.util.convertor.Configration;
import io.jimble.util.json.formatter.FormatterConfigKeys;
import io.jimble.util.json.formatter.IFormatter;
import io.jimble.util.json.formatter.stream.OutputStreamWriterWrapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * LocalDateTimeフォーマットクラス.
 *
 * @author DN
 */
public class LocalDateTimeFormatter implements IFormatter {

	/**
	 * インスタンス.
	 */
	public static final LocalDateTimeFormatter INSTANCE = new LocalDateTimeFormatter();

	/**
	 * 日付フォーマッタ.
	 */
	private DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void format (OutputStreamWriterWrapper writer, Configration conf, Object obj) throws Exception {

		Object f = null;
		if (conf != null && conf.outputDateTypeFormat != null) {

			writer.write('"');
			if (conf.isOutputDateType) {
				writer.write("date::");
			}
			writer.write(conf.outputDateTypeFormat.format((LocalDateTime) obj));
			writer.write('"');

		} else if (conf != null && (f = conf.get(FormatterConfigKeys.FORMAT_DATE_TO_STRING)) != null) {

			if (f instanceof DateTimeFormatter) {

				writer.write('"');
				if (conf.isOutputDateType) {
					writer.write("date::");
				}
				writer.write(((DateTimeFormatter) f).format((LocalDateTime) obj));
				writer.write('"');

			} else if (f instanceof String) {

				writer.write('"');
				try {
					DateTimeFormatter sdf = DateTimeFormatter.ofPattern(f.toString());
					if (conf.isOutputDateType) {
						writer.write("date::");
					}
					writer.write(sdf.format((LocalDateTime) obj));
					conf.put(FormatterConfigKeys.FORMAT_DATE_TO_STRING, sdf);
				} catch (Exception e) {
				}
				writer.write('"');

			} else {

				writer.write('"');
				if (conf.isOutputDateType) {
					writer.write("date::");
				}
				writer.write(sdf.format((LocalDateTime) obj));
				writer.write('"');

			}

		} else {

			writer.write('"');
			if (conf != null && conf.isOutputDateType) {
				writer.write("date::");
			}
			writer.write(sdf.format((LocalDateTime) obj));
			writer.write('"');

		}

	}

}
