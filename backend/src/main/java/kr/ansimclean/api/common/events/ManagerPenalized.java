package kr.ansimclean.api.common.events;

import java.util.UUID;

// 아웃박스 이벤트 (CODE_GUIDE.md 3-6). 클래스 이름이 outbox_events 의 type 이 되어 핸들러를 고른다
// — 이름을 바꾸면 이미 쌓인 행이 "핸들러 없음" 으로 실패한다.
// 발행: ManagerPenaltyService.record — 패널티를 원장에 적을 때마다 (예: JobService 의 방문 취소·늦은 시작,
// StaleBookingCleaner 의 미방문).
// category = 패널티 종류, points = 이번 점수, total = 누적 점수. 점수는 소수라 글자로 싣는다(BigDecimal.toPlainString, 예: "0.5").
// stage = 이번 기록으로 넘은 단계(app_settings.manager_penalty.thresholds 의 action: warn·suspend·review), 넘지 않았으면 null.
//   한 기록이 여러 단계를 넘으면 하나를 고른다: suspend > warn > 그 밖(review) — 정지와 해지 심사를 함께 넘어도 suspend (합친 38건 검토 R4).
// suspendedUntil = stage 가 suspend 일 때 정지 마지막 날 "YYYY-MM-DD"(그날까지 정지 — 넘은 정지 단계 중 가장 긴 것), 그 밖에는 null.
// nextSuspendPoints·nextSuspendDays = stage 가 warn 일 때 그 위의 첫 정지 단계(기본 5점·7일), 없거나 warn 이 아니면 null.
// 처리: push.PushHandler — 기록 한 건에 푸시 하나. warn = 경고 전용, suspend = 정지 전용(둘 다 알림 설정과 무관),
// 그 밖(stage null·review)은 points 가 0 이면(무료 취소) 보내지 않고, 아니면 "패널티가 기록됐어요 / 누적 N점 …" (push 토글).
// 새 칸들은 문제 목록 14번에서 더했다 — 그 전에 쌓인 이벤트는 값이 없어(null) 일반 알림으로 간다.
/** 매니저에게 패널티가 기록됐다(방문 취소·늦은 시작·미방문) → 매니저 푸시 — 경고·정지 단계를 넘었으면 그 전용 문구 (docs/manager-policy.md §3) */
public record ManagerPenalized(UUID managerId, UUID bookingId, String category, String points, String total,
                               String stage, String suspendedUntil, Integer nextSuspendPoints, Integer nextSuspendDays) {}
