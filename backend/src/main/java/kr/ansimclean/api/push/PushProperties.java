package kr.ansimclean.api.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.push.* — mode: logging(로컬·테스트, 보내지 않고 로그) | fcm(Firebase Cloud Messaging).
 * firebaseServiceAccountJson 은 서비스 계정 JSON 을 base64 한 줄로 (환경변수 FIREBASE_SERVICE_ACCOUNT_JSON).
 * application.yml 의 app.push.* 값을 담는다. ApiApplication 의 @EnableConfigurationProperties 가 등록한다.
 * 쓰는 곳 예: FcmPushSender(서비스 계정). mode 는 두 PushSender 구현의 @ConditionalOnProperty 가 설정에서 직접 읽는다.
 * application.yml 은 PUSH_MODE 에 기본값을 두지 않는다 — 비면 PushSender 빈이 하나도 없어 서버가 뜨지 않는다.
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(String mode, String firebaseServiceAccountJson) {}
