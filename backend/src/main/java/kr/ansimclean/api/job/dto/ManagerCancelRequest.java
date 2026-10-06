package kr.ansimclean.api.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /manager/jobs/{id}/cancel — 매니저 방문 취소 (docs/manager-policy.md). reason 은 app_settings.manager_cancel_reasons 의 key */
// @NotBlank = 비었거나 공백뿐이면 400(message 문구), @Size(max = 500) = 상세 설명은 500자까지(없어도 된다).
// 목록에 없는 key 인지는 JobService.doCancelByManager 가 따로 검사한다
// 500 을 바꾸면 매니저 앱 apps/manager/src/api/mock.js 의 INPUT_LIMITS.jobCancelDetail(상세 입력란 maxLength)도 같이 바꾼다 —
// 앱이 더 많이 받으면 확인을 눌러도 여기서 400(VALIDATION)이라 취소가 안 된다
public record ManagerCancelRequest(@NotBlank(message = "취소 사유를 선택해주세요") String reason,
                                   @Size(max = 500) String detail) {}
