create table if not exists cvk.attachments(
 rootUri text references cvk.candidate(uri),
 id serial primary key,
 uri text unique,
 doc bytea,
 created timestamp default now()
);