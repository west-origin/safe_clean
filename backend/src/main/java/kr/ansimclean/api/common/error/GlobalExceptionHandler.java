package kr.ansimclean.api.common.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import kr.ansimclean.api.common.web.RequestLogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// 컨트롤러·서비스에서 던져진 예외를 한곳에서 받아 같은 모양의 JSON 오류 응답으로 바꾼다.
// @RestControllerAdvice: 모든 컨트롤러에 공통으로 붙는 예외 처리기. @ExceptionHandler(X.class): X 예외가 나면 그 메서드가 응답을 만든다.
// 스프링은 예외 타입에 가장 가까운 처리기를 고르고, 어디에도 안 맞으면 Exception 처리기(internal, 500)로 간다.
// 필터 단계에서 거부된 요청(토큰 없음·권한 없음·관리자 토큰 틀림)은 컨트롤러까지 오지 않으므로 여기가 아니라
// 그 필터가 같은 모양으로 직접 쓴다 (예: SecurityConfig, AdminTokenFilter).
/** 예외 → { error: { code, message } }. 스택트레이스·SQL 은 절대 응답에 나가지 않는다 (CLAUDE.md §11-3). */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 서비스가 일부러 던진 오류: ErrorCode 의 HTTP 상태 + 예외에 담긴 한국어 문구 그대로.
     * 예외: INTERNAL(500, 예: 가격표·app_settings 값 없음)은 아래 internal()(예상하지 못한 예외)과 같은 본문 — 일반 문구 + '(문의 번호 …)' + requestId.
     * 사용자가 화면의 문의 번호를 알려 주면 운영자가 그 번호(rid)로 로그를 찾는다. 예전에는 번호가 없어 시각·경로로 찾아야 했다 (문제 목록 30번)
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> api(ApiException e, HttpServletRequest request) {
        // INTERNAL(500)·UPSTREAM(502) 은 응답에 원인이 없으므로 서버 로그에 남긴다(설정 누락·외부 장애를 여기서 본다). 나머지는 업무 결과라 로그 없음
        // (같은 502 인 LIVE_PAUSE_UNCONFIRMED 도 여기서는 남기지 않는다 — 요청 로그에 상태만 남는다)
        if (e.code() == ErrorCode.INTERNAL) {
            String rid = RequestLogFilter.currentId(request);
            log.error("INTERNAL rid={}: {}", rid, e.getMessage(), e); // 서비스가 적은 문구는 로그에만 — 응답은 일반 문구
            return internalResponse(rid);
        }
        if (e.code() == ErrorCode.UPSTREAM) log.warn("UPSTREAM: {}", e.getMessage());
        return ResponseEntity.status(e.code().status()).body(ErrorResponse.of(e.code(), e.getMessage()));
    }

    /**
     * 요청 본문(@Valid 가 붙은 DTO)의 검증 실패 → 400. 틀린 필드가 여럿이어도 첫 번째 문구 하나만 보여 준다.
     * 필드 오류가 없으면(객체 전체 검사만 실패) 기본 문구를 쓴다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
        var first = e.getBindingResult().getFieldErrors().stream().findFirst();
        return validation(first.map(f -> displayable(f.getDefaultMessage())).orElse(ErrorCode.VALIDATION.defaultMessage()));
    }

    /** 컨테이너 요소 검증(List<@Valid …>)·메서드 파라미터 검증 실패 (Spring 의 내장 메서드 검증) */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> invalidMethodArgs(HandlerMethodValidationException e) {
        var first = e.getAllErrors().stream().findFirst();
        return validation(first.map(err -> displayable(err.getDefaultMessage())).orElse(ErrorCode.VALIDATION.defaultMessage()));
    }

    /** 그 밖의 Bean Validation 위반(jakarta.validation 이 직접 던진 경우) → 400, 첫 번째 문구 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> invalidParam(ConstraintViolationException e) {
        var first = e.getConstraintViolations().stream().findFirst();
        return validation(first.map(v -> displayable(v.getMessage())).orElse(ErrorCode.VALIDATION.defaultMessage()));
    }

    /**
     * 본문 JSON 을 읽을 수 없음·필수 쿼리 파라미터나 헤더 없음·타입이 안 맞음(예: UUID 자리에 다른 글자) → 400.
     * 스프링의 영어 원문 대신 한 가지 한국어 문구로 답한다.
     */
    @ExceptionHandler({ HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class })
    public ResponseEntity<ErrorResponse> unreadable(Exception e) {
        return validation("요청 형식이 올바르지 않아요");
    }

    /** 사진 업로드(multipart) 오류 → 400: 파일이 너무 큼 / 파일 칸이 없음. 5MB 는 application.yml 의 max-file-size 와 같은 값이다 */
    @ExceptionHandler({ org.springframework.web.multipart.MaxUploadSizeExceededException.class, org.springframework.web.multipart.support.MissingServletRequestPartException.class })
    public ResponseEntity<ErrorResponse> upload(Exception e) {
        String message = e instanceof org.springframework.web.multipart.MaxUploadSizeExceededException ? "사진은 5MB 이하로 올려주세요" : "사진 파일을 첨부해주세요";
        return ResponseEntity.status(ErrorCode.VALIDATION.status()).body(ErrorResponse.of(ErrorCode.VALIDATION, message));
    }

    /** 경로는 있는데 그 HTTP 메서드(GET·POST …)는 없음 → 405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.status()).body(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /** 본문 형식(Content-Type)을 받을 수 없거나, 요청이 원한 응답 형식(Accept)을 줄 수 없음 → 415 */
    @ExceptionHandler({ HttpMediaTypeNotSupportedException.class, HttpMediaTypeNotAcceptableException.class })
    public ResponseEntity<ErrorResponse> mediaType(Exception e) {
        return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.status()).body(ErrorResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    /** UNIQUE·FK 위반 — 같은 요청이 동시에 두 번 들어온 경우가 대부분. 스택(전화번호 등 DETAIL 포함)은 응답에도 로그에도 남기지 않는다 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException e) {
        log.warn("DB 제약 위반 → 409: {}", e.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.CONFLICT.status()).body(ErrorResponse.of(ErrorCode.CONFLICT));
    }

    /** 없는 경로 → 404 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> noResource(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status()).body(ErrorResponse.of(ErrorCode.NOT_FOUND));
    }

    // 아래 두 개는 보안 예외가 컨트롤러 안쪽에서 던져졌을 때의 처리다.
    // 경로 권한(SecurityConfig)에서 걸린 요청은 필터 단계에서 SecurityConfig 의 entryPoint·deniedHandler 가 응답한다
    /** 권한 없음 → 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status()).body(ErrorResponse.of(ErrorCode.FORBIDDEN));
    }

    /** 로그인 필요 → 401 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> unauthenticated(AuthenticationException e) {
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.status()).body(ErrorResponse.of(ErrorCode.UNAUTHENTICATED));
    }

    /**
     * 위 어디에도 맞지 않는 예외(예상하지 못한 버그) → 500. 원인은 서버 로그에만 남기고, 응답에는 일반 문구와 요청 번호만 준다.
     * 요청 번호(rid)는 RequestLogFilter 가 요청마다 붙인 값 — 고객이 "문의 번호" 를 알려 주면 그 번호로 로그를 찾는다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internal(Exception e, HttpServletRequest request) {
        String rid = RequestLogFilter.currentId(request);
        log.error("처리되지 않은 예외 rid={}", rid, e); // 원인은 로그에만 — 응답의 requestId 로 찾는다
        return internalResponse(rid);
    }

    /** 500 INTERNAL 본문 (위 api() 의 INTERNAL 과 internal() 이 같이 쓴다). rid 가 없으면(필터를 거치지 않은 요청) 번호 없이 일반 문구만 */
    private static ResponseEntity<ErrorResponse> internalResponse(String rid) {
        String message = ErrorCode.INTERNAL.defaultMessage() + (rid == null ? "" : " (문의 번호 " + rid + ")");
        return ResponseEntity.status(ErrorCode.INTERNAL.status()).body(ErrorResponse.of(ErrorCode.INTERNAL, message, rid));
    }

    /** 검증 문구는 화면에 그대로 보여도 되는 한국어여야 한다(§11-3). 애노테이션에 message 를 안 적어 기본 로케일 문구가 오면 일반 문구로 바꾼다 */
    private static String displayable(String message) {
        if (message == null) return ErrorCode.VALIDATION.defaultMessage();
        // 0xAC00~0xD7A3 = 한글 음절(가~힣) 범위. 한 글자라도 있으면 우리가 적은 한국어 문구로 본다
        return message.codePoints().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3) ? message : ErrorCode.VALIDATION.defaultMessage();
    }

    /** 400 VALIDATION 응답을 만든다 (위 검증 처리기들이 같이 쓴다) */
    private static ResponseEntity<ErrorResponse> validation(String message) {
        return ResponseEntity.status(ErrorCode.VALIDATION.status()).body(ErrorResponse.of(ErrorCode.VALIDATION, message));
    }
}
