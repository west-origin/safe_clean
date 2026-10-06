package kr.ansimclean.api.job.dto;

import java.util.UUID;

/** PUT /manager/jobs/{id}/checklist/{itemId} 응답 → { jobId, itemId, done } (바뀐 뒤의 값). 만드는 곳: JobService.setChecklistItem */
public record ChecklistResponse(UUID jobId, UUID itemId, boolean done) {}
