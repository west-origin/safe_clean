package kr.ansimclean.api.job.dto;

import java.util.UUID;

/** → { jobId, reason, since: "14:32부터 중단됨" } */
// POST /manager/jobs/{id}/pause 응답. 만드는 곳: JobService.pause — 이미 멈춰 있었으면 처음 멈춘 사유·시각을 돌려준다. since 는 서울 시각
public record PauseResponse(UUID jobId, String reason, String since) {}
