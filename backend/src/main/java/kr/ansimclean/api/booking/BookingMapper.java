package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.BookingResponse;
import kr.ansimclean.api.booking.dto.ReservationResponse;
import kr.ansimclean.api.common.format.KoreanFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Booking → 응답 record. 표시 문자열은 KoreanFormat 만 (BACKEND_PLAN.md §6.1 Reservations 매핑) */
// 부르는 곳: BookingService (예약 생성·상세 응답 → toBooking, 예약 목록·카드 한 장 → toReservation).
// DB 를 읽지 않는다 — 매니저 이름·문구·녹화본 여부처럼 따로 읽어야 하는 값은 부르는 쪽이 인자로 넘긴다.
// final 클래스 + private 생성자 = 객체를 만들지 않고 static 메서드만 쓰는 도구 모음.
public final class BookingMapper {
    static final String CANCELLED_BEFORE_ASSIGN = "배정 전 취소"; // 매니저 없이 취소된 예약의 매니저 칸
    static final String MANAGER_SUFFIX = " 매니저"; // 이름 뒤에 붙인다 → "김미영 매니저"

    private BookingMapper() {}

    /** @param managerNames manager_id → 이름 (배정된 예약만), pendingLabel = 매니저가 없을 때의 매니저 칸 문구 (BookingService.cleanerLabel), recordingReady = 녹화본 준비됨 */
    public static ReservationResponse toReservation(Booking b, String categoryLabel, Map<UUID, String> managerNames, Map<UUID, String> managerIntros, String pendingLabel, LocalDate todaySeoul, boolean recordingReady, Instant now, int deadlineHours) {
        // 예약 탭 카드 한 장을 만든다. BookingService 는 categoryLabel 에 카드 제목(정기청소면 "· 정기 2/6회" 가 붙은 것)을,
        // pendingLabel 에 cleanerLabel 결과(배정 전이면 app_settings.cleaner_pending_label, 매칭 실패면 실패 문구)를 넘긴다.
        String manager = managerLabel(b, managerNames, pendingLabel);
        // 일정 행: 오늘 진행 중(in_progress·paused)인 작업만 "오늘 오후 2시", 나머지는 "2026년 11월 1일 (일) 오전 9시" 형식
        boolean today = BookingStatus.ACTIVE.contains(b.getStatus()) && b.getServiceDate().equals(todaySeoul);
        String schedule = today ? "오늘 " + KoreanFormat.timeKo(b.getServiceTime()) : KoreanFormat.dateTimeLong(b.getServiceDate(), b.getServiceTime());
        // 인자 순서 = ReservationResponse 필드 순서: id, tab, category, title, status, date, time, series, recordingReady, changeable, rows.
        // changeable = 시작 전 상태이고 변경·취소 기한 안인가. false 면 고객 앱 예약 카드가 변경·취소 버튼 대신 안내 문구를 보인다
        return new ReservationResponse(b.getId(), tab(b.getStatus()), b.getCategoryKey(), categoryLabel, status(b.getStatus()),
                b.getServiceDate().toString(), KoreanFormat.hhmm(b.getServiceTime()), b.getSeriesId() != null, recordingReady,
                BookingStatus.BEFORE_START.contains(b.getStatus()) && b.isBeforeDeadline(now, deadlineHours),
                rows(schedule, b.getRoadAddress(), manager, b.getManagerId() == null ? null : managerIntros.get(b.getManagerId())));
    }

    /**
     * 매니저 칸 한 줄. 이름을 아는 매니저가 있으면 "이름 매니저", 매니저 없이 취소됐으면 "배정 전 취소", 그 밖에는 pendingLabel.
     * 목록 카드(toReservation)와 예약 상세 응답(BookingService.get → toBooking 의 cleaner)이 이 한 곳을 같이 쓴다 —
     * 따로 계산하던 때는 매니저 없이 취소된 예약이 목록에서는 '배정 전 취소', 상세(예약 완료 화면)에서는 배정 전·매칭 실패 문구였다 (문제 목록 9번)
     */
    static String managerLabel(Booking b, Map<UUID, String> managerNames, String pendingLabel) {
        if (b.getManagerId() != null && managerNames.containsKey(b.getManagerId())) return managerNames.get(b.getManagerId()) + MANAGER_SUFFIX;
        if (b.getStatus() == BookingStatus.CANCELLED) return CANCELLED_BEFORE_ASSIGN;
        return pendingLabel;
    }

