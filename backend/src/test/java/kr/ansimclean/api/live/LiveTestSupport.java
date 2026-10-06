package kr.ansimclean.api.live;

import kr.ansimclean.api.common.config.AppProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 라이브 단위 테스트 공통 값 */
// 쓰는 곳 예: 같은 live 패키지의 LiveServiceTest·LiveReconcilerTest.
// 고정 시각·고객/매니저 id·설정값·바디캠 기기·카메라 참가자를 만들어 준다.
// static 값과 메서드만 모은 도구 클래스라 객체를 만들지 않는다(생성자가 private). final = 이어받을 수 없다
final class LiveTestSupport {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 화 14:00 서울
    // 테스트 실행마다 새로 뽑는 id. 한 번 실행하는 동안에는 모든 테스트가 같은 값을 쓴다 (static = 클래스에 하나)
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    private LiveTestSupport() {}

    /** mute 확인 폴링 2회·대기 0ms */
    // 서버 설정(application.yml 의 app.*)을 스프링 없이 직접 만든다. 주소·키는 가짜 값이다.
    // Livekit 의 끝 두 값이 muteConfirmAttempts=2·muteConfirmSleepMs=0 — LiveService.setMuted 가 트랙을 2번만 다시 읽고 쉬지 않아 테스트가 빨리 끝난다.
    // Storage 의 마지막 값 "recordings" 가 녹화 버킷 이름이다 (Storage 삭제·서명 URL 검증에 이 이름이 나온다)
    static AppProperties props() {
        return new AppProperties(new AppProperties.Cors(List.of()), new AppProperties.Supabase("https://sb.example", "srk", "secret", "s"),
                new AppProperties.Livekit("cloud", "wss://lk.example", "key", "secret", 2, 0),
                new AppProperties.Storage("https://sb.example/storage/v1/s3", "ak", "sk", "ap-northeast-2", "recordings"), new AppProperties.Admin("admin-token"));
    }

    // MANAGER 의 바디캠 기기 한 대 (DB 에 저장하지 않은 객체). Ingress id 는 "ing-1", 처음 상태는 offline·잠금 해제
    static ManagerDevice device() {
        return ManagerDevice.register(MANAGER, "안심캠 #AC-00231", "AC-00231", "ing-1", "rtmp://lk.example/x", "abcd-ef-1234");
    }

    // LiveKit 방에 들어온 바디캠 참가자(이름표 "camera-매니저id")를 흉내 낸다. 영상 트랙 하나(trackSid)만 있고,
    // muted = 그 트랙이 지금 막혀 있는지. 구독 권한은 두 인자 생성자라 true 다
    static LiveKitGateway.Participant camera(String trackSid, boolean muted) {
        return new LiveKitGateway.Participant("camera-" + MANAGER, List.of(new LiveKitGateway.Track(trackSid, true, muted)));
    }
}
