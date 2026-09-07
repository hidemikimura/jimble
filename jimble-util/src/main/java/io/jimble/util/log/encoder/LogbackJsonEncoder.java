package io.jimble.util.log.encoder;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import io.jimble.util.convertor.Configration;
import io.jimble.util.json.Dson;
import io.jimble.util.data.Data;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogbackJsonEncoder extends EncoderBase<ILoggingEvent> {

	/* empty bytes */
	private static final byte[] EMPTY_BYTES = new byte[0];

	/* 改行 */
	private static final byte[] LINE_SEPARATOR = "\n".getBytes(StandardCharsets.UTF_8);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] headerBytes() {

		return EMPTY_BYTES;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] footerBytes() {

		return EMPTY_BYTES;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] encode(ILoggingEvent iLoggingEvent) {

		Data logData = new Data();

		logData.put("@timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(iLoggingEvent.getTimeStamp())));
		logData.put("@version", iLoggingEvent.getSequenceNumber());
		logData.put("message", iLoggingEvent.getMessage());
		logData.put("logger_name", iLoggingEvent.getLoggerName());
		logData.put("thread_name", iLoggingEvent.getThreadName());
		logData.put("level", iLoggingEvent.getLevel().toString());
		logData.put("level_value", iLoggingEvent.getLevel().levelInt);

		{
			Object[] argumentArray = iLoggingEvent.getArgumentArray();
			if (argumentArray != null) {
				for (Object o : argumentArray) {
					if (o instanceof Data d) {
						logData.putAll(d);
					}
				}
			}
		}

		try (
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			BufferedOutputStream bos = new BufferedOutputStream(baos);
		) {

			Configration configration = new Configration();
			configration.isOutputUnknown = false;
			configration.MaxHierarchy = 3;

			Dson.encodes(logData, bos, "UTF-8");
			bos.write("\n".getBytes(StandardCharsets.UTF_8));
			bos.flush();
			return baos.toByteArray();

		} catch (Exception ex) {

			return EMPTY_BYTES;

		}

	}

}
