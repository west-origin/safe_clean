package kr.ansimclean.api.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /admin/penalties/{id}/waive */
// 요청 본문 JSON { "note": "진단서 확인" } — 무엇을 보고 면제했는지. 원장 행의 waived_note 에 남는다(매니저 앱에는 보이지 않는다).
// @NotBlank(빈 글 금지)·@Size(최대 500자)는 AdminPenaltyController 가 토큰 검사 뒤 Validator 로 검사한다
public record WaiveRequest(@NotBlank @Size(max = 500) String note) {}
