alter table venues
    add column if not exists service_radius_meters double precision not null default 1000;
