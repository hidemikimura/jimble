# このサンプル（approval-forms）が要る列だけを持つ。
# 同じ「申請」でも、approval-list の request は staff_id を持ち、明細を持たない。
# サンプルごとに単体で読めるようにしてあるので、列が違って当たり前である（N-3 の 5.1）。

# --- !Ups

create table request (
	id           bigserial     primary key
	, kind       varchar(20)   not null
	, amount     bigint        not null
	, needed_on  date          null
	, note       text          null
	, status     varchar(20)   not null
	, created_at timestamp     not null
);

comment on table request is '申請';
comment on column request.id is '申請ID';
comment on column request.kind is '種別（travel / supply / book）';
comment on column request.amount is '金額（円）';
comment on column request.needed_on is '希望日';
comment on column request.note is '備考';
comment on column request.status is '状態（draft / pending）';
comment on column request.created_at is '作成日時';

create table request_item (
	id           bigserial     primary key
	, request_id bigint        not null
	, name       varchar(100)  not null
	, amount     bigint        not null
	, sort_no    integer       not null
);

create index request_item_request on request_item (request_id, sort_no);

comment on table request_item is '申請の明細';
comment on column request_item.id is '明細ID';
comment on column request_item.request_id is '申請ID';
comment on column request_item.name is '品目';
comment on column request_item.amount is '金額（円）';
comment on column request_item.sort_no is '並び順';

create table attachment (
	id             bigserial     primary key
	, request_id   bigint        not null
	, file_name    varchar(250)  not null
	, content_type varchar(100)  not null
	, bytes        bigint        not null
	, created_at   timestamp     not null
);

comment on table attachment is '添付';
comment on column attachment.id is '添付ID';
comment on column attachment.request_id is '申請ID';
comment on column attachment.file_name is '保存したファイル名';
comment on column attachment.content_type is '種類';
comment on column attachment.bytes is '大きさ';
comment on column attachment.created_at is '作成日時';

# --- !Downs

drop table attachment;
drop table request_item;
drop table request;
