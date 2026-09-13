package io.jimble.util.internal.json.formatter.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.internal.json.formatter.FormatterConfigKeys;
import io.jimble.util.internal.json.formatter.IFormatter;
import io.jimble.util.internal.json.formatter.stream.OutputStreamWriterWrapper;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Dateフォーマットクラス.
 *
 * @author DN
 */
public class DateFormatter implements IFormatter {

	/**
	 * インスタンス.
	 */
	public static final DateFormatter INSTANCE = new DateFormatter();

	/**
	 * 日付フォーマッタ.
	 */
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	/* Dateフォーマット */
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void format (OutputStreamWriterWrapper writer, Configration conf, Object obj) throws Exception {

		Object f = null;
		if (conf != null && conf.outputDateTypeFormat() != null) {

			LocalDateTime localDateTime = ((Date) obj).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
			writer.write('"');
			if (conf.isOutputDateType()) {
				writer.write("date::");
			}
			writer.write(conf.outputDateTypeFormat().format(localDateTime));
			writer.write('"');

		} else if (conf != null && (f = conf.get(FormatterConfigKeys.FORMAT_DATE_TO_STRING)) != null) {

			if (f instanceof DateFormat) {

				writer.write('"');
				if (conf.isOutputDateType()) {
					writer.write("date::");
				}
				writer.write(((DateFormat) f).format(obj));
				writer.write('"');

			} else if (f instanceof String) {

				writer.write('"');
				try {
					SimpleDateFormat sdf = new SimpleDateFormat(f.toString());
					if (conf.isOutputDateType()) {
						writer.write("date::");
					}
					writer.write(sdf.format(obj));
					conf.put(FormatterConfigKeys.FORMAT_DATE_TO_STRING, sdf);
				} catch (Exception e) {
				}
				writer.write('"');

			} else {

				LocalDateTime localDateTime = ((Date) obj).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
				writer.write('"');
				if (conf.isOutputDateType()) {
					writer.write("date::");
				}
				writer.write(DATE_TIME_FORMATTER.format(localDateTime));
				writer.write('"');

			}

		} else {

			LocalDateTime localDateTime = ((Date) obj).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
			writer.write('"');
			if (conf != null && conf.isOutputDateType()) {
				writer.write("date::");
			}
			writer.write(DATE_TIME_FORMATTER.format(localDateTime));
			writer.write('"');

		}

	}

}
