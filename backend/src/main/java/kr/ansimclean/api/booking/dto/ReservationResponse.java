package kr.ansimclean.api.booking.dto;

import java.util.List;
import java.util.UUID;

/**
 * Reservations 카드 1장 — 앱 RESERVATIONS 모양. tab/status/rows 는 서버가 §6.1 규칙으로 만든다.
 * series = 정기청소 회차 (취소 시트가 "이번 회차만 / 정기청소 전체" 를 보여준다), recordingReady = 완료 탭 "녹화 영상 보기" (§9.2-8)
 * changeable = 지금 변경·취소할 수 있는가(시작 전 ∧ 방문 cancel_deadline_hours 시간 전, 시드 24) — 카드가 버튼을 미리 감춘다(시트를 열고 확인한 뒤에야 불가를 알던 것, 검증 검토)
 * 값은 BookingMapper.toReservation 이 채운다. 같은 모양을 여러 응답이 쓴다 — 예: GET /customer/reservations 의 목록 한 칸,
 * 고객 홈의 다가오는 예약(customer/dto/HomeResponse.upcoming). 필드를 바꾸면 그 응답들이 모두 바뀐다.
 * tab = "upcoming" 또는 "done" 또는 "cancelled", status = "assigning"·"scheduled"·"inProgress"·"done"·"cancelled" 중 하나,
 * date = "YYYY-MM-DD", time = "HH:mm", category = 카테고리 키, title = 카드 제목.
 * rows = 카드 안의 줄(label·value): 일정·주소·매니저, 매니저 소개가 있으면 한 줄 더.
 */
public record ReservationResponse(UUID id, String tab, String category, String title, String status, String date, String time, boolean series, boolean recordingReady, boolean changeable, List<Row> rows) {
    public record Row(String label, String value) {}
}
