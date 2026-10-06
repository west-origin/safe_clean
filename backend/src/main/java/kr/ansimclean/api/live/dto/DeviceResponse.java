package kr.ansimclean.api.live.dto;

import java.util.List;

/** GET /manager/device — 촬영 장비 화면 (앱 EQUIPMENT_DEVICE + 바디캠 설정 정보, §9.2-4) */
/** online = 배지 색 판정용 원시값 (앱이 connection 문구를 비교하지 않게, 검증 검토) */
// 만드는 곳: LiveSessionService.device. 받는 곳: 매니저 앱 api/manager.js 의 getEquipmentDevice.
// name = 기기 이름, connection = "정상 연결" | "연결 안 됨", status = 장비 상태 4줄(배터리·네트워크·카메라·실시간 전송),
// lastOnline = 마지막 연결 시각 "9월 15일 14:32"(서울 시간, 한 번도 붙은 적 없으면 null)
public record DeviceResponse(String name, String connection, boolean online, List<DeviceStatusRow> status, Stream stream, String lastOnline) {
    /** 기기에 한 번 설정하는 RTMP 주소와 마스킹된 키 ("****-**-1234"). 전체 키는 운영자에게 */
    public record Stream(String url, String keyMasked) {}
}
