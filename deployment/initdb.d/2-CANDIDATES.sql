CREATE TABLE IF NOT exists cvk.candidates (
    name text,
    uri text,
    association text,
    pos integer,
    photo text,
    registration text,
    details text,
    attachments hstore,
    refs hstore,
    hash integer PRIMARY KEY,
    created timestamp
);
