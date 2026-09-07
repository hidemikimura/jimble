package io.jimble.util.log;

import io.jimble.util.net.HostNames;
import io.jimble.util.net.LocalAddress;
import io.jimble.util.string.StringUtil;
import io.jimble.util.data.Data;
import io.jimble.core.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Objects;

/**
 * ログ
 */
public class Log {

	/**
	 * 出力先
	 *
	 * <p>既定は SLF4J。テストや特殊な出力先のための差し替え点。</p>
	 */
	@FunctionalInterface
	public interface Sink {

		/**
		 * 出力する
		 *
		 * @param loggerName	ロガー名
		 * @param level			レベル
		 * @param message		メッセージ
		 * @param data			構造化データ
		 * @param throwable		例外。無ければ null
		 */
		void write (String loggerName, Level level, String message, Data data, Throwable throwable);

	}

	/**
	 * ログに毎回付ける項目を提供する
	 *
	 * <p>
	 * <b>上位モジュールが登録する。</b>操作情報など、Web 固有の項目はこれで足す。
	 * これにより jimble-util が Web を知らずに済む。
	 * </p>
	 */
	@FunctionalInterface
	public interface FieldProvider {

		/**
		 * 項目名
		 *
		 * @return	項目名
		 */
		String name ();

		/**
		 * 値を取得する
		 *
		 * @return	値。null なら出力しない
		 */
		default Object value () {

			return null;

		}

	}

	/* ローカル情報 */
	private static final Data localInfo = new Data();
	static {
		try {
			localInfo.put("ip", LocalAddress.get());
		} catch (Exception ignore) {}
		try {
			localInfo.put("name", HostNames.get());
		} catch (Exception ignore) {}

	}

	/* 既定の出力先（SLF4J） */
	private static final Sink DEFAULT_SINK = (loggerName, level, message, data, throwable) -> {

		Logger logger = LoggerFactory.getLogger(loggerName);
		Object[] args = throwable == null ? new Object[]{ data } : new Object[]{ data, throwable };

		switch (level) {
			case ERROR -> logger.error(message, args);
			case WARN -> logger.warn(message, args);
			case INFO -> logger.info(message, args);
			case DEBUG -> logger.debug(message, args);
			case TRACE -> logger.trace(message, args);
		}

	};

	/* 出力先 */
	private static volatile Sink sink = DEFAULT_SINK;

	/* 追加項目 */
	private static final List<FieldProvider> fieldProviders = new CopyOnWriteArrayList<>();

	/* インスタンス（実行状態は Context が持つので1つでよい） */
	private static final Log INSTANCE = new Log();

	/**
	 * 出力先を差し替える
	 *
	 * @param value	出力先
	 */
	public static void sink (Sink value) {

		sink = Objects.requireNonNull(value, "sink");

	}

	/**
	 * 出力先を既定に戻す
	 */
	public static void resetSink () {

		sink = DEFAULT_SINK;

	}

	/**
	 * ログに毎回付ける項目を登録する
	 *
	 * @param provider	項目
	 */
	public static void addFieldProvider (FieldProvider provider) {

		fieldProviders.add(Objects.requireNonNull(provider, "provider"));

	}

	/**
	 * 実行ID
	 *
	 * <p>コンテキストのスコープ外では "-"。</p>
	 *
	 * @return	実行ID
	 */
	public String requestId () {

		return Context.isBound() ? Context.current().executionId() : "-";

	}

	/**
	 * SQL実行回数
	 *
	 * @return	SQL実行回数
	 */
	public long sqlExecuteCount () {

		return Context.isBound() ? Context.current().sqlExecuteCount() : 0L;

	}

	/**
	 * SQL実行時間（ナノ秒）
	 *
	 * @return	SQL実行時間
	 */
	public long sqlExecuteTime () {

		return Context.isBound() ? Context.current().sqlExecuteTime().toNanos() : 0L;

	}

	/**
	 * コンストラクタ
	 */
	protected Log () {

	}

	// region ログオブジェクト取得

