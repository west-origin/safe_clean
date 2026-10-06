package kr.ansimclean.api.settlement.dto;

import java.util.List;
import java.util.UUID;

// 매니저 앱 정산 화면(getSettlement)이 받는 응답. SettlementService.screen 이 만든다.
// 필드 이름이 JSON 키가 된다. 금액은 모두 "85,000원" 같은 표시 문구다.
/** GET /manager/settlements — 앱 SETTLEMENT 모양 (§6.5) */
/** nextBefore = 다음 페이지 커서("<마지막 항목의 생성 시각 ISO>_<정산 id>", 앱은 풀지 않고 그대로 돌려보낸다). 더 없으면 null — 앱 '더 보기' */
public record SettlementResponse(Monthly monthly, List<Stat> stats, List<Item> items, String nextBefore) {
    /** 머리 카드: { label: "이번 달 예상 정산", amount } */
    public record Monthly(String label, String amount) {}

    /** tone = 'default' | 'success' */
    // 통계 한 칸 — 서비스는 '완료 업무'(이번 달 건수)·'정산 예정'·'정산 완료' 를 이 순서로 넣는다
    public record Stat(String label, String value, String tone) {}

    /** status = 'settlementPending' | 'complete' (StatusBadge), title = "강남구 기본청소" */
    // 목록 한 줄. id 는 상세로 갈 때 쓰는 정산 id, date 는 방문일("9월 18일"), amount 는 최종 정산액
    public record Item(UUID id, String date, String status, String title, String amount) {}
}
