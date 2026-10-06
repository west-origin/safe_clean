package kr.ansimclean.api.common.sms;

import kr.ansimclean.api.common.format.KoreanFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 국내 문자 API (Solapi). 인증은 HMAC-SHA256 서명 헤더:
 *   Authorization: HMAC-SHA256 apiKey=…, date=…, salt=…, signature=HMAC(date+salt, apiSecret)
 * 다른 업체(알리고·NHN·NCP)로 바꾸려면 이 클래스만 복사해 고치고 app.sms.provider 를 바꾼다.
 * 켜지는 조건: app.sms.provider=solapi (환경변수 SMS_PROVIDER). RestClient 는 스프링의 HTTP 클라이언트다.
 * 업체를 바꿀 때는 ProductionGuard 의 provider=solapi 검사도 고친다 — 그대로 두면 운영 기동이 멈춘다.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "solapi")
public class SolapiSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(SolapiSmsSender.class);
    private static final String ENDPOINT = "https://api.solapi.com/messages/v4/send"; // 문자 발송 API 주소
    private static final SecureRandom RANDOM = new SecureRandom(); // 서명용 salt 를 만드는 예측하기 어려운 난수

    private final SmsProperties props;
    private final RestClient client;

    /** 키·비밀키·발신번호가 하나라도 비어 있으면 기동을 멈춘다 */
    public SolapiSmsSender(SmsProperties props, RestClient.Builder builder) {
        // FcmPushSender 처럼 기동 때 막는다 — SMS_PROVIDER=solapi 인데 키가 비면 첫 가입자가 UPSTREAM 을 보고서야 알았다 (검증 검토)
        if (isBlank(props.apiKey()) || isBlank(props.apiSecret()) || isBlank(props.sender())) {
            throw new IllegalStateException("SMS_PROVIDER=solapi 인데 SMS_API_KEY·SMS_API_SECRET·SMS_SENDER 가 비어 있어요");
        }
        this.props = props;
        this.client = builder.build();
    }

    // null 이거나 공백뿐인가
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Solapi 에 문자 한 건을 보낸다. 어떤 실패든 SmsException 으로 바꿔 던진다 (예외 메시지에는 가린 번호만 넣는다) */
    @Override
    public void send(String e164, String text) {
        try {
            client.post()
                    .uri(ENDPOINT)
                    .header("Authorization", authorization())
                    .contentType(MediaType.APPLICATION_JSON)
                    // 국내 발송은 0 으로 시작하는 국내 형식을 쓴다
                    .body(Map.of("message", Map.of("to", localNumber(e164), "from", props.sender(), "text", text)))
                    // retrieve: 응답이 4xx·5xx 면 예외를 던진다. 응답 본문은 읽지 않는다(toBodilessEntity)
                    .retrieve()
                    .toBodilessEntity();
            log.info("인증번호 문자 발송 {}", KoreanFormat.maskPhone(e164));
        } catch (Exception e) {
            throw new SmsException("문자 발송 실패 " + KoreanFormat.maskPhone(e164), e);
        }
    }

    /** "+821012345678" → "01012345678" */
    // 앞의 "+82" 세 글자를 떼고 0 을 붙인다. 들어오는 번호는 OtpService.toE164 가 만든 +8210… 형식이다
    static String localNumber(String e164) {
        return "0" + e164.substring(3);
    }

    /**
     * Solapi 인증 헤더 값을 만든다. date = 지금 시각(ISO-8601, UTC), salt = 부를 때마다 새 16바이트 난수(hex),
     * signature = date+salt 를 apiSecret 으로 HMAC 한 값. 요청마다 새로 만든다.
     */
    private String authorization() {
        String date = Instant.now().toString();
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        return "HMAC-SHA256 apiKey=" + props.apiKey() + ", date=" + date + ", salt=" + salt + ", signature=" + sign(date + salt, props.apiSecret());
    }

    /** HMAC-SHA256(message, secret) 결과를 hex 문자열로 돌려준다. 실패하면 SmsException */
    static String sign(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new SmsException("서명 생성 실패", e);
        }
    }

    static {
        // MessageDigest 를 쓰지 않지만, 잘못된 JDK 설정을 기동 시점에 드러내기 위한 자리 (아무 동작 없음)
        try {
            MessageDigest.getInstance("SHA-256");
        } catch (Exception ignored) {
            // 이 JDK 에는 SHA-256 이 없다 — Mac 초기화에서 다시 드러난다
        }
    }
}
