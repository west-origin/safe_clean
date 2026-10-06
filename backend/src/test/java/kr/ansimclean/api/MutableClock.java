package kr.ansimclean.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

// IT 는 이 시계로 만든 AppClock 을 @Primary 빈으로 넣어 서버의 '지금' 을 테스트가 정한다 (예: DeletedAccountPurgeIT 의 FixedClock).
// 단위 테스트도 new AppClock(이 시계) 로 서비스를 만들어 쓴다 (예: SessionRevocationServiceTest).
// OtpServiceTest 의 MutableClock 은 이름만 같은 그 파일 전용 클래스다.
/** 테스트용(통합·단위): 시각을 옮길 수 있는 Clock (스케줄러 동작을 "며칠 뒤" 로 검증할 때) */
public class MutableClock extends Clock {
    // AtomicReference: 여러 스레드(요청 처리 스레드와 테스트 스레드)가 같이 읽고 바꿔도 안전한 값 상자
    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    /** start 시각에 멈춰 있는 시계. set 으로 옮기기 전까지 시간이 흐르지 않는다 */
    public MutableClock(Instant start, ZoneId zone) {
        this.now = new AtomicReference<>(start);
        this.zone = zone;
    }

    /** 지금 시각을 instant 로 옮긴다 (앞으로도 뒤로도) */
    public void set(Instant instant) {
        now.set(instant);
    }

    // 아래 세 메서드는 java.time.Clock 이 요구하는 것 (@Override = 부모 클래스의 메서드를 여기서 구현한다)
    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this; // AppClock 이 Asia/Seoul 로 감싸지만 instant 는 시간대와 무관하다
    }

    /** AppClock.now() 가 결국 이 값을 읽는다 */
    @Override
    public Instant instant() {
        return now.get();
    }
}
