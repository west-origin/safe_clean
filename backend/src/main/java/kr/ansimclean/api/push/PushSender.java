package kr.ansimclean.api.push;

import java.util.Map;

// 기기 토큰 하나로 알림 한 건을 보내는 약속(인터페이스). PushService 는 이 인터페이스만 알고,
// 실제 구현은 설정 app.push.mode 에 따라 둘 중 하나만 스프링 빈으로 만들어져 들어간다.
/** 푸시 발송 대역. 구현: FcmPushSender(app.push.mode=fcm) · LoggingPushSender(logging) */
public interface PushSender {
    // 발송 결과: SENT 보냄 · INVALID_TOKEN 다시 보내도 안 되는 토큰(앱 삭제·만료 등) · FAILED 일시 실패
    enum Result { SENT, INVALID_TOKEN, FAILED }

    /**
     * @param collapseKey 같은 이벤트의 재시도가 기기에 두 번 쌓이지 않게 (PushHandler 가 이벤트 type + 본문 해시로 만든다)
     * @return INVALID_TOKEN 이면 토큰을 지운다. FAILED 는 아웃박스가 재시도한다
     */
    Result send(String token, String title, String body, Map<String, String> data, String collapseKey);
}
