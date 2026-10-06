package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: JobService 의 매니저 방문 취소(ManagerReleased 와 함께). ManagerReleased 는 PushHandler 가 맡고,
// 이 이벤트는 LiveKit 을 부르는 핸들러가 맡는다 (아웃박스는 이벤트 타입마다 핸들러가 하나다).
// 처리: live.CameraReleaseHandler — 그 매니저 바디캠이 이 예약 방("booking-" + 예약 id)에 묶여 있을 때만 대기 방으로 옮긴다.
/** 매니저가 방문을 취소했다 → 그 매니저 바디캠(Ingress)이 이 예약 방에 묶여 있으면 대기 방으로 돌린다 (전체 재점검 J1, live.CameraReleaseHandler) */
public record CameraReleased(UUID bookingId, UUID managerId) {}
