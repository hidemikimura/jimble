# --- !Ups

create index post_published on post (published, created_at);

# --- !Downs

drop index post_published;
