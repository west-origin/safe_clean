package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.ReservationResponse;
import kr.ansimclean.api.catalog.dto.PriceQuote;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Reservations 카드 매핑 (BACKEND_PLAN.md §6.1 마지막 단락) — 앱 mock RESERVATIONS 의 값과 같아야 한다 */
// BookingMapper.toReservation 을 DB 없이 부른다. 예약 엔티티는 진짜로 만들고 상태 메서드(beginAssigning·assign·start…)로
// 원하는 상태까지 옮긴 뒤 카드의 탭·상태·행 문구를 본다.
// toReservation 인자 순서: 예약, 카드 제목, 매니저 이름표, 매니저 소개표, 배정 전 문구, 서울 오늘, 녹화본 준비, 지금, 기한 시간
class BookingMapperTest {
    static final UUID MANAGER = UUID.randomUUID();
    static final LocalDate TODAY = LocalDate.of(2026, 9, 19); // '오늘' 로 넘기는 서울 날짜

    /** 테스트용 예약 한 건 (seriesId 가 있으면 정기 회차). 금액 80,000 · 수수료 8,000 · 3시간 */
    static Booking booking(LocalDate date, LocalTime time, UUID seriesId) {
        return Booking.create(UUID.randomUUID(), seriesId, "home", seriesId == null ? "oneTime" : "recurring", date, time, "부산광역시 중앙대로 2127-23", null,
                "부산광역시", null, List.of(), List.of(), List.of(), null, null, new PriceQuote(80000, 0, 80000, 8000, 180), Instant.EPOCH);
    }

    // 배정까지 마친 1회 예약. 이름표·소개표에 그 매니저가 있으면 행이 일정 → 주소 → "이름 매니저" → 소개 4줄이 된다
    @Test
    void 배정된_예약은_scheduled_이고_매니저_이름에_매니저가_붙는다() {
        Booking b = booking(LocalDate.of(2026, 11, 1), LocalTime.of(9, 0), null);
        b.beginAssigning();
        b.assign(MANAGER, Instant.EPOCH);
        ReservationResponse r = BookingMapper.toReservation(b, "가정/원룸 청소", Map.of(MANAGER, "김미영"), Map.of(MANAGER, "경력 2년 · 완료 청소 3건 · 평점 4.9"), "배정 후 알려드려요", TODAY, false, Instant.EPOCH, 24);
        assertThat(r.tab()).isEqualTo("upcoming");
        assertThat(r.status()).isEqualTo("scheduled");
        assertThat(r.title()).isEqualTo("가정/원룸 청소");
        assertThat(r.date()).isEqualTo("2026-11-01");
        assertThat(r.time()).isEqualTo("09:00");
        assertThat(r.series()).isFalse();
        assertThat(r.changeable()).isTrue(); // 시작 전이고 24시간 전
        // 같은 예약을 '지금 = 약속 1시간 전' 으로 다시 만들면 기한(24시간)이 지나 변경·취소 버튼을 감춘다
        assertThat(BookingMapper.toReservation(b, "가정/원룸 청소", Map.of(MANAGER, "김미영"), Map.of(), "배정 후 알려드려요", TODAY, false, b.getStartsAt().minusSeconds(3600), 24).changeable()).isFalse(); // 1시간 전
        // 배정되면 매니저 한 줄 소개가 더 붙는다 — 이름만으로는 누가 오는지 알 수 없었다 (사용성 검토 2026-09-22)
        assertThat(r.rows()).extracting(ReservationResponse.Row::value).containsExactly("2026년 11월 1일 (일) 오전 9시", "부산광역시 중앙대로 2127-23", "김미영 매니저", "경력 2년 · 완료 청소 3건 · 평점 4.9");
    }

    // 생성 직후(requested) 정기 회차 — 카드 상태는 "assigning", 매니저 행(rows[2])은 넘긴 배정 전 문구
    @Test
    void 배정_중은_assigning_과_안내_문구() {
        Booking b = booking(LocalDate.of(2026, 11, 14), LocalTime.of(14, 0), UUID.randomUUID());
        ReservationResponse r = BookingMapper.toReservation(b, "가전청소", Map.of(), Map.of(), "배정 후 알려드려요", TODAY, false, Instant.EPOCH, 24);
        assertThat(r.status()).isEqualTo("assigning");
        assertThat(r.series()).isTrue();
        assertThat(r.rows().get(0).value()).isEqualTo("2026년 11월 14일 (토) 오후 2시");
        assertThat(r.rows().get(2).value()).isEqualTo("배정 후 알려드려요");
    }

    // 방문일 = TODAY 이고 청소 시작(in_progress)까지 옮긴 예약 — 일정 행은 날짜 대신 "오늘 …"
    @Test
    void 오늘_진행_중이면_일정은_오늘_로_시작하고_inProgress() {
        Booking b = booking(TODAY, LocalTime.of(14, 0), null);
        b.beginAssigning();
        b.assign(MANAGER, Instant.EPOCH);
        b.start(Instant.EPOCH, TODAY);
        ReservationResponse r = BookingMapper.toReservation(b, "가정/원룸 청소", Map.of(MANAGER, "김미영"), Map.of(MANAGER, "경력 2년 · 완료 청소 3건 · 평점 4.9"), "배정 후 알려드려요", TODAY, false, Instant.EPOCH, 24);
        assertThat(r.status()).isEqualTo("inProgress");
        assertThat(r.rows().get(0).value()).isEqualTo("오늘 오후 2시");
    }

    // 완료 예약 → done 탭·done 상태 (extracting("tab", "status") = 두 필드를 꺼내 한 번에 비교).
    // 매니저 없이 시스템이 취소한 예약 → cancelled 탭, 매니저 행은 '배정 전 취소'
    @Test
    void 완료와_취소는_각_탭으로_가고_미배정_취소는_배정_전_취소() {
        Booking done = booking(LocalDate.of(2026, 8, 3), LocalTime.of(14, 0), null);
        done.beginAssigning();
        done.assign(MANAGER, Instant.EPOCH);
        done.start(Instant.EPOCH, LocalDate.of(2026, 8, 3));
        done.complete(Instant.EPOCH);
        assertThat(BookingMapper.toReservation(done, "가전청소", Map.of(MANAGER, "김미영"), Map.of(), "x", TODAY, false, Instant.EPOCH, 24)).extracting("tab", "status").containsExactly("done", "done");

        Booking cancelled = booking(LocalDate.of(2026, 7, 20), LocalTime.of(10, 0), null);
        cancelled.cancelBySystem("unassigned", Instant.EPOCH);
        ReservationResponse r = BookingMapper.toReservation(cancelled, "사무실 청소", Map.of(), Map.of(), "x", TODAY, false, Instant.EPOCH, 24);
        assertThat(r.tab()).isEqualTo("cancelled");
        assertThat(r.rows().get(2).value()).isEqualTo("배정 전 취소");
    }
}
