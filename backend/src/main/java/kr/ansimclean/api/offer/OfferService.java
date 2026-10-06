package kr.ansimclean.api.offer;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingDispatcher;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.booking.MatchingRetryWindow;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.BookingAssigned;
import kr.ansimclean.api.common.events.BookingRescheduled;
import kr.ansimclean.api.common.events.MatchingFailed;
import kr.ansimclean.api.common.events.OffersDispatched;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.job.JobService;
import kr.ansimclean.api.live.LiveSessionService;
import kr.ansimclean.api.offer.dto.AcceptResponse;
import kr.ansimclean.api.offer.dto.CallDetailResponse;
import kr.ansimclean.api.offer.dto.CallItem;
import kr.ansimclean.api.offer.dto.RedispatchResponse;
import kr.ansimclean.api.offer.dto.CallsResponse;
import kr.ansimclean.api.offer.dto.RejectResponse;
import kr.ansimclean.api.profile.AddressRegion;
import kr.ansimclean.api.profile.ManagerProfile;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.schedule.AvailabilityService;
import kr.ansimclean.api.schedule.dto.AvailabilityStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 매칭 = "콜" (BACKEND_PLAN.md §6.2). BookingDispatcher 의 실제 구현(@Primary) — 예약 서비스가 생성·변경·취소 때 부른다.
 * 수락은 매니저 행 → 예약 → 오퍼 순서로 잠그고(데드락 방지), 최종 방어는 bookings_manager_no_overlap 제약(위반 = OFFER_TAKEN).
 * 재매칭(거절·만료로 회차가 닫힘)은 지금 매칭 구간(마지막 일정 변경·매니저 방문 취소 이후)에 이미 오퍼를 받은 매니저를 제외하고
 * max_dispatch_rounds 회차까지만 — 그 뒤는 운영자 로그(§13-8). 예외: 운영자 재매칭(redispatchByAdmin)은 이미 받은 매니저에게도 다시 보낸다.
 *
 * 부르는 곳 예: 예약 서비스(BookingDispatcher 로 — 생성·일정 변경·취소 때), OfferController(매니저 콜 API).
 * 만료·자동 재매칭은 스케줄러(OfferExpirer·MatchingRetry)가 부른다.
 * 푸시는 outbox.publish 로 DB 에 적어 두기만 한다 (아웃박스, CODE_GUIDE.md 3-6).
 * 트랜잭션: BookingDispatcher 메서드(dispatch·onRescheduled 등)는 부르는 쪽(예: BookingService·JobService)의 묶음에 합쳐진다 — 여기서 예외가 나면
 * 예약 생성·일정 변경 같은 예약 쪽 변경도 함께 되돌려진다. 스케줄러가 부르는 expireDue·retryFailedMatches·releaseHeldDispatches 는 실행 한 번이 한 묶음이다.
 * Primary = BookingDispatcher 구현이 여럿일 때(예: 로그만 남기는 NoopBookingDispatcher) 스프링이 이쪽을 넣게 한다.
 * 밤(서울 21시~08시)에는 새 콜을 보내는 자동 경로(예약 생성·일정 변경·방문 취소 뒤·거절·만료 뒤 다음 회차·정기 회차)가 콜 대신 예약에 보류 표시만 남기고,
 * 아침 첫 자동 재매칭(MatchingRetry 08:05)이 releaseHeldDispatches 로 보낸다 (문제 목록 2번, 사용자 결정 ③). 미루면 약속까지 3시간이 안 남는 급한 예약과
 * 운영자 재매칭은 바로 보낸다. 밤·급한 예약의 기준은 booking/MatchingRetryWindow 한 곳에 있다.
 */
@Service
@Primary
@Transactional
public class OfferService implements BookingDispatcher {
    // app_settings 의 키 이름 (CODE_GUIDE.md 3-10 — 운영 중에 값을 바꿀 수 있다)
    public static final String SETTING_OFFER_TTL = "offer_ttl_minutes"; // 일반 콜 유효 시간(분)
    public static final String SETTING_PREFERRED_TTL = "preferred_offer_ttl_minutes"; // 우선 콜 유효 시간(분)
    public static final String SETTING_MAX_ROUNDS = "max_dispatch_rounds"; // 재매칭 최대 회차 — 이 회차의 콜까지 모두 닫히면 매칭 실패 (회차는 예약 전체에서 센다)
    public static final String SETTING_ETA = "eta_placeholder"; // 콜 카드의 이동 시간 칸 값
    static final String NOTE_PREFIX = "특이사항: "; // 콜 카드 note 앞에 붙는 말
    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    private final OfferRepository offers;
    private final BookingService bookings;
    private final ProfileService profiles;
    private final AvailabilityService availability;
    private final JobService jobs;
    private final LiveSessionService live;
    private final CatalogService catalog;
    private final AppSettingsService settings;
    private final OutboxService outbox;
    private final Idempotent idempotent;
    private final AppClock clock;
    private final kr.ansimclean.api.profile.ManagerPenaltyService penalties; // 방문 취소·미방문 이력 — 이 예약의 콜·다음 회차 우선 콜에서 뺄 매니저를 고른다

    // 콜 탭 수신 상태 카드의 문구 (calls() 에서 쓴다. 앱은 콜이 없을 때 이 카드를 보여 준다)
    static final String APPROVAL_PENDING = "승인 대기 중";
    static final String SUSPENDED = "콜 배정 정지 중"; // 매니저 정책 (docs/manager-policy.md)
    static final String APPROVAL_PENDING_DETAIL = "신원 확인과 기본 교육 이수가 끝나면 청소 요청을 받을 수 있어요";
    /** (코드에서 정함) 콜을 받은 뒤 그 시간에 휴무·근무 불가를 넣었거나 근무시간을 바꿔 수락을 막을 때 — 앱 콜 목록·상세가 그대로 보인다 (문제 목록 7번) */
    static final String OUTSIDE_AVAILABILITY = "근무 일정과 맞지 않는 시간이라 수락할 수 없어요. 근무 일정 관리에서 확인해주세요";

    public OfferService(OfferRepository offers, BookingService bookings, ProfileService profiles, AvailabilityService availability, JobService jobs,
                        LiveSessionService live, CatalogService catalog, AppSettingsService settings, OutboxService outbox, Idempotent idempotent, AppClock clock,
                        kr.ansimclean.api.profile.ManagerPenaltyService penalties) {
        this.penalties = penalties;
        this.offers = offers;
        this.bookings = bookings;
        this.profiles = profiles;
        this.availability = availability;
        this.jobs = jobs;
        this.live = live;
        this.catalog = catalog;
        this.settings = settings;
        this.outbox = outbox;
        this.idempotent = idempotent;
        this.clock = clock;
    }

