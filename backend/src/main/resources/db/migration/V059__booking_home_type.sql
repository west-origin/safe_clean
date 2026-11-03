-- V059__booking_home_type.sql — 가정/원룸 청소(category_key = 'home')의 종류 (2026-11-03 사용자 요청)
-- 고객 앱 홈의 '가정'·'원룸' 두 버튼이 같은 home 카테고리로 예약돼 서버가 어느 쪽인지 몰랐다. 예약 행에 남겨 카드 제목을 "가정 청소"/"원룸 청소" 로 보인다.
-- 'house' = 가정, 'studio' = 원룸. 다른 카테고리·옛 앱 버전 예약은 null (BookingService.doCreate). 가격·옵션·체크리스트는 그대로 home 하나를 쓴다.
alter table bookings add column home_type text null;
alter table bookings add constraint bookings_home_type_check check (home_type in ('house', 'studio'));