	/**
	 * ログオブジェクト取得
	 *
	 * @return	ログオブジェクト
	 */
	protected static Log getLog () {

		return INSTANCE;

	}

	// endregion

	// region applicationログ

	/**
	 * Applicationログ DEBUG
	 *
	 * @param loggerName	ロガー名
	 * @param throwable		Throwable
	 * @param objs			ログに出力したい値
	 */
	public static void appDebug (String loggerName, Throwable throwable, Object...objs) {

		getLog().a(loggerName, Level.DEBUG, throwable, objs);

	}

	/**
	 * Applicationログ DEBUG
	 *
	 * @param loggerName	ロガー名
	 * @param message		Throwable
	 * @param objs			ログに出力したい値
	 */
	public static void appDebug (String loggerName, String message, Object...objs) {

		getLog().a(loggerName, Level.DEBUG, message, objs);

	}

	/**
	 * Applicationログ INFO
	 *
	 * @param loggerName	ロガー名
	 * @param throwable		Throwable
	 * @param objs			ログに出力したい値
	 */
	public static void appInfo (String loggerName, Throwable throwable, Object...objs) {

		getLog().a(loggerName, Level.INFO, throwable, objs);

	}

	/**
	 * Applicationログ INFO
	 *
	 * @param loggerName	ロガー名
	 * @param message		Throwable
	 * @param objs			ログに出力したい値
	 */
	public static void appInfo (String loggerName, String message, Object...objs) {

		getLog().a(loggerName, Level.INFO, message, objs);

	}

	/**
	 * Applicationログ WARN
	 *
	 * @param loggerName	ロガー名
	 * @param throwable		Throwable
	 * @param objs			ログに出力したい値
	 */
	public static void appWarn (String loggerName, Throwable throwable, Object...objs) {

		getLog().a(loggerName, Level.WARN, throwable, objs);

	}

	/**
	 * Applicationログ WARN
	 *
	 * @param loggerName	ロガー名
	 * @param message		Throwable
	 * @param objs			ログに出力したい値
	 */
	public static void appWarn (String loggerName, String message, Object...objs) {

		getLog().a(loggerName, Level.WARN, message, objs);

	}

	/**
	 * Applicationログ ERROR
	 *
	 * @param loggerName	ロガー名
	 * @param throwable		Throwable
	 * @param objs			ログに出力したい値
	 */
	public static void appError (String loggerName, Throwable throwable, Object...objs) {

		getLog().a(loggerName, Level.ERROR, throwable, objs);

	}

	/**
	 * Applicationログ ERROR
	 *
	 * @param loggerName	ロガー名
	 * @param message		Throwable
	 * @param objs			ログに出力したい値
	 */
	public static void appError (String loggerName, String message, Object...objs) {

		getLog().a(loggerName, Level.ERROR, message, objs);

	}

	/**
	 * Applicationログ
	 *
	 * @param loggerName	ロガー名
	 * @param level			ログレベル
	 * @param throwable		Throwable
	 * @param objs			ログに出力したい値
	 */
	public void a (String loggerName, Level level, Throwable throwable, Object[] objs) {

		Data data = createLogObject(objs);
		data.put("throwable", throwable);
		logApplication(loggerName, level, throwable.getMessage(), data);

	}

	/**
	 * Applicationログ
	 *
	 * @param loggerName	ロガー名
	 * @param level			ログレベル
	 * @param message		メッセージ
	 * @param objs			ログに出力したい値
	 */
	public void a (String loggerName, Level level, String message, Object[] objs) {

		Data data = createLogObject(objs);
		logApplication(loggerName, level, message, data);

	}

	// endregion

	// region Errorログ

	/**
	 * Errorログ
	 *
	 * @param throwable Throwable
	 * @param objs      ログに出力したい値
	 */
	public static void error (Throwable throwable, Object...objs) {

		getLog().e(throwable, objs);

	}

	/**
	 * Errorログ
	 *
	 * @param message   メッセージ
	 * @param objs      ログに出力したい値
	 */
	public static void error (String message, Object...objs) {

		getLog().e(message, objs);

	}

