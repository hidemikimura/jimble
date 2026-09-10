# このサンプル（approval-list）が要る列だけを持つ。
# staff はログインしないので password_hash が無い。approval-auth とは列が違う（N-3 の 5.1）。

# --- !Ups

create table department (
	id           bigserial     primary key
	, name       varchar(100)  not null
	, created_at timestamp     not null
);

comment on table department is '部署';

create table staff (
	id              bigserial     primary key
	, department_id bigint        not null
	, name          varchar(100)  not null
	, created_at    timestamp     not null
);

create index staff_department on staff (department_id);

comment on table staff is '社員';

create table request (
	id           bigserial     primary key
	, staff_id   bigint        not null
	, kind       varchar(20)   not null
	, amount     bigint        not null
	, needed_on  date          not null
	, status     varchar(20)   not null
	, created_at timestamp     not null
);

create index request_status on request (status, created_at);
create index request_staff on request (staff_id, created_at);

comment on table request is '申請';

# --- !Downs

drop table request;
drop table staff;
drop table department;
