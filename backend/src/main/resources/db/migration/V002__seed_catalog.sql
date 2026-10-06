-- V002__seed_catalog.sql — apps/*/src/api/mock.js 에서 scripts/backend-mock-export.mjs seed 로 생성 (Figma 문구 그대로)
insert into service_categories (key, label, promo, promo_text, sort) values
  (E'home', E'가정/원룸 청소', E'recurring', null, 1),
  (E'office', E'사무실 청소', E'placeholder', E'프로모션 배너 영역\nOffice 전용 이미지 필요', 2),
  (E'appliance', E'가전청소', E'placeholder', E'프로모션 배너 영역\nAppliance 전용 이미지 필요', 3),
  (E'move-in', E'입주(이사) 청소', E'placeholder', E'프로모션 배너 영역\nMoveIn 전용 이미지 필요', 4);

-- 결정 §13-3: 임시 가격표 (원). 운영 전 새 마이그레이션으로 교체
insert into catalog_prices (category_key, frequency, base_amount) values
  (E'home', 'recurring', 80000),
  (E'home', 'oneTime', 100000),
  (E'office', 'recurring', 120000),
  (E'office', 'oneTime', 150000),
  (E'appliance', 'recurring', 60000),
  (E'appliance', 'oneTime', 70000),
  (E'move-in', 'recurring', 150000),
  (E'move-in', 'oneTime', 180000);

insert into catalog_options (category_key, key, kind, label, price, icon, sort) values
  (E'home', E'dishes', 'free', E'설거지', 0, 0, 1),
  (E'home', E'laundry', 'free', E'세탁', 0, 1, 2),
  (E'home', E'bedding', 'free', E'침구교체', 0, 2, 3),
  (E'home', E'pet', 'paid', E'반려동물\n배변 청소', 3000, 3, 4),
  (E'home', E'fridge', 'paid', E'냉장고 \n정리 및 청소', 10000, 4, 5),
  (E'office', E'pantry', 'free', E'탕비실 정리', 0, 0, 1),
  (E'office', E'meeting', 'free', E'회의실 정돈', 0, 1, 2),
  (E'office', E'recycle', 'free', E'분리배출', 0, 2, 3),
  (E'office', E'carpet', 'paid', E'카펫\n스팀 청소', 50000, 3, 4),
  (E'office', E'window', 'paid', E'유리창\n외부 청소', 35000, 4, 5),
  (E'appliance', E'filter', 'free', E'필터 세척', 0, 0, 1),
  (E'appliance', E'polish', 'free', E'외관 광택', 0, 1, 2),
  (E'appliance', E'drain', 'free', E'배수구 청소', 0, 2, 3),
  (E'appliance', E'ac', 'paid', E'에어컨\n분해 청소', 40000, 3, 4),
  (E'appliance', E'washer', 'paid', E'세탁조\n살균', 25000, 4, 5),
  (E'move-in', E'sill', 'free', E'창틀 청소', 0, 0, 1),
  (E'move-in', E'veranda', 'free', E'베란다', 0, 1, 2),
  (E'move-in', E'closet', 'free', E'붙박이장', 0, 2, 3),
  (E'move-in', E'grout', 'paid', E'줄눈\n시공', 30000, 3, 4),
  (E'move-in', E'sick', 'paid', E'새집증후군\n케어', 50000, 4, 5);

insert into catalog_notice_chips (category_key, key, label, icon, sort) values
  (E'home', E'pet', E'반려동물이 있어요', E'pet', 1),
  (E'home', E'baby', E'영유아가 있어요', E'baby', 2),
  (E'home', E'smoke', E'흡연 공간이 있어요', E'smoke', 3),
  (E'home', E'valuables', E'귀중품이 있어요', E'lock', 4),
  (E'home', E'stairs', E'계단 이용이 필요해요', E'stairs', 5),
  (E'home', E'noElevator', E'엘리베이터 없음', E'elevator', 6),
  (E'home', E'remodel', E'리모델링 후 청소예요', E'tools', 7),
  (E'home', E'caution', E'특별히 주의할 공간이 있어요', E'warning', 8),
  (E'home', E'etc', E'기타', E'dots', 9),
  (E'office', E'secure', E'보안 구역이 있어요', E'lock', 1),
  (E'office', E'access', E'출입 등록이 필요해요', E'lock', 2),
  (E'office', E'night', E'야간 작업이 필요해요', E'clock', 3),
  (E'office', E'weekend', E'주말만 가능해요', E'clock', 4),
  (E'office', E'server', E'서버실이 있어요', E'server', 5),
  (E'office', E'elevator', E'엘리베이터 제한 있어요', E'elevator', 6),
  (E'office', E'desk', E'개인 자리는 제외해주세요', E'desk', 7),
  (E'office', E'caution', E'특별히 주의할 공간이 있어요', E'warning', 8),
  (E'office', E'etc', E'기타', E'dots', 9),
  (E'appliance', E'wall', E'벽걸이형이에요', E'ac', 1),
  (E'appliance', E'ceiling', E'천장형이에요', E'ac', 2),
  (E'appliance', E'stand', E'스탠드형이에요', E'ac', 3),
  (E'appliance', E'old', E'설치 10년이 넘었어요', E'clock', 4),
  (E'appliance', E'ladder', E'사다리 작업이 필요해요', E'stairs', 5),
  (E'appliance', E'noWater', E'물 사용이 어려워요', E'droplet', 6),
  (E'appliance', E'warranty', E'보증기간 중이에요', E'shield', 7),
  (E'appliance', E'caution', E'특별히 주의할 기기가 있어요', E'warning', 8),
  (E'appliance', E'etc', E'기타', E'dots', 9),
  (E'move-in', E'boxes', E'이삿짐이 아직 있어요', E'box', 1),
  (E'move-in', E'new', E'신축 건물이에요', E'building', 2),
  (E'move-in', E'remodel', E'리모델링 직후예요', E'tools', 3),
  (E'move-in', E'noElevator', E'엘리베이터가 없어요', E'elevator', 4),
  (E'move-in', E'noParking', E'주차 공간이 없어요', E'parking', 5),
  (E'move-in', E'utilities', E'수도·전기 사용 가능해요', E'droplet', 6),
  (E'move-in', E'pet', E'반려동물이 있어요', E'pet', 7),
  (E'move-in', E'caution', E'특별히 주의할 공간이 있어요', E'warning', 8),
  (E'move-in', E'etc', E'기타', E'dots', 9);

insert into catalog_frequency_copy (category_key, frequency, description) values
  (E'home', E'recurring', E'매주 지정한 요일에 진행해요'),
  (E'home', E'oneTime', E'원하는 날짜에 한 번만 진행해요.'),
  (E'office', E'recurring', E'매주 지정한 요일에 진행해요'),
  (E'office', E'oneTime', E'원하는 날짜에 한 번만 진행해요'),
  (E'appliance', E'recurring', E'매주 지정한 요일에 진행해요'),
  (E'appliance', E'oneTime', E'원하는 날짜에 한 번만 진행해요'),
  (E'move-in', E'recurring', E'매주 지정한 요일에 진행해요'),
  (E'move-in', E'oneTime', E'입주 전 한 번만 진행해요');

-- 체크리스트 템플릿: Figma Manager/Job/InProgress 의 항목(가정 청소). 다른 카테고리 템플릿은 없음 → 배정 시 home 을 대신 쓴다 (판단 항목)
insert into checklist_templates (category_key, group_name, label, sort) values
  ('home', E'거실', E'바닥 청소', 1),
  ('home', E'거실', E'먼지 제거', 2),
  ('home', E'거실', E'테이블 정리', 3),
  ('home', E'주방', E'싱크대', 11),
  ('home', E'주방', E'가스레인지', 12),
  ('home', E'주방', E'조리대', 13),
  ('home', E'욕실', E'세면대', 21),
  ('home', E'욕실', E'변기', 22),
  ('home', E'욕실', E'바닥', 23);

insert into catalog_reasons (kind, label, sort) values
  ('reject', E'거리가 너무 멀어요', 1),
  ('reject', E'시간이 맞지 않아요', 2),
  ('reject', E'업무 내용이 맞지 않아요', 3),
  ('reject', E'예상 금액이 맞지 않아요', 4),
  ('reject', E'개인 사정', 5),
  ('reject', E'기타', 6),
  ('pause', E'고객 요청', 1),
  ('pause', E'화장실 이용', 2),
  ('pause', E'개인적인 상황', 3),
  ('pause', E'네트워크 문제', 4),
  ('pause', E'카메라 문제', 5),
  ('pause', E'기타', 6);
