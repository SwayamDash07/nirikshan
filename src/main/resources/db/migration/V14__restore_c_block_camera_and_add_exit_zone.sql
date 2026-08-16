update zones
set latitude = 20.36376025781561,
    longitude = 85.81713519590612
where lower(name) = 'c block gate';

insert into zones (
    current_density,
    current_people_count,
    current_risk_level,
    last_updated,
    latitude,
    longitude,
    name,
    polygon,
    radius_meters,
    venue_id,
    bottleneck_detected
)
select 0,
       0,
       'LOW',
       current_timestamp,
       20.36360062582813,
       85.81631877217335,
       'C Block Exit Gate',
       null,
       70,
       venue.id,
       false
from venues venue
where lower(venue.name) = 'kiit campus 25'
  and not exists (
      select 1 from zones existing_zone
      where existing_zone.venue_id = venue.id
        and lower(existing_zone.name) = 'c block exit gate'
  );
