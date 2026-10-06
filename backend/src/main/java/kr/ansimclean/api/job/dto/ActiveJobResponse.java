package kr.ansimclean.api.job.dto;

import kr.ansimclean.api.live.dto.DeviceStatusRow;

import java.util.List;
import java.util.UUID;

/**
 * GET /manager/jobs/active — 앱 ACTIVE_JOB 모양 (§6.3). elapsed 는 초기값이고 앱 타이머는 startedAt(ISO)·pausedSeconds 로 계산한다.
 * since = "14:02부터 진행 · 실시간 촬영 중" (바디캠이 안 붙었으면 "카메라 연결 대기 중")
 * sinceLabel = "14:02부터 진행" (라이브 상태 문구는 앱이 3초 폴링 값으로 붙인다), streaming = 조회 시점 바디캠 송출 여부 (검증 검토: 앱이 since 를 ' · ' 로 되파싱했다)
 * 필드 이름이 JSON 키라 매니저 앱 api/mock.js 의 ACTIVE_JOB 키와 같아야 한다.
 * 만드는 곳: JobService.activeJob. equipment = 바디캠 상태 행(카메라·배터리·네트워크, LiveSessionService.activeJobEquipment), requests = 요청사항 줄.
 */
/** status = 작업 상태 "inProgress"|"paused" — 앱 타이머가 라이브 상태가 아니라 이걸로 멈춘다 (자체 점검 #43) */
public record ActiveJobResponse(UUID id, String status, String title, String elapsed, String startedAt, long pausedSeconds, String since, String sinceLabel, boolean streaming,
                                List<DeviceStatusRow> equipment, List<String> requests, List<Group> checklist) {
    /** 체크리스트 묶음 하나 (예: '거실') — 묶음 이름과 그 안의 항목들 */
    public record Group(String group, List<Item> items) {}

    /** 체크리스트 한 줄. id 는 체크 토글 요청(PUT …/checklist/{itemId})의 itemId 로 쓴다 */
    public record Item(UUID id, String label, boolean done) {}
}
