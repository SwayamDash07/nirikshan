alter table users add column if not exists phone_number varchar(16);
alter table users add column if not exists sms_opt_in boolean not null default false;
