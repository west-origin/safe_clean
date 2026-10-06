package kr.ansimclean.api.common.outbox;

import kr.ansimclean.api.common.time.AppClock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이벤트 발행. **업무 트랜잭션 안에서** 부른다 — 행이 업무 데이터와 같이 커밋되어야 유실되지 않는다.
 * 실제 발송은 OutboxDispatcher 가 커밋 뒤에 한다.
 * 부르는 곳 예: BookingService·OfferService (common/events 의 record 를 넘긴다).
 */
@Service
public class OutboxService {
    private final OutboxEventRepository repository;
    private final AppClock clock;
    private final ApplicationEventPublisher events; // 스프링 앱 안의 이벤트를 올리는 도구 (OutboxQueued 를 올린다)
    private final JsonMapper json = JsonMapper.builder().build(); // 이벤트 record → JSON 문자열 (Jackson). payload 컬럼에 그대로 들어간다

    public OutboxService(OutboxEventRepository repository, AppClock clock, ApplicationEventPublisher events) {
        this.repository = repository;
        this.clock = clock;
        this.events = events;
    }

    /** 도메인 이벤트(common/events 의 record). 타입 = record 의 단순 클래스 이름 */
    // MANDATORY(CODE_GUIDE.md 3-3): 업무 트랜잭션 밖에서 부르면 예외가 난다 — 행이 업무 데이터와 따로 커밋되지 않게 한다
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Object event) {
        publish(event.getClass().getSimpleName(), event);
    }

    /**
     * 타입 이름을 직접 받아 행을 저장하고, 커밋 뒤 디스패처를 깨울 OutboxQueued 를 올린다.
     * publish(Object) 가 이리로 오는 것은 같은 클래스 안 호출이라 이 메서드의 MANDATORY 는 다시 검사되지 않는다
     * (바깥 publish(Object) 가 이미 검사했다).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String type, Object payload) {
        repository.save(OutboxEvent.pending(type, json.writeValueAsString(payload), clock.now()));
        events.publishEvent(new OutboxQueued()); // 커밋되면 디스패처가 바로 돈다 (롤백되면 무시)
    }
}
