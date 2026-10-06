package kr.ansimclean.api.settlement;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.settlement.dto.SettlementDetailResponse;
import kr.ansimclean.api.settlement.dto.SettlementResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.domain.Limit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 쓰는 곳 예: SettlementController(정산 목록·상세),
// JobService(작업 완료 때 createFor, 완료 화면의 예상 정산액 findByBooking).
// 예약 정보(날짜·시간·주소·카테고리)는 BookingService, 고객 이름은 ProfileService, 짧은 카테고리 라벨은 CatalogService 에서 받는다.
/** 정산 (BACKEND_PLAN.md §6.5). 생성은 작업 완료와 같은 트랜잭션에서(JobService → createFor), 화면 값은 여기서 만든다 */
@Service
// 클래스 기본은 읽기 전용 트랜잭션(CODE_GUIDE.md 3-3). 쓰기가 있는 createFor 만 메서드에 따로 MANDATORY 를 붙였다
@Transactional(readOnly = true)
public class SettlementService {
    // 목록 한 페이지 기본 50건, 최대 100건
    static final int LIST_LIMIT = 50;
    static final int LIST_MAX = 100;
    // 아래는 응답에 그대로 나가는 화면 문구
    static final String LABEL_MONTHLY = "이번 달 예상 정산";
    static final String LABEL_DONE = "완료 업무";
    static final String LABEL_PENDING = "정산 예정";
    static final String LABEL_COMPLETE = "정산 완료";
    static final String LABEL_FINAL = "최종 정산";

    private final SettlementRepository settlements;
    private final BookingService bookings;
    private final ProfileService profiles;
    private final CatalogService catalog;
    private final AppClock clock;

