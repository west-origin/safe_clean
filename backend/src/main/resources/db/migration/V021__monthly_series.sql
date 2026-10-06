-- V021__monthly_series.sql — 정기청소를 '한 달 동안 매주' → '매월 같은 날짜에 한 번' 으로 (사용자 결정 2026-09-22).
-- series_term_months 는 이제 '몇 개월 = 몇 회' 를 뜻한다. 6 = 6개월 6회. 운영 중에 이 값만 바꾸면 회차 수가 바뀐다.
update app_settings set value = '6'::jsonb where key = 'series_term_months';

-- 주기 설명 문구도 사실대로 (Figma 원본은 "매주 지정한 요일에 진행해요" — 디자이너 확인 필요, PROGRESS 판단 항목)
update catalog_frequency_copy set description = E'매월 같은 날짜에 한 번 진행해요' where frequency = 'recurring';
