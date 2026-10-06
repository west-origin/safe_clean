package kr.ansimclean.api.support;

import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.support.dto.FaqResponse;
import kr.ansimclean.api.support.dto.InquiryRequest;
import kr.ansimclean.api.support.dto.InquiryResponse;
import kr.ansimclean.api.support.dto.MyInquiryResponse;
import kr.ansimclean.api.support.dto.NoticeResponse;
import kr.ansimclean.api.support.dto.NoticeSummary;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** docs/api.md §5.5. FAQ·공지는 인증 없이(SecurityConfig permitAll), 문의는 로그인한 사용자 */
// 경로 앞부분은 /api/v1/support. 연락처(GET /contact)도 로그인 없이 열려 있다.
// 고객 앱(customer.js)과 매니저 앱(manager.js)이 같은 경로를 부르고, FAQ·공지는 audience 쿼리(customer|manager)로 대상을 가른다.
// 규칙(대상 검사·멱등·속도 제한)은 SupportService 에 있고, 여기서는 문의 본문의 @Valid 검사만 한다.
// 운영자 답변은 AdminInquiryController.
@RestController
@RequestMapping(SecurityConfig.API + "/support")
public class SupportController {
    private final SupportService service;

    public SupportController(SupportService service) {
        this.service = service;
    }

    /** GET /support/faq?audience=… → [{ id, question, answer }] (고객 앱 자주 묻는 질문 화면의 getFaq) */
    // @RequestParam: 쿼리 문자열의 값. 빠지면 400, customer·manager 가 아니면 SupportService 가 400
    @GetMapping("/faq")
    public List<FaqResponse> faq(@RequestParam String audience) {
        return service.faq(audience);
    }

    /** GET /support/notices?audience=… → 공지 목록 [{ id, title, date }] (그 대상 + all, 최신순) */
    @GetMapping("/notices")
    public List<NoticeSummary> notices(@RequestParam String audience) {
        return service.notices(audience);
    }

    /** GET /support/notices/{id}?audience=… → 공지 본문 { id, title, date, body }. 다른 대상의 공지면 404 */
    @GetMapping("/notices/{id}")
    public NoticeResponse notice(@PathVariable UUID id, @RequestParam String audience) {
        return service.notice(id, audience);
    }

    /** 고객센터 전화번호 (없으면 phone=null) — 앱은 있을 때만 전화 버튼을 그린다 */
    // singletonMap: 키 하나짜리 Map → JSON { "phone": "…" }. 값이 null 이어도 되는 Map 이라 null 이 그대로 나간다
    @GetMapping("/contact")
    public java.util.Map<String, String> contact() {
        return java.util.Collections.singletonMap("phone", service.supportPhone());
    }

    /** GET /support/inquiries → 로그인한 사용자(고객·매니저)의 문의와 답변, 최신순. 두 앱의 '내 문의' 화면(getMyInquiries) */
    // @AuthenticationPrincipal: 스프링 시큐리티가 요청 토큰에서 만든 로그인 사용자(CurrentUser)를 넣어 준다
    @GetMapping("/inquiries")
    public List<MyInquiryResponse> myInquiries(@AuthenticationPrincipal CurrentUser user) {
        return service.myInquiries(user.id());
    }

    /**
     * POST /support/inquiries { content } → { id }. Idempotency-Key 헤더가 있으면 같은 키 재시도는 한 번만 저장된다.
     * 고객 앱 submitInquiry · 매니저 앱 submitSupportInquiry 가 부른다.
     */
    // 본문 검사(@Valid)에 걸리면 이 메서드는 실행되지 않고 400 VALIDATION, 문구는 InquiryRequest 의 message
    @PostMapping("/inquiries")
    public InquiryResponse inquiry(@AuthenticationPrincipal CurrentUser user, @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                   @Valid @RequestBody InquiryRequest req) {
        return service.submit(user.id(), key, req.content());
    }
}
