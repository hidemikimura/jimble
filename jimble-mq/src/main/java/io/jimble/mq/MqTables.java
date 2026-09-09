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

		dbVersion.add(1)
			.mysql("""
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
				.formatted(queueName, queueName))
			.postgresql("""
				create table "%s"
				(
				    id           bigserial primary key,
				    execute_type varchar(100)   not null,
				    mq_key       varchar(200)   not null,
				    status       varchar(100)   not null,
				    scheduled_at timestamp      null,
				    retry_count  int default 0  not null,
				    data         jsonb          not null,
				    log_info     jsonb          null,
				    created_at   timestamp      not null,
				    updated_at   timestamp      not null
				)
			""".formatted(queueName)
			, "create index %s__index_1 on \"%s\" (execute_type, status, scheduled_at, id)"
				.formatted(queueName, queueName)
			, "create index %s__index_2 on \"%s\" (status, updated_at)"
				.formatted(queueName, queueName));

		/*
		 * 分散トレーシング（要件 NF-O-05）。
		 *
		 * 積んだところと処理したところを1本のトレースで繋ぐために、
		 * W3C Trace Context の traceparent を持つ。トレースを使わないアプリでは
		 * <b>ずっと null のまま</b>で、書き込みも読み出しもしない。
		 *
		 * <b>data（アプリの内容）には入れない。</b>そちらに入れると、
		 * アプリが自分で入れた覚えのない鍵が増える。
		 *
		 * traceparent は「00-<32桁>-<16桁>-<2桁>」の 55 文字と決まっているが、
		 * 版が上がると伸びうるので少し余裕を持たせてある。
		 */
		dbVersion.add(2)
			.mysql("alter table `%s` add column traceparent varchar(64) null comment 'トレース'"
				.formatted(queueName))
			.postgresql("alter table \"%s\" add column traceparent varchar(64)"
				.formatted(queueName));

		dbVersion.apply(db);

	}

}