	/**
	 * Errorログ
	 *
	 * @param throwable Throwable
	 * @param objs      ログに出力したい値
	 */
	public void e (Throwable throwable, Object[] objs) {

		Data data = createLogObject(objs, true);
		data.put("throwable", throwable);
		logError("error", throwable.getMessage(), data);

	}

	/**
	 * Errorログ
	 *
	 * @param message   メッセージ
	 * @param objs      ログに出力したい値
	 */
	public void e (String message, Object[] objs) {

		Data data = createLogObject(objs, true);
		logError("error", message, data);

	}

	// endregion

	// region Traceログ

	/**
	 * Traceログ
	 *
	 * @param message   メッセージ
	 * @param objs      ログに出力したい値
	 */
	public static void trace (String message, Object...objs) {

		getLog().t(message, objs);

	}

	/**
	 * Traceログ
	 *
	 * @param message   メッセージ
	 * @param objs      ログに出力したい値
	 */
	public void t (String message, Object[] objs) {

		Data data = createLogObject(objs);
		logTrace(getClass(), message, data);

	}

	// endregion

	// region Debugログ

	/**
	 * Debugログ
	 *
	 * @param message   メッセージ
	 * @param objs      ログに出力したい値
	 */
	public static void debug (String message, Object...objs) {

		getLog().d(message, objs);

	}

	/**
	 * Debugログ
	 *
	 * @param message   メッセージ
	 * @param objs      ログに出力したい値
	 */
	public void d (String message, Object[] objs) {

		Data data = createLogObject(objs);
		logDebug(getClass(), message, data);

	}

	// endregion

	// region Infoログ

	/**
	 * Infoログ
	 *
	 * @param message   メッセージ
	 * @param objs      ログに出力したい値
	 */
	public static void info (String message, Object...objs) {

		getLog().i(message, objs);

	}

	/**
	 * Infoログ
	 *
	 * @param message   メッセージ
	 * @param objs      ログに出力したい値
	 */
	public void i (String message, Object[] objs) {

		Data data = createLogObject(objs);
		logInfo(getClass(), message, data);

	}

	// endregion

	// region Warnログ

	/**
	 * Warnログ
	 *
	 * @param message   メッセージ
	 * @param objs      ログに出力したい値
	 */
	public static void warn (String message, Object...objs) {

		getLog().w(message, objs);

	}

	/**
	 * Warnログ
	 *
	 * @param message   メッセージ
	 * @param objs      ログに出力したい値
	 */
	public void w (String message, Object[] objs) {

		Data data = createLogObject(objs, true);
		logWarn(getClass(), message, data);

	}

	// endregion

	// region ログ出力

	/**
	 * Traceログ出力
	 *
	 * @param kind      ログ種別
	 * @param message   メッセージ
	 * @param data      データ
	 */
	public void logTrace (String kind, String message, Data data) {

		logApplication(kind, Level.TRACE, message, data);

	}

	/**
	 * Traceログ出力
	 *
	 * @param cls       クラス
	 * @param message   メッセージ
	 * @param data      データ
	 */
	public void logTrace (Class<?> cls, String message, Data data) {

		logApplication(cls.getName(), Level.TRACE, message, data);

	}

	/**
	 * Debugログ出力
	 *
	 * @param kind      ログ種別
	 * @param message   メッセージ
	 * @param data      データ
	 */
	public void logDebug (String kind, String message, Data data) {

		logApplication(kind, Level.DEBUG, message, data);

	}

	/**
	 * Debugログ出力
	 *
	 * @param cls       クラス
	 * @param message   メッセージ
	 * @param data      データ
	 */
	public void logDebug (Class<?> cls, String message, Data data) {

		logApplication(cls.getName(), Level.DEBUG, message, data);

	}

	/**
	 * Infoログ出力
	 *
	 * @param kind      ログ種別
	 * @param message   メッセージ
	 * @param data      データ
	 */
	public void logInfo (String kind, String message, Data data) {

		logApplication(kind, Level.INFO, message, data);

	}

