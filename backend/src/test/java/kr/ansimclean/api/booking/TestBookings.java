package kr.ansimclean.api.booking;

import kr.ansimclean.api.catalog.dto.PriceQuote;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** 다른 패키지의 단위 테스트가 쓰는 예약 엔티티 공장 (금액 85,000 / 수수료 8,500 / 3시간) */
// 쓰는 곳 예: StaleBookingCleanerTest(같은 패키지), OfferServiceTest(다른 패키지 — 그래서 클래스와 메서드가 public 이다).
// 진짜 Booking.create 와 상태 전이 메서드(beginAssigning → assign → start)를 차례로 불러 원하는 상태를 만든다 —
// 상태 값을 직접 넣지 않으므로 엔티티의 전이 규칙을 그대로 탄다. DB 에 저장하지 않는다(id 는 create 가 무작위로 정한다).
// static 메서드만 모은 도구 클래스라 객체를 만들지 않는다(생성자가 private).
public final class TestBookings {
    // 견적: 기본 80,000 + 옵션 5,000 = 85,000원, 수수료 8,500원, 소요 180분
    public static final PriceQuote QUOTE = new PriceQuote(80000, 5000, 85000, 8500, 180);
    public static final String ROAD = "서울특별시 강남구 테헤란로 123"; // 도로명 주소. 시·도(서울특별시)·시군구(강남구)는 create 인자로 따로 넣는다

    private TestBookings() {}

    /** 방금 만든 1회 가정청소 예약 (requested — 매칭 시작 전) */
    public static Booking requested(UUID customerId, LocalDate date, LocalTime time) {
        // 인자 순서: 고객, 시리즈(없음), 카테고리, 주기, 날짜, 시각, 주소·상세, 시·도, 시군구,
        // 무료 옵션, 유료 옵션, 주의사항, 요청 문구, 수리 종류(없음), 견적, 생성 시각
        return Booking.create(customerId, null, "home", "oneTime", date, time, ROAD, "3층", "서울특별시", "강남구",
                List.of("dishes"), List.of("pet"), List.of("pet"), "잘 부탁드려요", null, QUOTE, Instant.parse("2026-09-15T05:00:00Z"));
    }

    /** 매칭 중인 예약 (requested → assigning) */
    public static Booking assigning(UUID customerId, LocalDate date, LocalTime time) {
        Booking b = requested(customerId, date, time);
        b.beginAssigning();
        return b;
    }

    /** 정기청소 회차(seriesId 있음), 배정됨 */
    public static Booking scheduledSeries(UUID customerId, UUID managerId, UUID seriesId, LocalDate date, LocalTime time) {
        // requested() 와 같은 값에 seriesId 와 주기 'recurring' 만 다르다
        Booking b = Booking.create(customerId, seriesId, "home", "recurring", date, time, ROAD, "3층", "서울특별시", "강남구",
                List.of("dishes"), List.of("pet"), List.of("pet"), "잘 부탁드려요", null, QUOTE, Instant.parse("2026-09-15T05:00:00Z"));
        b.beginAssigning();
        b.assign(managerId, Instant.parse("2026-09-15T05:00:00Z"));
        return b;
    }

    /** 매니저가 수락해 배정된 1회 예약 (assigning → scheduled) */
    public static Booking scheduled(UUID customerId, UUID managerId, LocalDate date, LocalTime time) {
        Booking b = assigning(customerId, date, time);
        b.assign(managerId, Instant.parse("2026-09-15T05:00:00Z"));
        return b;
    }

    /** 청소를 시작한 작업 (scheduled → in_progress). startedAt = 시작 시각 */
    public static Booking inProgress(UUID customerId, UUID managerId, LocalDate date, LocalTime time, Instant startedAt) {
        Booking b = scheduled(customerId, managerId, date, time);
        // start 는 '오늘(서울)' 이 방문일과 같아야 통과한다 — 방문일(date)을 오늘로 넘겨 그 검사를 맞춘다
        b.start(startedAt, date);
        return b;
    }
}
