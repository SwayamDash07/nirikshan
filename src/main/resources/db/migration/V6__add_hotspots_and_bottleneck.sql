alter table risk_events add column if not exists hotspot_regions text;
alter table zones add column if not exists bottleneck_detected boolean not null default false;