	/**
	 * Infoログ出力
	 *
	 * @param cls       クラス
	 * @param message   メッセージ
	 * @param data      データ
	 */
	public void logInfo (Class<?> cls, String message, Data data) {

		logApplication(cls.getName(), Level.INFO, message, data);

	}

	/**
	 * Warnログ出力
	 *
	 * @param kind      ログ種別
	 * @param message   メッセージ
	 * @param data      データ
	 */
	public void logWarn (String kind, String message, Data data) {

		logApplication(kind, Level.WARN, message, data);

	}

	/**
	 * Warnログ出力
	 *
	 * @param cls       クラス
	 * @param message   メッセージ
	 * @param data      データ
	 */
	public void logWarn (Class<?> cls, String message, Data data) {

		logApplication(cls.getName(), Level.WARN, message, data);

	}

	/**
	 * Errorログ出力
	 *
	 * @param kind      ログ種別
	 * @param message   メッセージ
	 * @param data      データ
	 */
	public void logError (String kind, String message, Data data) {

		logApplication(kind, Level.ERROR, message, data);

	}

	/**
	 * Errorログ出力
	 *
	 * @param cls       クラス
	 * @param message   メッセージ
	 * @param data      データ
	 */
	public void logError (Class<?> cls, String message, Data data) {

		logApplication(cls.getName(), Level.ERROR, message, data);

	}

	/**
	 * Applicationログ出力
	 *
	 * @param kind      ログ種別
	 * @param message   メッセージ
	 * @param data      データ
	 */
	public void logApplication (String kind, Level level, String message, Data data) {

		sink.write(kind, level, message, data, null);

	}

	// endregion


	// region アクセスログ

	/** アクセスログのロガー名 */
	public static final String LOGGER_ACCESS = "access";

	/** ロガー名：ボットのアクセスログ（要件 F-H-05） */
	public static final String LOGGER_ACCESS_BOT = "access.bot";

	/**
	 * アクセスログを出力する
	 *
	 * <p>
	 * 実行ID・SQL実行回数・SQL実行時間は自動で入る（要件 F-U-05 / NF-O-02）。
	 * <b>コンテキストのスコープ内で呼ぶこと。</b>
	 * </p>
	 *
	 * @param message	メッセージ
	 * @param fields	追加項目
	 */
	public static void access (String message, Data fields) {

		access(message, fields, false);

	}

	/**
	 * アクセスログ（要件 F-H-05）
	 *
	 * <p>
	 * <b>ボットのアクセスは別のロガーに出す。</b>
	 * まとめて出すと、ボットの分で人のアクセスが埋もれる。
	 * logback 側で {@code jimble.access.bot} を別のファイルに振り分ける。
	 * </p>
	 *
	 * @param message	メッセージ
	 * @param fields	追加項目
	 * @param isBot		ボットからのアクセスか
	 */
	public static void access (String message, Data fields, boolean isBot) {

		Log log = getLog();

		Data data = log.createLogObject(null);
		if (fields != null) {
			data.putAll(fields);
		}

		log.logInfo(isBot ? LOGGER_ACCESS_BOT : LOGGER_ACCESS, message, data);

	}

	// endregion

	// region ログオブジェクト生成

	/**
	 * ログオブジェクト生成
	 *
	 * @param data  データ
	 * @return  ログオブジェクト
	 */
	public Object[] toLogObject (Data data) {

		return new Object[]{ data };

	}

	/**
	 * ログオブジェクト生成
	 *
	 * @param objs  データ
	 * @return  ログオブジェクト
	 */
	public Data createLogObject (Object[] objs) {

		return createLogObject(objs, false);

	}

