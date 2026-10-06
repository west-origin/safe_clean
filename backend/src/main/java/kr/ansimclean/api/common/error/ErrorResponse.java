package kr.ansimclean.api.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

// JSON 으로 바뀔 때 필드 이름(error, code, message, requestId)이 그대로 키가 된다 — 이름을 바꾸면 두 앱의 http.js 도 같이 고친다.
// 만드는 곳: GlobalExceptionHandler(컨트롤러까지 온 요청), 필터 단계에서 거부하는 곳(예: SecurityConfig 의 401·403, AdminTokenFilter).
/** 모든 오류 응답의 모양: { "error": { "code", "message", "requestId"? } } (BACKEND_PLAN.md §5.0). 앱 http.js 가 ApiError 로 바꾼다. requestId 는 INTERNAL(500)에만 — 처리되지 않은 예외든 서비스가 던진 ApiException(INTERNAL) 이든 같다. 문의 때 로그를 찾는 열쇠 */
public record ErrorResponse(Body error) {
    // @JsonInclude(NON_NULL): requestId 가 null 이면 JSON 에서 그 키를 아예 빼 { code, message } 만 나간다
    public record Body(String code, String message, @JsonInclude(JsonInclude.Include.NON_NULL) String requestId) {}

    /** 문구를 직접 준 오류 (requestId 없음) */
    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(new Body(code.name(), message, null));
    }

    /** INTERNAL(500)용 — 요청 번호를 같이 싣는다 (GlobalExceptionHandler.internalResponse) */
    public static ErrorResponse of(ErrorCode code, String message, String requestId) {
        return new ErrorResponse(new Body(code.name(), message, requestId));
    }

    /** ErrorCode 의 기본 문구를 쓰는 오류 */
    public static ErrorResponse of(ErrorCode code) {
        return of(code, code.defaultMessage());
    }
}
