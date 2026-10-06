package kr.ansimclean.api.job.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /manager/jobs/{id}/pause — reason 은 catalog_reasons(kind=pause) 문구 */
// 본문 { reason }. @NotBlank = 비면 400("중단 사유를 골라주세요"). 목록에 있는 문구인지는 JobService.pause 가 catalog.isReason 으로 검사한다.
// 사유 목록은 앱이 GET /catalog/reasons?kind=pause 로 받아 일시정지 시트에 보여 준다 (api/manager.js getPauseReasons)
public record PauseRequest(@NotBlank(message = "중단 사유를 골라주세요") String reason) {}
