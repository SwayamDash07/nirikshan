-- SMS preferences and the old channel discriminator are no longer part of Nirikshan.
-- V18 is retained as migration history; this forward migration removes its columns
-- from databases that already applied it.
alter table users drop column if exists phone_number;
alter table users drop column if exists sms_opt_in;
alter table announcement_drafts drop column if exists channel;
