# --- !Ups

alter table post add index post_published (published, created_at);

# --- !Downs

alter table post drop index post_published;