    // ── BookingDispatcher (예약 서비스가 같은 트랜잭션에서 부른다) ──

    /**
     * 이번 회차(booking.dispatchRound)로 후보 전원에게 일반 콜을 보낸다. 밤이면 보내지 않고 아침으로 미룬다(heldForNight) —
     * 아침에는 excludeManagerIds 대신 '지금 매칭 구간에서 이미 콜을 받은 매니저' 를 뺀다. 여기로 오는 exclude 는 그 안에 들거나(재매칭·우선 콜 거절)
     * 이 방문을 취소한 매니저(dispatchNow 의 bookingDroppers 로 늘 빠진다)라 결과가 같다
     */
    @Override // BookingDispatcher 인터페이스의 메서드를 여기서 구현한다는 표시
    public void dispatch(Booking booking, Set<UUID> excludeManagerIds) {
        if (heldForNight(booking, null, false)) return;
        dispatchNow(booking, excludeManagerIds);
    }

    /**
     * dispatch 의 본체 — 밤이어도 지금 보낸다 (아침 보내기·운영자 재매칭·낮의 우선 콜 대신 일반 매칭이 부른다).
     * 빼는 매니저: excludeManagerIds + 이 방문을 취소한 매니저 + 이번 회차에 이미 콜을 받은 매니저.
     * 후보가 한 명도 없으면 매칭 실패 표시 + 고객 알림(matchingFailed)으로 끝난다 (이미 실패 표시가 있으면 다시 알리지 않는다)
     */
    private void dispatchNow(Booking booking, Set<UUID> excludeManagerIds) {
        dispatchNow(booking, excludeManagerIds, null);
    }

    /**
     * 위와 같고, quietManagerId(없으면 null)는 콜은 받되 콜 푸시(OffersDispatched '새 콜이 도착했어요')의 받는 사람에서 뺀다 —
     * 운영자 재매칭이 밤에 미뤄 둔 일정 변경 전 매니저에게 쓴다. 그 매니저에게는 BookingRescheduled('예약 일정이 변경됐어요 … 새 콜을 확인해주세요')
     * 하나가 콜 목록으로 안내한다 (합친 38건 검토 R2·R17 — 낮의 일정 변경·아침 보내기와 같게, 문제 목록 15번 결정 A)
     */
    private void dispatchNow(Booking booking, Set<UUID> excludeManagerIds, UUID quietManagerId) {
        Set<UUID> skip = new HashSet<>(excludeManagerIds);
        skip.addAll(penalties.bookingDroppers(booking.getId())); // 이 방문을 취소한 매니저는 몇 번 다시 콜을 돌려도 빠진다 — 회차가 바뀌면 exclude 가 새로 시작해서 다시 받았다 (자체 점검 #6)
        for (Offer o : offers.findByBookingId(booking.getId())) {
            if (o.getRound() == booking.getDispatchRound()) skip.add(o.getManagerId()); // 같은 회차에 두 번 보낼 수 없다 (unique)
        }
        List<UUID> candidates = findCandidates(booking, skip);
        if (candidates.isEmpty()) {
            log.error("매칭 후보 없음 — 운영자 확인 필요(§13-8): 예약={} 회차={} 지역={} {}", booking.getId(), booking.getDispatchRound(), booking.getSido(), booking.getSigungu());
            matchingFailed(booking);
            return;
        }
        send(booking, candidates, settings.integer(SETTING_OFFER_TTL), false, true, quietManagerId);
    }

    /**
     * 정기청소 회차를 직전 매니저에게 먼저 주는 우선 콜 (BookingService.startAssigning 이 managerId 가 있을 때 부른다).
     * 그 매니저가 조건에 안 맞으면 우선 콜 없이 곧바로 일반 매칭으로 넘어간다. 밤이면 아침으로 미루고, 아침에도 이 매니저에게 먼저 우선 콜로 보낸다
     */
    @Override
    public void dispatchPreferred(Booking booking, UUID managerId) {
        // 이 정기청소를 취소·미방문한 매니저에게는 다음 회차를 먼저 주지 않는다 — 스케줄러·일정 변경 어느 길로 와도 여기서 (docs/manager-policy.md §4, 전체 재점검 B2)
        if (penalties.droppedSeries(managerId, booking.getSeriesId())) {
            dispatch(booking, Set.of()); // 우선 제안만 빠진다 — 일반 콜로는 계속 받을 수 있다(정책 §4, 스케줄러 경로와 같게, 자체 점검 #7). 이 회차를 취소했으면 dispatch 가 뺀다. 밤이면 dispatch 가 미룬다
            return;
        }
        if (heldForNight(booking, managerId, false)) return;
        if (!offerPreferred(booking, managerId, true)) dispatchNow(booking, Set.of());
    }

    /**
     * 밤이면 새 콜을 보내지 않고 예약에 보류 표시를 남긴 뒤 true — 부른 쪽은 보내지 않고 끝낸다. 아침(releaseHeldDispatches)에 보낸다.
     * 낮이거나, 미루면 약속까지 3시간이 안 남는 급한 예약이면 false — 지금 보낸다 (MatchingRetryWindow.holdUntilMorning).
     * preferredManagerId·rescheduled = 아침에 먼저 우선 콜을 줄 매니저와 그가 일정 변경 전 매니저인지 (Booking.holdDispatch)
     */
    private boolean heldForNight(Booking booking, UUID preferredManagerId, boolean rescheduled) {
        Instant now = clock.now();
        if (!MatchingRetryWindow.holdUntilMorning(now, booking.getStartsAt())) return false;
        booking.holdDispatch(now, preferredManagerId, rescheduled);
        log.info("밤이라 새 콜을 아침으로 미룸: 예약={} 회차={}", booking.getId(), booking.getDispatchRound());
        return true;
    }

    /**
     * 미뤄 둔 매칭을 지금 보낸다 — 아침 보내기(releaseHeldDispatches)와, 밤에 미룬 예약의 새 일정이 급해졌을 때(onRescheduled)가 부른다.
     * 우선 매니저가 있으면 그에게 먼저(정기청소 직전 매니저면 그사이 이 정기청소를 취소·미방문했는지 다시 본다), 안 되면 지금 매칭 구간에서
     * 이미 콜을 받은 매니저를 빼고 후보 전원에게. 일정 변경 전 매니저였으면 '예약 일정이 변경됐어요' 안내를 여기서 적는다 — 밤에는 BookingService 가 적지 않았다
     */
    private void sendHeld(Booking b) {
        UUID preferred = b.getDispatchHeldManagerId();
        boolean rescheduled = b.isDispatchHeldRescheduled();
        b.clearDispatchHold();
        boolean dropped = preferred != null && !rescheduled && penalties.droppedSeries(preferred, b.getSeriesId());
        boolean offered = preferred != null && !dropped && offerPreferred(b, preferred, !rescheduled); // 일정 변경 전 매니저의 우선 콜은 콜 푸시 없이 (15번)
        if (!offered) dispatchNow(b, offeredInSegment(b));
        if (rescheduled) outbox.publish(new BookingRescheduled(b.getId(), b.getCustomerId(), preferred, offered));
    }

