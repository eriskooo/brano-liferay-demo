CREATE SCHEMA blacklist;

CREATE TABLE blacklist.Blacklist_Record (
    id BIGINT PRIMARY KEY
);

CREATE TABLE blacklist.blacklist_note (
    id BIGINT PRIMARY KEY
);

CREATE ROLE crm_app LOGIN PASSWORD 'x';
