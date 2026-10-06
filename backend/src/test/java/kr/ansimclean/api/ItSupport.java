package kr.ansimclean.api;

import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.schedule.AvailabilityService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

// 쓰는 곳 예: MatchingIT·JobFlowIT.
// 각 IT 는 진짜 ProfileService 와 @MockitoBean JwtDecoder(가짜 토큰 해석기)를 넘겨 준비 데이터를 만든다.
/** 통합 테스트 공통: 가입이 끝난 사용자를 만들고 그 사용자의 Bearer 토큰을 돌려준다 (JWT 검증은 대역) */
public final class ItSupport {
    private ItSupport() {} // static 메서드만 모은 클래스라 객체를 만들지 않는다

    /** 만든 사용자: id 와 Authorization 헤더에 그대로 넣을 "Bearer …" 문자열 */
    public record User(UUID id, String bearer) {}

    /**
     * 인증번호·Supabase·주소·약관 단계를 건너뛰고 프로필 행을 바로 만든 뒤 가입 완료로 표시한다.
     * 토큰은 "t-<id>" 같은 가짜 문자열이고, mock JwtDecoder 가 그 문자열을 이 사용자의 JWT 로 풀어 주게 정한다.
     * role 은 "customer" 또는 "manager". 약관 동의 기록은 남기지 않는다 (필요한 테스트는 SQL 로 직접 넣는다)
     */
    public static User signedUp(ProfileService profiles, JwtDecoder jwtDecoder, String role, String name) {
        UUID id = UUID.randomUUID();
        // 번호는 id 에서 뽑은 8자리 — 살아 있는 계정끼리 번호가 겹치면 profiles_phone_live 고유 인덱스에 걸리므로 사용자마다 다르게 한다
        profiles.createForSignup(id, role, name, null, "+8210" + String.format("%08d", Math.floorMod(id.hashCode(), 100000000))); // Math.abs(MIN_VALUE) 는 음수
        profiles.completeSignup(id);
        String token = "t-" + id;
        // Supabase 토큰과 같은 모양(sub = 사용자 id, aud = authenticated, 1시간 뒤 만료). 진짜 디코더가 아니라 서명 검사는 없다
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "HS256").subject(id.toString()).claim("aud", List.of("authenticated"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(jwtDecoder.decode(token)).thenReturn(jwt);
        return new User(id, "Bearer " + token);
    }

    /** 승인된 매니저: 기본 근무시간(월~금 09–18) + 활동 지역 */
    public static User approvedManager(ProfileService profiles, AvailabilityService availability, JwtDecoder jwtDecoder, String name, String area, String sido) {
        User u = signedUp(profiles, jwtDecoder, "manager", name);
        availability.createDefaultHours(u.id());
        // 운영자 승인 대신 직접 부른다 — 미승인 매니저는 콜 후보가 아니다. area 예: "서울특별시 강남구", sido 예: "서울특별시"
        profiles.approveManager(u.id(), List.of(area), sido);
        return u;
    }

    /** 통합 테스트는 클래스당 컨테이너 하나를 쓰고 롤백이 없다 — 테스트마다 사용자 데이터(프로필과 그 아래 전부)를 비운다. 카탈로그·약관·설정 seed 는 남긴다 */
    public static void resetUserData(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        // truncate … cascade: 표를 통째로 비운다. cascade 는 이 표들을 외래 키로 가리키는 표(예약·콜 등)까지 함께 비운다
        jdbc.execute("truncate table profiles, idempotency_keys, phone_verifications, outbox_events, livekit_events cascade");
    }

    /** 고객 예약 생성(POST /customer/bookings) 요청 본문 JSON — 가정 청소 1회, 옵션 없음, 주의사항 반려동물. date "YYYY-MM-DD", time "HH:mm" */
    public static String bookingBody(String date, String time) {
        return "{\"category\":\"home\",\"frequency\":\"oneTime\",\"date\":\"" + date + "\",\"time\":\"" + time + "\"," +
                "\"roadAddress\":\"서울특별시 강남구 테헤란로 123\",\"detailAddress\":\"3층\",\"freeOptions\":[],\"paidOptions\":[],\"notices\":[\"pet\"],\"request\":\"잘 부탁드려요\"}";
    }
}