    /**
     * 아침: 밤에 미뤄 둔 매칭을 보낸다 (MatchingRetry 가 08:05~20:05 매시 부른다 — 보통 08:05 첫 실행에서 끝난다). 보낸 예약 수를 돌려준다.
     * 잠금 순서 = 예약 id 오름차순 (OfferExpirer·자동 재매칭과 같다). 실행 전체가 한 트랜잭션이라 한 건에서 예외가 나면 이번 실행 전체가 되돌려지고
     * 다음 시각(1시간 뒤)에 다시 시도한다 — 그동안 고객 카드는 배정 전 문구 그대로다('밤에 미룬 매칭 보내기 실패' 로그)
     */
    public int releaseHeldDispatches() {
        int count = 0;
        for (UUID id : bookings.dispatchHeldIds()) {
            Booking b = bookings.lockForUpdate(id);
            if (b.getStatus() != BookingStatus.ASSIGNING || !b.isDispatchHeld()) continue; // 잠그는 사이 취소됐거나 이미 보냈다
            sendHeld(b);
            count++;
        }
        return count;
    }

    /**
     * 한 매니저에게 우선 오퍼(10분). 일반 후보와 같은 조건(승인·정지 아님·지역·근무 가능·겹치는 작업 없음)을 통과할 때만 —
     * 근무 불가 날이거나 같은 시간에 다른 작업이 있으면 수락해도 안 되거나(OFFER_TAKEN) 쉬는 날 배정돼 10분을 헛되이 기다렸다 (자체 점검 #21). 보냈으면 true
     * callPush = 콜 푸시(OffersDispatched → '단골 고객의 정기청소 콜')를 적을지. 정기청소 다음 회차는 true, 일정 변경의 원래 매니저는 false —
     * 그 매니저에게는 '예약 일정이 변경됐어요 … 새 콜을 확인해주세요'(BookingRescheduled)가 콜 목록으로 안내하므로 1회 예약에 '정기청소' 문구가 가지 않는다 (문제 목록 15번)
     */
    private boolean offerPreferred(Booking booking, UUID managerId, boolean callPush) {
        if (!profiles.isApprovedManager(managerId)) return false; // 탈퇴·승인 취소·정지
        // 승인된 매니저 목록에서 이 사람을 찾는다 (정지·탈퇴면 목록에 없어 null)
        ManagerProfile m = profiles.approvedManagers().stream().filter(x -> x.getManagerId().equals(managerId)).findFirst().orElse(null);
        if (m == null || !eligible(m, booking)) return false;
        send(booking, List.of(managerId), settings.integer(SETTING_PREFERRED_TTL), true, callPush);
        return true;
    }

    /**
     * 예약이 취소됐을 때 (고객 취소·오래된 예약 정리): 답을 기다리던 콜을 모두 닫아 매니저 콜 목록에서 사라지게 하고, 밤에 미뤄 둔 매칭도 지운다.
     * 미뤄 둔 '일정 변경 전 매니저' 안내('예약 일정이 변경됐어요')는 보내지 않는다 — 대신 BookingService.cancelOne 이 그 매니저에게
     * '예약이 취소됐어요'(BookingCancelled)를 밤에도 바로 적는다. 그래서 cancelOne 은 이 메서드가 보류를 지우기 전에 그 매니저를 읽어 둔다 (합친 38건 검토 R1·R14)
     */
    @Override
    public void onCancelled(Booking booking) {
        for (Offer o : offers.findByBookingIdAndStatus(booking.getId(), Offer.PENDING)) o.cancel();
        booking.clearDispatchHold();
    }

    /**
     * 고객이 일정을 바꿨을 때 (BookingService): 옛 일정의 콜을 모두 무효로 하고, 체크리스트를 비우고, 라이브 세션을 새로 준비한 뒤
     * 이 회차를 맡았던 매니저에게 먼저 우선 콜을 보낸다(없거나 조건이 안 맞으면 후보 전원에게 일반 콜).
     * 우선 콜을 실제로 보냈으면 true (이전 매니저에게 가는 일정 변경 푸시 문구가 이 값으로 갈린다).
     * 새 매칭 구간은 Booking.reschedule 이 이미 시작했다 — 옛 일정의 콜을 받은 매니저도 다시 받을 수 있다.
     * 이 우선 콜은 콜 푸시를 따로 적지 않는다 — 예전에는 1회 예약에도 '단골 고객의 정기청소 콜' 이 '일정이 변경됐어요' 와 함께 갔다 (문제 목록 15번).
     * 밤이면 콜을 아침으로 미루고 false (그때는 원래 매니저 안내도 아침에 — 아래 heldForNight·sendHeld)
     */
    @Override
    public boolean onRescheduled(Booking booking, UUID previousManagerId) {
        for (Offer o : offers.findByBookingId(booking.getId())) {
            // 둘 중 하나만 효과가 있다: 수락했던 콜 → superseded, 대기 콜 → cancelled. 이미 닫힌 콜은 그대로
            o.supersede();
            o.cancel();
        }
        jobs.clearChecklist(booking.getId());
        live.ensureCreated(booking.getId()); // 라이브 세션 행을 없으면 만들고 있으면 처음 상태로 되돌린다
        // 밤: 원래 매니저(있으면)를 우선 매니저로 남기고 미룬다 → false. BookingService 는 보류면 안내를 적지 않고, 아침에 sendHeld 가 우선 콜과 안내를 함께 보낸다.
        // 밤사이 두 번째 변경이면 previousManagerId 가 null 이라 앞서 남긴 원래 매니저가 그대로 남는다 (Booking.holdDispatch)
        if (heldForNight(booking, previousManagerId, true)) return false;
        // 밤에 미뤄 둔 매칭이 있는데 새 일정이 가까워 지금 보내야 한다 — 아침 보내기와 같게 (원래 매니저 안내도 sendHeld 가 적는다)
        if (booking.isDispatchHeld()) {
            sendHeld(booking);
            return false;
        }
        // 이 회차를 맡았던 매니저 — '다음 회차' 가 아니라 자기 회차라 정기청소 취소 이력(droppedSeries)은 보지 않는다. 예전에는 빠지는데도 '새 콜' 푸시를 받았다 (자체 점검 #18)
        if (previousManagerId != null && offerPreferred(booking, previousManagerId, false)) return true;
        dispatchNow(booking, Set.of());
        return false;
    }

