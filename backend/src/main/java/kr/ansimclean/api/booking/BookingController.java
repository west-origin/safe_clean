package kr.ansimclean.api.booking;

import jakarta.validation.Valid;
import kr.ansimclean.api.booking.dto.BookingResponse;
import kr.ansimclean.api.booking.dto.CancelRequest;
import kr.ansimclean.api.booking.dto.CreateBookingRequest;
import kr.ansimclean.api.booking.dto.QuoteRequest;
import kr.ansimclean.api.booking.dto.QuoteResponse;
import kr.ansimclean.api.booking.dto.ReservationResponse;
import kr.ansimclean.api.booking.dto.ReservationsResponse;
import kr.ansimclean.api.booking.dto.ScheduleChangeRequest;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 고객 예약 API (docs/api.md §5.2). /customer/** 는 ROLE_CUSTOMER (SecurityConfig) */
// 모든 경로 앞에 /api/v1/customer 가 붙는다. 고객 앱 api/customer.js 가 부른다 (예: 예약 생성, 예약 목록).
// 규칙은 BookingService 에 있고, 정기청소 전체 취소(wholeSeries)만 BookingSeriesService 로 보낸다.
// @AuthenticationPrincipal CurrentUser = 요청을 보낸 로그인 사용자(id·역할). 남의 예약 id 를 보내면 서비스가 NOT_FOUND 로 답한다.
@RestController
@RequestMapping(SecurityConfig.API + "/customer")
public class BookingController {
    private final BookingService bookings;
    private final BookingSeriesService series;

    public BookingController(BookingService bookings, BookingSeriesService series) {
        this.bookings = bookings;
        this.series = series;
    }

    /** POST /customer/bookings/quote — 예약 확인 화면의 요금 계산. 저장하지 않는다 */
    @PostMapping("/bookings/quote")
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest req) {
        return bookings.quote(req);
    }

    /**
     * POST /customer/bookings — 예약 만들기(정기청소면 회차 전부)와 첫 회차 매칭 시작.
     * Idempotency-Key 헤더는 없어도 되고(그러면 매번 새로 만든다), 있으면 같은 키의 재요청에 저장된 응답을 돌려준다 (CODE_GUIDE.md 3-7)
     */
    @PostMapping("/bookings")
    public BookingResponse create(@AuthenticationPrincipal CurrentUser user, @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                  @Valid @RequestBody CreateBookingRequest req) {
        return bookings.create(user.id(), key, req);
    }

    /** GET /customer/bookings/{id} — 예약 1건(예: 예약 완료 화면이 다시 읽는다). 정기청소면 회차 목록도 싣는다 */
    @GetMapping("/bookings/{id}")
    public BookingResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return bookings.get(user.id(), id);
    }

    /** GET /customer/reservations — 예약 탭의 카드 목록 (예정·완료·취소 전부, 시작 시각 최근 순) */
    @GetMapping("/reservations")
    public ReservationsResponse reservations(@AuthenticationPrincipal CurrentUser user) {
        return bookings.reservations(user.id());
    }

    /** PATCH /customer/reservations/{id}/schedule — 날짜·시간 바꾸기. 일정이 실제로 바뀌면 배정된 매니저가 풀리고 다시 매칭한다. 카드 한 장을 돌려준다 */
    @PatchMapping("/reservations/{id}/schedule")
    public ReservationResponse reschedule(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @Valid @RequestBody ScheduleChangeRequest req) {
        return bookings.reschedule(user.id(), id, req);
    }

    /**
     * POST /customer/reservations/{id}/cancel — 취소. wholeSeries = true 면 그 정기청소의 기한 안 회차 전부, 아니면 이 회차(또는 1회 예약)만.
     * 본문은 없어도 된다(required = false) — 없으면 이 회차만 취소하고 사유는 서비스가 'customer' 로 적는다
     */
    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @Valid @RequestBody(required = false) CancelRequest req) {
        CancelRequest r = req == null ? new CancelRequest(null, false) : req;
        // 1회 예약에 wholeSeries 를 보내도 cancelWhole 이 회차 하나 취소(bookings.cancel)로 넘긴다
        return r.wholeSeries() ? series.cancelWhole(user.id(), id, r) : bookings.cancel(user.id(), id, r);
    }
}
