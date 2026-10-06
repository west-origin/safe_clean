package kr.ansimclean.api.live;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

// livekit_events 테이블 저장소 (키 = LiveKit 이벤트 id 문자열).
// LiveWebhookService 가 중복 확인·기록에, RecordingRetention 이 오래된 행 정리에 쓴다
public interface LivekitEventRepository extends JpaRepository<LivekitEvent, String> {
    /** 중복 방어용 행은 며칠이면 충분 — 매일 정리 (검증 검토: 세션당 수십 행이 영구 누적됐다) */
    long deleteByReceivedAtBefore(Instant before); // received_at < before 인 행을 지우고 지운 개수를 돌려준다 (RecordingRetention 이 로그에 쓴다)
}
