# --- !Ups

create table comment (
	id         bigint unsigned auto_increment comment 'コメントID' primary key
	, post_id    bigint unsigned not null comment '記事ID'
	, name       varchar(100)    not null comment '名前'
	, body       text            not null comment '本文'
	, created_at datetime        not null comment '作成日時'
	, index comment_post (post_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment 'コメント';

# --- !Downs

drop table comment;
