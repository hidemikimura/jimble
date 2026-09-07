package io.jimble.util.log.encoder;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import io.jimble.util.convertor.Configration;
import io.jimble.util.json.Dson;
import io.jimble.util.data.Data;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class LogbackErrorEncoder extends EncoderBase<ILoggingEvent> {

	/* empty bytes */
	private static final byte[] EMPTY_BYTES = new byte[0];

	/* 赤字 */
	private static final byte[] ANSI_RED = "\u001B[31m".getBytes(StandardCharsets.UTF_8);
	private static final byte[] ANSI_RESET = "\u001B[0m".getBytes(StandardCharsets.UTF_8);

	/* 改行 */
	private static final byte[] LINE_SEPARATOR = "\n".getBytes(StandardCharsets.UTF_8);

	/* タブ */
	private static final byte[] TAB = "\t".getBytes(StandardCharsets.UTF_8);

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

		Throwable throwable = null;
		if (logData.containsKey("throwable") && logData.get("throwable") instanceof Throwable th) {
			throwable = th;
		}
		if (throwable == null) {
			for (Map.Entry<String, Object> entry : logData.entrySet()) {
				if (entry.getValue() instanceof Throwable th) {
					throwable = th;
					break;
				}
			}
		}

		if (throwable == null) {
			try (
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				BufferedOutputStream bos = new BufferedOutputStream(baos);
			) {

				Configration configration = new Configration();
				configration.isOutputUnknown = false;
				configration.MaxHierarchy = 3;

				bos.write(ANSI_RED);
				Dson.encodes(logData, bos, "UTF-8");
				bos.write(ANSI_RESET);
				bos.write(LINE_SEPARATOR);
				bos.flush();
				return baos.toByteArray();

			} catch (Exception ex) {

				return EMPTY_BYTES;

			}
		} else {
			try (
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				BufferedOutputStream bos = new BufferedOutputStream(baos)
			) {

				byte[] suffix = "[%s]-[%s] %s ".formatted(
					new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(iLoggingEvent.getTimeStamp()))
					, iLoggingEvent.getThreadName()
					, iLoggingEvent.getLoggerName()
				).getBytes(StandardCharsets.UTF_8);

				bos.write(ANSI_RED);

				bos.write(suffix);
				bos.write(head(logData, throwable).getBytes(StandardCharsets.UTF_8));
				bos.write(LINE_SEPARATOR);

				for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
					bos.write(TAB);
					bos.write(stackTraceElement.toString().getBytes(StandardCharsets.UTF_8));
					bos.write(LINE_SEPARATOR);
				}

				outputCaused(throwable, bos, suffix);

				bos.write(ANSI_RESET);

				bos.flush();
				return baos.toByteArray();

			} catch (Exception ex) {

				return EMPTY_BYTES;

			}
		}

	}

	/**
	 * 1行目を組み立てる（要件 D-84）
	 *
	 * <p>
	 * <b>呼んだ側が書いた説明を落とさない。</b>
	 * {@code Log.error(cause, "保存できませんでした: id=3")} の説明は
	 * {@code objects} に入る（メッセージは例外のものになる）ので、
	 * ここで拾わないと<b>画面には例外のメッセージしか出ない</b>。
	 * </p>
	 *
	 * <p>
	 * 実行 ID も入れる。アクセスログと突き合わせるためである。
	 * </p>
	 *
	 * @param logData	ログのデータ
	 * @param throwable	例外
	 * @return	1行目
	 */
	private static String head (Data logData, Throwable throwable) {

		StringBuilder head = new StringBuilder();

		String requestId = logData.getStringOptional("request_id");

		if (!requestId.isEmpty() && !"-".equals(requestId)) {
			head.append('[').append(requestId).append("] ");
		}

		String explanation = explanation(logData);

		if (!explanation.isEmpty()) {
			head.append(explanation).append(" / ");
		}

		head.append(name(throwable));

		return head.toString();

	}

	/**
	 * 呼んだ側が書いた説明
	 *
	 * @param logData	ログのデータ
	 * @return	説明（無ければ空）
	 */
	private static String explanation (Data logData) {

		Object objects = logData.get("objects");

		if (!(objects instanceof Iterable<?> list)) {
			return "";
		}

		StringBuilder result = new StringBuilder();

		for (Object item : list) {

			if (!(item instanceof CharSequence text) || text.isEmpty()) {
				continue;
			}

			if (!result.isEmpty()) {
				result.append(' ');
			}

			result.append(text);

		}

		return result.toString();

	}

	/**
	 * 例外の名前とメッセージ
	 *
	 * <p>
	 * <b>メッセージが null でも落ちない。</b>
	 * {@code NullPointerException} のようにメッセージを持たない例外は多く、
	 * そこで落ちると<b>ログの行がまるごと消える</b>（この encode は
	 * 例外を握って空を返すため）。
	 * </p>
	 *
	 * @param throwable	例外
	 * @return	名前（メッセージがあれば添える）
	 */
	private static String name (Throwable throwable) {

		String message = throwable.getMessage();

		return (message == null || message.isEmpty())
			? throwable.getClass().getName()
			: throwable.getClass().getName() + ": " + message;

	}

	private void outputCaused (Throwable throwable, BufferedOutputStream bos, byte[] suffix) throws IOException {

		if (throwable.getCause() == null) {
			return;
		}

		Throwable throwableCase = throwable.getCause();

		bos.write(suffix);
		bos.write("Caused by: ".getBytes(StandardCharsets.UTF_8));
		bos.write(name(throwableCase).getBytes(StandardCharsets.UTF_8));
		bos.write(LINE_SEPARATOR);

		for (StackTraceElement stackTraceElement : throwableCase.getStackTrace()) {
			bos.write(TAB);
			bos.write(stackTraceElement.toString().getBytes(StandardCharsets.UTF_8));
			bos.write(LINE_SEPARATOR);
		}

		outputCaused(throwableCase, bos, suffix);

	}

}