    /** 배정된 매니저가 있으면 한 줄 소개를 더한다 — 이름만 보여 누가 오는지 알 수 없었다 (사용성 검토 2026-09-22) */
    private static List<ReservationResponse.Row> rows(String schedule, String address, String manager, String intro) {
        // 카드의 라벨·값 행: 일정 → 주소 → 매니저 (→ 매니저 소개). List.of 는 바꿀 수 없는 목록이라 행을 더하려고 ArrayList 로 옮겨 담는다
        List<ReservationResponse.Row> rows = new java.util.ArrayList<>(List.of(
                new ReservationResponse.Row("일정", schedule), new ReservationResponse.Row("주소", address), new ReservationResponse.Row("매니저", manager)));
        if (intro != null) rows.add(new ReservationResponse.Row("매니저 소개", intro));
        return rows;
    }

    /** @param visits 정기청소면 시리즈의 회차 전부(날짜순), 1회면 자기 자신 */
    public static BookingResponse toBooking(Booking b, String categoryLabel, String cleaner, List<Booking> visits) {
        // 예약 생성 응답과 GET /customer/bookings/{id} 가 쓴다. cleaner = 매니저 칸 문구(부르는 쪽이 정한다).
        // visitRows = 회차마다 { id, "2026-11-01", "2026년 11월 1일 (일)" }
        List<BookingResponse.Visit> visitRows = visits.stream()
                .map(v -> new BookingResponse.Visit(v.getId(), v.getServiceDate().toString(), KoreanFormat.dateLongWeekday(v.getServiceDate()))).toList();
        long total = visits.stream().mapToLong(Booking::getTotalAmount).sum(); // 회차 금액의 합 — 취소된 회차도 visits 에 있으면 더한다
        // perVisitLabel = 이 회차 금액("85,000원"), totalAmount/totalLabel = 위 합계.
        // matchingFailed = 매칭 실패 표시가 남아 있는가 (배정되면 Booking.assign 이 지운다 — 매니저가 있으면 늘 false).
        // cancelled = 취소된 예약인가 — 매칭 실패 표시 없이(콜이 나가 있거나 밤 보류 중) 취소된 수리 예약을 다시 열어도 완료 화면이 '배정 중' 대신
        // cleaner('배정 전 취소')를 보이게 한다 (합친 38건 검토 R16)
        return new BookingResponse(b.getId(), b.getCategoryKey(), b.getFrequency(), b.getServiceDate().toString(), KoreanFormat.hhmm(b.getServiceTime()),
                b.getRoadAddress(), b.getDetailAddress(), categoryLabel, cleaner, b.getManagerId() != null, b.getMatchingFailedAt() != null,
                b.getStatus() == BookingStatus.CANCELLED, b.getSeriesId(), visitRows,
                KoreanFormat.won(b.getTotalAmount()), total, KoreanFormat.won(total));
    }

    /** 예약 상태 → 카드가 들어갈 탭 키: done / cancelled / 그 밖은 upcoming(예정) */
    static String tab(BookingStatus s) {
        // switch 식 = 값에 따라 결과 하나를 고른다 (default = 위에 없는 나머지 전부)
        return switch (s) {
            case DONE -> "done";
            case CANCELLED -> "cancelled";
            default -> "upcoming";
        };
    }

    /**
     * 서버 상태 → 앱 카드 상태. requested·assigning 은 둘 다 "assigning"(매칭 중),
     * in_progress·paused 는 둘 다 "inProgress". 앱 값은 camelCase 라 DB 값과 표기가 다르다
     */
    static String status(BookingStatus s) {
        return switch (s) {
            case REQUESTED, ASSIGNING -> "assigning";
            case SCHEDULED -> "scheduled";
            case IN_PROGRESS, PAUSED -> "inProgress";
            case DONE -> "done";
            case CANCELLED -> "cancelled";
        };
    }
}
