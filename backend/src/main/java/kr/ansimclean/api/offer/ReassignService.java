package kr.ansimclean.api.offer;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.common.activity.ActivityLogService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.AdminAssigned;
import kr.ansimclean.api.common.events.BookingAssigned;
import kr.ansimclean.api.common.events.CameraReleased;
import kr.ansimclean.api.common.events.CancelRequestDecided;
import kr.ansimclean.api.common.events.ManagerReleased;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.job.JobCancelRequest;
import kr.ansimclean.api.job.JobService;
import kr.ansimclean.api.live.LiveSessionService;
import kr.ansimclean.api.offer.dto.AdminCancelRequestItem;
import kr.ansimclean.api.offer.dto.AssignResponse;
import kr.ansimclean.api.offer.dto.AssignableManager;
import kr.ansimclean.api.offer.dto.CancelDecisionResponse;
import kr.ansimclean.api.offer.dto.ReassignmentItem;
import kr.ansimclean.api.profile.ManagerPenalty;
import kr.ansimclean.api.profile.ManagerPenaltyService;
import kr.ansimclean.api.profile.ManagerProfile;
import kr.ansimclean.api.profile.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 어드민의 매니저 방문 취소 승인·거절과 관리자 배정 (docs/domain.md '매니저 방문 취소 승인·재배정', 2026-11-13 사용자 결정).
 * 부르는 곳: AdminReassignController (/admin/cancel-requests/**, /admin/reassignments, /admin/bookings/{id}/assign…).
 * 모든 메서드가 먼저 AccessPolicy 로 어드민 + MFA 를 확인한다(경로 규칙 SecurityConfig 다음의 두 번째 겹).
 *
 * 잠금 순서 (데드락 방지 — 콜 수락과 같은 방향):
 *  - 승인·거절: 예약 → 요청 (자동 종료·고객 취소도 예약을 먼저 잠근다)
 *  - 직접 배정: 매니저 → 예약 → 오퍼 (OfferService.doAccept 와 같다)
 * 재배정 콜 보내기·관리자 배정 대기 표시는 매칭 쪽 규칙이라 OfferService(startReassign·awaitAdmin)에 있다.
 */
@Service
@Transactional
public class ReassignService {
    private static final Logger log = LoggerFactory.getLogger(ReassignService.class);
    // 상태 이력(booking_status_history.reason) 값
    static final String HISTORY_CANCEL_APPROVED = "cancel_approved"; // 승인으로 배정이 풀렸다 (scheduled → assigning)
    static final String HISTORY_ADMIN_ASSIGNED = "admin_assigned";   // 어드민이 직접 배정했다 (assigning → scheduled)
    // (코드에서 정함) 오류 문구 — 관리자 화면이 그대로 보인다
    static final String BOOKING_CHANGED = "예약 상태가 바뀌어 승인할 수 없어요. 목록을 새로 고쳐주세요";
    static final String NOT_ASSIGNING = "배정을 기다리는 예약만 배정할 수 있어요";
    static final String MANAGER_UNAVAILABLE = "콜을 받을 수 없는 매니저예요 (미승인·정지·탈퇴)";
    static final String MANAGER_DROPPED = "이 방문을 취소한 매니저예요";
    static final String MANAGER_OUT_OF_AREA = "활동 지역이 아닌 매니저예요";
    static final String MANAGER_BUSY = "그 시간에 다른 작업이 있는 매니저예요";
    static final int NOTE_MAX = 500; // 거절 메모 최대 글자 수 (매니저 취소 상세와 같다)

    private final OfferService offers;
    private final OfferRepository offerRows;
    private final BookingService bookings;
    private final JobService jobs;
    private final ProfileService profiles;
    private final ManagerPenaltyService penalties;
    private final LiveSessionService live;
    private final OutboxService outbox;
    private final ActivityLogService activity;
    private final AccessPolicy policy;
    private final AppClock clock;

    public ReassignService(OfferService offers, OfferRepository offerRows, BookingService bookings, JobService jobs, ProfileService profiles,
                           ManagerPenaltyService penalties, LiveSessionService live, OutboxService outbox, ActivityLogService activity,
                           AccessPolicy policy, AppClock clock) {
        this.offers = offers;
        this.offerRows = offerRows;
        this.bookings = bookings;
        this.jobs = jobs;
        this.profiles = profiles;
        this.penalties = penalties;
        this.live = live;
        this.outbox = outbox;
        this.activity = activity;
        this.policy = policy;
        this.clock = clock;
    }

    // ── 취소 요청 ──

    /** GET /admin/cancel-requests — 대기 중인 요청, 오래된 순 */
    @Transactional(readOnly = true)
    public List<AdminCancelRequestItem> cancelRequests(CurrentUser actor) {
        policy.require(actor, AccessPolicy.Action.BOOKING_CANCEL_DECIDE, AccessPolicy.Resource.of("cancel_request", null));
        List<JobCancelRequest> pending = jobs.pendingCancelRequests();
        Map<UUID, Booking> byId = new HashMap<>();
        for (Booking b : bookings.findAll(pending.stream().map(JobCancelRequest::getBookingId).toList())) byId.put(b.getId(), b);
        List<UUID> people = new ArrayList<>(pending.stream().map(JobCancelRequest::getManagerId).toList());
        byId.values().forEach(b -> people.add(b.getCustomerId()));
        Map<UUID, String> names = profiles.namesOf(people);
        Map<String, String> reasons = reasonLabels();
        List<AdminCancelRequestItem> out = new ArrayList<>();
        for (JobCancelRequest r : pending) {
            Booking b = byId.get(r.getBookingId());
            if (b == null) continue; // 예약 행이 사라졌다 (cascade 로 요청도 지워지므로 거의 없다)
            out.add(new AdminCancelRequestItem(r.getId(), b.getId(), names.getOrDefault(r.getManagerId(), ""), reasons.getOrDefault(r.getReason(), r.getReason()),
                    r.getDetail(), r.getRequestedAt().toString(), seoulLabel(r.getRequestedAt()),
                    KoreanFormat.dateTimeLong(b.getServiceDate(), b.getServiceTime()), b.getRoadAddress(), names.getOrDefault(b.getCustomerId(), "")));
        }
        return out;
    }

    /**
     * POST /admin/cancel-requests/{id}/approve — 배정을 풀고 재배정을 시작한다. 이미 승인한 요청이면 같은 응답(두 어드민이 동시에 눌러도 한 번).
     * 거절·자동 종료된 요청이나, 예약이 그 매니저의 scheduled 가 아니면 INVALID_STATE
     */
    public CancelDecisionResponse approve(CurrentUser actor, UUID requestId) {
        policy.require(actor, AccessPolicy.Action.BOOKING_CANCEL_DECIDE, AccessPolicy.Resource.of("cancel_request", requestId));
        UUID bookingId = jobs.cancelRequestBookingId(requestId);
        Booking b = bookings.lockForUpdate(bookingId);             // 1. 예약
        JobCancelRequest r = jobs.lockCancelRequest(requestId);   // 2. 요청
        if (r.isApproved()) return approvedResponse(r, b);        // 같은 요청을 두 번 승인 — 처음 결과 그대로
        if (!r.isPending()) throw new ApiException(ErrorCode.INVALID_STATE, "이미 처리된 취소 요청이에요");
        UUID managerId = r.getManagerId();
        if (b.getStatus() != BookingStatus.SCHEDULED || !managerId.equals(b.getManagerId())) throw new ApiException(ErrorCode.INVALID_STATE, BOOKING_CHANGED);
        Instant now = clock.now();
        BookingStatus from = b.getStatus();
        b.releaseByManager(); // scheduled → assigning, 매니저 칸 비움
        bookings.recordTransition(b, from, actor.id(), HISTORY_CANCEL_APPROVED);
        jobs.clearChecklist(b.getId()); // 다음 매니저가 처음부터 시작하게
        // 0점 원장 행 — 점수는 없지만 이 예약의 재배정 콜·수락·직접 배정과 이 정기청소의 다음 회차 우선 콜에서 이 매니저를 뺀다 (사용자 결정: 점수 없음)
        penalties.record(managerId, b.getId(), b.getSeriesId(), ManagerPenalty.CANCEL_APPROVED, r.getReason(), r.getDetail());
        r.approve(actor.id(), now);
        boolean calling = offers.startReassign(b, managerId, r.getId()); // 24시간 재배정 콜 또는 곧바로 관리자 배정 대기
        activity.record(actor, ActivityLogService.CANCEL_REQUEST_APPROVE, "booking", b.getId().toString(),
                Map.of("status", from.name(), "managerId", managerId.toString()),
                Map.of("status", b.getStatus().name(), "next", calling ? "calling" : "awaitingAdmin"), r.getReason());
        // 고객 '매니저 배정이 취소됐어요 · 다른 매니저를 찾고 있어요'(대기여도 어드민이 찾는다), 매니저 '방문 취소가 승인됐어요', 카메라 정리
        outbox.publish(new ManagerReleased(b.getId(), b.getCustomerId()));
        outbox.publish(new CancelRequestDecided(r.getId(), b.getId(), managerId, true));
        outbox.publish(new CameraReleased(b.getId(), managerId));
        log.info("방문 취소 승인: 예약={} 요청={} 다음={}", b.getId(), r.getId(), calling ? "재배정 콜" : "관리자 배정 대기");
        return approvedResponse(r, b);
    }

    /** POST /admin/cancel-requests/{id}/reject { note } — 예약은 그대로. 대기 중이 아니면 INVALID_STATE */
    public CancelDecisionResponse reject(CurrentUser actor, UUID requestId, String note) {
        policy.require(actor, AccessPolicy.Action.BOOKING_CANCEL_DECIDE, AccessPolicy.Resource.of("cancel_request", requestId));
        String memo = note == null || note.isBlank() ? null : note.strip();
        if (memo != null && memo.length() > NOTE_MAX) throw ApiException.validation("메모는 " + NOTE_MAX + "자까지 적을 수 있어요");
        UUID bookingId = jobs.cancelRequestBookingId(requestId);
        Booking b = bookings.lockForUpdate(bookingId);
        JobCancelRequest r = jobs.lockCancelRequest(requestId);
        r.reject(actor.id(), memo, clock.now()); // 대기 중이 아니면 INVALID_STATE
        activity.record(actor, ActivityLogService.CANCEL_REQUEST_REJECT, "booking", b.getId().toString(), null, Map.of("requestId", r.getId().toString()), memo);
        outbox.publish(new CancelRequestDecided(r.getId(), b.getId(), r.getManagerId(), false));
        return new CancelDecisionResponse(r.getId(), b.getId(), r.getStatus(), null, null);
    }

    private CancelDecisionResponse approvedResponse(JobCancelRequest r, Booking b) {
        // next: 지금 재배정 콜이 열려 있으면 calling, 관리자 배정 대기면 awaitingAdmin. 그 사이 배정·취소됐으면 null
        String next = b.getStatus() != BookingStatus.ASSIGNING ? null : b.isReassigning() ? "calling" : b.getAdminAssignPendingAt() != null ? "awaitingAdmin" : null;
        return new CancelDecisionResponse(r.getId(), b.getId(), r.getStatus(), next, b.isReassigning() ? b.getReassignUntil().toString() : null);
    }

    // ── 관리자 배정 ──

    /** GET /admin/reassignments — 관리자 배정 대기 예약, 방문 가까운 순. 고객 연락처·주소는 MFA 를 통과한 어드민에게만 (rbac.md §7.1-b) */
    @Transactional(readOnly = true)
    public List<ReassignmentItem> reassignments(CurrentUser actor) {
        policy.require(actor, AccessPolicy.Action.BOOKING_ASSIGN, AccessPolicy.Resource.of("booking", null));
        List<Booking> waiting = bookings.adminAssignPending();
        Map<UUID, JobCancelRequest> requests = new LinkedHashMap<>();
        for (Booking b : waiting) jobs.latestCancelRequest(b.getId()).ifPresent(r -> requests.put(b.getId(), r));
        List<UUID> people = new ArrayList<>(waiting.stream().map(Booking::getCustomerId).toList());
        requests.values().forEach(r -> people.add(r.getManagerId()));
        Map<UUID, String> names = profiles.namesOf(people);
        Map<String, String> reasons = reasonLabels();
        List<ReassignmentItem> out = new ArrayList<>();
        for (Booking b : waiting) {
            JobCancelRequest r = requests.get(b.getId());
            List<String> address = new ArrayList<>();
            address.add(b.getRoadAddress());
            if (b.getDetailAddress() != null) address.add(b.getDetailAddress());
            out.add(new ReassignmentItem(b.getId(), b.getAdminAssignReason(), b.getAdminAssignPendingAt().toString(), b.getStartsAt().toString(),
                    KoreanFormat.dateTimeLong(b.getServiceDate(), b.getServiceTime()), address,
                    new ReassignmentItem.Customer(names.getOrDefault(b.getCustomerId(), ""), customerPhone(b.getCustomerId())),
                    r == null ? null : new ReassignmentItem.Manager(r.getManagerId(), names.getOrDefault(r.getManagerId(), "")),
                    r == null ? null : r.getRequestedAt().toString(),
                    r == null || r.getDecidedAt() == null || !r.isApproved() ? null : r.getDecidedAt().toString(),
                    r == null ? null : reasons.getOrDefault(r.getReason(), r.getReason())));
        }
        return out;
    }

    /**
     * GET /admin/bookings/{id}/assignable-managers — 이 예약에 배정할 수 있는 매니저: 승인 ∧ 정지·탈퇴 아님 ∧ 활동 지역 ∧ 겹치는 작업 없음 ∧ 이 방문을 취소하지 않음.
     * available = 그 시간이 근무시간 안인가 (밖이어도 어드민이 통화하고 배정할 수 있다). 근무시간 안인 매니저가 먼저
     */
    @Transactional(readOnly = true)
    public List<AssignableManager> assignableManagers(CurrentUser actor, UUID bookingId) {
        policy.require(actor, AccessPolicy.Action.BOOKING_ASSIGN, AccessPolicy.Resource.of("booking", bookingId));
        Booking b = bookings.find(bookingId).orElseThrow(ApiException::notFound);
        if (b.getStatus() != BookingStatus.ASSIGNING) throw new ApiException(ErrorCode.INVALID_STATE, NOT_ASSIGNING);
        Set<UUID> dropped = penalties.bookingDroppers(bookingId);
        List<ManagerProfile> ok = profiles.approvedManagers().stream() // 승인 ∧ 정지 아님 ∧ 탈퇴 아님
                .filter(m -> !dropped.contains(m.getManagerId()) && OfferService.inArea(m, b)
                        && !bookings.hasOverlappingWork(m.getManagerId(), b.getStartsAt(), b.getEndsAt()))
                .toList();
        List<UUID> ids = ok.stream().map(ManagerProfile::getManagerId).toList();
        Map<UUID, String> names = profiles.namesOf(ids);
        Map<UUID, String> phones = profiles.phonesOf(ids);
        List<AssignableManager> out = new ArrayList<>();
        for (ManagerProfile m : ok) {
            UUID id = m.getManagerId();
            out.add(new AssignableManager(id, names.getOrDefault(id, ""), phoneOrNull(phones.get(id)), offers.worksThen(id, b)));
        }
        out.sort((x, y) -> Boolean.compare(y.available(), x.available())); // 근무시간 안 먼저, 같으면 원래 순서
        return out;
    }

    /**
     * POST /admin/bookings/{id}/assign { managerId } — assigning 예약을 그 매니저에게 직접 배정한다.
     * 매니저 → 예약 순서로 잠그고 assignableManagers 와 같은 조건을 다시 본다(근무시간은 보지 않는다). 수락된 콜 행 1건을 새 회차로 만든다.
     * 최종 방어는 콜 수락과 같은 DB 제약(offers_one_accepted, bookings_manager_no_overlap) — 위반이면 MANAGER_BUSY
     */
    public AssignResponse assign(CurrentUser actor, UUID bookingId, UUID managerId) {
        policy.require(actor, AccessPolicy.Action.BOOKING_ASSIGN, AccessPolicy.Resource.of("booking", bookingId));
        if (managerId == null) throw ApiException.validation("배정할 매니저를 골라주세요");
        if (!profiles.exists(managerId)) throw ApiException.notFound();
        ManagerProfile m = profiles.lockManager(managerId);    // 1. 매니저 (없으면 NOT_FOUND)
        Booking b = bookings.lockForUpdate(bookingId);          // 2. 예약
        if (b.getStatus() != BookingStatus.ASSIGNING) throw new ApiException(ErrorCode.INVALID_STATE, NOT_ASSIGNING);
        if (!profiles.isApprovedManagerNow(managerId)) throw new ApiException(ErrorCode.INVALID_STATE, MANAGER_UNAVAILABLE);
        if (penalties.bookingDroppers(bookingId).contains(managerId)) throw new ApiException(ErrorCode.INVALID_STATE, MANAGER_DROPPED);
        if (!OfferService.inArea(m, b)) throw new ApiException(ErrorCode.INVALID_STATE, MANAGER_OUT_OF_AREA);
        if (bookings.hasOverlappingWork(managerId, b.getStartsAt(), b.getEndsAt())) throw new ApiException(ErrorCode.INVALID_STATE, MANAGER_BUSY);
        Instant now = clock.now();
        boolean wasReassign = b.isInReassign();
        String reason = b.getAdminAssignReason();
        try {
            for (Offer o : offerRows.findByBookingIdAndStatus(bookingId, Offer.PENDING)) o.cancel(); // 아직 열린 콜(재배정·일반)은 닫는다
            b.clearDispatchHold();
            b.nextDispatchRound(); // 새 회차 — 이 매니저가 앞 회차에 콜을 받았어도 unique(booking, manager, round) 에 걸리지 않게
            offerRows.save(Offer.assignedByAdmin(bookingId, managerId, b.getDispatchRound(), b.managerPay(), wasReassign, now));
            b.assign(managerId, now); // assigning → scheduled, 재배정·대기 표시 지움
            jobs.createChecklist(b);
            live.ensureCreated(bookingId);
            bookings.recordTransition(b, BookingStatus.ASSIGNING, actor.id(), HISTORY_ADMIN_ASSIGNED);
            activity.record(actor, ActivityLogService.BOOKING_ASSIGN, "booking", bookingId.toString(),
                    reason == null ? Map.of("status", "ASSIGNING") : Map.of("status", "ASSIGNING", "adminAssignReason", reason),
                    Map.of("status", "SCHEDULED", "managerId", managerId.toString()), null);
            outbox.publish(new BookingAssigned(bookingId, b.getCustomerId(), managerId)); // 고객 '매니저가 배정됐어요'
            outbox.publish(new AdminAssigned(bookingId, managerId));                      // 매니저 '관리자가 청소를 배정했어요'
            offerRows.flush(); // 제약 위반을 여기서 받는다
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.INVALID_STATE, MANAGER_BUSY);
        }
        log.info("어드민 직접 배정: 예약={} 매니저={}", bookingId, managerId);
        return new AssignResponse(bookingId, managerId);
    }

    // ── 보조 ──

    /** 취소 사유 key → 라벨 (app_settings.manager_cancel_reasons) */
    private Map<String, String> reasonLabels() {
        Map<String, String> out = new HashMap<>();
        for (JsonNode r : penalties.reasons()) out.put(r.path("key").asString(), r.path("label").asString());
        return out;
    }

    /** 고객 예약 연락처(인증된 번호, 없으면 가입 번호)를 화면 모양으로. 탈퇴 등으로 못 찾으면 null */
    private String customerPhone(UUID customerId) {
        try {
            return phoneOrNull(profiles.contactPhone(customerId));
        } catch (ApiException e) {
            return null;
        }
    }

    private static String phoneOrNull(String e164) {
        return e164 == null || e164.isBlank() ? null : KoreanFormat.phoneDisplay(e164);
    }

    /** 서울 시각 "11월 13일 오후 3:05" */
    private static String seoulLabel(Instant at) {
        return KoreanFormat.dateTimeShort(AppClock.toSeoul(at).toLocalDate(), AppClock.toSeoul(at).toLocalTime());
    }
}
