package kr.ansimclean.api.live;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** livekit_events — 웹훅 중복 방어: 같은 event id 는 한 번만 처리한다 (§8.1) */
// 행은 LiveWebhookService.handle 이 처리 직전에 넣고(id 가 없는 이벤트는 넣지 않는다),
// RecordingRetention(매일 04:00)이 받은 지 7일 지난 행을 지운다
@Entity
@Table(name = "livekit_events")
public class LivekitEvent {
    // LiveKit 이 붙인 이벤트 id — 기본 키라 같은 id 는 한 행뿐이다
    @Id
    @Column(columnDefinition = "text")
    private String id;

    // 이벤트 이름 (ingress_started 등). 지금 코드에서 다시 읽는 곳은 없고 DB 에 남는 기록이다
    @Column(columnDefinition = "text", nullable = false)
    private String event;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected LivekitEvent() {} // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 of() 를 쓴다

    /** 새 행: 받은 이벤트 id·이름과 받은 시각(now) */
    public static LivekitEvent of(String id, String event, Instant now) {
        LivekitEvent e = new LivekitEvent();
        e.id = id;
        e.event = event;
        e.receivedAt = now;
        return e;
    }

    public String getId() { return id; }
    public String getEvent() { return event; }
}
