package kr.ansimclean.api.live.dto;

/** 장비 상태 한 줄 (앱 EQUIPMENT_OK 모양). status = 'ok' | 'issue' */
// label = 줄 이름("카메라" 등), value = 보이는 값("연결됨"·"확인 필요" 등). 만드는 곳: LiveSessionService.
// 쓰는 응답 예: GET /manager/device/status(목록 그대로), 진행 중 작업 응답(ActiveJobResponse.equipment)
public record DeviceStatusRow(String label, String value, String status) {
    /** 정상 줄 (status = "ok") */
    public static DeviceStatusRow ok(String label, String value) {
        return new DeviceStatusRow(label, value, "ok");
    }

    /** 문제 있는 줄 (status = "issue") */
    public static DeviceStatusRow issue(String label, String value) {
        return new DeviceStatusRow(label, value, "issue");
    }
}
