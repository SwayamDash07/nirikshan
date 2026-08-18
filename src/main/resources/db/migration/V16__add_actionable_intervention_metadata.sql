alter table recommendations add column if not exists affected_route varchar(255);
alter table recommendations add column if not exists flow_direction varchar(64);
alter table recommendations add column if not exists duration_minutes integer;
alter table recommendations add column if not exists confidence double precision;
alter table recommendations add column if not exists barricade_instruction varchar(64);