	/**
	 * ログオブジェクト生成
	 *
	 * @param objs  			データ
	 * @param needStackTrace	スタックトレース要否
	 * @return  ログオブジェクト
	 */
	public Data createLogObject (Object[] objs, boolean needStackTrace) {

		// ログのデータだけは toString() が JSON のまま（LogData 参照）
		Data logData = new LogData();

		// ローカル情報
		logData.put("local_info", localInfo);

		// 実行ID
		logData.put("request_id", requestId());

		// SQL実行回数
		logData.put("sql_execute_count", sqlExecuteCount());

		// SQL実行時間（ミリ秒）
		logData.put("sql_execute_time", sqlExecuteTime() / 1000000d);

		// スタックトレース
		if (needStackTrace) {
			logData.put("stack_trace", new Throwable());
		}

		// 追加項目（上位モジュールが登録する）
		for (FieldProvider provider : fieldProviders) {
			Object value = provider.value();
			if (value != null) {
				logData.put(provider.name(), value);
			}
		}

		// オブジェクト
		if (objs != null && objs.length > 0) {
			logData.put("objects", convertLogArray(objs));
		}

		return logData;

	}

	/**
	 * CollectionをログListに変換する
	 *
	 * @param src   Collection
	 * @return  ログList
	 */
	@SuppressWarnings("unchecked")
	public List<Object> convertLogList (Collection<?> src) {

		List<Object> dest = new ArrayList<>();
		for (Object value : src) {

			try {

				if (value != null && value.getClass().isArray()) {

					dest.add(convertLogArray((Object[]) value));

				} else if (value instanceof Collection) {

					dest.add(convertLogList((Collection<?>) value));

				} else if (value instanceof Map) {

					dest.add(convertLogMap((Map<String, ?>) value));

				} else {

					dest.add(value);

				}

			} catch (Exception ex) {

				dest.add(value);

			}

		}

		return dest;

	}

	/**
	 * ArrayをログListに変換する
	 *
	 * @param src   Array
	 * @return  ログList
	 */
	@SuppressWarnings("unchecked")
	public List<?> convertLogArray (Object[] src) {

		List<Object> dest = new ArrayList<>();
		for (Object value : src) {

			try {

				if (value != null && value.getClass().isArray()) {

					dest.add(convertLogArray((Object[]) value));

				} else if (value instanceof Collection) {

					dest.add(convertLogList((Collection<?>) value));

				} else if (value instanceof Map) {

					dest.add(convertLogMap((Map<String, ?>) value));

				} else if (value != null) {

					dest.add(value);

				}

			} catch (Exception ex) {

				dest.add(value);

			}

		}

		return dest;

	}

	private static final String LOG_KEY_PATTERN_1 = ".";
	private static final String LOG_KEY_PATTERN_2 = " ";

	/**
	 * MapをログMapに変換する
	 *
	 * @param src   Map
	 * @return  ログMap
	 */
	@SuppressWarnings("unchecked")
	public Map<String, ?> convertLogMap (Map<String, ?> src) {

		Map<String, Object> dest = new LinkedHashMap<>();
		for (Map.Entry<String, ?> entry : src.entrySet()) {

			String key = entry.getKey();
			Object value = entry.getValue();

			try {

				if (value != null && value.getClass().isArray()) {

					dest.put(
						key.replace(LOG_KEY_PATTERN_1, "_").replace(LOG_KEY_PATTERN_2, "_")
						, convertLogArray((Object[]) value)
					);

				} else if (value instanceof Collection) {

					dest.put(
						key.replace(LOG_KEY_PATTERN_1, "_").replace(LOG_KEY_PATTERN_2, "_")
						, convertLogList((Collection<?>) value)
					);

				} else if (value instanceof Map) {

					dest.put(
						key.replace(LOG_KEY_PATTERN_1, "_").replace(LOG_KEY_PATTERN_2, "_")
						, convertLogMap((Map<String, ?>) value)
					);

				} else {

					dest.put(
						key.replace(LOG_KEY_PATTERN_1, "_").replace(LOG_KEY_PATTERN_2, "_")
						, value
					);

				}

			} catch (Exception ex) {

				dest.put(
					key.replace(LOG_KEY_PATTERN_1, "_").replace(LOG_KEY_PATTERN_2, "_")
					, value
				);

			}

		}

		return dest;

	}

	// endregion

}
