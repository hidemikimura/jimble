# このサンプル（approval-jobs）が要る列だけを持つ。
# 締切バッチと通知しか扱わないので、金額と締切と状態しかない。
# 他のサンプルの同名テーブルとは列が違う（N-3 の 5.1）。

# --- !Ups

create table request (
	id           bigserial     primary key
	, staff_id   bigint        not null
	, amount     bigint        not null
	, needed_on  date          not null
	, status     varchar(20)   not null
	, created_at timestamp     not null
);

-- 締切バッチが「まだ承認されていないもののうち、締切が近いもの」を引く
create index request__status_needed_on on request (status, needed_on);

comment on table request is '申請';

/*
 * 書庫。
 *
 * ArchiveChunkBatch が request からここへ移す（要件 F-B-12）。
 * 読む先と書く先を別のテーブルにしてあるのは、
 * チャンクバッチの「読む DB と書く DB は別」という形が
 * そのまま目に見えるようにするためである。
 */
create table request_archive (
	id            bigint        primary key
	, staff_id    bigint        not null
	, amount      bigint        not null
	, needed_on   date          not null
	, status      varchar(20)   not null
	, created_at  timestamp     not null
	, archived_at timestamp     not null
);

comment on table request_archive is '申請（書庫）';

create table notice (
	id             bigserial     primary key
	, request_id   bigint        not null
	, to_staff_id  bigint        not null
	, kind         varchar(30)   not null
	, sent_at      timestamp
	, created_at   timestamp     not null
);

/*
 * 冪等性のための一意キー（要件 F-M-05）。
 *
 * MQ は「1回だけ実行する」ことを約束しない。
 * 同じ申請に同じ種類の通知が2つ入らないことを DB 側で担保しておくと、
 * 2回実行されても壊れない。
 */
create unique index notice__request_kind on notice (request_id, kind);

comment on table notice is '通知';

# --- !Downs

drop table notice;
drop table request_archive;
drop table request;
