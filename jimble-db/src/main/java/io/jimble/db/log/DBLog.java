package io.jimble.db.log;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.version.DBVersion;

/**
 * log
 */
public class DBLog {

	/**
	 * ログ保存
	 *
	 * @param db	DB
	 * @param log	内容
	 */
	/** 設定キー：DB ログを残すか */
	public static final String KEY_ENABLED = "log.db";

	/** スタックトレースに残す段数 */
	public static final int STACK_TRACE_DEPTH = 20;

	/**
	 * いまのスタックトレース（文字列）
	 *
	 * @return	スタックトレース
	 */
	private static java.util.List<String> stackTrace () {

		java.util.List<String> lines = new java.util.ArrayList<>();

		StackTraceElement[] elements = new Throwable().getStackTrace();

		for (int i = 0; i < elements.length && lines.size() < STACK_TRACE_DEPTH; i++) {
			lines.add(elements[i].toString());
		}

		return lines;

	}

	public static void save (DB db, Data log) {

		if (log == null) {
			return;
		}

		if (!DBUtil.isUseDB()) {
			return;
		}

		/*
		 * 設定キーが移送元のまま（jooby.log.db）だったので、
		 * jimble の設定では絶対に有効にならなかった。
		 */
		if (!Conf.conf().getBoolean(KEY_ENABLED, false)) {
			return;
		}

		/*
		 * Throwable をそのまま入れない。これは JSON にして db_log.content に保存されるので、
		 * 例外オブジェクトのままだと読めるものにならない（バッチ履歴で同じ穴を踏んでいる）。
		 */
		log.putData("stack_trace", stackTrace());

		db.insert("""
				INSERT INTO db_log (
					content
					, created_at
				) VALUES (
					?
					, NOW()
				)
			"""
			, log
		);

	}

	/**
	 * 初期化
	 *
	 * @param db DB
	 */
	public static void init (DB db) {

		DBVersion dbVersion = new DBVersion("db_log", "汎用ログ情報");
		dbVersion.add(1)
			.mysql("""
				create table db_log
				(
					id         bigint unsigned auto_increment comment 'ID' primary key,
					content    longtext null comment '内容',
					created_at datetime not null comment '登録日時'
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
			""".formatted(dbVersion.placeholder()))
			.postgresql("""
				create table db_log
				(
					id         bigserial primary key,
					content    text null,
					created_at timestamp not null
				)
			""");
		dbVersion.apply(db);

	}

}
