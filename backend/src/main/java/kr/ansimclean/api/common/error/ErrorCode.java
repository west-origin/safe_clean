package kr.ansimclean.api.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드. HTTP 상태와 기본 문구는 여기서만 정한다 (BACKEND_PLAN.md §5.0).
 * message 는 화면에 그대로 보여도 되는 한국어다.
 * enum: 정해진 값 몇 개만 가질 수 있는 자바 타입. 각 상수가 (HTTP 상태, 기본 문구) 한 쌍을 갖는다.
 * 상수 이름이 응답 JSON 의 code 문자열로 그대로 나간다(ErrorResponse.of 의 code.name()).
 * 앱이 이 문자열로 분기하는 곳이 있으므로(예: OFFER_TAKEN, OTP_EXPIRED, LOGIN_FAILED) 이름을 바꾸면 앱도 같이 고쳐야 한다.
 * 응답으로 바꾸는 곳: 컨트롤러까지 온 요청은 GlobalExceptionHandler, 필터 단계에서 거부된 요청은 그 필터가 직접
 * (예: SecurityConfig 의 401·403, AdminTokenFilter 의 관리자 토큰 401).
 */
public enum ErrorCode {
    VALIDATION(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없어요"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요"),
    INVALID_STATE(HttpStatus.CONFLICT, "지금은 할 수 없는 동작이에요"),
    OFFER_EXPIRED(HttpStatus.CONFLICT, "이미 마감된 요청이에요"),
    OFFER_TAKEN(HttpStatus.CONFLICT, "다른 매니저가 먼저 수락했어요"),
    // 기한 초과 거절은 Booking.cancel·reschedule 이 설정된 기한 시간을 넣은 문구("방문 N시간 전까지만 …")로 던진다.
    // 아래 기본 문구는 그 문구 없이 던질 때만 쓰여 숫자를 넣지 않는다 — 기한(app_settings.cancel_deadline_hours)은 운영 중에 바뀔 수 있다 (문제 목록 25번)
    BOOKING_NOT_CANCELLABLE(HttpStatus.CONFLICT, "취소할 수 있는 기한이 지났어요"),
    BOOKING_NOT_CHANGEABLE(HttpStatus.CONFLICT, "변경할 수 있는 기한이 지났어요"),
    SLOT_UNAVAILABLE(HttpStatus.CONFLICT, "선택할 수 없는 날짜·시간이에요"),
    PHONE_TAKEN(HttpStatus.CONFLICT, "이미 가입한 번호예요. 로그인해서 이어서 진행해주세요"),
    PHONE_NOT_FOUND(HttpStatus.NOT_FOUND, "가입되지 않은 번호예요"),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "휴대폰 번호 또는 비밀번호를 확인해주세요"),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "인증번호가 맞지 않아요"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 만료됐어요. 다시 받아주세요"),
    OTP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요"), // 인증번호 요청·확인 한도
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아요. 잠시 후 다시 시도해주세요"), // 로그인·탈퇴·사진 업로드 등 그 밖의 한도 (검증 검토: 전부 OTP_ 이름이었다)
    PASSWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상이어야 해요"),
    PASSWORD_CHANGED(HttpStatus.CONFLICT, "비밀번호가 변경됐어요. 새 비밀번호로 로그인해주세요"), // 재설정은 됐는데 새 세션 발급만 실패 (코드에서 정함, 자체 점검 #22)
    LIVE_NOT_AVAILABLE(HttpStatus.CONFLICT, "지금은 실시간 영상을 볼 수 없어요"),
    LIVE_PAUSE_UNCONFIRMED(HttpStatus.BAD_GATEWAY, "촬영을 중단하지 못했어요. 다시 시도해주세요"), // 바디캠 mute·unmute 가 확인되지 않음 (LiveService, 재개 실패에도 쓴다)
    RECORDING_NOT_READY(HttpStatus.NOT_FOUND, "녹화 영상이 아직 준비되지 않았어요"),
    IN_PROGRESS(HttpStatus.CONFLICT, "같은 요청을 처리하고 있어요"), // 같은 멱등 키의 앞 요청이 아직 끝나지 않음 (Idempotent)
    IDEMPOTENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "이전 요청과 내용이 달라요"), // 같은 멱등 키인데 본문이 다름 (Idempotent)
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청이에요"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "요청 형식이 올바르지 않아요"),
    CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청이에요"), // DB 의 UNIQUE·FK 제약 위반 (GlobalExceptionHandler.conflict)
    UPSTREAM(HttpStatus.BAD_GATEWAY, "잠시 후 다시 시도해주세요"), // 외부 서비스(문자·Supabase·LiveKit 등) 호출 실패
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해주세요");

    private final HttpStatus status;
    private final String defaultMessage;

    /** 위 상수 목록의 괄호 안 값이 여기로 들어온다 */
    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    /** 응답 HTTP 상태 (400·401·404·409 …) */
    public HttpStatus status() {
        return status;
    }

    /** ApiException 에 문구를 따로 주지 않았을 때 쓰는 문구 */
    public String defaultMessage() {
        return defaultMessage;
    }
}
