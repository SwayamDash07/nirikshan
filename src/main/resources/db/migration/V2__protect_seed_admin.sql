alter table users add column if not exists protected_admin boolean not null default false;

update users
set protected_admin = true
where id = (
    select min(id)
    from users
    where role = 'ADMIN' and created_by_admin_id is null
);
