# 監査ログ（要件 F-G-09）。
#
# <b>これは別のデータベースである。</b>
# ディレクトリ名（approval_data_audit_example）がデータソース名と対応している。
#
# トップレベルの db { } にしてあるので、ここのマイグレーションが当たる。
# subs { } にぶら下げていたら<b>当たらない</b>——
# データソースごとの処理はトップレベルしか回らないためである。

# --- !Ups

create table audit_log (
	id           bigserial     primary key
	, staff_id   bigint        not null
	, action     varchar(50)   not null
	, target     varchar(100)  not null
	, created_at timestamp     not null
);

create index audit_log__created_at on audit_log (created_at);

comment on table audit_log is '監査ログ';

# --- !Downs

drop table audit_log;
