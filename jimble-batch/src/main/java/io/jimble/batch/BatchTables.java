package io.jimble.batch;

import io.jimble.db.FrameworkTables;
import io.jimble.db.DB;
import io.jimble.db.version.DBVersion;

/**
 * バッチが使うテーブル
 *
 * <p>
 * jimble 自身のテーブルなので、アプリのマイグレーション（要件 F-G-05）ではなく
 * {@link DBVersion} で作る。コード生成の対象からも外してある（D-21）。
 * </p>
 *
 * <table>
 *   <caption>テーブル</caption>
 *   <tr><th>テーブル</th><th>役割</th></tr>
 *   <tr><td>{@code batch_master}</td><td>バッチの一覧と設定（要件 F-B-09 で明示登録したものが入る）</td></tr>
 *   <tr><td>{@code batch_history}</td><td>実行履歴（要件 F-B-08）</td></tr>
 *   <tr><td>{@code batch_execute_info}</td><td>いま走っているバッチ（要件 F-B-05）</td></tr>
 * </table>
 */
public final class BatchTables {

	private BatchTables () {}

	/**
	 * テーブルを作る
	 *
	 * @param db	DB
	 */
	public static void install (DB db) {

		master(db);
		history(db);
		executeInfo(db);

	}

	/**
	 * バッチマスタ
	 *
	 * @param db	DB
	 */
	private static void master (DB db) {

		DBVersion dbVersion = new DBVersion(FrameworkTables.BATCH_MASTER, "バッチマスタ");

		/*
		 * 移送元は 6 回の ALTER に分かれていた（運用しながら育った形）。
		 * jimble では最初から1つの CREATE にまとめる。
		 * 移送元の DB を引き継ぐわけではないので、履歴を再現する必要がない。
		 */
		dbVersion.add(1)
			.mysql("""
				create table `batch_master`
				(
				    class_name                         varchar(200)     not null comment 'バッチクラス名',
				    name                               varchar(200)     null comment 'バッチ名',
				    status                             varchar(200)     not null comment 'ステータス',
				    is_scheduler                       int unsigned     default 0 not null comment 'スケジューラ判定',
				    is_enable_scheduler                int unsigned     default 1 not null comment 'スケジューラー対象',
				    cron                               varchar(200)     null comment 'cron',
				    default_cron                       varchar(200)     null comment 'デフォルトcron',
				    allow_concurrent_execution         int unsigned     default 1 not null comment '同時実行可能数',
				    default_allow_concurrent_execution int unsigned     default 1 not null comment 'デフォルト同時実行可能数',
				    settings                           json             null comment '設定情報',
				    default_settings                   json             null comment 'デフォルト設定情報',
				    created_at                         datetime         null comment '登録日時',
				    constraint batch_master_pk primary key (class_name)
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
			""".formatted(dbVersion.placeholder())
			, "create index batch_master__index_1 on `batch_master` (created_at)"
			, "create index batch_master__index_2 on `batch_master` (status)")
			.postgresql("""
				create table batch_master
				(
				    class_name                         varchar(200) not null,
				    name                               varchar(200) null,
				    status                             varchar(200) not null,
				    is_scheduler                       int          default 0 not null,
				    is_enable_scheduler                int          default 1 not null,
				    cron                               varchar(200) null,
				    default_cron                       varchar(200) null,
				    allow_concurrent_execution         int          default 1 not null,
				    default_allow_concurrent_execution int          default 1 not null,
				    settings                           jsonb        null,
				    default_settings                   jsonb        null,
				    created_at                         timestamp    null,
				    constraint batch_master_pk primary key (class_name)
				)
			"""
			, "create index batch_master__index_1 on batch_master (created_at)"
			, "create index batch_master__index_2 on batch_master (status)");

		dbVersion.apply(db);

	}

	/**
	 * バッチ履歴
	 *
	 * @param db	DB
	 */
	private static void history (DB db) {

		DBVersion dbVersion = new DBVersion(FrameworkTables.BATCH_HISTORY, "バッチ履歴");

		dbVersion.add(1)
			.mysql("""
				create table `batch_history`
				(
				    id            bigint unsigned auto_increment comment 'ID' primary key,
				    class_name    varchar(200)    not null comment 'バッチクラス名',
				    name          varchar(200)    null comment 'バッチ名',
				    status        varchar(200)    not null comment 'ステータス',
				    cancel_status int(1) unsigned not null comment 'キャンセルステータス',
				    execute_info  json            null comment '実行情報',
				    starts_at     datetime        null comment '開始日時',
				    ends_at       datetime        null comment '終了日時',
				    required_time bigint unsigned null comment '所要時間(秒)'
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
			""".formatted(dbVersion.placeholder())
			, "create index batch_history__index_1 on `batch_history` (status)"
			, "create index batch_history__index_2 on `batch_history` (class_name)"
			, "create index batch_history__index_3 on `batch_history` (status, class_name, starts_at)")
			.postgresql("""
				create table batch_history
				(
				    id            bigserial primary key,
				    class_name    varchar(200) not null,
				    name          varchar(200) null,
				    status        varchar(200) not null,
				    cancel_status int          not null,
				    execute_info  jsonb        null,
				    starts_at     timestamp    null,
				    ends_at       timestamp    null,
				    required_time bigint       null
				)
			"""
			, "create index batch_history__index_1 on batch_history (status)"
			, "create index batch_history__index_2 on batch_history (class_name)"
			, "create index batch_history__index_3 on batch_history (status, class_name, starts_at)");

		dbVersion.apply(db);

	}

	/**
	 * バッチ実行情報
	 *
	 * @param db	DB
	 */
	private static void executeInfo (DB db) {

		DBVersion dbVersion = new DBVersion(FrameworkTables.BATCH_EXECUTE_INFO, "バッチ実行情報");

		dbVersion.add(1)
			.mysql("""
				create table batch_execute_info (
					uid          varchar(250) not null comment 'UID' primary key,
					scheduler_id varchar(250) null comment 'スケジューラID',
					class_name   varchar(200) not null comment 'クラス名',
					created_at   datetime     not null comment '登録日時',
					updated_at   datetime     not null comment '更新日時'
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
			""".formatted(dbVersion.placeholder())
			, "create index batch_execute_info__index_1 on batch_execute_info (class_name, updated_at)"
			, "create index batch_execute_info__index_2 on batch_execute_info (updated_at)"
			, "create index batch_execute_info__index_3 on batch_execute_info (scheduler_id, updated_at)")
			.postgresql("""
				create table batch_execute_info (
					uid          varchar(250) not null primary key,
					scheduler_id varchar(250) null,
					class_name   varchar(200) not null,
					created_at   timestamp    not null,
					updated_at   timestamp    not null
				)
			"""
			, "create index batch_execute_info__index_1 on batch_execute_info (class_name, updated_at)"
			, "create index batch_execute_info__index_2 on batch_execute_info (updated_at)"
			, "create index batch_execute_info__index_3 on batch_execute_info (scheduler_id, updated_at)");

		dbVersion.apply(db);

	}

}
