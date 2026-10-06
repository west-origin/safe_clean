package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: LateStartNotifier.notifyDue (1분 간격) — 예약마다 한 번(Booking.markLateNotified). minutes 는 패널티 설정의 지각 기준 분.
// 처리: push.PushHandler — 고객에게 "약속 시간이 N분 넘게 지났어요" 푸시 (알림 설정과 무관).
/** 배정된 매니저가 약속 시간 + minutes 가 지나도 청소를 시작하지 않았다 — 고객 안내 푸시 한 번 (docs/manager-policy.md §2, 자체 점검 #25) */
public record ManagerLate(UUID bookingId, UUID customerId, UUID managerId, int minutes) {}
