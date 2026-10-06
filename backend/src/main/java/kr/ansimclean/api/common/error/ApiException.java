package kr.ansimclean.api.common.error;

// 사용 예: throw new ApiException(ErrorCode.INVALID_STATE, "청소를 먼저 완료해주세요");
// → 응답 409 { "error": { "code": "INVALID_STATE", "message": "청소를 먼저 완료해주세요" } }.
// RuntimeException 이라 메서드에 throws 를 적지 않는다. 트랜잭션 안에서 던지면 기본은 그 묶음을 되돌리지만,
// noRollbackFor = ApiException.class 가 붙은 메서드(예: LiveService.pause, OtpService.verify)는 던져도
// 그때까지 바꾼 값을 저장한다 (CODE_GUIDE.md 3-3). 주의: 바깥 묶음에 합쳐진 안쪽 @Transactional 메서드가 noRollbackFor 없이
// 이 예외를 던지면 묶음 전체에 되돌림 표시가 남아 바깥 커밋이 500 이 된다 (BookingService.requireManagerBooking 의 설명).
/** 서비스·컨트롤러가 던지는 유일한 예외. GlobalExceptionHandler 가 ErrorResponse 로 바꾼다. */
public class ApiException extends RuntimeException {
    private final ErrorCode code;

    /** 문구는 ErrorCode 의 기본 문구를 쓴다 */
    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    /** 상황에 맞는 문구를 직접 준다. message 는 화면에 그대로 보이므로 한국어 안내문이어야 한다 */
    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /** HTTP 상태와 응답의 code 문자열을 정하는 오류 종류 */
    public ErrorCode code() {
        return code;
    }

    /** 404 "찾을 수 없어요". 남의 예약처럼 권한이 없는 리소스에도 이걸 써서 존재 여부를 드러내지 않는다 (CLAUDE.md §11-8) */
    public static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND);
    }

    /** 400 입력값 오류 + 무엇이 틀렸는지 알려 주는 문구 */
    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION, message);
    }
}
