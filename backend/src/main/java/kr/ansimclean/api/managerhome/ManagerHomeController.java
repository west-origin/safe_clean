package kr.ansimclean.api.managerhome;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.managerhome.dto.HomeResponse;
import kr.ansimclean.api.managerhome.dto.ScheduleResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** GET /manager/home · /manager/schedule?from=&to= (docs/api.md §5.3) */
// 실제 경로 앞에 "/api/v1/manager" 가 붙는다.
// /manager/** 는 SecurityConfig 가 매니저 권한만 통과시킨다. 받는 곳: 매니저 앱 api/manager.js 의 getHome·getSchedule.
// AuthenticationPrincipal CurrentUser = 로그인 토큰에서 꺼낸 지금 사용자(id·역할)
@RestController
@RequestMapping(SecurityConfig.API + "/manager")
public class ManagerHomeController {
    private final ManagerHomeService service;

    public ManagerHomeController(ManagerHomeService service) {
        this.service = service;
    }

    // 매니저 홈 화면 한 벌: 이름·승인/정지 안내·오늘 건수·진행 중 작업·새 콜 요약·남은 일정
    @GetMapping("/home")
    public HomeResponse home(@AuthenticationPrincipal CurrentUser user) {
        return service.home(user.id());
    }

    // 일정 화면. from·to 는 선택 쿼리("YYYY-MM-DD", DateTimeFormat ISO.DATE 로 LocalDate 로 바뀐다).
    // 빠지면 서비스가 채운다: from 없으면 오늘, to 없으면 from + 6일
    @GetMapping("/schedule")
    public ScheduleResponse schedule(@AuthenticationPrincipal CurrentUser user,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.schedule(user.id(), from, to);
    }
}
