package kr.ansimclean.api.booking;

import kr.ansimclean.api.catalog.dto.PriceQuote;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 예약 상태기계 (BACKEND_PLAN.md §6.1) — 허용된 전이만 되고 나머지는 INVALID_STATE */
// Booking 엔티티만 쓰는 순수 단위 테스트 (가짜·DB 없음). 상태를 바꾸는 메서드(beginAssigning·assign·start…)를 직접 부르고
// 상태·시각·회차 값을 본다. assertThatThrownBy(…).extracting("code") = 던진 ApiException 의 code 필드를 꺼내 비교한다.
class BookingTest {
    static final PriceQuote QUOTE = new PriceQuote(80000, 5000, 85000, 8500, 180); // 기본 80,000 + 옵션 5,000 = 85,000, 수수료 8,500, 180분
    static final LocalDate DATE = LocalDate.of(2026, 9, 22); // 화요일
    static final Instant CREATED = Instant.parse("2026-09-15T05:00:00Z"); // 9/15 14:00 서울 — 방문(9/22 14:00)보다 일주일 앞이라 기한 안
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    /** 9/22(화) 14:00 부산 1회 예약, 막 만든 상태(requested) */
    static Booking booking() {
        return Booking.create(CUSTOMER, null, "home", "oneTime", DATE, LocalTime.of(14, 0), "부산광역시 중앙대로 2127-23", "802동 203호",
                "부산광역시", null, List.of("dishes"), List.of("fridge"), List.of("pet"), "잘 부탁드려요", null, QUOTE, CREATED);
    }