    public SettlementService(SettlementRepository settlements, BookingService bookings, ProfileService profiles, CatalogService catalog, AppClock clock) {
        this.settlements = settlements;
        this.bookings = bookings;
        this.profiles = profiles;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** 작업 완료 트랜잭션 안에서만. 이미 있으면 그대로(멱등 — booking_id unique 가 DB 방어) */
    // MANDATORY: 작업 완료(JobService.finish) 묶음 밖에서 부르면 예외. 완료와 정산이 한 묶음으로 저장·취소돼
    // 정산만 생기거나 완료만 저장되는 일이 없다
    @Transactional(propagation = Propagation.MANDATORY)
    public Settlement createFor(Booking booking) {
        // 있으면 그 행, 없으면 예약에 고정된 금액으로 pending 행을 새로 저장한다
        return settlements.findByBookingId(booking.getId())
                .orElseGet(() -> settlements.save(Settlement.pending(booking.getId(), booking.getManagerId(), booking.getTotalAmount(), booking.getPlatformFee(), clock.now())));
    }

    /** 예약 한 건의 정산. 없으면 빈 Optional. JobService 완료 화면이 예상 정산액에 쓴다 */
    public Optional<Settlement> findByBooking(UUID bookingId) {
        return settlements.findByBookingId(bookingId);
    }

    /** Manager/Settlement — 합계는 DB 집계, 목록은 before 커서로 한 페이지(기본 50, 최대 100). '이번 달' 은 정산 생성(=작업 완료) 시각 기준 (domain.md §6.5) */
    public SettlementResponse screen(UUID managerId) {
        return screen(managerId, null, LIST_LIMIT);
    }

    /** 커서 "<ISO>_<id>" (앱은 그대로 돌려보낸다). 옛 형식(ISO 만)도 받는다 — id 는 최대값 */
    static final UUID MAX_ID = new UUID(-1L, -1L); // 모든 비트 1. Postgres uuid 비교에서 가장 커서 'id < MAX_ID' 가 그 시각 정산을 모두 통과시킨다

    /** 컨트롤러가 부르는 목록. before 커서 문자열을 (시각, id) 로 풀고, 형식이 틀리면 400. before 가 없으면 첫 페이지 */
    public SettlementResponse screen(UUID managerId, String before, int limit) {
        Instant at = null;
        UUID id = MAX_ID;
        if (before != null && !before.isBlank()) {
            // '_' 앞은 ISO 시각, 뒤는 정산 id. '_' 가 없으면 옛 형식(시각만)으로 본다
            int sep = before.indexOf('_');
            try {
                at = Instant.parse(sep < 0 ? before : before.substring(0, sep));
                if (sep >= 0) id = UUID.fromString(before.substring(sep + 1));
            } catch (RuntimeException e) {
                throw ApiException.validation("before 커서가 올바르지 않아요");
            }
        }
        return screen(managerId, at, id, limit);
    }

    /**
     * 실제 계산: 합계(완료·예정)·이번 달(합·건수)은 DB 집계로, 목록은 커서 앞 한 페이지로 만든다.
     * before 가 null 이면 첫 페이지. 응답의 nextBefore 는 다음 페이지 커서("시각_id"), 더 없으면 null
     */
    SettlementResponse screen(UUID managerId, Instant before, UUID beforeId, int limit) {
        int size = Math.max(1, Math.min(limit, LIST_MAX)); // limit 을 1~100 으로 자른다
        // '이번 달' 경계는 서울 시각 기준: 이번 달 1일 0시 ~ 다음 달 1일 0시
        YearMonth thisMonth = YearMonth.from(clock.nowSeoul());
        Instant monthFrom = thisMonth.atDay(1).atStartOfDay(AppClock.ZONE).toInstant();
        Instant monthTo = thisMonth.plusMonths(1).atDay(1).atStartOfDay(AppClock.ZONE).toInstant();
        Object[] sums = row(settlements.sumsByManager(managerId));
        Object[] month = row(settlements.monthlyByManager(managerId, monthFrom, monthTo));
        // 집계 칸의 정확한 숫자 타입(Long 등)에 기대지 않고 Number 로 받아 long·int 로 바꾼다
        long complete = ((Number) sums[0]).longValue();
        long pending = ((Number) sums[1]).longValue();
        long monthly = ((Number) month[0]).longValue();
        int doneThisMonth = ((Number) month[1]).intValue();
        // 커서가 없으면 '지금 + 1일' — Instant.MAX 는 Postgres timestamptz 범위를 넘어 22008 오류 (실서버 확인)
        Instant cursor = before == null ? clock.now().plus(Duration.ofDays(1)) : before;
        List<Settlement> recent = settlements.pageBefore(managerId, cursor, beforeId, Limit.of(size + 1)); // 한 건 더 받아 다음 페이지 유무를 안다
        Settlement last = recent.size() > size ? recent.get(size - 1) : null;
        String nextBefore = last == null ? null : last.getCreatedAt() + "_" + last.getId(); // (시각, id) 키셋 — 같은 시각의 정산이 빠지지 않는다
        if (recent.size() > size) recent = recent.subList(0, size);
        Map<UUID, Booking> byId = bookingsOf(recent);
        List<SettlementResponse.Item> items = new ArrayList<>();
        for (Settlement s : recent) {
            Booking b = byId.get(s.getBookingId());
            if (b == null) continue; // 예약을 못 찾은 정산은 목록에서 뺀다
            // 날짜는 방문일("9월 18일"), status 는 앱 StatusBadge 값(complete / settlementPending), 금액은 최종 정산액
            items.add(new SettlementResponse.Item(s.getId(), KoreanFormat.dateShort(b.getServiceDate()), s.isComplete() ? "complete" : "settlementPending",
                    itemTitle(b), KoreanFormat.won(s.getFinalAmount())));
        }
        // 머리 카드(이번 달 예상 정산) + 통계(이번 달 완료 건수 · 전체 기간 정산 예정 합 · 전체 기간 정산 완료 합) + 목록 + 다음 커서
        return new SettlementResponse(new SettlementResponse.Monthly(LABEL_MONTHLY, KoreanFormat.won(monthly)),
                List.of(new SettlementResponse.Stat(LABEL_DONE, doneThisMonth + "건", "default"),
                        new SettlementResponse.Stat(LABEL_PENDING, KoreanFormat.won(pending), "default"),
                        new SettlementResponse.Stat(LABEL_COMPLETE, KoreanFormat.won(complete), "success")),
                items, nextBefore);
    }

    /** JPQL 집계는 Object[] 한 행 — 드라이버에 따라 Object[][] 로 감싸져 올 때가 있어 풀어 준다 */
    private static Object[] row(Object[] r) {
        return r.length == 1 && r[0] instanceof Object[] inner ? inner : r;
    }

    /** Manager/Settlement/Detail */
    // 내 정산이 아니거나 예약을 못 찾으면 404. title = "9월 18일 · 강남구 기본청소",
    // subtitle = 약속 시간대 + 고객 이름(첫 글자만 남기고 가림) + " 고객". 이름을 못 찾으면 빈 이름으로 둔다
    public SettlementDetailResponse detail(UUID managerId, UUID id) {
        Settlement s = settlements.findByIdAndManagerId(id, managerId).orElseThrow(ApiException::notFound);
        Booking b = bookings.find(s.getBookingId()).orElseThrow(ApiException::notFound);
        String customer = profiles.namesOf(List.of(b.getCustomerId())).getOrDefault(b.getCustomerId(), "");
        // 내역 3줄(서비스 금액 · 플랫폼 수수료(빼는 값이라 음수 표시) · 기타(0이면 "0원")) + 마지막 줄 최종 정산(brand 톤)
        return new SettlementDetailResponse(s.getId(), KoreanFormat.dateShort(b.getServiceDate()) + " · " + itemTitle(b),
                KoreanFormat.timeRange(b.getServiceTime(), b.getServiceTime().plusMinutes(b.getDurationMinutes())) + " · " + KoreanFormat.maskName(customer) + " 고객",
                List.of(new SettlementDetailResponse.Row("서비스 금액", KoreanFormat.won(s.getServiceAmount()), "default"),
                        new SettlementDetailResponse.Row("플랫폼 수수료", KoreanFormat.wonSigned(-s.getPlatformFee()), "secondary"),
                        new SettlementDetailResponse.Row("기타", s.getOtherFee() == 0 ? KoreanFormat.won(0) : KoreanFormat.wonSigned(-s.getOtherFee()), "secondary")),
                new SettlementDetailResponse.Row(LABEL_FINAL, KoreanFormat.won(s.getFinalAmount()), "brand"));
    }

    /** "강남구 기본청소" = 시군구(없으면 도로명 주소에서 시/도를 뺀 두 어절, KoreanFormat.addressArea) + 짧은 카테고리 라벨 */
    String itemTitle(Booking b) {
        String area = b.getSigungu() != null ? b.getSigungu() : KoreanFormat.addressArea(b.getRoadAddress());
        return area + " " + catalog.shortLabel(b.getCategoryKey());
    }

    /** 정산 목록의 예약을 한 번에 불러와 '예약 id → 예약' 표(Map)로 만든다 — 정산마다 따로 조회하지 않게 */
    private Map<UUID, Booking> bookingsOf(List<Settlement> list) {
        return bookings.findAll(list.stream().map(Settlement::getBookingId).toList()).stream().collect(Collectors.toMap(Booking::getId, Function.identity()));
    }
}