    /** 매니저가 맡았던 방문을 취소했을 때 (JobService): 옛 콜을 정리하고 새 회차·새 매칭 구간으로 다시 매칭한다. 취소한 매니저만 뺀다 */
    // 후보가 한 명도 없으면 dispatch 가 바로 매칭 실패 표시·알림(MatchingFailed)을 적는다 — JobService 는 그 표시를 보고
    // '다른 매니저를 찾고 있어요'(ManagerReleased)를 적지 않는다 (문제 목록 5번). 밤이면 dispatch 가 아침으로 미룬다
    @Override
    public void onReleased(Booking booking, UUID releasedManagerId) {
        for (Offer o : offers.findByBookingId(booking.getId())) {
            o.supersede(); // 취소한 매니저의 accepted — 그대로 두면 다음 매니저의 수락이 offers_one_accepted 에 걸려 OFFER_TAKEN 이 된다
            o.cancel();
        }
        booking.restartAfterRelease(); // 새 회차(+ 새 매칭 구간) — 같은 회차면 처음 콜을 받았던(수락 때 cancelled 된) 매니저 전원이 다시 빠진다
        dispatch(booking, Set.of(releasedManagerId));
    }

    /** §6.2-1 후보: approved ∧ 활동 지역 ∧ 요일 근무시간·근무불가 ∧ 겹치는 작업 없음 */
    List<UUID> findCandidates(Booking booking, Set<UUID> exclude) {
        LocalTime start = booking.getServiceTime();
        LocalTime end = start.plusMinutes(booking.getDurationMinutes());
        if (!end.isAfter(start)) end = LocalTime.MAX; // 자정을 넘는 작업은 그날 끝까지로 본다
        // (∧ = 그리고) 위 start·end 는 이 메서드에서 더 쓰이지 않는다 — 시간 검사는 eligible 이 같은 계산을 다시 해서 한다.
        // 승인됐고 정지·탈퇴가 아닌 매니저 전원을 돌며 exclude 에 없고 조건에 맞는 사람만 모은다. 결과는 매니저 id 목록
        List<UUID> out = new ArrayList<>();
        for (ManagerProfile m : profiles.approvedManagers()) {
            if (!exclude.contains(m.getManagerId()) && eligible(m, booking)) out.add(m.getManagerId());
        }
        return out;
    }

    /** 한 매니저가 이 예약의 후보인가: 활동 지역 ∧ 그 시간 근무 가능 ∧ 겹치는 작업 없음 (승인·정지는 approvedManagers 가 거른다) */
    private boolean eligible(ManagerProfile m, Booking booking) {
        UUID id = m.getManagerId();
        // 메모리에서 끝나는 지역 검사를 먼저 해서, 지역이 다르면 DB 조회(근무 가능·겹치는 작업)를 하지 않는다
        return inArea(m, booking) && worksThen(id, booking) && !bookings.hasOverlappingWork(id, booking.getStartsAt(), booking.getEndsAt());
    }

    /**
     * 이 매니저가 그 방문 시간에 근무하는가 (요일 근무시간·휴무·근무 불가, AvailabilityService.isAvailable).
     * 후보 판정(eligible)과 수락(doAccept)이 같은 기준을 쓰게 한 곳에 둔다 — 콜을 받은 뒤 근무 불가를 넣었는데 수락되던 문제 (문제 목록 7번)
     */
    private boolean worksThen(UUID managerId, Booking booking) {
        LocalTime start = booking.getServiceTime();
        LocalTime end = start.plusMinutes(booking.getDurationMinutes());
        if (!end.isAfter(start)) end = LocalTime.MAX; // 자정을 넘는 작업은 그날 끝까지로 본다
        return availability.isAvailable(managerId, booking.getServiceDate(), start, end);
    }

    /** areas 에 '시도 시군구' 가 있는가. 예약에 시군구가 없으면(주소 검색 API 전) 시도만 비교 */
    static boolean inArea(ManagerProfile m, Booking b) {
        if (b.getSido() == null) return false; // 예약 주소의 지역을 모르면 아무에게도 보내지 않는다
        String managerSido = AddressRegion.canonicalSido(m.getSido());
        if (b.getSigungu() == null) return b.getSido().equals(managerSido);
        return m.getAreas().stream().anyMatch(area -> canonicalArea(area).equals(b.getSido() + " " + b.getSigungu()));
    }

    /** "서울 강남구" 로 등록된 활동 지역도 "서울특별시 강남구" 와 같게 본다 */
    private static String canonicalArea(String area) {
        int space = area.indexOf(' ');
        if (space < 0) return AddressRegion.canonicalSido(area);
        return AddressRegion.canonicalSido(area.substring(0, space)) + " " + area.substring(space + 1).trim();
    }

    /**
     * 콜을 실제로 만든다: 매니저마다 대기 콜 한 행을 저장하고(만료 = 지금 + ttlMinutes 분, 금액 = 예약의 매니저 수령액),
     * callPush 면 콜 알림 푸시 요청을 아웃박스에 한 건 적는다 (트랜잭션이 끝난 뒤 PushHandler 가 매니저들에게 보낸다.
     * 일반 콜은 '새 콜이 도착했어요', 우선 콜은 단골 고객 문구). callPush 가 false 인 곳은 일정 변경의 우선 콜뿐이다(offerPreferred)
     */
    private void send(Booking booking, List<UUID> managerIds, int ttlMinutes, boolean preferred, boolean callPush) {
        send(booking, managerIds, ttlMinutes, preferred, callPush, null);
    }

    /** 위와 같고, quietManagerId(없으면 null)는 콜 행은 만들되 콜 푸시 받는 사람에서 뺀다 (dispatchNow 참고). 받을 사람이 없으면 푸시를 적지 않는다 */
    private void send(Booking booking, List<UUID> managerIds, int ttlMinutes, boolean preferred, boolean callPush, UUID quietManagerId) {
        Instant now = clock.now();
        Instant expires = now.plus(Duration.ofMinutes(ttlMinutes));
        for (UUID m : managerIds) {
            offers.save(Offer.pending(booking.getId(), m, booking.getDispatchRound(), booking.managerPay(), expires, preferred, now));
        }
        if (!callPush) return;
        List<UUID> pushTo = managerIds.stream().filter(m -> !m.equals(quietManagerId)).toList();
        if (!pushTo.isEmpty()) outbox.publish(new OffersDispatched(booking.getId(), pushTo, preferred));
    }

    // ── 수락·거절·만료 ──

    /** 콜 수락 (§6.2-3). Idempotency-Key 가 있으면 재요청은 저장된 { jobId } */
    public AcceptResponse accept(UUID managerId, UUID offerId, String idempotencyKey) {
        // 멱등 키 (CODE_GUIDE.md 3-7). 키가 없으면 그냥 실행. 수락이 오류(OFFER_TAKEN 등)로 끝나면 키 기록도 함께 되돌려져 같은 키로 다시 시도할 수 있다
        return idempotent.run(idempotencyKey, managerId, "accept:" + offerId, AcceptResponse.class, () -> doAccept(managerId, offerId));
    }

