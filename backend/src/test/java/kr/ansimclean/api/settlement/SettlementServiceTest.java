package kr.ansimclean.api.settlement;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.TestBookings;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.settlement.dto.SettlementDetailResponse;
import kr.ansimclean.api.settlement.dto.SettlementResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 정산 생성·화면 값 (BACKEND_PLAN.md §6.5) — 숫자는 예약 스냅샷에서만 */
// SettlementService 단위 테스트. 정산 저장소·BookingService·ProfileService·CatalogService 는 Mockito 가짜이고,
// 예약은 TestBookings(테스트용 예약 공장: 금액 85,000 · 수수료 8,500 · 3시간 · 강남구)로 만든다.
// 확인하는 것: 정산 생성이 예약에 고정된 금액을 쓰고 두 번 불려도 한 행만 만드는지, 매니저 정산 화면(합계·통계·목록·페이지 커서),
// 정산 상세 화면 문구. 스프링 없이 객체를 직접 만들어 부르므로 Transactional 설정은 여기서 동작하지 않는다
// (예: createFor 의 MANDATORY 는 실제로는 묶음 밖에서 부르면 예외지만, 여기서는 그대로 불린다)
class SettlementServiceTest {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 서비스 시계를 고정한 시각 (정산 생성 시각·'이번 달' 기준)
    static final AppClock CLOCK = new AppClock(Clock.fixed(NOW, AppClock.ZONE));
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    SettlementRepository repo = mock(SettlementRepository.class);
    BookingService bookings = mock(BookingService.class);
    ProfileService profiles = mock(ProfileService.class);
    CatalogService catalog = mock(CatalogService.class);
    SettlementService service;
    Booking b1;
    Booking b2;

    // 각 테스트 전에: 같은 고객·매니저의 배정된 예약 두 건(9/17 10시, 8/20 14시)을 만들고,
    // save 는 받은 객체를 그대로 돌려주게, 가정청소(home)의 짧은 라벨은 '기본청소' 로 정한다
    @BeforeEach
    void setUp() {
        service = new SettlementService(repo, bookings, profiles, catalog, CLOCK);
        b1 = TestBookings.scheduled(CUSTOMER, MANAGER, LocalDate.of(2026, 9, 17), LocalTime.of(10, 0));
        b2 = TestBookings.scheduled(CUSTOMER, MANAGER, LocalDate.of(2026, 8, 20), LocalTime.of(14, 0));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(catalog.shortLabel("home")).thenReturn("기본청소");
    }

    // 처음: 정산이 없으면 예약 금액 85,000 · 수수료 8,500 → 최종 76,500 의 대기(pending) 정산을 저장한다.
    // 두 번째: 이미 있으면 그 객체를 그대로 돌려주고(isSameAs = 같은 객체인지), save 는 전체에서 한 번만 불렸다
    @Test
    void 생성은_예약_스냅샷_금액으로_한_번만() {
        when(repo.findByBookingId(b1.getId())).thenReturn(Optional.empty());
        Settlement s = service.createFor(b1);
        assertThat(s.getServiceAmount()).isEqualTo(85000);
        assertThat(s.getPlatformFee()).isEqualTo(8500);
        assertThat(s.getFinalAmount()).isEqualTo(76500);
        assertThat(s.getStatus()).isEqualTo(Settlement.PENDING);

        when(repo.findByBookingId(b1.getId())).thenReturn(Optional.of(s));
        assertThat(service.createFor(b1)).isSameAs(s);
        verify(repo).save(any());
    }

