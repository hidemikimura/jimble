# --- !Ups

alter table post add column image_name varchar(250) null comment '画像ファイル名' after body;

# --- !Downs

alter table post drop column image_name;
