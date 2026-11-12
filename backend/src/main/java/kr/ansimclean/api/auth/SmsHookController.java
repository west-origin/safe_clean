package kr.ansimclean.api.auth;

import java.util.Map;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /hooks/supabase/send-sms — Supabase Auth Send SMS Hook (HTTPS). 로그인 토큰 없이 오고 서명으로 검증한다 (SecurityConfig permitAll).
 * SUPABASE_SMS_HOOK_SECRET 이 비어 있으면 404 — Hook 을 켜지 않은 환경. 내용은 SmsHookService.
 * 오류 본문은 Supabase Hook 형식 { "error": { "http_code", "message" } } — Supabase 가 이 상태·문구를 앱 요청의 오류로 돌려준다.
 */
@RestController
public class SmsHookController {
    private final SmsHookService service;

    public SmsHookController(SmsHookService service) {
        this.service = service;
    }

    @PostMapping(SecurityConfig.API + "/hooks/supabase/send-sms")
    public ResponseEntity<Map<String, Object>> sendSms(
            @RequestHeader(value = "webhook-id", required = false) String id,
            @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
            @RequestHeader(value = "webhook-signature", required = false) String signature,
            @RequestBody(required = false) String body) {
        if (!service.enabled()) return ResponseEntity.notFound().build();
        SmsHookService.Result r = service.handle(id, timestamp, signature, body);
        if (r.status() == 200) return ResponseEntity.ok(Map.of());
        return ResponseEntity.status(r.status()).body(Map.of("error", Map.of("http_code", r.status(), "message", r.message())));
    }
}
