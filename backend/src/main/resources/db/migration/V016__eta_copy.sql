-- V016__eta_copy.sql — 콜 카드 이동 시간 문구: '배정 후 안내' 는 어디서도 안내되지 않았다(검증 검토). 거리 계산 전까지 사실대로
update app_settings set value = '"이동 시간은 준비 중이에요"'::jsonb where key = 'eta_placeholder';
