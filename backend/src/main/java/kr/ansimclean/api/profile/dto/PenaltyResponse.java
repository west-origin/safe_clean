package kr.ansimclean.api.profile.dto;

import java.util.List;
import java.util.UUID;

/** GET /manager/penalties — MY › 패널티 내역 (docs/manager-policy.md) */
// ManagerPenaltyService.screen 이 만든다. points = 지금 누적 점수(숫자 글자), nextThreshold = 다음 단계 안내 문구(모든 단계를 넘었으면 null),
// suspension = 정지 중일 때만 안내 문구(아니면 null), items = 기록 목록(최근 것 먼저)
public record PenaltyResponse(String points, String nextThreshold, String suspension, List<Item> items) {
    /** points 는 "+1점" · "-1점" 처럼 부호가 붙은 표시용 문자열 */
    // 기록 한 줄: title = 종류 이름, date = 서울 시각 표시 글자, status = 상태 문구, detail = 기록에 적힌 설명
    public record Item(UUID id, String title, String points, String date, String status, String detail) {}
}
