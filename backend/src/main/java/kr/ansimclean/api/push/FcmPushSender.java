package kr.ansimclean.api.push;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

// 푸시를 실제로 휴대폰에 보내는 PushSender 구현 (Firebase Cloud Messaging, 줄여서 FCM).
// PushService.send 가 사용자의 기기 토큰마다 send 를 부르고, 돌려준 결과로 토큰을 지우거나 실패 수를 센다.
// @ConditionalOnProperty: 설정 app.push.mode 가 fcm 일 때만 스프링이 이 클래스를 만든다 (logging 이면 LoggingPushSender).
/** Firebase Cloud Messaging (firebase-admin). 서비스 계정 JSON 은 base64 환경변수 — 파일을 이미지에 넣지 않는다 */
@Component
@ConditionalOnProperty(name = "app.push.mode", havingValue = "fcm")
public class FcmPushSender implements PushSender {
    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private final FirebaseMessaging messaging; // Firebase 발송 클라이언트. 생성할 때 한 번 만들고 계속 쓴다

    // 스프링이 쓰는 생성자: 설정(PushProperties)의 서비스 계정으로 Firebase 클라이언트를 만든다.
    // 값이 비었거나 읽을 수 없으면 IllegalStateException 이 나서 서버 기동이 멈춘다 (아래 messaging 메서드)
    @Autowired // 생성자가 둘(테스트용 FirebaseMessaging 주입)이라 Spring 에 어느 것인지 알려야 한다 — 없으면 기동 실패
    public FcmPushSender(PushProperties props) {
        this(messaging(props));
    }

    // 테스트용 생성자 — FcmPushSenderTest 가 가짜(mock) FirebaseMessaging 을 넣어 실제로 보내지 않고 결과 처리만 확인한다
    FcmPushSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    /**
     * base64 한 줄로 받은 서비스 계정 JSON 을 풀어 Firebase 앱을 만들고, 그 앱의 발송 클라이언트를 돌려준다.
     * 실패하면 IllegalStateException (base64 가 아니면 IllegalArgumentException, JSON 이 잘못되면 IOException 을 감싼다).
     */
    private static FirebaseMessaging messaging(PushProperties props) {
        if (props.firebaseServiceAccountJson() == null || props.firebaseServiceAccountJson().isBlank()) {
            throw new IllegalStateException("PUSH_MODE=fcm 인데 FIREBASE_SERVICE_ACCOUNT_JSON 이 비어 있어요");
        }
        try {
            // strip: 환경변수 앞뒤에 붙은 공백·줄바꿈을 떼고 base64 를 푼다
            byte[] json = Base64.getDecoder().decode(props.firebaseServiceAccountJson().strip());
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json));
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    // 서비스 계정의 project_id — 명시하지 않으면 FCM URL 이 projects/null 이 된다
                    .setProjectId(credentials instanceof ServiceAccountCredentials sa ? sa.getProjectId() : null)
                    // 기본 Apache 전송은 오류 본문을 gzip 으로 두 번 풀려다 실패해(ZipException) MessagingErrorCode 가 항상 null 이 된다 →
                    // 무효 토큰(UNREGISTERED)이 '일시 실패' 로 보여 아웃박스가 시도 한도(OutboxEvent.MAX_ATTEMPTS)까지 헛되이 다시 보낸다. JDK 전송은 한 번만 푼다 (2026-09-21 실측)
                    .setHttpTransport(new NetHttpTransport())
                    .build();
            // Firebase 앱은 같은 이름(기본 이름)으로 두 번 만들 수 없다 — 이 JVM 에 이미 있으면 그것을 다시 쓴다
            FirebaseApp app = FirebaseApp.getApps().isEmpty() ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance();
            return FirebaseMessaging.getInstance(app);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("FIREBASE_SERVICE_ACCOUNT_JSON 을 읽을 수 없어요 (base64 한 줄인지 확인)", e);
        }
    }

    /**
     * 기기 토큰 하나로 알림 한 건을 보낸다. 결과: 보냄 SENT, 다시 보내도 안 될 토큰 INVALID_TOKEN(PushService 가 지운다),
     * 그 밖의 실패 FAILED(PushHandler 가 던져 아웃박스가 나중에 다시 보낸다).
     */
    @Override
    public Result send(String token, String title, String body, Map<String, String> data, String collapseKey) {
        // 메시지 = 받을 기기 토큰 + 알림(제목·본문) + data + 안드로이드용 설정 + iOS(APNs)용 설정
        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                // data: 두 앱의 push/pushClient.js 가 data.type 으로 알림을 탭했을 때 갈 화면을 고른다
                .putAllData(data)
                // 안드로이드: 아직 기기에 전달되지 않은 메시지는 같은 collapseKey 의 새 메시지로 바뀐다(꺼져 있던 기기에 한 번만). HIGH = 바로 전달하는 우선순위
                .setAndroidConfig(AndroidConfig.builder().setCollapseKey(collapseKey).setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder().setTag(collapseKey).build()) // 같은 이벤트의 재전송이 트레이에 두 번 쌓이지 않게 (기기가 켜져 있어도)
                        .build())
                // iOS: apns-collapse-id 가 같은 알림은 하나로 바뀌어 보인다. sound default = 기본 알림음
                .setApnsConfig(ApnsConfig.builder().putHeader("apns-collapse-id", collapseKey).setAps(Aps.builder().setSound("default").build()).build())
                .build();
        try {
            messaging.send(message);
            return Result.SENT;
        } catch (FirebaseMessagingException e) {
            // FCM 이 돌려준 오류 코드로 '토큰 문제(지운다)' 와 '일시 실패(다시 보낸다)' 를 가른다
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
                return Result.INVALID_TOKEN; // 앱 삭제·토큰 만료·다른 Firebase 프로젝트의 토큰 — 다시 보내도 같으니 지운다
            }
            log.warn("FCM 발송 실패 {}: {}", code, e.getMessage()); // 토큰·본문은 남기지 않는다
            return Result.FAILED;
        }
    }
}
