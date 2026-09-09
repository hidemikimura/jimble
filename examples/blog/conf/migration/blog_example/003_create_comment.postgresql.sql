# --- !Ups

create table comment (
	id         bigserial    primary key
	, post_id    bigint       not null
	, name       varchar(100) not null
	, body       text         not null
	, created_at timestamp    not null
);

create index comment_post on comment (post_id, created_at);

comment on table comment is 'コメント';
comment on column comment.id is 'コメントID';
comment on column comment.post_id is '記事ID';
comment on column comment.name is '名前';
comment on column comment.body is '本文';
comment on column comment.created_at is '作成日時';

# --- !Downs

drop table comment;
