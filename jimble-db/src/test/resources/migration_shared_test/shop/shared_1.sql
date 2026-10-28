# --- !Ups
create table shared_item (id int primary key, name varchar(20) not null);
create table shared_tag (id int primary key);
# --- !Downs
drop table shared_tag;
drop table shared_item;
