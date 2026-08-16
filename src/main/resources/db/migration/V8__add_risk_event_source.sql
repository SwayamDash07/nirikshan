alter table risk_events add column if not exists source varchar(20) not null default 'LIVE';
