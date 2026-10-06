package kr.ansimclean.api.job.dto;

import java.util.UUID;

/** GET /manager/jobs/active/pause — 앱 PAUSED_JOB 모양 */
// 매니저 앱 일시정지 화면(Job/Paused) 값. since = "14:32부터 중단됨", note = app_settings.pause_note 안내문. 만드는 곳: JobService.pausedJob
public record PausedJobResponse(UUID jobId, String since, String note) {}
