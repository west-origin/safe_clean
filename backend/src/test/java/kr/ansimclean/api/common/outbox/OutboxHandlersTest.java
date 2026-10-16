package kr.ansimclean.api.common.outbox;

import kr.ansimclean.api.auth.SupabaseUserDeletionHandler;
import kr.ansimclean.api.common.events.AccountDeleted;
import kr.ansimclean.api.common.supabase.SupabaseAuthClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.push.PushHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 핸들러 없는 타입은 failed 로 남는다(조용히 버리지 않음). common/events 의 record 가 전부 어떤 핸들러에 속하는지 여기서 잡는다 */
// 이벤트 type 은 record 의 클래스 이름이다(OutboxService.publish 가 getSimpleName 으로 적는다) — 그래서 클래스 이름끼리 비교한다.
class OutboxHandlersTest {
    // common/events 패키지의 record 이름을 전부 모아, 핸들러들이 맡은 type 목록에 모두 들어 있는지 본다.
    // 새 이벤트 record 를 만들고 핸들러에 등록하지 않으면 여기서 실패한다
    @Test
    void 모든_도메인_이벤트에_핸들러가_있다() {
        // 클래스패스 스캐너: 스프링의 빈 찾기 도구를 빌려 패키지 안의 클래스를 훑는다. false = @Component 같은 기본 조건을 쓰지 않고,
        // 아래 필터(Record 를 상속 = record 로 선언된 것)만 고른다
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        Set<String> events = new HashSet<>();
        // 찾은 클래스의 전체 이름(kr.….BookingAssigned)에서 마지막 점 뒤(BookingAssigned)만 남긴다
        scanner.findCandidateComponents("kr.ansimclean.api.common.events")
                .forEach(bd -> events.add(bd.getBeanClassName().substring(bd.getBeanClassName().lastIndexOf('.') + 1)));
        assertThat(events).hasSizeGreaterThanOrEqualTo(11); // 스캔이 헛돌아 빈 목록이면 아래 비교가 그냥 통과하므로 최소 개수를 건다

        // 핸들러가 맡은 type 모으기: 생성자가 간단한 두 핸들러는 목을 넣어 직접 만들고 types() 를 부른다
        Set<String> handled = new HashSet<>(new PushHandler(mock(kr.ansimclean.api.push.PushService.class), mock(kr.ansimclean.api.profile.ProfileService.class)).types());
        handled.addAll(new SupabaseUserDeletionHandler(mock(SupabaseAuthClient.class)).types());
        // 나머지 두 핸들러는 types() 를 부르지 않고 이름을 직접 적는다 — 그 핸들러의 type 을 바꾸면 여기도 같이 바꾼다
        handled.add("AccountMediaDeleted"); // live.AccountMediaDeletionHandler (생성자 의존이 많아 타입만)
        handled.add("CameraReleased");      // live.CameraReleaseHandler (전체 재점검 J1)
        handled.addAll(new kr.ansimclean.api.payment.PaymentWorkHandler(
                mock(kr.ansimclean.api.payment.CheckoutService.class), mock(kr.ansimclean.api.payment.TossPaymentGateway.class)).types());
        assertThat(handled).containsAll(events);
        // 겹치면 OutboxDispatcher 생성자가 막는다 — 여기서도 확인
        assertThat(new PushHandler(mock(kr.ansimclean.api.push.PushService.class), mock(kr.ansimclean.api.profile.ProfileService.class)).types()).doesNotContain("AccountDeleted");
    }

    // 발행 → 처리 왕복: OutboxService.publish 가 저장소에 넘긴 행(type·JSON 본문)을 잡아,
    // 그 본문 그대로 SupabaseUserDeletionHandler 에 넘기면 같은 사용자 id 로 Supabase 삭제를 부르는지 본다
    @Test
    void AccountDeleted_는_publish_의_JSON_그대로_핸들러가_읽는다() {
        UUID user = UUID.randomUUID();
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        org.mockito.ArgumentCaptor<OutboxEvent> saved = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class); // save 에 넘어간 행을 잡는다
        new OutboxService(repository, new AppClock(Clock.fixed(Instant.EPOCH, AppClock.ZONE)), mock(ApplicationEventPublisher.class))
                .publish(new AccountDeleted(user));
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("AccountDeleted");

        SupabaseAuthClient supabase = mock(SupabaseAuthClient.class);
        new SupabaseUserDeletionHandler(supabase).handle(saved.getValue().getType(), saved.getValue().getPayload());
        verify(supabase).deleteUser(user);
    }
}
