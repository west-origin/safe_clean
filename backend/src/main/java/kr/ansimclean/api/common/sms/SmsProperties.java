package kr.ansimclean.api.common.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.sms.* (값은 환경변수 SMS_*). provider: logging = 콘솔 출력(로컬·테스트), solapi = 실제 발송.
 * sender 는 통신사에 사전등록한 발신번호.
 * yml 의 app.sms.api-key 같은 값이 같은 이름의 record 필드(apiKey)로 채워진다. ApiApplication 의
 * EnableConfigurationProperties 가 등록하고, SolapiSmsSender 가 키·발신번호를 읽는다.
 * provider 값은 LoggingSmsSender·SolapiSmsSender 의 ConditionalOnProperty 가 설정에서 직접 읽어 구현을 고른다.
 */
@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(String provider, String apiKey, String apiSecret, String sender) {}
