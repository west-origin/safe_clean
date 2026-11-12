package kr.ansimclean.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.sms.SmsSender;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Supabase Auth "Send SMS Hook" 수신 — Supabase 가 만든 인증번호를 우리 문자 업체(SmsSender, 운영 Solapi)로 보낸다.
 * 이 Hook 을 Supabase 에서 켜기 전까지는 호출되지 않는다 (개발은 테스트 번호 + 고정 인증번호라 문자 자체가 없다).
 * 켜는 절차·전환 순서는 docs/auth/phone-otp.md "SOLAPI 전환".
 *
 * 보안:
 *  1) 서명 — Standard Webhooks(webhook-id·webhook-timestamp·webhook-signature, HMAC-SHA256). 비밀은 환경변수 SUPABASE_SMS_HOOK_SECRET
 *     (Supabase 가 주는 "v1,whsec_…"). 비어 있으면 Hook 자체가 꺼진다(404).
 *  2) 허용 목록 — 우리 서버가 방금 시작한 고객 인증 요청(customer_phone_challenges)과 계정·번호가 같을 때만 보낸다.
 *     공개 키로 Supabase /otp 를 직접 불러 아무 번호로 문자를 보내게 하는 비용 공격을 막는다.
 *  3) 번호별 시간당 한도.
 */
@Service
public class SmsHookService {
    private static final Logger log = LoggerFactory.getLogger(SmsHookService.class);
    /** 서명 시각 허용 오차 (재전송 공격 방지) */
    static final Duration TOLERANCE = Duration.ofMinutes(5);
    /** 우리 서버가 요청을 기록한 뒤 Supabase 가 Hook 을 부르기까지 허용 시간 (보통 1초 안) */
    static final Duration CHALLENGE_WINDOW = Duration.ofMinutes(1);

    /** Hook 응답: status 200 이면 성공, 아니면 Supabase 형식 오류 본문 */
    public record Result(int status, String message) {
        static final Result OK = new Result(200, null);
    }

    private final byte[] key; // null 이면 Hook 꺼짐
    private final CustomerPhoneStore store;
    private final SmsSender sms;
    private final RateLimiter limiter;
    private final AppClock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public SmsHookService(@Value("${app.sms.hook-secret:}") String secret, CustomerPhoneStore store, SmsSender sms, RateLimiter limiter, AppClock clock) {
        this.key = decodeSecret(secret);
        this.store = store;
        this.sms = sms;
        this.limiter = limiter;
        this.clock = clock;
    }

    public boolean enabled() {
        return key != null;
    }

    /** "v1,whsec_BASE64" 또는 "whsec_BASE64" → 키 바이트. 비었으면 null */
    static byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) return null;
        String s = secret.trim();
        if (s.startsWith("v1,")) s = s.substring(3);
        if (s.startsWith("whsec_")) s = s.substring(6);
        return Base64.getDecoder().decode(s);
    }

    public Result handle(String id, String timestamp, String signatures, String body) {
        if (!verifySignature(id, timestamp, signatures, body)) return new Result(401, "invalid signature");
        JsonNode payload;
        try {
            payload = json.readTree(body);
        } catch (RuntimeException e) {
            return new Result(400, "invalid payload");
        }
        JsonNode user = payload.path("user");
        String otp = payload.path("sms").path("otp").asString("");
        String target = firstNonBlank(user.path("new_phone").asString(""), user.path("phone_change").asString(""), user.path("phone").asString(""));
        if (otp.isBlank() || target.isBlank() || user.path("id").asString("").isBlank()) return new Result(400, "invalid payload");
        String e164 = target.startsWith("+") ? target : "+" + target;
        UUID userId;
        try {
            userId = UUID.fromString(user.path("id").asString());
        } catch (IllegalArgumentException e) {
            return new Result(400, "invalid payload");
        }
        Instant now = clock.now();
        boolean allowed = store.challenge(userId)
                .filter(c -> c.phone().equals(e164) && c.requestedAt().isAfter(now.minus(CHALLENGE_WINDOW)))
                .isPresent();
        if (!allowed) {
            log.warn("허용되지 않은 문자 발송 요청을 거절했다 user={} phone={}", userId, KoreanFormat.maskPhone(e164));
            return new Result(403, "이 번호로는 인증번호를 보낼 수 없어요");
        }
        try {
            limiter.check("sms-hook-phone", e164, CustomerPhoneService.REQUESTS_PER_HOUR);
        } catch (RuntimeException e) {
            return new Result(429, "인증 요청이 너무 많아요. 잠시 후 다시 시도해주세요");
        }
        try {
            sms.send(e164, "[안심클린] 인증번호 " + otp + " (" + CustomerPhoneService.CODE_TTL.toMinutes() + "분 안에 입력해주세요)");
        } catch (SmsSender.SmsException e) {
            log.error("Hook 문자 발송 실패 {}", KoreanFormat.maskPhone(e164));
            return new Result(502, "인증번호를 보내지 못했어요. 잠시 후 다시 시도해주세요");
        }
        return Result.OK;
    }

    /** Standard Webhooks 서명 검사: base64(HMAC-SHA256(key, id.timestamp.body)) 가 헤더의 v1 서명 중 하나와 같은가 */
    boolean verifySignature(String id, String timestamp, String signatures, String body) {
        if (key == null || id == null || timestamp == null || signatures == null || body == null) return false;
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        Instant sent = Instant.ofEpochSecond(ts);
        Instant now = clock.now();
        if (sent.isBefore(now.minus(TOLERANCE)) || sent.isAfter(now.plus(TOLERANCE))) return false;
        byte[] expected = sign(id + "." + timestamp.trim() + "." + body);
        for (String part : signatures.trim().split(" ")) {
            int comma = part.indexOf(',');
            if (comma < 0 || !part.substring(0, comma).equals("v1")) continue;
            try {
                if (MessageDigest.isEqual(expected, Base64.getDecoder().decode(part.substring(comma + 1)))) return true;
            } catch (IllegalArgumentException ignored) {
                // 잘못된 base64 는 무시하고 다음 서명을 본다
            }
        }
        return false;
    }

    byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 계산 실패", e);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return "";
    }
}
