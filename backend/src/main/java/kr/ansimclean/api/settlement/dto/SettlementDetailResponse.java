package kr.ansimclean.api.settlement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

// 매니저 앱 정산 상세 화면(getSettlementDetail)이 받는 응답. SettlementService.detail 이 만든다.
// 필드 이름이 JSON 키가 된다. @JsonProperty("final") = finalRow 를 JSON 에서는 "final" 키로 쓴다
/** GET /manager/settlements/{id} — 앱 SETTLEMENT_DETAIL 모양. JSON 키 "final" 은 Java 예약어라 이름만 다르다 */
public record SettlementDetailResponse(UUID id, String title, String subtitle, List<Row> breakdown, @JsonProperty("final") Row finalRow) {
    /** tone = 'default' | 'secondary' (breakdown 줄) · 'brand' (final 줄) */
    // 금액 한 줄 { label, value: "85,000원" 같은 문구, tone }
    public record Row(String label, String value, String tone) {}
}
