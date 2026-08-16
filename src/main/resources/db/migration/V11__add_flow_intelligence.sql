alter table risk_events add column if not exists dominant_direction varchar(32);
alter table risk_events add column if not exists direction_degrees double precision;
alter table risk_events add column if not exists direction_confidence double precision not null default 0;
alter table risk_events add column if not exists directional_consistency double precision not null default 0;
alter table risk_events add column if not exists reverse_movement_ratio double precision not null default 0;
alter table risk_events add column if not exists conflicting_movement_ratio double precision not null default 0;
alter table risk_events add column if not exists behavior_state varchar(64) not null default 'INSUFFICIENT_DATA';
alter table risk_events add column if not exists behavior_explanation text;
