# このサンプル（approval-data）が要る列だけを持つ。
# 承認とキャッシュしか扱わないので、種別も締切も無い。
# 他のサンプルの同名テーブルとは列が違う（N-3 の 5.1）。

# --- !Ups

create table request (
	id            bigserial     primary key
	, staff_id    bigint        not null
	, amount      bigint        not null
	, status      varchar(20)   not null
	, decided_by  bigint
	, decided_at  timestamp
	, created_at  timestamp     not null
);

comment on table request is '申請';

create table notice (
	id             bigserial     primary key
	, request_id   bigint        not null
	, to_staff_id  bigint        not null
	, kind         varchar(30)   not null
	, created_at   timestamp     not null
);

/*
 * 同じ申請に同じ種類の通知は1件だけ。
 *
 * <b>これがあるおかげで、承認のトランザクションに意味が出る。</b>
 * 状態を変えたあとで通知の登録が弾かれたとき、
 * 囲っていなければ<b>状態だけ変わって通知が無い</b>申請が残る。
 */
create unique index notice__request_kind on notice (request_id, kind);

comment on table notice is '通知';

/*
 * レート。キャッシュの題材。
 *
 * めったに変わらないのに、金額を出すたびに引かれる。
 * こういうものがキャッシュの相手である。
 */
create table rate (
	id           bigserial     primary key
	, code       varchar(20)   not null unique
	, value      bigint        not null
	, updated_at timestamp     not null
);

comment on table rate is 'レート';

# --- !Downs

drop table rate;
drop table notice;
drop table request;