    @Test
    void 생성_직후는_requested_이고_금액과_시간이_고정된다() {
        Booking b = booking();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.REQUESTED);
        assertThat(b.getTotalAmount()).isEqualTo(85000);
        assertThat(b.managerPay()).isEqualTo(76500); // 85,000 − 수수료 8,500
        assertThat(b.getStartsAt()).isEqualTo(Instant.parse("2026-09-22T05:00:00Z")); // 서울 14:00
        assertThat(b.getEndsAt()).isEqualTo(b.getStartsAt().plus(Duration.ofMinutes(180)));
        assertThat(b.getPaymentStatus()).isEqualTo("unpaid");
        assertThat(b.getDispatchRound()).isEqualTo(1);
    }

    @Test
    void 정상_흐름_requested_assigning_scheduled_in_progress_paused_done() {
        Booking b = booking();
        b.beginAssigning();
        b.assign(MANAGER, CREATED);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.SCHEDULED);
        assertThat(b.getManagerId()).isEqualTo(MANAGER);
        b.start(Instant.parse("2026-09-22T04:55:00Z"), DATE);
        b.pause();
        b.resume();
        b.complete(Instant.parse("2026-09-22T08:00:00Z"));
        assertThat(b.getStatus()).isEqualTo(BookingStatus.DONE);
        assertThat(b.getEndedAt()).isNotNull();
    }

    // 차례로: 매칭 전(requested)에 배정 → 배정 뒤 방문 전날 시작 → 시작 전 일시정지 → 시작 전 완료, 모두 거부된다
    @Test
    void 당일이_아니면_시작할_수_없고_잘못된_전이는_INVALID_STATE() {
        Booking b = booking();
        assertThatThrownBy(() -> b.assign(MANAGER, CREATED)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        b.beginAssigning();
        b.assign(MANAGER, CREATED);
        assertThatThrownBy(() -> b.start(CREATED, DATE.minusDays(1))).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        assertThatThrownBy(b::pause).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> b.complete(CREATED)).isInstanceOf(ApiException.class);
    }

    // 경계값: 딱 24시간 전(limit)은 되고 1초 늦으면 BOOKING_NOT_CANCELLABLE. 이미 취소된 예약을 또 취소하면 INVALID_STATE
    // (엔티티 기준이다. BookingService.cancel 은 고객이 이미 취소한 예약이면 엔티티를 부르지 않고 같은 카드를 돌려준다)
    @Test
    void 취소는_방문_24시간_전까지만() {
        Booking b = booking();
        b.beginAssigning();
        Instant limit = b.getStartsAt().minus(Duration.ofHours(24));
        assertThatThrownBy(() -> b.cancel("customer", limit.plusSeconds(1), 24))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.BOOKING_NOT_CANCELLABLE);
        b.cancel("customer", limit, 24);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b.getCancelReason()).isEqualTo("customer");
        assertThatThrownBy(() -> b.cancel("customer", limit, 24)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void 시작한_뒤에는_고객이_취소할_수_없다() {
        Booking b = booking();
        b.beginAssigning();
        b.assign(MANAGER, CREATED);
        b.start(CREATED, DATE);
        assertThatThrownBy(() -> b.cancel("customer", CREATED, 24)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void 일정_변경은_scheduled_를_assigning_으로_되돌리고_매니저를_비우며_회차를_올린다() {
        Booking b = booking();
        b.beginAssigning();
        b.assign(MANAGER, CREATED);
        UUID previous = b.reschedule(DATE.plusDays(1), LocalTime.of(10, 0), CREATED, 24); // 돌려받는 값 = 풀린 원래 매니저
        assertThat(previous).isEqualTo(MANAGER);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.ASSIGNING);
        assertThat(b.getManagerId()).isNull();
        assertThat(b.getAssignedAt()).isNull();
        assertThat(b.getDispatchRound()).isEqualTo(2);
        assertThat(b.getStartsAt()).isEqualTo(Instant.parse("2026-09-23T01:00:00Z")); // 9/23 10:00 서울
        assertThat(b.getEndsAt()).isEqualTo(b.getStartsAt().plus(Duration.ofMinutes(180)));
    }

    @Test
    void 지각_안내는_배정된_예약이_기준_시각을_넘겼을_때_한_번만() { // 자체 점검 #25
        Booking b = booking();
        b.beginAssigning();
        assertThat(b.markLateNotified(b.getStartsAt().plusSeconds(3600), 30)).isFalse(); // 배정 전
        b.assign(MANAGER, CREATED);
        assertThat(b.markLateNotified(b.getStartsAt().plusSeconds(29 * 60), 30)).isFalse(); // 아직 29분
        assertThat(b.markLateNotified(b.getStartsAt().plusSeconds(30 * 60), 30)).isTrue();
        assertThat(b.markLateNotified(b.getStartsAt().plusSeconds(40 * 60), 30)).isFalse(); // 한 번만
    }

    // 매칭 구간(matchingFromRound) = 이 회차부터의 콜만 '받은 적' 으로 센다는 시작 회차.
    // 회차 변화: 시작 1 → 재매칭 2(구간 1 그대로) → 일정 변경 3(구간 3) → 배정 뒤 매니저 취소·재시작 4(구간 4)
    @Test
    void 일정_변경과_매니저_방문_취소는_새_매칭_구간을_시작하고_재매칭은_구간을_그대로_둔다() { // 자체 점검 후속 검토 (V043)
        Booking b = booking();
        b.beginAssigning();
        assertThat(b.getMatchingFromRound()).isEqualTo(1);
        b.nextDispatchRound(); // 거절·만료 뒤 재매칭 — 같은 일정
        assertThat(b.getMatchingFromRound()).isEqualTo(1);
        b.reschedule(DATE.plusDays(1), LocalTime.of(10, 0), CREATED, 24);
        assertThat(b.getMatchingFromRound()).isEqualTo(b.getDispatchRound()).isEqualTo(3);
        b.assign(MANAGER, CREATED);
        b.releaseByManager();
        b.restartAfterRelease();
        assertThat(b.getMatchingFromRound()).isEqualTo(b.getDispatchRound()).isEqualTo(4);
    }

    @Test
    void 매칭_실패_표시는_일정을_바꾸면_지워져_다시_실패하면_또_알린다() { // 자체 점검 #19
        Booking b = booking();
        b.beginAssigning();
        assertThat(b.markMatchingFailed(CREATED)).isTrue();
        b.reschedule(DATE.plusDays(1), LocalTime.of(10, 0), CREATED, 24);
        assertThat(b.getMatchingFailedAt()).isNull();
        assertThat(b.markMatchingFailed(CREATED)).isTrue(); // 예전: 표시가 남아 false → 고객 푸시 없음
    }

    // 약속 23시간 전의 변경은 BOOKING_NOT_CHANGEABLE. 기한 안이면 바뀌지만 requested(매칭 전)는 상태·회차가 그대로이고 풀 매니저도 없다(null)
    @Test
    void 일정_변경도_24시간_규칙을_따르고_requested_는_상태가_그대로다() {
        Booking b = booking();
        Instant late = b.getStartsAt().minus(Duration.ofHours(23));
        assertThatThrownBy(() -> b.reschedule(DATE.plusDays(1), LocalTime.of(10, 0), late, 24))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.BOOKING_NOT_CHANGEABLE);
        UUID previous = b.reschedule(DATE.plusDays(1), LocalTime.of(10, 0), CREATED, 24);
        assertThat(previous).isNull();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.REQUESTED);
        assertThat(b.getDispatchRound()).isEqualTo(1);
    }

    // 운영자가 기한을 48시간으로 바꿨을 때: 거절 문구도 그 시간을 말한다 — 고정 글자 '24시간' 이면 36시간 남은 방문에
    // '24시간 전까지만' 이라고 해 사실과 달랐다 (문제 목록 25번)
    @Test
    void 기한을_넘긴_취소_변경의_거절_문구는_설정된_기한_시간을_말한다() {
        Booking b = booking();
        b.beginAssigning();
        Instant late = b.getStartsAt().minus(Duration.ofHours(36)); // 48시간 기한 안쪽
        assertThatThrownBy(() -> b.cancel("customer", late, 48)).isInstanceOf(ApiException.class).hasMessage("방문 48시간 전까지만 취소할 수 있어요");
        assertThatThrownBy(() -> b.reschedule(DATE.plusDays(1), LocalTime.of(10, 0), late, 48)).isInstanceOf(ApiException.class).hasMessage("방문 48시간 전까지만 변경할 수 있어요");
    }

    // 약속 시각이 지난 뒤(시작 + 1초)에도 시스템 취소(cancelBySystem)는 된다 — 방문일이 지난 예약을 정리하는 길
    @Test
    void 시스템_취소는_기한_검사가_없다() {
        Booking b = booking();
        b.cancelBySystem("unassigned", b.getStartsAt().plusSeconds(1));
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b.getCancelReason()).isEqualTo("unassigned");
    }
}