    /**
     * 실제 수락 처리. 매니저 → 예약 → 오퍼 순서로 잠근다. 거절·만료도 예약 → 오퍼 순서라,
     * 두 요청이 서로 상대가 잡은 행을 기다리다 멈추는 데드락이 생기지 않는다.
     * 규칙 검사를 통과하면 예약을 이 매니저에게 배정하고, 같은 예약의 다른 대기 콜을 닫고, 체크리스트·라이브 세션을 준비하고,
     * 상태 이력을 남기고, 고객에게 '매니저 배정' 알림을 아웃박스에 적는다. 결과 jobId = 예약 id
     */
    private AcceptResponse doAccept(UUID managerId, UUID offerId) {
        // 0. 잠그기 전에 예약 id 만 알아낸다 (내 콜이 아니면 NOT_FOUND)
        UUID bookingId = offers.findBookingId(offerId, managerId).orElseThrow(ApiException::notFound);
        ManagerProfile me = profiles.lockManager(managerId);   // 1. 매니저 행 — 같은 매니저의 동시 수락은 여기서 줄을 선다
        // 아래 두 검사에 걸리는 오퍼(정지·승인 취소 전에 받은 것)는 콜 목록·홈에 보이지 않고(pendingFor) OfferExpirer 가 30초 안에 닫는다 —
        // 그 사이 열어 둔 상세 화면에서 수락을 누른 경우를 여기서 막는다 (문제 목록 6번)
        if (!me.isApproved()) throw new ApiException(ErrorCode.INVALID_STATE, "승인된 매니저만 수락할 수 있어요"); // 승인 취소·탈퇴 뒤 남은 오퍼
        if (me.isSuspended(clock.today())) throw new ApiException(ErrorCode.INVALID_STATE, "콜 배정 정지 중이라 수락할 수 없어요"); // 정지 전에 받은 오퍼도 (감사 M-07, docs/manager-policy.md §3)
        Booking booking = bookings.lockForUpdate(bookingId);   // 2. 예약
        Offer offer = offers.findForUpdate(offerId).orElseThrow(ApiException::notFound); // 3. 오퍼
        if (penalties.bookingDroppers(bookingId).contains(managerId)) throw new ApiException(ErrorCode.OFFER_TAKEN); // 취소 전에 받아 둔 옛 오퍼로 다시 맡지 않게 (자체 점검 #6)
        Instant now = clock.now();
        offer.accept(now);                                      // pending 아니면 OFFER_TAKEN, 만료면 OFFER_EXPIRED
        if (booking.getStatus() != BookingStatus.ASSIGNING) throw new ApiException(ErrorCode.OFFER_EXPIRED); // 취소됨 포함
        // 콜을 받은 뒤 그 시간에 휴무·근무 불가를 넣었거나 근무시간을 줄였으면 막는다 — 후보 판정과 같은 기준(worksThen).
        // 그대로 두면 근무 불가(휴무)와 맡은 작업이 겹친 채 남아 미방문·방문 취소로 이어졌다 (문제 목록 7번, 사용자 결정 ①). 활동 지역은 다시 보지 않는다
        if (!worksThen(managerId, booking)) throw new ApiException(ErrorCode.INVALID_STATE, OUTSIDE_AVAILABILITY);
        if (bookings.hasOverlappingWork(managerId, booking.getStartsAt(), booking.getEndsAt())) throw new ApiException(ErrorCode.OFFER_TAKEN);
        BookingStatus from = booking.getStatus(); // 상태 이력에 '어디서 → 어디로' 를 남기려고 바꾸기 전 상태를 기억한다
        // DB 제약 위반(bookings_manager_no_overlap = 한 매니저에게 시간이 겹치는 작업, offers_one_accepted = 한 예약에 수락 둘)은
        // DataIntegrityViolationException 으로 온다. 위 잠금·검사를 비켜 간 경우의 최종 방어라 OFFER_TAKEN('이미 마감된 요청') 으로 바꿔 돌려준다.
        // 이 예외로 배정·콜 닫기·알림 행까지 이 트랜잭션 전체가 되돌려진다
        try {
            booking.assign(managerId, now);
            for (Offer other : offers.findByBookingIdAndStatus(bookingId, Offer.PENDING)) other.cancel();
            jobs.createChecklist(booking);
            live.ensureCreated(bookingId);
            bookings.recordTransition(booking, from, managerId, null);
            outbox.publish(new BookingAssigned(bookingId, booking.getCustomerId(), managerId));
            offers.flush(); // bookings_manager_no_overlap 위반을 여기서 받는다
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.OFFER_TAKEN);
        }
        return new AcceptResponse(bookingId);
    }

    /**
     * 콜 거절 (§6.2-4). 이미 내가 거절한 콜이면 저장된 사유를 그대로 돌려준다(멱등 — 응답이 늦어 다시 눌러도 같은 결과).
     * 그 밖에 닫힌 콜은 Offer.reject 가 닫힌 이유대로 오류를 던진다(내가 수락 = INVALID_STATE, 다른 이유로 닫힘 = OFFER_TAKEN, 만료 = OFFER_EXPIRED).
     * 예전에는 닫힌 콜도 200 이라 앱의 '이미 마감된 요청이에요' 가 나오지 않았다 (문제 목록 10번)
     */
    public RejectResponse reject(UUID managerId, UUID offerId, String reason) {
        if (!catalog.isReason("reject", reason)) throw ApiException.validation("알 수 없는 거절 사유예요"); // 관리 목록(catalog_reasons)에 있는 거절 사유만 받는다
        UUID bookingId = offers.findBookingId(offerId, managerId).orElseThrow(ApiException::notFound);
        Booking booking = bookings.lockForUpdate(bookingId); // 잠금 순서 예약 → 오퍼 (수락·만료와 같게)
        Offer offer = offers.findForUpdate(offerId).orElseThrow(ApiException::notFound);
        if (Offer.REJECTED.equals(offer.getStatus())) return new RejectResponse(offerId, offer.getRejectReason()); // 이미 거절했다 — 처음 사유 그대로, 재매칭도 다시 하지 않는다
        offer.reject(reason, clock.now());
        afterClosed(booking, offer);
        return new RejectResponse(offerId, reason);
    }

    /** OfferExpirer(30초): pending ∧ 만료 → expired, 그 예약에 pending 이 없으면 재매칭 (§6.2-5) */
    public int expireDue() {
        Instant now = clock.now();
        int count = 0;
        // id 목록을 먼저 받고 한 건씩 잠근다. 목록을 받은 뒤 잠그기 전에 매니저가 수락·거절했을 수 있어서
        // 잠근 뒤의 최신 상태로 expire() 가 false 면 건너뛴다. 메서드 전체가 한 트랜잭션이라 한 건에서 예외가 나면 이번 실행의 만료가 모두 되돌려진다
        for (UUID id : offers.findExpiredPendingIds(now)) {
            UUID bookingId = offers.findBookingIdById(id).orElse(null);
            if (bookingId == null) continue; // 그사이 행이 사라졌다
            Booking booking = bookings.lockForUpdate(bookingId);   // 잠금 순서: 예약 → 오퍼 (수락과 같다)
            Offer offer = offers.findForUpdate(id).orElse(null);
            if (offer == null || !offer.expire(now)) continue;
            count++;
            afterClosed(booking, offer);
        }
        return count;
    }

    /**
     * OfferExpirer(30초, 만료 처리 다음): 정지·승인 취소·탈퇴로 더는 수락할 수 없는 매니저의 대기 콜을 닫고(cancelled) 필요하면 다음 후보에게 보낸다 (문제 목록 6번).
     * 예전에는 정지 전에 받은 콜이 만료될 때까지 남아 콜 목록에 수락할 수 없는 카드가 보였고, 다른 후보가 모두 거절해도 그 콜이 '대기' 로 세어져
     * 재매칭이 최대 30분(우선 콜 10분) 늦어졌다. 닫은 뒤는 거절·만료와 같다(afterClosed) — 밤이면 이어지는 재매칭은 아침으로 미룬다.
     * 잠금 순서 = 예약 → 오퍼, 예약 id 순 (expireDue 와 같다). 목록을 받은 뒤 잠그기 전에 다시 받을 수 있게 됐으면(정지 면제 등) 건너뛴다 —
     * 이 재검사는 DB 의 지금 값으로 한다(profiles.isApprovedManagerNow). 닫은 콜 수를 돌려준다
     */
    public int closeBlockedOffers() {
        List<UUID> holders = offers.findPendingManagerIds();
        if (holders.isEmpty()) return 0; // 대기 콜이 없으면 매니저 목록도 읽지 않는다 (30초마다 도는 작업)
        Set<UUID> eligible = profiles.approvedManagers().stream().map(ManagerProfile::getManagerId).collect(Collectors.toSet()); // 승인 ∧ 정지 아님 ∧ 탈퇴 아님
        List<UUID> blocked = holders.stream().filter(m -> !eligible.contains(m)).toList();
        if (blocked.isEmpty()) return 0;
        int count = 0;
        for (UUID id : offers.findPendingIdsByManagerIds(blocked)) {
            UUID bookingId = offers.findBookingIdById(id).orElse(null);
            if (bookingId == null) continue; // 그사이 행이 사라졌다
            Booking booking = bookings.lockForUpdate(bookingId);
            Offer offer = offers.findForUpdate(id).orElse(null);
            if (offer == null || !offer.isPending()) continue; // 잠그는 사이 수락·거절·만료됐다
            // 잠그는 사이 다시 받을 수 있게 됐다(정지 면제 등). DB 의 지금 값으로 본다 — 위 approvedManagers() 가 이 매니저 객체를 이미 올려 두어
            // isApprovedManager 는 그 옛 정지 값을 돌려줬다 (합친 38건 검토 R3)
            if (profiles.isApprovedManagerNow(offer.getManagerId())) continue;
            offer.cancel();
            count++;
            afterClosed(booking, offer);
        }
        return count;
    }

    /** 거절·만료·정지 매니저 콜 닫기 뒤: 우선 오퍼였으면 그 매니저를 빼고 일반 매칭(같은 회차), 일반 오퍼가 모두 닫혔으면 다음 회차 재매칭 */
    void afterClosed(Booking booking, Offer offer) {
        if (booking.getStatus() != BookingStatus.ASSIGNING) return; // 이미 배정됐거나 취소된 예약이면 더 할 일이 없다
        if (offer.isPreferred()) {
            dispatch(booking, Set.of(offer.getManagerId()));
            return;
        }
        if (offers.countByBookingIdAndRoundAndStatus(booking.getId(), booking.getDispatchRound(), Offer.PENDING) == 0) redispatch(booking);
    }

    /** 예약 행에 표시하고 고객에게 한 번 알린다 — 카드 문구가 매칭 실패 안내로 바뀐다 (검증 검토: 로그 한 줄뿐이었다) */
    // 카드의 매니저 칸 = app_settings.cleaner_matching_failed_label, 자동 재매칭이 약속 전에 못 보면 _final_label (BookingService.cleanerLabel)
    private void matchingFailed(Booking booking) {
        Instant now = clock.now();
        if (!booking.markMatchingFailed(now)) return; // 이미 실패 표시가 있으면 고객에게 다시 알리지 않는다
        // 안내가 실제로 가능한 길만 말하게 — 저녁에 실패한 다음 날 오전 예약은 자동 재매칭도 일정 변경도 안 된다 (자체 점검 후속 검토)
        boolean retryable = kr.ansimclean.api.booking.MatchingRetryWindow.retryPossible(now, booking.getStartsAt());
        boolean changeable = booking.isBeforeDeadline(now, catalog.cancelDeadlineHours());
        // retryable = 약속 전에 자동 재매칭이 한 번이라도 돌 수 있나, changeable = 아직 일정 변경 기한 안인가.
        // 고객 푸시 문구가 이 두 값의 조합으로 갈린다 (PushHandler 의 MatchingFailed)
        outbox.publish(new MatchingFailed(booking.getId(), booking.getCustomerId(), retryable, changeable));
    }

    /**
     * 이번 회차 콜이 거절·만료로 모두 닫혔을 때 다음 회차로 다시 보낸다 (afterClosed 가 부른다).
     * 이미 최대 회차(max_dispatch_rounds)에 닿았으면 더 보내지 않고 매칭 실패로 끝낸다.
     * 회차 번호는 매칭 구간과 상관없이 예약 전체에서 이어진다 — 일정 변경·방문 취소·운영자/자동 재매칭도 회차를 올리므로 그 뒤에는 남은 재매칭 횟수가 줄어든다
     */
    void redispatch(Booking booking) {
        if (booking.getDispatchRound() >= settings.integer(SETTING_MAX_ROUNDS)) {
            log.error("매칭 실패 — 운영자 확인 필요(§13-8): 예약={} 회차 {} 까지 모두 거절·만료", booking.getId(), booking.getDispatchRound());
            matchingFailed(booking);
            return;
        }
        // 지금 매칭 구간의 콜만 '받은 적' 으로 센다 — 방문 취소 전 다른 매니저 수락으로 닫힌 콜만 받은 매니저가 새 구간에서 빠지지 않게 (자동 재매칭과 같은 기준, 자체 점검 후속 검토)
        Set<UUID> offered = offeredInSegment(booking);
        booking.nextDispatchRound();
        dispatch(booking, offered); // 밤이면 회차만 올리고 아침으로 미룬다
    }

    /** 지금 매칭 구간(마지막 일정 변경·매니저 방문 취소 이후 회차)에 이 예약의 콜을 받은 매니저 — 재매칭·자동 재매칭·아침 보내기가 '이미 받은 매니저' 로 뺀다 */
    private Set<UUID> offeredInSegment(Booking b) {
        return offers.findByBookingId(b.getId()).stream().filter(o -> o.getRound() >= b.getMatchingFromRound()).map(Offer::getManagerId).collect(Collectors.toSet());
    }

    /** 자동 재매칭은 약속 시각까지 이만큼은 남은 예약만 — 30분 오퍼를 돌리고 매니저가 준비할 시간 (안내 문구와 같은 기준: MatchingRetryWindow) */
    static final Duration RETRY_MIN_LEAD = kr.ansimclean.api.booking.MatchingRetryWindow.MIN_LEAD;

    /**
     * 자동 재매칭 (offer/MatchingRetry, 사용자 결정 2026-09-27): 매칭 실패로 멈춘 예약에 **지금 매칭 구간에서 이 예약의 콜을 받지 않은** 후보가 생겼으면
     * (새로 승인됐거나 근무시간·지역을 바꾼 매니저) 새 회차로 그들에게만 보낸다. 이 구간에서 이미 받았던 매니저에게 되풀이해 보내지 않고,
     * 새 후보가 없으면 아무것도 하지 않는다(매시간 실패 알림을 반복하지 않게). 모두 모르는 체하면 평소처럼 회차가 끝나 다시 매칭 실패로 돌아간다.
     * 전원에게 다시 보내는 것은 운영자 redispatchByAdmin. 보낸 예약 수를 돌려준다
     */
    public int retryFailedMatches() {
        Instant now = clock.now();
        Instant startsAfter = now.plus(RETRY_MIN_LEAD);
        int count = 0;
        for (UUID id : bookings.matchingFailedIds(startsAfter)) { // 잠금 순서 = id 오름차순 (OfferExpirer 와 같다)
            Booking b = bookings.lockForUpdate(id);
            if (b.getStatus() != BookingStatus.ASSIGNING || b.getMatchingFailedAt() == null || !b.getStartsAt().isAfter(startsAfter)) continue; // 잠그는 사이 바뀌었다
            // 지금 매칭 구간(마지막 일정 변경·매니저 방문 취소 이후)의 콜만 '받은 적' 으로 센다 — 옛 일정의 콜이나 다른 매니저 수락으로 닫힌 콜을 받은 매니저가 영영 빠지지 않게
            Set<UUID> offered = offeredInSegment(b);
            List<UUID> fresh = findCandidates(b, offered);
            fresh.removeAll(penalties.bookingDroppers(id)); // 이 방문을 취소한 매니저는 늘 뺀다 (자체 점검 #6)
            if (fresh.isEmpty()) continue;
            for (Offer o : offers.findByBookingIdAndStatus(id, Offer.PENDING)) o.cancel();
            b.restartMatching(); // 새 회차 + 실패 표시 지움 — 다시 실패하면 고객에게 또 알린다
            send(b, fresh, settings.integer(SETTING_OFFER_TTL), false, true);
            log.info("자동 재매칭: 예약={} 회차={} 새 후보={}명", id, b.getDispatchRound(), fresh.size());
            count++;
        }
        return count;
    }

    /**
     * 운영자 재매칭 (POST /admin/bookings/{id}/redispatch, docs/runbook.md): 매칭 실패로 멈춘 예약을 새 회차로 다시 돌린다.
     * 예전 runbook 은 SQL 로 표시만 지우면 '다음 재매칭 스케줄러가 집어 간다' 고 했지만 그런 스케줄러는 없었다 (자체 점검 #5).
     * 운영자가 지역·근무시간을 고친 뒤라 이미 받았던 매니저에게도 다시 보낸다(이 방문을 취소한 매니저는 dispatch 가 뺀다). offered = 새로 보낸 오퍼 수(0 이면 다시 매칭 실패)
     * 부르는 곳: AdminOfferController. 매칭 중(assigning)인 예약이 아니면 INVALID_STATE. 남은 대기 콜을 닫고, 새 회차로 올리고(실패 표시도 지움),
     * 후보 전원에게 다시 보낸다. 실패 표시를 지웠으므로 후보가 없으면 고객에게 실패 알림이 한 번 더 간다.
     * 매칭 구간(matchingFromRound)은 그대로라, 이 뒤 자동 재매칭은 여기서 받은 매니저까지 '받은 적' 으로 센다
     */
    public RedispatchResponse redispatchByAdmin(UUID bookingId) {
        Booking b = bookings.lockForUpdate(bookingId);
        if (b.getStatus() != BookingStatus.ASSIGNING) throw new ApiException(ErrorCode.INVALID_STATE, "매칭 중인 예약만 다시 매칭할 수 있어요");
        // 운영자는 밤에도 지금 보낸다 — 밤에 미뤄 둔 매칭은 지운다. 미뤄 둔 '일정 변경 전 매니저' 가 있었으면 그 안내만 여기서 적는다(새 콜을 받았는지로 문구가 갈린다).
        // 그 매니저도 일반 콜은 받지만 콜 푸시에서는 빠진다 — 안내 하나가 콜 목록으로 부른다(두 푸시가 함께 가지 않게, 합친 38건 검토 R2·R17)
        UUID heldPrevious = b.isDispatchHeldRescheduled() ? b.getDispatchHeldManagerId() : null;
        b.clearDispatchHold();
        for (Offer o : offers.findByBookingIdAndStatus(bookingId, Offer.PENDING)) o.cancel();
        b.restartMatching();
        dispatchNow(b, Set.of(), heldPrevious);
        List<Offer> sent = offers.findByBookingId(bookingId).stream().filter(o -> o.isPending() && o.getRound() == b.getDispatchRound()).toList();
        if (heldPrevious != null) {
            boolean reached = sent.stream().anyMatch(o -> o.getManagerId().equals(heldPrevious));
            outbox.publish(new BookingRescheduled(b.getId(), b.getCustomerId(), heldPrevious, reached));
        }
        log.warn("운영자 재매칭: 예약={} 회차={} 오퍼={}", bookingId, b.getDispatchRound(), sent.size());
        return new RedispatchResponse(bookingId, sent.size());
    }

    // ── 화면 ──

    /**
     * 콜 탭 화면 (GET /manager/offers): 내 살아 있는 콜 카드들 + 수신 상태 카드 + 승인 여부. tabBadge = 카드 수.
     * 수신 상태 카드는 정지 → 미승인 → 평소(근무시간) 순서로 정한다 — 정지된 매니저는 approved 도 false 지만 '승인 대기' 가 아니라 정지 안내를 본다.
     * 정지·미승인 매니저에게는 콜 카드를 싣지 않는다(pendingFor) — 수락할 수 없는 카드 대신 앱이 빈 목록에서 이 안내 카드를 그린다 (문제 목록 6번)
     */
    @Transactional(readOnly = true)
    public CallsResponse calls(UUID managerId) {
        Instant now = clock.now();
        List<Offer> pending = pendingFor(managerId);
        // 콜마다 예약을 따로 조회하지 않고 한 번에 불러와 '예약 id → 예약' 표(Map)로 만든다
        Map<UUID, Booking> byId = bookings.findAll(pending.stream().map(Offer::getBookingId).toList()).stream().collect(Collectors.toMap(Booking::getId, Function.identity()));
        String eta = blankToNull(settings.get(SETTING_ETA, String.class)); // 거리 계산 전까지는 비어 있다 → 앱이 줄을 그리지 않는다
        List<CallItem> items = pending.stream().filter(o -> byId.containsKey(o.getBookingId())).map(o -> toItem(o, byId.get(o.getBookingId()), eta, now)).toList(); // 예약을 못 찾은 콜은 뺀다
        boolean approved = profiles.isApprovedManager(managerId); // 승인됐고 정지·탈퇴가 아니면 true
        // 미승인·정지 매니저는 매칭 후보가 아니다 — '콜 수신 중' 대신 사실대로 (검증 검토 · 매니저 정책 2026-09-22)
        String suspended = profiles.suspensionNotice(managerId);
        AvailabilityStatus status;
        if (suspended != null) status = new AvailabilityStatus(SUSPENDED, suspended);
        else if (!approved) status = new AvailabilityStatus(APPROVAL_PENDING, APPROVAL_PENDING_DETAIL);
        else status = availability.status(managerId, clock.nowSeoul()); // 평소: 서울 시각 기준 근무시간 설정으로 '콜 수신 중' / '근무 시간 외'
        return new CallsResponse(items.size(), items, status, approved);
    }

    /**
     * 콜 상세 화면 (GET /manager/offers/{id}): 카드 내용 + 고객 요청사항(여러 줄을 줄바꿈으로 이어 붙인 한 문자열).
     * 내 콜이 아니면 NOT_FOUND, 이미 닫혔거나 만료 시각이 지났으면 OFFER_EXPIRED
     */
    @Transactional(readOnly = true)
    public CallDetailResponse call(UUID managerId, UUID offerId) {
        Offer o = offers.findByIdAndManagerId(offerId, managerId).orElseThrow(ApiException::notFound);
        Instant now = clock.now();
        if (!o.isPending() || o.isExpired(now)) throw new ApiException(ErrorCode.OFFER_EXPIRED);
        Booking b = bookings.find(o.getBookingId()).orElseThrow(ApiException::notFound);
        return CallDetailResponse.of(toItem(o, b, blankToNull(settings.get(SETTING_ETA, String.class)), now), String.join("\n", b.requestLines()));
    }

    /** 빈 문자열·공백만 있는 값도 null 로 바꾼다 — eta 설정이 비어 있으면 null 로 보내 앱이 그 줄을 그리지 않게 */
    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /**
     * 매니저의 살아 있는 콜 (만료 임박순). 홈의 "새로운 청소 요청" 도 이걸 쓴다.
     * 정지·승인 취소·탈퇴 매니저면 빈 목록 — 정지 전에 받은 콜도 수락할 수 없으니(doAccept) 카드를 보이지 않는다. 그 콜은 OfferExpirer 가 30초 안에 닫는다 (문제 목록 6번)
     */
    @Transactional(readOnly = true)
    public List<Offer> pendingFor(UUID managerId) {
        if (!profiles.isApprovedManager(managerId)) return List.of();
        Instant now = clock.now();
        // DB 에서는 상태만 거른다 — 만료 시각이 지났지만 OfferExpirer(30초 주기)가 아직 닫지 못한 콜을 여기서 뺀다
        return offers.findByManagerIdAndStatusOrderByExpiresAt(managerId, Offer.PENDING).stream().filter(o -> !o.isExpired(now)).toList();
    }

    /**
     * 콜 + 예약 → 콜 카드 한 장(CallItem). 날짜·금액·주소는 KoreanFormat 으로 화면 문자열로 만든다.
     * 주소는 앞 3어절까지, 건물 번호 전에서 끊는다 — 수락 전 후보 전원에게 정확한 주소가 보이지 않게 (KoreanFormat.addressHead)
     */
    CallItem toItem(Offer o, Booking b, String eta, Instant now) {
        String label = catalog.requireCategory(b.getCategoryKey()).getLabel(); // 청소 종류 이름
        // 고객이 고른 특이사항이 여러 개여도 카드에는 첫 번째 하나만 "특이사항: …" 으로 보인다
        String note = b.getNotices().isEmpty() ? null : NOTE_PREFIX + catalog.noticeLabels(b.getCategoryKey(), b.getNotices()).get(0);
        return new CallItem(o.getId(), KoreanFormat.untilExpiry(o.getExpiresAt(), now), o.getExpiresAt().toString(),
                KoreanFormat.dateTimeCall(b.getServiceDate(), b.getServiceTime()), KoreanFormat.addressHead(b.getRoadAddress(), 3) + " · " + label,
                KoreanFormat.won(o.getPayAmount()), eta, note, details(b));
    }

    /** 수락 전에 알아야 할 것 — 소요 시간·주거 정보·청소 범위 (사용성 검토 2026-09-22: 예전에는 날짜·주소·금액뿐이었다) */
    private List<String> details(Booking b) {
        List<String> out = new ArrayList<>();
        out.add("예상 소요 " + b.getDurationMinutes() / 60 + "시간"); // 정수 나눗셈이라 분 단위는 버린다 (90분 → "1시간")
        String housing = profiles.housingSummary(b.getCustomerId()); // 고객 가입 때 받은 거주 정보 한 줄, 없으면 null
        if (housing != null) out.add(housing);
        List<String> scope = new ArrayList<>(catalog.optionLabels(b.getCategoryKey(), b.getFreeOptions()));
        scope.addAll(catalog.optionLabels(b.getCategoryKey(), b.getPaidOptions()));
        if (!scope.isEmpty()) out.add("청소 범위: " + String.join(", ", scope).replace("\n", " ")); // 무료+유료 옵션 이름을 쉼표로, 이름 안 줄바꿈은 공백으로
        return out;
    }
}
