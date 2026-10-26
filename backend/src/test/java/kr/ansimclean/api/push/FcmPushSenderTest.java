package kr.ansimclean.api.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** FCM 오류 코드 → 결과 매핑. 무효 토큰(UNREGISTERED·INVALID_ARGUMENT·SENDER_ID_MISMATCH)만 삭제 대상, 나머지는 재시도. Firebase 서버는 부르지 않는다 */
// FcmPushSender(Firebase 로 실제 푸시를 보내는 구현) 단위 테스트. 발송 클라이언트를 Mockito 가짜로 바꿔
// 테스트용 생성자로 넣는다 — 네트워크 없이 '보냄·토큰 삭제·다시 보내기' 결과 판정과 기동 시 설정 검사만 본다
class FcmPushSenderTest {
    FirebaseMessaging messaging = mock(FirebaseMessaging.class); // 가짜 Firebase 발송 클라이언트
    FcmPushSender sender = new FcmPushSender(messaging);

    @Test
    void 두_앱의_Android_iOS_알림에는_소리를_지정하지_않는다() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("sent");
        sender.send("token", "알림", "본문", Map.of("type", "call"), "call-1");
        var captured = org.mockito.ArgumentCaptor.forClass(Message.class);
        org.mockito.Mockito.verify(messaging).send(captured.capture());
        String wire = com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toString(captured.getValue());
        var json = com.google.gson.JsonParser.parseString(wire).getAsJsonObject();
        var android = json.getAsJsonObject("android").getAsJsonObject("notification");
        assertThat(android.get("channel_id").getAsString()).isEqualTo("ansimclean_silent_v1");
        assertThat(android.get("default_sound").getAsBoolean()).isFalse();
        assertThat(android.has("sound")).isFalse();
        assertThat(json.getAsJsonObject("apns").getAsJsonObject("payload").getAsJsonObject("aps").has("sound")).isFalse();
    }

    /** 가짜 FCM 오류 하나를 만든다 — getMessagingErrorCode 가 code 를, getMessage 가 "x" 를 돌려준다 (code 가 null 이면 코드를 못 읽은 오류) */
    static FirebaseMessagingException error(MessagingErrorCode code) {
        FirebaseMessagingException e = mock(FirebaseMessagingException.class);
        when(e.getMessagingErrorCode()).thenReturn(code);
        when(e.getMessage()).thenReturn("x");
        return e;
    }

    // 먼저 발송 성공(메시지 id 반환) → SENT. 그다음 thenThrow(a, b, c, d) 로 부를 때마다 차례로 다른 오류를 던지게 해
    // 네 번 보내며 결과를 본다: UNREGISTERED·INVALID_ARGUMENT → INVALID_TOKEN(PushService 가 토큰을 지운다),
    // UNAVAILABLE·코드 없음 → FAILED(아웃박스가 다시 보낸다). 삭제 대상인 SENDER_ID_MISMATCH 는 이 테스트가 확인하지 않는다
    @Test
    void 무효_토큰은_INVALID_TOKEN_일시_오류는_FAILED_성공은_SENT() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/p/messages/1");
        assertThat(sender.send("tok", "t", "b", Map.of("type", "call"), "k")).isEqualTo(PushSender.Result.SENT);

        FirebaseMessagingException unregistered = error(MessagingErrorCode.UNREGISTERED);
        FirebaseMessagingException invalid = error(MessagingErrorCode.INVALID_ARGUMENT);
        FirebaseMessagingException unavailable = error(MessagingErrorCode.UNAVAILABLE);
        FirebaseMessagingException unknown = error(null); // 본문을 못 읽은 경우(전송 계층 문제) — 지우지 않고 재시도
        when(messaging.send(any(Message.class))).thenThrow(unregistered, invalid, unavailable, unknown);
        assertThat(sender.send("tok", "t", "b", Map.of(), "k")).isEqualTo(PushSender.Result.INVALID_TOKEN);
        assertThat(sender.send("tok", "t", "b", Map.of(), "k")).isEqualTo(PushSender.Result.INVALID_TOKEN);
        assertThat(sender.send("tok", "t", "b", Map.of(), "k")).isEqualTo(PushSender.Result.FAILED);
        assertThat(sender.send("tok", "t", "b", Map.of(), "k")).isEqualTo(PushSender.Result.FAILED);
    }

    // 스프링이 쓰는 생성자(PushProperties)에 빈 서비스 계정 값·base64 가 아닌 값을 주면 IllegalStateException.
    // 이 생성자는 빈을 만들 때 불리므로, 실제 서버에서는 기동이 거기서 멈춘다
    @Test
    void 서비스_계정이_비었거나_base64_가_아니면_기동_때_바로_실패한다() {
        assertThatThrownBy(() -> new FcmPushSender(new PushProperties("fcm", ""))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FcmPushSender(new PushProperties("fcm", "not base64!!"))).isInstanceOf(IllegalStateException.class);
    }
}
