package kr.ansimclean.api.common.supabase;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Supabase Auth(GoTrue) REST 호출 — 서버만 부른다(service_role 키). BACKEND_PLAN.md §7.1.
 * 쓰는 것: admin 사용자 생성/비밀번호 변경/삭제, 휴대폰+비밀번호 로그인, refresh, logout.
 * 쓰지 않는 것: /otp, /signup (인증번호는 우리가 보내고, 가입은 admin API 로만).
 * 부르는 곳 예: AuthService(가입·로그인 등), SupabaseUserDeletionHandler(탈퇴 뒤 아웃박스가 사용자 삭제를 다시 시도할 때).
 * 오류 처리: Supabase 가 오류로 답하거나 연결이 안 되면 ApiException 으로 바꿔 던진다
 * (이미 있는 번호 → PHONE_TAKEN, 자격 증명 오류 → LOGIN_FAILED/UNAUTHENTICATED, 한도 → RATE_LIMITED, 나머지 → UPSTREAM 502).
 * 예외: 로그아웃 두 메서드는 실패해도 던지지 않고 로그만 남기고, deleteUser 는 이미 없는 사용자(404)를 성공으로 친다.
 */
@Component
public class SupabaseAuthClient {
    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthClient.class);

    // AuthService 가 이 값과 우리 프로필로 앱에 줄 응답(AuthResponse)을 만든다
    /** 로그인 응답 (expiresIn 은 초) */
    public record Session(String accessToken, String refreshToken, long expiresIn, UUID userId) {
        /** access JWT 의 session_id claim (Supabase 가 방금 준 토큰이라 서명 검증 없이 payload 만 읽는다). 못 읽으면 null */
        public String sessionId() {
            // 부르는 곳: AuthService.refresh — 새로 받은 세션이 우리가 끊은 세션 목록에 있는지 이 id 로 확인한다
            try {
                // JWT 는 '헤더.내용.서명' 세 조각을 점으로 이은 문자열이다. 가운데(내용)를 base64url 로 풀면 JSON 이 나온다
                String[] parts = accessToken.split("\\.");
                if (parts.length < 2) return null;
                JsonNode claims = JsonMapper.builder().build().readTree(java.util.Base64.getUrlDecoder().decode(parts[1]));
                JsonNode sid = claims.get("session_id");
                return sid == null || sid.isNull() ? null : sid.asString();
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    private final RestClient client; // HTTP 호출 도구. 기본 주소가 '<Supabase 주소>/auth/v1' 로 고정돼 있다
    private final String serviceRoleKey; // 모든 권한을 가진 서버 전용 키 (환경변수에서 온다. 앱에는 절대 두지 않는다)
    private final JsonMapper json = JsonMapper.builder().build(); // 오류 응답 본문(JSON)을 읽을 때 쓴다

    public SupabaseAuthClient(AppProperties props, RestClient.Builder builder) {
        this.serviceRoleKey = props.supabase().serviceRoleKey();
        this.client = builder.baseUrl(props.supabase().url() + "/auth/v1").build();
    }

    // 부르는 곳: AuthService.signup — 이 번호의 Supabase 사용자가 없을 때. 돌려주는 값은 새 사용자 id(= 우리 프로필 id)
    /** 사용자 생성. 휴대폰은 우리가 이미 인증했으므로 phone_confirm=true. 이미 있는 번호면 PHONE_TAKEN */
    public UUID createUser(String phone, String password) {
        // RestClient 는 retrieve() 뒤 body()·toBodilessEntity() 에서 실제로 요청을 보내고, 4xx·5xx 응답이면 HttpStatusCodeException 을,
        // 연결 자체가 안 되면 ResourceAccessException 을 던진다. 아래 메서드들도 같은 모양으로 둘을 나눠 잡는다
        try {
            JsonNode body = admin().post().uri("/admin/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("phone", phone, "password", password, "phone_confirm", true))
                    .retrieve().body(JsonNode.class);
            return UUID.fromString(body.get("id").asString());
        } catch (HttpStatusCodeException e) {
            // 오류 본문의 error_code 가 phone_exists 면 이미 가입된 번호로 본다
            if (errorCode(e).equals("phone_exists")) throw new ApiException(ErrorCode.PHONE_TAKEN);
            throw upstream("사용자 생성", e);
        } catch (ResourceAccessException e) {
            throw unreachable(e); // 연결 거부·타임아웃·DNS — 500 INTERNAL 이 아니라 UPSTREAM
        }
    }

    /**
     * Supabase 사용자의 비밀번호를 바꾼다 (admin API, 결과 본문은 쓰지 않는다).
     * 부르는 곳: AuthService.resetPassword(비밀번호 재설정), AuthService.signup(이 번호의 Supabase 사용자가 이미 남아 있어 재사용할 때)
     */
    public void updatePassword(UUID userId, String password) {
        try {
            admin().put().uri("/admin/users/{id}", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("password", password))
                    .retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            throw upstream("비밀번호 변경", e);
        } catch (ResourceAccessException e) {
            throw unreachable(e); // 연결 거부·타임아웃·DNS — 500 INTERNAL 이 아니라 UPSTREAM
        }
    }

    /**
     * Supabase 사용자 삭제. 이미 없으면(404) 오류 없이 끝난다 — 탈퇴 직후 AuthService 가 한 번 지우고
     * 아웃박스 핸들러(SupabaseUserDeletionHandler)가 다시 부를 수 있어서, 두 번째 호출이 실패로 남으면 안 된다.
     * 가입 도중 실패했을 때 새로 만든 사용자를 되돌리는 데도 쓴다(AuthService.signup)
     */
    public void deleteUser(UUID userId) {
        try {
            admin().delete().uri("/admin/users/{id}", userId).retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) return; // 이미 없음
            throw upstream("사용자 삭제", e);
        } catch (ResourceAccessException e) {
            throw unreachable(e); // 연결 거부·타임아웃·DNS — 500 INTERNAL 이 아니라 UPSTREAM
        }
    }

    // 로그인만이 아니라 비밀번호 확인에도 쓴다 (예: AuthService 의 로그인, 탈퇴 전 비밀번호 확인 — 이때 생긴 세션은 부르는 쪽이 바로 끊는다)
    /** 휴대폰+비밀번호 로그인. 틀리면 LOGIN_FAILED (번호가 없는지 비밀번호가 틀린지 구분하지 않는다) */
    public Session passwordGrant(String phone, String password) {
        // 로그인·토큰 갱신은 admin() 을 쓰지 않고 apikey 헤더만 싣는다 (로그아웃은 사용자 access 토큰을 Authorization 에 싣는다)
        try {
            JsonNode body = client.post().uri("/token?grant_type=password")
                    .header("apikey", serviceRoleKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("phone", phone, "password", password))
                    .retrieve().body(JsonNode.class);
            return session(body);
        } catch (HttpStatusCodeException e) {
            if (isCredentialError(e)) throw new ApiException(ErrorCode.LOGIN_FAILED);
            if (e.getStatusCode().value() == 429) throw new ApiException(ErrorCode.RATE_LIMITED, "로그인 시도가 너무 많아요. 잠시 후 다시 시도해주세요");
            throw upstream("로그인", e); // 422·설정 오류 등은 '비밀번호 오류' 가 아니다
        } catch (ResourceAccessException e) {
            throw unreachable(e); // 연결 거부·타임아웃·DNS — 500 INTERNAL 이 아니라 UPSTREAM
        }
    }

    // 부르는 곳: AuthService.refresh — 받은 세션이 우리가 끊은 세션인지는 그쪽이 sessionId() 로 한 번 더 확인한다
    /** refresh 토큰으로 새 세션. 만료·폐기된 토큰이면 UNAUTHENTICATED (앱은 로그아웃 처리) */
    public Session refresh(String refreshToken) {
        try {
            JsonNode body = client.post().uri("/token?grant_type=refresh_token")
                    .header("apikey", serviceRoleKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("refresh_token", refreshToken))
                    .retrieve().body(JsonNode.class);
            return session(body);
        } catch (HttpStatusCodeException e) {
            if (isCredentialError(e)) throw new ApiException(ErrorCode.UNAUTHENTICATED);
            if (e.getStatusCode().value() == 429) throw new ApiException(ErrorCode.RATE_LIMITED);
            throw upstream("토큰 갱신", e); // 앱은 401 일 때만 로그아웃한다 — 429·502 로 세션을 버리면 안 된다
        } catch (ResourceAccessException e) {
            throw unreachable(e); // 연결 거부·타임아웃·DNS — 500 INTERNAL 이 아니라 UPSTREAM
        }
    }

    /** 그 access 토큰의 세션(refresh 토큰) 폐기. 실패해도 앱은 토큰을 지우므로 로그만 남긴다 */
    public void logout(String accessToken) {
        try {
            // scope=local: 이 access 토큰이 속한 세션 하나만 끊는다
            client.post().uri("/logout?scope=local")
                    .header("apikey", serviceRoleKey)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            log.warn("Supabase logout 실패 {}: {}", e.getStatusCode(), errorCode(e));
        } catch (ResourceAccessException e) {
            log.warn("Supabase logout 연결 실패: {}", e.getMessage()); // 앱은 어차피 토큰을 지운다
        }
    }

    /** 그 access 토큰을 **뺀** 나머지 세션 전부 폐기. 비밀번호 재설정 뒤 다른 기기의 로그인을 끊는다 */
    public void logoutOtherSessions(String accessToken) {
        // 실패해도 예외를 던지지 않는다 — 부르는 쪽(AuthService.resetPassword)은 우리 서버의 끊은 세션 목록으로 옛 세션을 먼저 막아 둔다
        try {
            // scope=others: 이 토큰의 세션만 남기고 같은 사용자의 나머지 세션을 끊는다
            client.post().uri("/logout?scope=others")
                    .header("apikey", serviceRoleKey)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            log.warn("Supabase 다른 세션 로그아웃 실패 {}: {}", e.getStatusCode(), errorCode(e));
        } catch (ResourceAccessException e) {
            log.warn("Supabase 다른 세션 로그아웃 연결 실패: {}", e.getMessage());
        }
    }

    /** 400·401·403 만 '자격 증명 문제' 로 본다. 429(한도)·422(형식)·기타는 우리 쪽·Supabase 쪽 문제라 UPSTREAM/한도로 구분한다 */
    private static boolean isCredentialError(HttpStatusCodeException e) {
        int s = e.getStatusCode().value();
        return s == 400 || s == 401 || s == 403;
    }

    /** admin 호출은 apikey + Bearer 둘 다 service_role */
    private RestClient admin() {
        // mutate(): 기존 client 설정(기본 주소)을 복사해 기본 헤더만 더한 새 RestClient 를 만든다
        return client.mutate().defaultHeader("apikey", serviceRoleKey).defaultHeader("Authorization", "Bearer " + serviceRoleKey).build();
    }

    /** Supabase 토큰 응답 JSON(access_token·refresh_token·expires_in·user.id)을 Session 으로 옮긴다 */
    private Session session(JsonNode body) {
        return new Session(body.get("access_token").asString(), body.get("refresh_token").asString(), body.get("expires_in").asLong(),
                UUID.fromString(body.get("user").get("id").asString()));
    }

    /** 오류 응답 본문의 error_code 값 (분기·로그용). 본문이 JSON 이 아니거나 값이 없으면 빈 문자열 */
    private String errorCode(HttpStatusCodeException e) {
        try {
            JsonNode n = json.readTree(e.getResponseBodyAsString());
            JsonNode code = n.get("error_code");
            return code == null ? "" : code.asString();
        } catch (Exception ignored) {
            return "";
        }
    }

    /** 연결 실패(거부·타임아웃·DNS)를 로그로 남기고 UPSTREAM 예외를 만든다. 던지는 것은 부르는 쪽(throw unreachable(e)) */
    private ApiException unreachable(ResourceAccessException e) {
        log.error("Supabase Auth 연결 실패: {}", e.getMessage());
        return new ApiException(ErrorCode.UPSTREAM);
    }

    /** Supabase 가 오류 상태로 답한 경우. what(무슨 호출인지)·상태·error_code 만 로그에 남기고 UPSTREAM 예외를 만든다 */
    private ApiException upstream(String what, HttpStatusCodeException e) {
        HttpStatusCode status = e.getStatusCode();
        log.error("Supabase Auth {} 실패 {} {}", what, status, errorCode(e)); // 본문 전체는 남기지 않는다(개인정보)
        return new ApiException(ErrorCode.UPSTREAM);
    }
}
