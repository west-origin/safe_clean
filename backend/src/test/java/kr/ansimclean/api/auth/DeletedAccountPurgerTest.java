package kr.ansimclean.api.auth;

import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.profile.ManagerPenaltyService;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.schedule.AvailabilityService;
import kr.ansimclean.api.support.SupportService;
import kr.ansimclean.api.support.TermsService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 스프링 없이 DeletedAccountPurger 를 new 로 만든다. 협력 서비스는 Mockito mock, 시계는 NOW 에 고정,
// TransactionTemplate 은 가짜 트랜잭션 관리자라 콜백을 그냥 실행한다. bookingPurge 는 예약 쪽 AccountPurge 구현체 자리의 가짜다.
// 실제 SQL 이 무엇을 비우는지는 DeletedAccountPurgeIT 가 본다
/** 탈퇴 + 5년 파기의 순서·범위 (BACKEND_PLAN §13-22, 사용자 결정 2026-09-27) */
class DeletedAccountPurgerTest {
    static final Instant NOW = Instant.parse("2026-09-27T03:00:00Z"); // 서울 2026-09-27 12:00
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    ProfileService profiles = mock(ProfileService.class);
    ManagerPenaltyService penalties = mock(ManagerPenaltyService.class);
    AvailabilityService availability = mock(AvailabilityService.class);
    TermsService terms = mock(TermsService.class);
    SupportService support = mock(SupportService.class);
    AccountPurge bookingPurge = mock(AccountPurge.class);
    DeletedAccountPurger purger = new DeletedAccountPurger(profiles, penalties, availability, terms, support, List.of(bookingPurge),
            new AppClock(Clock.fixed(NOW, AppClock.ZONE)), new TransactionTemplate(mock(PlatformTransactionManager.class)));

    @Test
    void 서울_달력으로_5년_전보다_먼저_탈퇴한_계정을_찾는다() {
        // dueForPurge 는 정하지 않아 빈 목록(mock 기본값) — 파기는 없고, 조회에 넘긴 기준 시각만 확인한다
        purger.purgeDue();
        verify(profiles).dueForPurge(NOW.atZone(AppClock.ZONE).minusYears(5).toInstant());
    }

    @Test
    void 고객은_기능별_파기_뒤_약관_동의_문의를_지우고_마지막에_파기_표시를_한다_매니저는_근무시간_패널티까지() {
        when(profiles.dueForPurge(any())).thenReturn(List.of(new ProfileService.PurgeTarget(CUSTOMER, "customer"), new ProfileService.PurgeTarget(MANAGER, "manager")));
        assertThat(purger.purgeDue()).isEqualTo(2);

        // 고객: 호출 순서까지 확인 (파기 표시 markPurged 가 마지막이어야 중간 실패 때 다음 날 다시 처리된다). 근무시간·패널티는 건드리지 않는다
        InOrder c = inOrder(bookingPurge, terms, support, profiles);
        c.verify(bookingPurge).purge(CUSTOMER, "customer");
        c.verify(terms).forgetAgreements(CUSTOMER);
        c.verify(support).forgetInquiries(CUSTOMER);
        c.verify(profiles).markPurged(CUSTOMER);
        verify(availability, never()).forget(CUSTOMER);
        verify(penalties, never()).forget(CUSTOMER);

        // 매니저: 예약 쪽 파기에도 넘기고(구현체가 역할을 보고 할 일을 정한다), 근무시간·패널티까지 지운다
        verify(bookingPurge).purge(MANAGER, "manager");
        verify(availability).forget(MANAGER);
        verify(penalties).forget(MANAGER);
        verify(profiles).markPurged(MANAGER);
    }

    @Test
    void 한_계정이_실패해도_파기_표시를_하지_않고_다른_계정은_계속한다() {
        when(profiles.dueForPurge(any())).thenReturn(List.of(new ProfileService.PurgeTarget(CUSTOMER, "customer"), new ProfileService.PurgeTarget(MANAGER, "manager")));
        // doThrow(…).when(mock).메서드(…): 돌려줄 값이 없는(void) 메서드가 예외를 던지게 정한다
        doThrow(new IllegalStateException("db")).when(bookingPurge).purge(CUSTOMER, "customer");
        assertThat(purger.purgeDue()).isEqualTo(1); // 파기한 계정 수 — 실패한 고객은 세지 않는다
        verify(profiles, never()).markPurged(CUSTOMER); // 다음 날 처음부터 다시
        verify(profiles).markPurged(MANAGER);
    }

    @Test
    void run_은_조회가_실패해도_던지지_않는다() {
        when(profiles.dueForPurge(any())).thenThrow(new IllegalStateException("db down"));
        purger.run(); // 예외가 밖으로 나오지 않으면 통과 — 스케줄러 입구는 로그만 남기고 다음 날 다시 돈다
    }
}
