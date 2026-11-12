-- 첫 1회 청소 주소 대조(2026-11-11): 도로명·지번 어느 쪽으로 적어도 같은 건물을 알아보도록, 예약 때 카카오 주소 API 로 찾은
-- 필지 고유값("법정동코드-산여부-본번-부번")을 1회 가정 청소 예약에 남긴다. 키가 없던 환경·옛 예약은 null(글자 비교로 대신).
alter table bookings add column building_key text;
comment on column bookings.building_key is '첫 1회 청소 주소 대조용 필지 고유값 (FirstCleaningService). 1회 가정 청소만 채운다';
create index bookings_first_cleaning_building on bookings(building_key)
  where building_key is not null and category_key = 'home' and frequency = 'oneTime';
