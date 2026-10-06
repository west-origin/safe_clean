package kr.ansimclean.api.live;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.live.dto.DeviceResponse;
import kr.ansimclean.api.live.dto.DeviceStatusRow;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /manager/device (Profile/Equipment) · /manager/device/status (Job/Preparing 장비 4행) — docs/api.md §5.3 */
// 실제 경로는 /api/v1/manager/device… (매니저만, CODE_GUIDE.md 3-11).
// 부르는 쪽: 매니저 앱 api/manager.js 의 getEquipmentDevice(장비 화면)·getEquipmentStatus(작업 준비 화면).
// @AuthenticationPrincipal CurrentUser = 요청을 보낸 로그인 매니저. 자기 바디캠만 볼 수 있다(매니저 id 로 찾는다). 읽기만 한다
@RestController
@RequestMapping(SecurityConfig.API + "/manager/device")
public class DeviceController {
    private final LiveSessionService service;

    public DeviceController(LiveSessionService service) {
        this.service = service;
    }

    /** GET /manager/device — 내 바디캠 이름·연결 상태·상태 4행·송출 주소(스트림 키는 가려서)·마지막 연결 시각. 등록 전이면 404 */
    @GetMapping
    public DeviceResponse device(@AuthenticationPrincipal CurrentUser user) {
        return service.device(user.id());
    }

    /** GET /manager/device/status — 작업 준비 화면의 장비 4행(카메라·배터리·네트워크·전송). 기기가 없어도 404 가 아니라 '연결 안 됨' 같은 행으로 답한다 */
    @GetMapping("/status")
    public List<DeviceStatusRow> status(@AuthenticationPrincipal CurrentUser user) {
        return service.deviceStatus(user.id());
    }
}
