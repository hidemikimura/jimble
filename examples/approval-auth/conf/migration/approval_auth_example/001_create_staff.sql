# このサンプル（approval-auth）が要る列だけを持つ。
# 同じ「社員」でも、approval-list の staff は部署を持ち、パスワードを持たない。
# サンプルごとに単体で読めるようにしてあるので、列が違って当たり前である（N-3 の 5.1）。
#
# 製品の接尾辞（.postgresql）は付けない。このサンプルは PostgreSQL 単独である。

# --- !Ups

create table staff (
	id              bigserial     primary key
	, login_id      varchar(100)  not null
	, password_hash varchar(255)  not null
	, name          varchar(100)  not null
	, role          varchar(20)   not null
	, created_at    timestamp     not null
);

create unique index staff_login_id on staff (login_id);

comment on table staff is '社員';
comment on column staff.id is '社員ID';
comment on column staff.login_id is 'ログインID';
comment on column staff.password_hash is 'パスワード（ハッシュ済み）';
comment on column staff.name is '氏名';
comment on column staff.role is '役割（member / approver）';
comment on column staff.created_at is '作成日時';

# --- !Downs

drop table staff;
