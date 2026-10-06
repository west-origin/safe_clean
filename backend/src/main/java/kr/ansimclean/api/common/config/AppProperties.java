package kr.ansimclean.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// 서버 설정값 묶음. ApiApplication 의 @EnableConfigurationProperties 가 등록하고, 필요한 빈이 생성자로 받아 쓴다
// (예: CorsConfig·LiveKitCloudGateway).
// @ConfigurationProperties(prefix = "app"): yml 의 app.cors·app.supabase … 를 같은 이름의 필드에 채운다
// (yml 의 kebab-case 이름 api-key 가 자바의 apiKey 로 이어진다).
// 그 밖의 app.* 는 여기 없고 따로 읽는다 (예: app.sms 는 SmsProperties, app.otp.salt 는 OtpService 의 @Value).
/** application.yml 의 app.* (값은 환경변수에서 온다 — BACKEND_PLAN.md §12) */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Supabase supabase, Livekit livekit, Storage storage, Admin admin) {
    // mode 는 어느 게이트웨이 빈을 쓸지도 정한다: cloud → LiveKitCloudGateway, logging(또는 설정 자체가 없음) → LoggingLiveKitGateway.
    // 빈 선택은 두 클래스의 @ConditionalOnProperty 가 설정 원문으로 한다 — 아래 간결 생성자의 기본값은 빈 선택에 쓰이지 않는다.
    // muteConfirm*: mute·unmute 명령 뒤 트랙 상태가 정말 바뀌었는지 LiveService.setMuted 가 몇 번·몇 ms 간격으로 다시 읽는지
    // (예: 일시정지·재개). 확인하는 동안 부른 쪽이 세션 행 잠금을 쥐고 있으므로, 늘리면 그만큼 요청과 잠금이 길어진다
    /** mode: logging = LiveKit 없이(카메라가 붙지 않는 대역, 로컬·테스트) | cloud = LiveKit Cloud. url 은 wss://… (서버 API 는 https 로 바꿔 부른다) */
    public record Livekit(String mode, String url, String apiKey, String apiSecret, Integer muteConfirmAttempts, Integer muteConfirmSleepMs) {
        // 간결 생성자(compact constructor): 값이 채워질 때 먼저 돈다. 비어 있는 값에 기본값을 넣는다.
        // application.yml 에도 6·500 이 적혀 있다 — 그 줄이 빠진 설정에서도 같은 값이 된다
        public Livekit {
            if (mode == null || mode.isBlank()) mode = "logging";
            if (muteConfirmAttempts == null) muteConfirmAttempts = 6;
            if (muteConfirmSleepMs == null) muteConfirmSleepMs = 500;
        }

        // LiveKitCloudGateway 가 서버 API 주소로 쓴다. 앱에 내려주는 접속 주소(LiveService 의 토큰 응답)는 url(wss://) 그대로다
        /** wss://x.livekit.cloud → https://x.livekit.cloud (서버 SDK 는 HTTP 호스트를 받는다) */
        public String httpUrl() {
            if (url == null) return ""; // url 이 설정되지 않아 null 이면 예외 대신 빈 문자열
            return url.replaceFirst("^wss://", "https://").replaceFirst("^ws://", "http://");
        }
    }

    // s3* 는 LiveKitCloudGateway 가 녹화 업로드 설정으로 넘긴다. recordingsBucket 은 그 업로드 대상이자
    // 녹화본 주소 발급·삭제에 쓰는 버킷이다 (예: LiveService·RecordingRetention)
    /** Supabase Storage 의 S3 호환 자격증명 (LiveKit Egress 가 녹화본을 올린다, §8.2) + 버킷 이름 */
    public record Storage(String s3Endpoint, String s3AccessKey, String s3SecretKey, String s3Region, String recordingsBucket) {
        // 버킷 이름이 비면 "recordings" 로 둔다
        public Storage {
            if (recordingsBucket == null || recordingsBucket.isBlank()) recordingsBucket = "recordings";
        }
    }

    /** 관리자 전용 엔드포인트(/admin/**)의 토큰 (§13-20). 비어 있으면 관리자 API 가 닫힌다 */
    public record Admin(String token) {} // AdminAuth 가 X-Admin-Token 헤더와 비교한다

    /** 브라우저 앱이 이 서버를 부를 수 있는 출처(origin) 목록. CorsConfig 가 쓰고 ProductionGuard 가 운영 값인지 검사한다 */
    public record Cors(List<String> allowedOrigins) {
        // ALLOWED_ORIGINS(쉼표로 나눈 값)의 앞뒤 공백을 지우고 빈 항목을 버린다. 값이 없으면 빈 목록 = 어떤 origin 도 허용하지 않음
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    // url 은 Supabase 프로젝트 주소. serviceRoleKey 는 서버에서만 쓰는 Supabase service role 키로
    // Supabase 호출 클라이언트(예: SupabaseStorageClient)가 요청 헤더에 싣는다
    /** jwtMode: jwks(비대칭, JWKS URL 로 검증) | secret(HS256, jwtSecret 으로 검증) */
    public record Supabase(String url, String serviceRoleKey, String jwtMode, String jwtSecret) {
        /** 로그인 토큰(JWT)의 발급자 값. SupabaseJwtDecoderConfig 가 토큰의 iss 가 이 값인지 검사한다 */
        public String issuer() {
            return url + "/auth/v1";
        }

        /** jwks 모드에서 토큰 서명을 확인할 공개키 목록 주소 (SupabaseJwtDecoderConfig) */
        public String jwksUri() {
            return url + "/auth/v1/.well-known/jwks.json";
        }
    }
}
