# --- !Ups

alter table post add column image_name varchar(250) null;

comment on column post.image_name is '画像ファイル名';

# --- !Downs

alter table post drop column image_name;
