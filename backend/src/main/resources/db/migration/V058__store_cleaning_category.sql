-- V058__store_cleaning_category.sql — 매장 청소 카테고리 'store' (2026-10-31 사용자 요청: 고객 앱 홈 "매장" 버튼)
-- Figma 에 매장 청소 화면이 없어 문구·옵션·칩·체크리스트는 코드에서 정함 (PROGRESS.md 판단 항목). 앱 apps/customer/src/api/mock.js 의 store 와 같다.
-- ServiceCategory 주석대로 카테고리를 더할 때 필요한 칸을 모두 넣는다: 가격표·옵션·주의사항 칩·주기 문구·체크리스트·duration_hours·짧은 라벨.
insert into service_categories (key, label, promo, promo_text, sort) values
  (E'store', E'매장 청소', E'placeholder', E'프로모션 배너 영역\nStore 전용 이미지 필요', 6);

-- 결정 §13-3 과 같은 임시 가격표 (원). 사무실 청소와 같은 값 — 운영 전 새 마이그레이션으로 교체
insert into catalog_prices (category_key, frequency, base_amount) values
  (E'store', 'recurring', 120000),
  (E'store', 'oneTime', 150000);

-- icon 은 앱 ASSETS.bookingOptionIcons.store 의 순서 (지금은 사무실 청소 이미지를 빌려 쓴다)
insert into catalog_options (category_key, key, kind, label, price, icon, sort) values
  (E'store', E'shelf', 'free', E'진열대 먼지 제거', 0, 0, 1),
  (E'store', E'entrance', 'free', E'출입문 유리 닦기', 0, 1, 2),
  (E'store', E'recycle', 'free', E'분리배출', 0, 2, 3),
  (E'store', E'kitchen', 'paid', E'주방\n기름때 청소', 50000, 3, 4),
  (E'store', E'signboard', 'paid', E'간판·외부\n유리 청소', 35000, 4, 5);

insert into catalog_notice_chips (category_key, key, label, icon, sort) values
  (E'store', E'beforeOpen', E'영업 시간 전후에만 가능해요', E'clock', 1),
  (E'store', E'night', E'야간 작업이 필요해요', E'clock', 2),
  (E'store', E'kitchen', E'주방 시설이 있어요', E'tools', 3),
  (E'store', E'goods', E'진열 상품이 있어요', E'box', 4),
  (E'store', E'access', E'출입 등록이 필요해요', E'lock', 5),
  (E'store', E'noParking', E'주차 공간이 없어요', E'parking', 6),
  (E'store', E'pet', E'반려동물이 있어요', E'pet', 7),
  (E'store', E'caution', E'특별히 주의할 공간이 있어요', E'warning', 8),
  (E'store', E'etc', E'기타', E'dots', 9);

-- 정기청소는 매월 1회 (V021 과 같은 문구)
insert into catalog_frequency_copy (category_key, frequency, description) values
  (E'store', E'recurring', E'매월 같은 날짜에 한 번 진행해요'),
  (E'store', E'oneTime', E'원하는 날짜에 한 번만 진행해요');

-- 작업 체크리스트 (V032 처럼 늘 하는 일만. 옵션은 작업 상세의 '청소 범위' 로 따로 보인다). 그룹 이름은 필수 촬영 공간이 된다 (V056)
insert into checklist_templates (category_key, group_name, label, sort) values
  ('store', E'매장 홀', E'바닥 청소', 1),
  ('store', E'매장 홀', E'테이블·의자 닦기', 2),
  ('store', E'매장 홀', E'쓰레기통 비우기', 3),
  ('store', E'출입구', E'출입문·손잡이 닦기', 11),
  ('store', E'화장실', E'세면대', 21),
  ('store', E'화장실', E'변기', 22);

update app_settings set value = value || '{"store":3}'::jsonb where key = 'duration_hours';               -- 기본 3시간 (사무실과 같음, 코드에서 정함)
update app_settings set value = value || '{"store":"매장청소"}'::jsonb where key = 'category_short_labels'; -- 정산 목록 등 짧은 라벨
