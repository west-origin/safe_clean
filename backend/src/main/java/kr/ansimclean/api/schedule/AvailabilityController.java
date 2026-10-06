package kr.ansimclean.api.schedule;

import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.schedule.dto.AvailabilityResponse;
import kr.ansimclean.api.schedule.dto.HoursRequest;
import kr.ansimclean.api.schedule.dto.UnavailableRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// 매니저 앱 '근무 가능 시간' 화면(screens/schedule/Availability.jsx, 근무 불가 추가 바텀시트 포함)이 부르는 API.
// 매니저 앱 api/manager.js 의 함수(예: getAvailability·addUnavailable)가 이 경로를 부른다.
// 여기는 입출력만 하고, 규칙(형식 검사·이미 맡은 작업과 겹치는지·탈퇴와 줄 서기)은 AvailabilityService 에 있다.
/** Manager/Schedule/Availability · AddUnavailable (docs/api.md §5.3) */
@RestController
// 경로 앞부분: /api/v1/manager/availability (SecurityConfig.API = "/api/v1"). 매니저만 들어온다 (CODE_GUIDE.md 3-11)
@RequestMapping(SecurityConfig.API + "/manager/availability")
public class AvailabilityController {
    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    /** GET — 요일별 기본 근무시간 7줄(월~일) + 근무 불가 목록. 지난 1회성 근무 불가는 빠진다 */
    // @AuthenticationPrincipal CurrentUser = 로그인 토큰에서 꺼낸 지금 사용자. 본인 것만 본다
    @GetMapping
    public AvailabilityResponse availability(@AuthenticationPrincipal CurrentUser user) {
        return service.availability(user.id());
    }

    /** PUT /hours — 기본 근무시간 수정. 본문은 [{ weekday, start, end, off }] 배열(온 요일만 바꾼다), 응답은 바뀐 뒤 7줄 */
    // List<@Valid HoursRequest>: 배열 안 줄마다 HoursRequest 의 검사(요일 0~6)를 돌린다. weekday 7 이면 400 (ManagerScreensControllerTest)
    @PutMapping("/hours")
    public List<AvailabilityResponse.DayHours> hours(@AuthenticationPrincipal CurrentUser user, @RequestBody List<@Valid HoursRequest> rows) {
        return service.updateHours(user.id(), rows);
    }

    /** DELETE /unavailable/{id} — 근무 불가 한 건 삭제. 내 것이 아니면 404 */
    // @ResponseStatus(NO_CONTENT) = 성공하면 본문 없이 204
    @DeleteMapping("/unavailable/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUnavailable(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        service.removeUnavailable(user.id(), id);
    }

    /** POST /unavailable — 근무 불가 추가. 이미 맡은 작업과 겹치면 409, 응답은 목록 한 줄 모양 */
    // 형식 검사(UnavailableRequest 의 @Pattern 등)가 틀리면 여기서 400. 없는 날짜·시작 ≥ 종료는 서비스가 400
    @PostMapping("/unavailable")
    public AvailabilityResponse.UnavailableItem unavailable(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody UnavailableRequest req) {
        return service.addUnavailable(user.id(), req);
    }
}
