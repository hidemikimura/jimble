# --- !Ups

create table post (
	id         bigint unsigned auto_increment comment '記事ID' primary key
	, title      varchar(250)  not null comment 'タイトル'
	, body       text          null comment '本文'
	, published  tinyint(1)    default 0 not null comment '公開'
	, created_at datetime      not null comment '作成日時'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '記事';

# --- !Downs

drop table post;
