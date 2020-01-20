-- resources/sql/inspect.sql
-- Schema and data extraction queries

-- :name all-tables
show tables

-- :name all-schema
select * from information_schema.columns
where table_name = :table_name

-- :snip select-snip
select :i*:cols

-- :snip from-snip
from :i*:tables

-- :name snip-query :? :*
:snip:select
:snip:from
