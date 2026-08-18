alter table citizen_reports add column if not exists client_event_id varchar(64);
create unique index if not exists ux_citizen_reports_client_event_id on citizen_reports(client_event_id);
