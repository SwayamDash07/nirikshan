alter table recommendations add column if not exists source varchar(20) not null default 'LIVE';
