package kr.ansimclean.api.common.sms;

import kr.ansimclean.api.common.format.KoreanFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬·테스트용. 문자를 보내지 않고 콘솔에 남긴다.
 * 본문에는 인증번호가 들어 있으므로 운영(prod)에서는 절대 쓰지 않는다 — app.sms.provider=solapi.
 * 켜지는 조건: app.sms.provider=logging (application-local.yml·application-test.yml 이 이 값을 쓴다).
 * 조건부 빈(@ConditionalOnProperty): 설정 값이 맞을 때만 이 빈을 만든다 — SmsSender 구현은 provider 값에 따라 하나만 생기고,
 * provider 가 비면 하나도 없어 기동이 실패한다 (application.yml). 운영에서 logging 이면 ProductionGuard 가 기동을 막는다.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "logging") // 기본값 없음 — SMS_PROVIDER 를 빠뜨린 운영 배포가 조용히 로그 발송으로 돌지 않게
public class LoggingSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    /** 기동할 때 경고 로그 한 줄로 로그 발송 모드임을 알린다 */
    public LoggingSmsSender() {
        log.warn("SMS_PROVIDER=logging → 인증번호가 문자로 나가지 않고 로그에만 남습니다 (운영은 solapi)");
    }

    /** 보내지 않고 로그에 남긴다. 번호는 가리고(KoreanFormat.maskPhone) 본문(인증번호 포함)은 그대로 남긴다 */
    @Override
    public void send(String e164, String text) {
        log.info("[문자·발송 안 함] {} → {}", KoreanFormat.maskPhone(e164), text);
    }
}
