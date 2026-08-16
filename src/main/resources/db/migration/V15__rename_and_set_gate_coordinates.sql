update zones
set latitude = 20.36366814775126,
    longitude = 85.81626264649513
where lower(name) = 'main gate';

update zones
set name = 'Main Gate Exit',
    latitude = 20.36360968378996,
    longitude = 85.81631763177884
where lower(name) in ('c block exit gate', 'main gate exit');