    // 준비: 대기 정산 s1(b1)과 완료 정산 s2(b2). DB 집계는 가짜로 정한다 —
    // 전체 합계 {완료 90,000, 예정 76,500}, 이번 달 {합 76,500, 1건}. 목록 조회(pageBefore)는 인자와 무관하게 [s1, s2].
    // 확인: 머리 카드 금액, 통계 3칸(이번 달 완료 건수 · 정산 예정 · 정산 완료(success 톤)), 목록 한 줄의 날짜·상태·제목·금액.
    // 뒷부분은 한 페이지 1건일 때의 다음 커서("시각_id")와, 그 커서로 다음 페이지를 요청할 때 저장소에 넘어가는 값을 본다.
    // Limit.of(2) = 1건 + 다음 페이지가 있는지 알려고 하나 더 받는 몫. 형식이 틀린 커서("garbage")는 ApiException(400)
    @Test
    void 화면은_이번_달_합_완료_건수_예정_완료_합과_목록_제목을_만든다() {
        Settlement s1 = Settlement.pending(b1.getId(), MANAGER, 85000, 8500, NOW);
        Settlement s2 = Settlement.pending(b2.getId(), MANAGER, 100000, 10000, Instant.parse("2026-08-20T08:00:00Z"));
        s2.markComplete(Instant.parse("2026-08-25T00:00:00Z"));
        when(repo.pageBefore(eq(MANAGER), any(), any(), any())).thenReturn(List.of(s1, s2));
        when(repo.sumsByManager(MANAGER)).thenReturn(new Object[] { 90000L, 76500L });
        when(repo.monthlyByManager(eq(MANAGER), any(), any())).thenReturn(new Object[] { 76500L, 1L });
        when(bookings.findAll(anyCollection())).thenReturn(List.of(b1, b2));

        SettlementResponse res = service.screen(MANAGER);

        assertThat(res.monthly().label()).isEqualTo("이번 달 예상 정산");
        assertThat(res.monthly().amount()).isEqualTo("76,500원");
        assertThat(res.stats()).extracting(SettlementResponse.Stat::value).containsExactly("1건", "76,500원", "90,000원");
        assertThat(res.stats().get(2).tone()).isEqualTo("success");
        assertThat(res.items()).hasSize(2);
        assertThat(res.items().get(0)).extracting("date", "status", "title", "amount").containsExactly("9월 17일", "settlementPending", "강남구 기본청소", "76,500원");
        assertThat(res.items().get(1).status()).isEqualTo("complete");
        assertThat(res.nextBefore()).isNull(); // 두 건뿐 — 다음 페이지 없음

        // limit 1 이면 한 건만 주고 다음 커서(마지막 항목의 생성 시각_id)를 돌려준다. 그 커서를 그대로 보내면 (시각, id) 로 이어 받는다 — 같은 시각의 정산이 빠지지 않게 (검수 4차)
        SettlementResponse page = service.screen(MANAGER, (String) null, 1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.nextBefore()).isEqualTo(NOW + "_" + s1.getId());
        service.screen(MANAGER, page.nextBefore(), 1);
        verify(repo).pageBefore(MANAGER, NOW, s1.getId(), org.springframework.data.domain.Limit.of(2));
        assertThatThrownBy(() -> service.screen(MANAGER, "garbage", 1)).isInstanceOf(kr.ansimclean.api.common.error.ApiException.class);
    }

    // 정산 상세: 제목 "방문일 · 지역 카테고리", 부제 "약속 시간대(3시간) · 가린 고객 이름 고객",
    // 내역 3줄(서비스 금액 · 수수료는 음수 · 기타 0원)과 최종 정산 줄. 조회만 하므로 save 는 불리지 않는다
    @Test
    void 상세는_시간_고객_내역_최종_정산() {
        Settlement s1 = Settlement.pending(b1.getId(), MANAGER, 85000, 8500, NOW);
        when(repo.findByIdAndManagerId(s1.getId(), MANAGER)).thenReturn(Optional.of(s1));
        when(bookings.find(b1.getId())).thenReturn(Optional.of(b1));
        when(profiles.namesOf(any())).thenReturn(Map.of(CUSTOMER, "김하늘"));

        SettlementDetailResponse res = service.detail(MANAGER, s1.getId());

        assertThat(res.title()).isEqualTo("9월 17일 · 강남구 기본청소");
        assertThat(res.subtitle()).isEqualTo("10:00 - 13:00 · 김○○ 고객");
        assertThat(res.breakdown()).extracting(SettlementDetailResponse.Row::value).containsExactly("85,000원", "-8,500원", "0원");
        assertThat(res.finalRow().label()).isEqualTo("최종 정산");
        assertThat(res.finalRow().value()).isEqualTo("76,500원");
        verify(repo, never()).save(any());
    }
}
