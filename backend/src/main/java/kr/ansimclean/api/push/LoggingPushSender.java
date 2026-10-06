package kr.ansimclean.api.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

// 푸시를 보내지 않고 로그 한 줄만 남기는 PushSender 구현. app.push.mode 가 logging 일 때만 빈이 된다.
// application-local.yml 은 PUSH_MODE 가 없으면 logging, application-test.yml 은 logging 으로 고정한다.
/** 로컬·테스트: 보내지 않고 로그. 기본값 없음 — PUSH_MODE 를 빠뜨린 운영 배포가 조용히 로그로만 돌지 않게(SmsSender 와 같은 규칙) */
@Component
@ConditionalOnProperty(name = "app.push.mode", havingValue = "logging")
public class LoggingPushSender implements PushSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    // 빈이 만들어질 때 경고를 한 번 남긴다 — 이 모드로 떴다는 것을 기동 로그에서 알 수 있게
    public LoggingPushSender() {
        log.warn("PUSH_MODE=logging → 푸시가 기기로 나가지 않고 로그에만 남습니다 (운영은 fcm)");
    }

    // 보내는 대신 로그. 토큰은 끝 6글자만 남긴다(전체 토큰을 로그에 남기지 않는다). 항상 SENT 라 재시도·토큰 삭제가 일어나지 않는다
    @Override
    public Result send(String token, String title, String body, Map<String, String> data, String collapseKey) {
        log.info("[푸시·발송 안 함] token=…{} {} / {} {}", token.length() > 6 ? token.substring(token.length() - 6) : "", title, body, data);
        return Result.SENT;
    }
}
