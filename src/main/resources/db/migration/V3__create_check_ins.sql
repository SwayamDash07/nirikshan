create table if not exists check_ins (
    id serial primary key,
    staff_name text not null,
    triggered_at timestamp with time zone not null default now(),
    responded_at timestamp with time zone,
    status text not null default 'pending',
    constraint check_ins_status_check check (status in ('pending', 'confirmed'))
);

create index if not exists idx_check_ins_staff_status on check_ins(staff_name, status);
create index if not exists idx_check_ins_triggered_at on check_ins(triggered_at);
