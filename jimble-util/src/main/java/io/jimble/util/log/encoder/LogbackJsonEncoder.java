package io.jimble.util.log.encoder;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import io.jimble.util.json.Dson;
import io.jimble.util.data.Data;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LogbackJsonEncoder extends EncoderBase<ILoggingEvent> {

	/* empty bytes */
	private static final byte[] EMPTY_BYTES = new byte[0];

	/* 改行 */
	private static final byte[] LINE_SEPARATOR = "\n".getBytes(StandardCharsets.UTF_8);

	/* 日時の書式（この機械の時間帯。SimpleDateFormat と違い、使い回せる） */
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

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

		logData.put("@timestamp", TIMESTAMP.format(Instant.ofEpochMilli(iLoggingEvent.getTimeStamp())));
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

		/*
		 * 1行ごとに BufferedOutputStream（8KB）を重ねない。書き先がもうメモリなので、重ねても写しが増えるだけ。
		 * 日時の書式も使い回す（SimpleDateFormat を毎回作っていた）
		 */
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream(512)) {

			Dson.encodes(logData, baos, "UTF-8");
			baos.write(LINE_SEPARATOR);
			return baos.toByteArray();

		} catch (Exception ex) {

			return EMPTY_BYTES;

		}

	}

}
