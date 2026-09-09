# --- !Ups

create table post (
	id         bigserial     primary key
	, title      varchar(250)  not null
	, body       text          null
	, published  boolean       default false not null
	, created_at timestamp     not null
);

comment on table post is '記事';
comment on column post.id is '記事ID';
comment on column post.title is 'タイトル';
comment on column post.body is '本文';
comment on column post.published is '公開';
comment on column post.created_at is '作成日時';

# --- !Downs

drop table post;
