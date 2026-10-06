package kr.ansimclean.api.job.dto;

import java.util.UUID;

/** POST /manager/jobs/{id}/start → { jobId, startedAt(ISO) } */
// startedAt = 실제 시작 시각(UTC, 예: "2026-09-15T05:02:00Z"). 진행 중(in_progress)인 작업을 다시 시작하면 처음 시각을 그대로 준다(일시정지 중이면 409). 만드는 곳: JobService.doStart
public record StartResponse(UUID jobId, String startedAt) {}
