package kr.ansimclean.api.job.dto;

import java.util.UUID;

/**
 * → { jobId } — 작업 재개·완료(매니저·운영자)의 응답. jobId 는 예약 id 와 같다.
 */
public record JobIdResponse(UUID jobId) {}
