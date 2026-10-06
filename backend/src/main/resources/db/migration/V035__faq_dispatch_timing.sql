-- 고객 FAQ '예약 후 보통 24시간 안에 배정' 이 정기청소와 달랐다 (자체 점검 #30, 2026-09-27 — 문구는 코드에서 정함)
--  · 정기청소는 첫 방문만 바로 매칭하고, 이후 방문은 각 방문 7일 전(app_settings.dispatch_lead_days)부터 찾는다
update faqs set answer = E'1회 청소는 예약하면 바로 매니저를 찾기 시작해요. 정기청소는 첫 방문은 바로, 이후 방문은 각 방문 7일 전부터 찾아요. 배정되면 알림으로 알려드려요.'
 where audience = 'customer' and question = E'매니저는 언제 배정되나요?';
