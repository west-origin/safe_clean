package kr.ansimclean.api.job.dto;

import java.util.List;

/** GET /manager/jobs/last-completed — 앱 COMPLETED_JOB 모양 (§6.3) */
// 매니저 앱 완료 화면(Job/Complete) 값. 만드는 곳: JobService.lastCompleted.
// title 은 app_settings.job_complete_title, subtitle = "강남구 테헤란로 · 청소 유형"
public record CompletedJobResponse(String title, String subtitle, List<SummaryRow> summary, Payout payout) {
    /** tone = 'default' | 'success' | 'issue' */
    // 요약 한 행 (청소 시간·총 소요·체크리스트·촬영 기록). tone 은 행 색
    public record SummaryRow(String label, String value, String tone) {}

    /** 예상 정산 칸 — label "예상 정산", amount "85,000원", note 는 app_settings.settlement_note 안내문 */
    public record Payout(String label, String amount, String note) {}
}
