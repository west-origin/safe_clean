package kr.ansimclean.api.support;

import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.support.dto.TermResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/terms — 인증 없음 (가입 화면에서 부른다) */
// SecurityConfig 가 /terms 아래 경로를 로그인 없이 통과시킨다.
// 목록: 고객 앱 getTerms(Auth/TermsConsent) · 매니저 앱 getManagerTerms(SignUp/Terms).
// 본문: 고객 앱 getTerm · 매니저 앱 getManagerTerm. 규칙은 TermsService 에 있다.
@RestController
@RequestMapping(SecurityConfig.API + "/terms")
public class TermsController {
    private final TermsService service;

    public TermsController(TermsService service) {
        this.service = service;
    }

    /** customer → [{ id, label, required }] · manager → { terms, note } */
    // 대상에 따라 응답 모양(배열 / 객체)이 달라 반환 형을 Object 로 둔다. audience 가 틀리면 400
    @GetMapping
    public Object list(@RequestParam String audience) {
        TermsService.requireAudience(audience);
        return Roles.MANAGER.equals(audience) ? service.managerTerms() : service.customerTerms();
    }

    /** GET /terms/{id}?audience=… → 약관 본문 { id, title, body }. 없는 약관이면 404, audience 가 틀리면 400 */
    @GetMapping("/{id}")
    public TermResponse term(@PathVariable String id, @RequestParam String audience) {
        return service.term(id, audience);
    }
}
