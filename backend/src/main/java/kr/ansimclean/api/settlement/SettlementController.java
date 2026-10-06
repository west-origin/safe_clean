package kr.ansimclean.api.settlement;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.settlement.dto.SettlementDetailResponse;
import kr.ansimclean.api.settlement.dto.SettlementResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 매니저 앱 정산 화면(screens/settlement/Settlement.jsx)과 정산 상세(SettlementDetail.jsx)가 부르는 API.
// 매니저 앱 api/manager.js 의 getSettlement·getSettlementDetail 가 이 경로를 부른다. 읽기 전용이고 계산은 SettlementService 에 있다.
/** GET /manager/settlements · /manager/settlements/{id} (docs/api.md §5.3) */
@RestController
// 경로 앞부분: /api/v1/manager/settlements (SecurityConfig.API = "/api/v1"). 매니저만 들어온다 (CODE_GUIDE.md 3-11)
@RequestMapping(SecurityConfig.API + "/manager/settlements")
public class SettlementController {
    private final SettlementService service;

    public SettlementController(SettlementService service) {
        this.service = service;
    }

    /** ?before=<이전 응답의 nextBefore>&limit=<1..100> — api.md 공통 규칙의 페이지네이션. before 는 앱이 그대로 돌려보내는 불투명 커서 */
    // @RequestParam = 주소 뒤 ?이름=값. before 는 없어도 되고(첫 페이지), limit 은 없으면 50. 범위 밖 limit 은 서비스가 1~100 으로 자른다
    // @AuthenticationPrincipal CurrentUser = 로그인 토큰에서 꺼낸 지금 사용자. 본인 정산만 본다
    @GetMapping
    public SettlementResponse screen(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        return service.screen(user.id(), before, limit);
    }

    /** GET /{id} — 정산 한 건 상세(금액 내역). 내 정산이 아니면 404 */
    @GetMapping("/{id}")
    public SettlementDetailResponse detail(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.detail(user.id(), id);
    }
}
