package kr.ansimclean.api.job.dto;

import java.util.List;
import java.util.UUID;

/** GET /manager/jobs/{id} — 앱 JOB_DETAIL 모양 (§6.3). status = StatusBadge 값 */
/** photos: 수리 예약의 첨부 사진 서명 URL(1시간) — 청소 예약은 빈 배열 (매니저 Figma 에 없음, 코드에서 정함) */
/** today: 작업 날짜가 오늘인가 — 시작(현장 도착)은 당일에만 된다. 아니면 startLabel 에 날짜가 붙는다 (직접 사용 점검 U9·U11) */
// 매니저 앱 작업 상세(Job/Detail) 값. 만드는 곳: JobService.job.
// address = 도로명 주소(+상세 주소) 줄, info = 고객(가린 이름)·청소 유형·예상 소요(+주거 정보) 행, pay = 매니저 몫 "85,000원",
// scope = 무료·유료 옵션 라벨, requests = 주의사항·수리 정보·고객 요청 줄
public record JobResponse(UUID id, String status, String startLabel, boolean today, List<String> address, List<Row> info, String pay, List<String> scope, List<String> requests, List<String> photos) {
    /** 정보 한 행 — 왼쪽 라벨(예: "고객")과 오른쪽 값 */
    public record Row(String label, String value) {}
}
