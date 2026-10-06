package kr.ansimclean.api.common.sms;

/**
 * 인증번호 문자 발송. 업체를 바꾸면 구현체 하나만 추가하고 application.yml 의 app.sms.provider 를 바꾼다.
 * 보낼 수 없으면 SmsException 을 던진다 (OtpService 가 UPSTREAM 으로 바꾼다).
 * 구현: LoggingSmsSender(provider=logging), SolapiSmsSender(provider=solapi). 부르는 곳: auth 패키지의 OtpService.request.
 * 아웃박스를 거치지 않는다 — OtpService 가 트랜잭션 밖에서 바로 부르고, 실패하면 그 요청에 UPSTREAM 으로 답한다(자동 재시도 없음).
 * 주의: 운영 기동 가드(ProductionGuard)는 provider=solapi 만 통과시킨다 — 업체를 바꾸면 그 검사도 같이 고친다.
 */
public interface SmsSender {
    /** @param e164 "+821012345678" · @param text 본문 (발신번호는 구현체가 설정에서 가져온다) */
    void send(String e164, String text);

    // 발송 실패 (RuntimeException). OtpService 가 잡아 발송 기록을 지우고 UPSTREAM 으로 바꾼다 — 기록을 남기면 재발송 한도에 헛되이 잡힌다
    class SmsException extends RuntimeException {
        public SmsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
