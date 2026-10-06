package kr.ansimclean.api.job.dto;

import jakarta.validation.constraints.NotNull;

/**
 * PUT /manager/jobs/{id}/checklist/{itemId} 본문 { done } — true 면 체크, false 면 해제.
 * boolean 이 아니라 Boolean(비어 있을 수 있는 객체)이라 본문에 done 이 없으면 null 이 되고, @NotNull 이 400 으로 막는다.
 */
public record ChecklistRequest(@NotNull Boolean done) {}
