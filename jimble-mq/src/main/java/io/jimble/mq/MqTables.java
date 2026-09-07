package io.jimble.mq;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.version.DBVersion;

/**
 * MQ が使うテーブル
 *
 * <p>
 * キュー1つにつきテーブル1つ。テーブル名はアプリが決める（{@code mq_main} など）。
 * </p>
 *
 * <p>
 * 移送元は運用しながら育った 4 回の {@code ALTER} に分かれており、
 * {@code priority} 列を追加してから削除する、といった履歴も残っていた。
 * <b>移送元の DB を引き継ぐわけではない</b>ので、最初から1つの {@code CREATE} にまとめる。
 * </p>
 */
public final class MqTables {

	private MqTables () {}

	/**
	 * すべてのデータソースにテーブルを作る
	 *
	 * @param queueName	テーブル名
	 */
	public static void install (String queueName) {

		for (DB db : DBUtil.getDBList()) {
			install(db, queueName);
		}

	}

	/**
	 * テーブルを作る
	 *
	 * @param db		DB
	 * @param queueName	テーブル名
	 */
	public static void install (DB db, String queueName) {

		DBVersion dbVersion = new DBVersion(queueName, "MQ (%s)".formatted(queueName));

		dbVersion.add(1, """
				create table `%s`
				(
				    id           bigint unsigned auto_increment comment 'ID' primary key,
				    execute_type varchar(100)           not null comment '実行種別',
				    mq_key       varchar(200)           not null comment 'MQキー',
				    status       varchar(100)           not null comment 'ステータス',
				    scheduled_at datetime               null comment '処理予定日時',
				    retry_count  int unsigned default 0 not null comment 'リトライ回数',
				    data         json                   not null comment 'データ',
				    log_info     json                   null comment 'ログ情報',
				    created_at   datetime               not null comment '登録日時',
				    updated_at   datetime               not null comment '更新日時'
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
			""".formatted(queueName, dbVersion.placeholder())
			// 拾うときの順路
			, "create index %s__index_1 on `%s` (execute_type, status, scheduled_at, id)"
				.formatted(queueName, queueName)
			// 迷子の行を拾い直すときの順路
			, "create index %s__index_2 on `%s` (status, updated_at)"
				.formatted(queueName, queueName)
		);

		dbVersion.apply(db);

	}

}
