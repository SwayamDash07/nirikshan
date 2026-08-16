alter table risk_events add column if not exists density_change double precision not null default 0;
alter table risk_events add column if not exists movement_slowdown double precision not null default 0;
alter table risk_events add column if not exists hotspot_persistence_seconds bigint not null default 0;
