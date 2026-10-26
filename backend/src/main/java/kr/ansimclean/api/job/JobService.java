package kr.ansimclean.api.job;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.BookingStatus;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.catalog.ChecklistTemplate;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.BookingCompleted;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.job.dto.ActiveJobResponse;
import kr.ansimclean.api.job.dto.ChecklistResponse;
import kr.ansimclean.api.job.dto.CompletedJobResponse;
import kr.ansimclean.api.job.dto.CustomerChecklistResponse;
import kr.ansimclean.api.job.dto.ContactResponse;
import kr.ansimclean.api.job.dto.JobIdResponse;
import kr.ansimclean.api.job.dto.JobResponse;
import kr.ansimclean.api.job.dto.PauseResponse;
import kr.ansimclean.api.job.dto.PausedJobResponse;
import kr.ansimclean.api.job.dto.StartResponse;
import kr.ansimclean.api.live.LiveSessionService;
import kr.ansimclean.api.live.LiveSession;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.settlement.Settlement;
import kr.ansimclean.api.settlement.SettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 매니저 작업 (BACKEND_PLAN.md §6.1 매니저 전이 + §6.3 화면 값). 상태는 Booking 의 메서드로만.
 * 일시정지는 라이브 쪽이 먼저(영상 차단 확인) — 여기는 그 선행 조건을 검사한다 (§8.2.1). 라이브 쪽에는 화면 값 읽기 외에
 * 선행 조건 확인(세션 행 잠금)과 작업 일시정지 시각 기록(live.jobPaused)만 하고, 외부 효과는 이벤트로.
 * 부르는 곳 예: JobController(매니저 앱 작업 화면), offer.OfferService(배정·일정 변경 때 체크리스트).
 * 트랜잭션(CODE_GUIDE.md 3-3): noRollbackFor 가 없어 ApiException 이 나면 그 요청의 변경이 전부 되돌아간다
 * (예: 완료 중 정산 생성이 실패하면 완료도 취소). createChecklist·clearChecklist 는 OfferService 의 묶음에 합쳐진다.
 * 체크리스트를 포함한 쓰기는 먼저 예약 행을 잠근다 — 동시 체크·해제·완료가 같은 순서로 처리된다.
 * 예외: 작업 상세의 수리 사진 서명 URL(storage.signedUrl)은 트랜잭션 안에서 Storage 를 직접 부른다 — 상태를 바꾸지 않는 호출이고,
 * 실패하면 그 사진만 빠진다(photoUrls).
 */
@Service
@Transactional
public class JobService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JobService.class);
    // 운영자 완료 때 상태 이력(recordTransition)의 reason 에 남기는 값 — 매니저가 누른 완료와 구분된다
    static final String REASON_ADMIN_COMPLETE = "admin";
    // app_settings 테이블의 key — 일시정지 화면 안내문 / 완료 화면 정산 안내 / 완료 화면 제목 (settings.get 으로 읽는다)
    public static final String SETTING_PAUSE_NOTE = "pause_note";
    public static final String SETTING_SETTLEMENT_NOTE = "settlement_note";
    public static final String SETTING_COMPLETE_TITLE = "job_complete_title";
    // 아래 문자열들은 서버가 만들어 보내는 화면 문구·오류 문구다
    static final String TITLE_IN_PROGRESS = "청소 진행 중";
    static final String STREAMING = "실시간 촬영 중";
    static final String WAITING_CAMERA = "카메라 연결 대기 중";
    static final String LABEL_PAYOUT = "예상 정산";
    static final String ALREADY_ACTIVE = "이미 진행 중인 작업이 있어요";
    static final String STOP_VIDEO_FIRST = "영상을 먼저 중단해주세요";
    static final String CANCELLED_JOB = "취소된 예약이에요";

    private final JobChecklistItemRepository checklist;
    private final JobChecklistEventRepository checklistEvents;
    private final RecordingSegmentRepository segments;
    private final kr.ansimclean.api.booking.JobMediaService media;
    private final JobPauseRepository pauses;
    private final BookingService bookings;
    private final ProfileService profiles;
    private final CatalogService catalog;
    private final LiveSessionService live;
    private final SettlementService settlements;
    private final AppSettingsService settings;
    private final OutboxService outbox;
    private final Idempotent idempotent;
    private final AppClock clock;
    private final SupabaseStorageClient storage;
    private final kr.ansimclean.api.profile.ManagerPenaltyService penalties;
    private final kr.ansimclean.api.booking.BookingDispatcher dispatcher;

    static final String REPAIR_PHOTO_BUCKET = "repair-photos"; // booking/RepairPhotoService.BUCKET 과 같다
    static final int PHOTO_URL_SECONDS = 60 * 60; // 수리 사진 서명 URL 유효 시간(1시간). 작업 상세를 열 때마다 새로 만든다

    // dispatcher 만 @Lazy(처음 쓸 때 연결): 구현체 OfferService 가 이 JobService 를 받아 쓰므로, 바로 받으면 서로를 먼저 만들어야 해 생성이 꼬인다
    public JobService(JobChecklistItemRepository checklist, JobChecklistEventRepository checklistEvents, JobPauseRepository pauses, BookingService bookings, ProfileService profiles, CatalogService catalog,
                      LiveSessionService live, SettlementService settlements, AppSettingsService settings, OutboxService outbox, Idempotent idempotent, AppClock clock,
                      SupabaseStorageClient storage, kr.ansimclean.api.profile.ManagerPenaltyService penalties,
                      @org.springframework.context.annotation.Lazy kr.ansimclean.api.booking.BookingDispatcher dispatcher,
                      RecordingSegmentRepository segments, kr.ansimclean.api.booking.JobMediaService media) {
        this.segments = segments;
        this.media = media;
        this.storage = storage;
        this.penalties = penalties;
        this.dispatcher = dispatcher;
        this.checklist = checklist;
        this.checklistEvents = checklistEvents;
        this.pauses = pauses;
        this.bookings = bookings;
        this.profiles = profiles;
        this.catalog = catalog;
        this.live = live;
        this.settlements = settlements;
        this.settings = settings;
        this.outbox = outbox;
        this.idempotent = idempotent;
        this.clock = clock;
    }

    // ── 배정·재배정 때 (OfferService 가 같은 트랜잭션에서) ──

    /**
     * 예약 카테고리의 체크리스트 템플릿을 이 예약의 job_checklist_items 행으로 복사한다.
     * 부르는 곳: OfferService 콜 수락(doAccept). 옛 행을 먼저 지우므로 다시 불려도 항목이 두 벌이 되지 않는다
     */
    public void createChecklist(Booking booking) {
        checklist.deleteByBookingId(booking.getId());
        for (ChecklistTemplate t : catalog.checklistTemplate(booking.getCategoryKey())) {
            checklist.save(JobChecklistItem.of(booking.getId(), t.getGroupName(), t.getLabel(), t.getSort()));
        }
    }

    /** 이 예약의 체크리스트 행을 모두 지운다. 부르는 곳 예: OfferService.onRescheduled(고객 일정 변경), 아래 doCancelByManager(매니저 방문 취소) */
    public void clearChecklist(UUID bookingId) {
        checklist.deleteByBookingId(bookingId);
    }

    // ── 전이 ──

    /** Job/Preparing "청소 시작" (§6.1 start). 라이브 세션 시작은 별도(§5.4). Idempotency-Key 재요청은 저장된 응답 */
    public StartResponse start(UUID managerId, UUID id, String idempotencyKey) {
        return idempotent.run(idempotencyKey, managerId, "start:" + id, StartResponse.class, () -> doStart(managerId, id));
    }

    /** 실제 시작: 예약 행을 잠그고 → 이미 in_progress 면 그대로(paused 면 Booking.start 가 409) → 다른 진행 중 작업이 없는지 → scheduled → in_progress → 이력 → 지각 검사 */
    private StartResponse doStart(UUID managerId, UUID id) {
        Booking b = bookings.lockManagerBooking(managerId, id);
        if (b.getRequiredPhotoRooms().isEmpty()) {
            List<String> rooms = checklist.findByBookingIdOrderBySort(id).stream().map(JobChecklistItem::getGroupName).distinct().toList();
            b.setRequiredPhotoRooms(rooms.isEmpty() ? List.of("청소 공간") : rooms);
        }
        if (b.getStatus() == BookingStatus.IN_PROGRESS) return new StartResponse(id, b.getStartedAt().toString()); // 키 없이 두 번 눌러도 같다
        // 매니저당 진행 중(in_progress·paused) 작업은 하나뿐이다 — 다른 작업을 끝내지 않았으면 409
        Optional<Booking> active = bookings.activeJob(managerId);
        if (active.isPresent() && !active.get().getId().equals(id)) throw new ApiException(ErrorCode.INVALID_STATE, ALREADY_ACTIVE);
        BookingStatus from = b.getStatus();
        b.start(clock.now(), clock.today()); // scheduled 이고 오늘일 때만
        bookings.recordTransition(b, from, managerId, null); // 상태 이력 한 줄 (바꾸기 전 → 지금 상태, 누가)
        recordTardy(b, managerId); // 약속 시간보다 많이 늦었으면 패널티 (docs/manager-policy.md)
        return new StartResponse(id, b.getStartedAt().toString());
    }

    /** 약속 시간 + tardyMinutes 를 넘겨 시작하면 지각 (docs/manager-policy.md, 사용자 승인 2026-09-22) */
    private void recordTardy(Booking b, UUID managerId) {
        // 기준: 실제 시작(startedAt)이 약속 시각(startsAt) + tardyMinutes 보다 **뒤**면 지각 — 초·밀리초까지 비교한다(30분 0초는 아니고 30분 30초는 지각).
        // LateStartNotifier 와 같은 기준이다: 그쪽은 약속 + tardyMinutes 가 되도록 시작하지 않은 예약에 고객 안내를 보내므로,
        // 안내를 받은 예약은 모두 이 기준으로 지각이 된다. 예전에는 분 아래를 버려 31분부터 기록했다 (문제 목록 26번).
        // tardyMinutes 는 app_settings.manager_penalty 의 값
        java.time.Instant limit = b.getStartsAt().plus(java.time.Duration.ofMinutes(penalties.tardyMinutes()));
        if (!b.getStartedAt().isAfter(limit)) return;
        // 내역 화면의 상세 문구는 분 단위로 버린 값이다 (30분 30초 → "30분 늦게 시작")
        long late = java.time.temporal.ChronoUnit.MINUTES.between(b.getStartsAt(), b.getStartedAt());
        penalties.record(managerId, b.getId(), b.getSeriesId(), kr.ansimclean.api.profile.ManagerPenalty.TARDY, null, late + "분 늦게 시작");
    }

    /**
     * 매니저가 방문을 취소한다 (docs/manager-policy.md). 예약은 취소하지 않고 **다시 매칭**한다 —
     * 방문 cancelCutoffHours(기본 4) 시간 전부터는 앱에서 할 수 없다(고객센터 전화). 시작한 작업도 막는다.
     */
    /** Idempotency-Key 가 있으면 재요청은 저장된 응답 — 응답이 늦어 다시 눌러도 '찾을 수 없어요' 가 아니라 같은 결과 (자체 점검 #44) */
    public kr.ansimclean.api.booking.dto.ReservationResponse cancelByManager(UUID managerId, UUID id, String reason, String detail, String idempotencyKey) {
        // 요청 해시에 사유·상세까지 넣는다 — 같은 키로 다른 사유를 보내면 다른 요청으로 보고 IDEMPOTENCY_MISMATCH 가 된다 (Idempotent.run)
        return idempotent.run(idempotencyKey, managerId, java.util.Arrays.asList("cancel", id, reason, detail), kr.ansimclean.api.booking.dto.ReservationResponse.class,
                () -> doCancelByManager(managerId, id, reason, detail));
    }

    /**
     * 실제 취소 순서: 예약 행 잠금 → scheduled 인지 → 컷오프·사유 검사 → 배정 풀기(scheduled → assigning) → 이력·체크리스트·패널티
     * → 다시 콜 → 고객 푸시·바디캠 정리는 아웃박스로. 응답은 이 예약의 고객 예약 카드 모양이다
     */
    private kr.ansimclean.api.booking.dto.ReservationResponse doCancelByManager(UUID managerId, UUID id, String reason, String detail) {
        Booking b = bookings.lockManagerBooking(managerId, id);
        if (b.getStatus() != BookingStatus.SCHEDULED) throw new ApiException(ErrorCode.INVALID_STATE, "지금은 취소할 수 없는 작업이에요");
        // 방문까지 남은 시간으로 패널티 구간(일찍 취소·늦게 취소)을 고른다. null = 컷오프 안이라 앱에서 취소할 수 없다(고객센터 안내 문구로 409)
        String category = penalties.cancelCategory(b.getStartsAt(), clock.now());
        if (category == null) throw penalties.cutoffException();
        // 보낸 사유 key 가 app_settings.manager_cancel_reasons 목록(JSON 배열)의 key 중 하나인가. known |= … = 하나라도 맞으면 true 로 남는다
        boolean known = false;
        for (tools.jackson.databind.JsonNode r : penalties.reasons()) known |= r.path("key").asString().equals(reason);
        if (!known) throw ApiException.validation("알 수 없는 취소 사유예요");
        BookingStatus from = b.getStatus();
        UUID seriesId = b.getSeriesId();
        b.releaseByManager(); // 매니저 칸을 비우고 assigning 으로 (Booking.releaseByManager)
        bookings.recordTransition(b, from, managerId, category); // 이력 reason = 패널티 구간
        clearChecklist(b.getId()); // 다음 매니저가 처음부터 시작하게 (일정 변경 때와 같다)
        penalties.record(managerId, b.getId(), seriesId, category, reason, detail); // 패널티 원장 한 줄 — 일찍 취소가 이번 달 무료 한도 안이면 0점, 기준을 넘으면 정지까지 여기서
        dispatcher.onReleased(b, managerId); // 새 회차로 다시 콜 — 취소한 매니저만 뺀다 (전체 재점검 O1)
        // 고객 푸시 "매니저 배정이 취소됐어요 · 다른 매니저를 찾고 있어요" (push.PushHandler, 알림 설정과 무관).
        // 다시 매칭할 후보가 없어 곧바로 매칭 실패로 표시됐으면 보내지 않는다 — 고객에게는 디스패처가 적은 실패 안내(MatchingFailed) 하나만 간다.
        // 예전에는 '못 찾았어요' 와 '찾고 있어요' 가 함께 가 서로 어긋났다 (문제 목록 5번). 밤이라 매칭을 아침으로 미뤘으면 실패 표시가 없어 '찾고 있어요' 가 간다
        if (b.getMatchingFailedAt() == null) outbox.publish(new kr.ansimclean.api.common.events.ManagerReleased(b.getId(), b.getCustomerId()));
        outbox.publish(new kr.ansimclean.api.common.events.CameraReleased(b.getId(), managerId)); // 바디캠이 이 고객 방에 묶여 있으면 대기 방으로 (전체 재점검 J1)
        return bookings.reservation(b.getCustomerId(), b.getId());
    }

    /** 촬영 일시정지 ②단계 (§8.2.1): 영상이 나가는 중이면 INVALID_STATE. 이미 paused 면 그대로(멱등) */
    // 앱은 ①단계 live/pause(영상 차단 확인)가 끝난 뒤 부른다. 이 요청이 실패하면 영상만 막히고 작업은 진행 중으로 남는다 —
    // 서버는 그 상태를 매니저가 재개할 때까지 막아 둔다 — 바디캠이 끊겼다 다시 붙어도 (live_sessions.video_held, LiveService.holdsVideo)
    public PauseResponse pause(UUID managerId, UUID id, String reason) {
        if (!catalog.isReason("pause", reason)) throw ApiException.validation("알 수 없는 중단 사유예요");
        Booking b = bookings.lockManagerBooking(managerId, id);
        if (b.getStatus() == BookingStatus.PAUSED) {
            // 이미 멈춰 있다 — 새 구간을 만들지 않고 열린 구간의 사유·시각을 그대로 돌려준다. paused 인데 열린 구간이 없으면 데이터가 어긋난 것이라 500
            JobPause open = openPause(id).orElseThrow(() -> new ApiException(ErrorCode.INTERNAL, ErrorCode.INTERNAL.defaultMessage()));
            return new PauseResponse(id, open.getReason(), KoreanFormat.sinceLabel(open.getPausedAt(), "부터 중단됨"));
        }
        // mayBeStreaming 은 라이브 세션 행도 잠그고 본다 — 이 확인과 paused 저장 사이에 카메라 재접속(웹훅)이 세션을 live 로 바꾸지 못하게
        if (live.mayBeStreaming(id)) throw new ApiException(ErrorCode.INVALID_STATE, STOP_VIDEO_FIRST); // paused+unmute_pending 도 막는다
        BookingStatus from = b.getStatus();
        Instant now = clock.now();
        // 상태 바꾸기(in_progress → paused) → 중단 구간 행 저장 → 라이브 세션에 작업 일시정지 시각 기록 → 상태 이력.
        // 작업 일시정지 시각을 세션에도 기록만 남긴다 (재개 판단은 세션의 videoHeld — 매니저의 재개 요청으로만 풀린다)
        b.pause();
        pauses.save(JobPause.open(id, reason, now));
        live.jobPaused(id, now);
        bookings.recordTransition(b, from, managerId, reason);
        return new PauseResponse(id, reason, KoreanFormat.sinceLabel(now, "부터 중단됨"));
    }

    /** 재개 ①단계 (앱은 이 다음에 live.resume). 이미 진행 중이면 그대로(멱등) */
    public JobIdResponse resume(UUID managerId, UUID id) {
        Booking b = bookings.lockManagerBooking(managerId, id);
        if (b.getStatus() == BookingStatus.IN_PROGRESS) return new JobIdResponse(id);
        BookingStatus from = b.getStatus();
        Instant now = clock.now();
        // 이 뒤의 live/resume 이 서버에 닿지 않아 영상만 막혀 남아도, 아래 jobResumed 가 '멈춤' 표시를 풀었으므로 재조정기가 다시 켠다
        b.resume(); // paused 가 아니면(예: 완료됨) Booking 이 409 로 막는다
        openPause(id).ifPresent(p -> p.close(now)); // 열린 중단 구간을 닫아 경과 시간 계산에서 빠지는 구간을 확정한다
        live.jobResumed(id); // 매니저가 재개했다 — 영상을 멈춰 둔 표시를 푼다 (live/resume 이 닿지 않아도 재조정기가 영상을 다시 켠다)
        bookings.recordTransition(b, from, managerId, null);
        return new JobIdResponse(id);
    }

    /** 청소 완료 (§6.1 complete): 열린 중단 닫기 · 정산 생성(같은 tx) · completed_count+1 · 고객 푸시. 라이브 정지는 B5 재조정기 */
    // 여기서는 라이브를 멈추지 않는다. 매니저 앱이 완료 응답 뒤 live/stop(LiveService.stop)을 부르고, 그 호출이 없거나 실패하면
    // (운영자 완료 포함) LiveReconciler 가 1분 안에 '작업 done 인데 세션 미종료' 를 보고 멈춘다
    public JobIdResponse complete(UUID managerId, UUID id, String idempotencyKey) {
        return idempotent.run(idempotencyKey, managerId, "complete:" + id, JobIdResponse.class, () -> doComplete(managerId, id));
    }

    /** 정기청소 회차면 시리즈 행 → 예약 행 순서로 잠근 뒤(고객 회차 취소와 같은 순서라 서로 기다리다 멈추지 않는다) 완료 처리 */
    private JobIdResponse doComplete(UUID managerId, UUID id) {
        Booking b = bookings.lockManagerBookingWithSeries(managerId, id);
        if (b.getStatus() != BookingStatus.DONE) {
            if (!BookingStatus.ACTIVE.contains(b.getStatus())) throw new ApiException(ErrorCode.INVALID_STATE, "진행 중인 작업만 완료할 수 있어요");
            media.requireComplete(b);
            if (live.find(id).filter(LiveSession::isEnded).isEmpty()) throw ApiException.validation("실시간 영상 종료를 다시 시도해주세요");
        }
        return finish(b, managerId);
    }

    /** 라이브 종료 의도를 먼저 영속화한다. stop 실패/앱 재실행에도 재송출하지 않고 종료를 재시도한다. */
    public JobIdResponse finishVideo(UUID managerId, UUID id) {
        Booking b = bookings.lockManagerBooking(managerId, id);
        // 녹화 장애와 송출 종료는 별개다. 종료 의도를 먼저 저장하고 LiveService.stop이 원격 방 종료를 확인한다.
        if (b.getVideoFinishedAt() == null && live.find(id).filter(s -> s.isActive() || (s.isEnded() && s.getStartedAt() != null)).isEmpty())
            throw ApiException.validation("실시간 영상을 시작한 뒤에 종료할 수 있어요");
        media.finishVideo(b);
        return new JobIdResponse(id);
    }

    /** 현장에 안방/작은방 등이 더 있으면 필수 사진 공간과 체크리스트를 함께 추가한다. */
    public JobIdResponse addRoom(UUID managerId, UUID id, String name) {
        Booking b = bookings.lockManagerBooking(managerId, id);
        if (!BookingStatus.ACTIVE.contains(b.getStatus()) || b.getBeforePhotosConfirmedAt() != null)
            throw ApiException.validation("청소 전 사진 단계에서만 방을 추가할 수 있어요");
        String room = name == null ? "" : name.trim();
        if (room.isEmpty() || room.length() > 20) throw ApiException.validation("공간 이름을 1~20자로 입력해주세요");
        List<String> rooms = new ArrayList<>(b.getRequiredPhotoRooms());
        if (rooms.contains(room)) return new JobIdResponse(id);
        if (rooms.size() >= 10) throw ApiException.validation("공간은 최대 10곳까지 등록할 수 있어요");
        rooms.add(room); b.setRequiredPhotoRooms(rooms);
        int sort = checklist.findByBookingIdOrderBySort(id).stream().mapToInt(JobChecklistItem::getSort).max().orElse(0) + 1;
        checklist.save(JobChecklistItem.of(id, room, "청소 완료", sort));
        return new JobIdResponse(id);
    }

    /**
     * 운영자 완료 (POST /admin/jobs/{id}/complete, docs/runbook.md): 매니저가 '청소 완료' 를 누르지 않고 떠나 in_progress·paused 로 남은 작업.
     * 매니저 완료와 같은 일(정산·완료 수·고객 푸시·시리즈 마감)을 하고, 라이브는 재조정기가 1분 안에 멈춘다 (자체 점검 #17)
     */
    public JobIdResponse completeByAdmin(UUID id) {
        Booking b = bookings.lockWithSeries(id); // 매니저 소유권 검사 없이 같은 순서(시리즈 → 예약)로 잠근다. 없는 id 면 404
        // 실제로 상태를 바꾸는 경우에만 경고 로그를 남긴다 (이미 완료면 아래 finish 가 아무것도 하지 않는다)
        if (b.getStatus() != BookingStatus.DONE) log.warn("운영자가 작업을 완료 처리: 예약={} 매니저={} 방문일={}", id, b.getManagerId(), b.getServiceDate());
        return finish(b, null);
    }

    /** actorId = 완료한 사람(매니저, 운영자면 null — 이력 reason 'admin') */
    private JobIdResponse finish(Booking b, UUID actorId) {
        UUID id = b.getId();
        UUID managerId = b.getManagerId();
        if (b.getStatus() == BookingStatus.DONE) return new JobIdResponse(id); // 이미 완료 — 정산·완료 수를 두 번 올리지 않는다
        BookingStatus from = b.getStatus();
        Instant now = clock.now();
        b.complete(now); // in_progress·paused → done. 그 밖의 상태(예: scheduled)는 409
        openPause(id).ifPresent(p -> p.close(now)); // 일시정지 중에 완료하면 그 구간도 지금 닫는다
        settlements.createFor(b); // 정산 행 — 같은 트랜잭션이라 여기서 실패하면 완료도 함께 되돌아간다
        profiles.incrementCompleted(managerId); // 매니저 프로필의 완료 건수 +1
        bookings.recordTransition(b, from, actorId, actorId == null ? REASON_ADMIN_COMPLETE : null);
        // 완료 푸시(고객 '청소 완료', 매니저 '정산 등록') — push.PushHandler 가 아웃박스에서 보낸다
        outbox.publish(new BookingCompleted(id, b.getCustomerId(), managerId));
        if (b.getSeriesId() != null) bookings.endSeriesIfNothingLeft(b.getSeriesId(), now); // 마지막 남은 회차였으면 시리즈를 닫는다 — active 로 남아 탈퇴를 막고 종료 안내를 보내지 않게 (자체 점검 #8)
        return new JobIdResponse(id);
    }

    /**
     * 체크리스트 한 줄 체크·해제. 예약 잠금 뒤 검사하므로 중복 요청과 완료 처리도 직렬화된다.
     * 같은 상태 재요청은 시각·이력을 바꾸지 않는다. 상태와 이력은 같은 트랜잭션에서 저장된다.
     */
    public ChecklistResponse setChecklistItem(UUID managerId, UUID jobId, UUID itemId, boolean done) {
        Booking b = bookings.lockManagerBooking(managerId, jobId);
        if (!BookingStatus.ACTIVE.contains(b.getStatus())) throw new ApiException(ErrorCode.INVALID_STATE, "진행 중인 작업에서만 체크할 수 있어요");
        if (b.getBeforePhotosConfirmedAt() == null || b.getVideoFinishedAt() != null)
            throw ApiException.validation("실시간 영상 진행 중에만 체크할 수 있어요");
        JobChecklistItem item = checklist.findByIdAndBookingId(itemId, jobId).orElseThrow(ApiException::notFound);
        if (item.isDone() == done) return new ChecklistResponse(jobId, itemId, done);
        Instant now = clock.now();
        item.mark(done, now);
        JobChecklistEvent event = checklistEvents.save(JobChecklistEvent.of(item, done, now, managerId));
        if (done) {
            LiveSession session = live.find(jobId).filter(LiveSession::isActive).orElseThrow(() -> ApiException.validation("영상 연결 후 다시 체크해주세요"));
            Instant start = segments.findFirstByLiveSessionIdOrderByEndAtDesc(jobId).map(RecordingSegment::getEndAt)
                    .orElse(session.getStartedAt() != null ? session.getStartedAt() : b.getBeforePhotosConfirmedAt());
            segments.save(RecordingSegment.of(jobId, event, item, start.isAfter(now) ? now : start, now, managerId));
        } else segments.revoke(jobId, itemId, now);
        return new ChecklistResponse(jobId, itemId, done);
    }

    // ── 화면 ──

    /**
     * 작업 상세(Job/Detail) 화면 값: 상태 배지·시작 문구·주소 줄·정보 행·정산 예정액·작업 범위(무료+유료 옵션)·요청사항·수리 사진.
     * 남의 작업은 404, 취소된 예약은 409
     */
    @Transactional(readOnly = true)
    public JobResponse job(UUID managerId, UUID id) {
        Booking b = bookings.requireManagerBooking(managerId, id);
        if (b.getStatus() == BookingStatus.CANCELLED) throw new ApiException(ErrorCode.INVALID_STATE, CANCELLED_JOB); // 배지에 '취소' 가 없다(Figma) → 상세를 열지 않는다
        String category = catalog.requireCategory(b.getCategoryKey()).getLabel();
        String customer = profiles.namesOf(List.of(b.getCustomerId())).getOrDefault(b.getCustomerId(), ""); // 못 찾으면 빈 문자열. 이름은 infoRows 가 '김○○' 처럼 가려 보낸다
        // 주소 줄: 도로명 주소, 상세 주소가 있으면 둘째 줄
        List<String> address = new ArrayList<>();
        address.add(b.getRoadAddress());
        if (b.getDetailAddress() != null) address.add(b.getDetailAddress());
        // 작업 범위: 무료 옵션 라벨 뒤에 유료 옵션 라벨 (예약 때 저장한 옵션 key 를 카탈로그 라벨로 바꾼다)
        List<String> scope = new ArrayList<>(catalog.optionLabels(b.getCategoryKey(), b.getFreeOptions()));
        scope.addAll(catalog.optionLabels(b.getCategoryKey(), b.getPaidOptions()));
        List<String> photos = photoUrls(b);
        // 날짜 없이 "14:00 시작" 만 있어 내일 작업이 오늘처럼 읽혔다 → 오늘이 아니면 날짜를 붙인다 (직접 사용 점검 U9)
        boolean today = b.getServiceDate().equals(clock.today());
        String start = (today ? "" : KoreanFormat.dateLabel(b.getServiceDate()) + " ") + KoreanFormat.hhmm(b.getServiceTime()) + " 시작";
        // pay = managerPay(결제 총액 − 플랫폼 수수료, 예약 때 고정한 값). 요청사항의 사진 장수는 실제로 URL 을 만든 장 수로 센다
        return new JobResponse(b.getId(), badge(b.getStatus()), start, today, address,
                infoRows(b, customer, category),
                KoreanFormat.won(b.managerPay()), scope, requests(b, photos.size()), photos);
    }

    /** 고객·유형·예상 소요 (+ 가입 때 받은 거주 정보가 있으면 한 줄 — 매니저가 현장을 미리 그릴 수 있게, 검증 검토) */
    private List<JobResponse.Row> infoRows(Booking b, String customer, String category) {
        List<JobResponse.Row> rows = new ArrayList<>(List.of(new JobResponse.Row("고객", KoreanFormat.maskName(customer)), new JobResponse.Row("청소 유형", category),
                new JobResponse.Row("예상 소요", b.getDurationMinutes() / 60 + "시간"))); // 예약 때 고정한 값 — 설정이 바뀌어도 콜 카드·일정 시간대와 같게 (자체 점검 #26)
        String housing = profiles.housingSummary(b.getCustomerId());
        if (housing != null) rows.add(new JobResponse.Row("주거 정보", housing));
        return rows;
    }

    /** 수리 사진 서명 URL — 없으면 빈 목록. Storage 가 실패해도 작업 상세는 열린다 */
    private List<String> photoUrls(Booking b) {
        List<String> out = new ArrayList<>();
        for (String path : b.getRepairPhotos()) {
            try {
                out.add(storage.signedUrl(REPAIR_PHOTO_BUCKET, path, PHOTO_URL_SECONDS));
            } catch (ApiException e) {
                log.warn("수리 사진 URL 실패 {}: {}", b.getId(), e.getMessage());
            }
        }
        return out;
    }

    /** 고객 연락처·지도 검색어 — 작업 당일 ±1일에만 (그 외 404, §5.3) */
    @Transactional(readOnly = true)
    public ContactResponse contact(UUID managerId, UUID id) {
        Booking b = bookings.requireManagerBooking(managerId, id);
        if (b.getStatus() == BookingStatus.CANCELLED) throw ApiException.notFound(); // 취소돼 배정이 풀린 예약의 고객 번호·주소는 더 볼 수 없다
        LocalDate today = clock.today(); // 서울 달력의 오늘 — 방문일(serviceDate)도 서울 날짜라 같은 기준으로 비교한다
        if (b.getServiceDate().isBefore(today.minusDays(1)) || b.getServiceDate().isAfter(today.plusDays(1))) throw ApiException.notFound();
        // 탈퇴한 고객이면 requireLive 가 404. 저장된 번호(+82…)를 010-1234-5678 모양으로 바꿔 보낸다
        String phone = profiles.requireLive(b.getCustomerId()).getPhone();
        return new ContactResponse(KoreanFormat.phoneDisplay(phone), b.getRoadAddress());
    }

    /** 고객 소유권 검사 후 같은 DB 스냅샷에서 현재 상태와 이력을 읽는다 (5초 폴링). */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CustomerChecklistResponse customerChecklist(UUID customerId, UUID bookingId) {
        Booking b = bookings.requireCustomerBooking(customerId, bookingId);
        List<JobChecklistItem> items = checklist.findByBookingIdOrderBySort(bookingId);
        Map<String, List<CustomerChecklistResponse.Item>> grouped = new LinkedHashMap<>();
        for (JobChecklistItem item : items) {
            grouped.computeIfAbsent(item.getGroupName(), key -> new ArrayList<>())
                    .add(new CustomerChecklistResponse.Item(item.getId(), item.getLabel(), item.isDone()));
        }
        List<CustomerChecklistResponse.Group> groups = grouped.entrySet().stream().map(e -> {
            int done = (int) e.getValue().stream().filter(CustomerChecklistResponse.Item::done).count();
            return new CustomerChecklistResponse.Group(e.getKey(), done, e.getValue().size(), e.getValue());
        }).toList();
        int done = groups.stream().mapToInt(CustomerChecklistResponse.Group::done).sum();
        Map<UUID, JobChecklistEvent> latest = new LinkedHashMap<>();
        for (JobChecklistEvent event : checklistEvents.findLatestByBookingId(bookingId)) latest.put(event.getItemId(), event);
        Optional<LiveSession> session = live.find(bookingId);
        // 요청 시각은 진단용 임시값이다. 실제 파일 시작 시각이 확인된 녹화에만 구간을 제공한다.
        Instant recordingStart = session.filter(LiveSession::isRecordingStartConfirmed).map(LiveSession::getRecordingStartedAt).orElse(null);
        Integer length = session.map(LiveSession::getDurationSeconds).orElse(null);
        List<CustomerChecklistResponse.Mark> timeline = items.stream().filter(JobChecklistItem::isDone)
                .sorted(Comparator.comparing(JobChecklistItem::getDoneAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(JobChecklistItem::getSort))
                .map(item -> {
                    JobChecklistEvent event = latest.get(item.getId());
                    // 과거 항목의 완료 개수는 유지하되 기록하지 않은 체크 시각은 만들어 내지 않는다.
                    Long offset = event != null && event.isDone() ? offsetSeconds(event.getChangedAt(), recordingStart, length) : null;
                    return new CustomerChecklistResponse.Mark(item.getId(), item.getGroupName(), item.getLabel(), offset);
                }).toList();
        return new CustomerChecklistResponse(done, items.size(), items.isEmpty() ? 0 : done * 100 / items.size(), groups, timeline, requests(b));
    }

    static Long offsetSeconds(Instant at, Instant recordingStart, Integer length) {
        if (recordingStart == null || at == null || at.isBefore(recordingStart)) return null;
        long offset = Duration.between(recordingStart, at).getSeconds();
        return length != null && length > 0 ? Math.min(offset, length) : offset;
    }

    /** Job/InProgress. 없으면 404 */
    @Transactional(readOnly = true)
    public ActiveJobResponse activeJob(UUID managerId) {
        Booking b = bookings.activeJob(managerId).orElseThrow(ApiException::notFound);
        Instant now = clock.now();
        // 경과 시간 = (지금 − 시작) − 모든 중단 구간 합. 앱은 이 값을 처음 보여 주고, 이후에는 startedAt·pausedSeconds 로 1초마다 스스로 센다
        // 완료 화면의 '총 소요'(lastCompleted)는 중단 구간을 빼지 않는다(벽시계, 2026-09-28 결정 — 같은 화면의 '청소 시간' 범위·Figma 예시와 맞춤).
        // 그래서 중단했던 작업은 총 소요가 마지막 경과 시간보다 길다
        long pausedSeconds = pausedSeconds(b, now);
        Duration elapsed = Duration.between(b.getStartedAt(), now).minusSeconds(pausedSeconds);
        boolean streaming = live.isStreaming(b.getId());
        String sinceLabel = KoreanFormat.clock(b.getStartedAt()) + "부터 진행";
        String since = sinceLabel + " · " + (streaming ? STREAMING : WAITING_CAMERA);
        // 체크리스트를 묶음 이름(groupName)별로 모은다. LinkedHashMap = 넣은 순서를 지키는 Map 이라 sort 순으로 처음 나온 묶음이 앞에 온다.
        // computeIfAbsent = 그 묶음 목록이 없으면 새로 만들고, 있으면 있던 목록을 꺼낸다
        Map<String, List<ActiveJobResponse.Item>> groups = new LinkedHashMap<>();
        for (JobChecklistItem i : checklist.findByBookingIdOrderBySort(b.getId())) {
            groups.computeIfAbsent(i.getGroupName(), k -> new ArrayList<>()).add(new ActiveJobResponse.Item(i.getId(), i.getLabel(), i.isDone()));
        }
        List<ActiveJobResponse.Group> list = groups.entrySet().stream().map(e -> new ActiveJobResponse.Group(e.getKey(), e.getValue())).toList();
        // status 는 작업 상태("paused"|"inProgress") — 앱 타이머는 라이브 상태가 아니라 이 값으로 멈춘다. equipment = 바디캠 상태 행(카메라·배터리·네트워크)
        return new ActiveJobResponse(b.getId(), b.getStatus() == BookingStatus.PAUSED ? "paused" : "inProgress", TITLE_IN_PROGRESS, KoreanFormat.hms(elapsed), b.getStartedAt().toString(), pausedSeconds, since, sinceLabel, streaming,
                live.activeJobEquipment(managerId), requests(b), list);
    }

    /** 홈 카드용 읽기 — 경과 시간이 아니라 누적 중단 초다 (경과 시간은 아래 elapsed) */
    @Transactional(readOnly = true)
    /** 홈 카드가 1초마다 경과 시간을 계산하는 데 쓰는 누적 중단 초 */
    public long pausedSecondsOf(Booking b) {
        return pausedSeconds(b, clock.now());
    }

    /** 시작부터 지금까지의 경과 시간(중단 구간 제외) — managerhome.ManagerHomeService 가 홈 카드의 첫 표시값("01:23:42")으로 쓴다 */
    public Duration elapsed(Booking b) {
        Instant now = clock.now();
        return Duration.between(b.getStartedAt(), now).minusSeconds(pausedSeconds(b, now));
    }

    /**
     * 일시정지 화면(Job/Paused) 값: 열린 중단 구간의 "14:32부터 중단됨" + app_settings.pause_note 안내문.
     * 진행 중 작업이 없거나, 있어도 paused 가 아니거나, 열린 구간이 없으면 404
     */
    @Transactional(readOnly = true)
    public PausedJobResponse pausedJob(UUID managerId) {
        Booking b = bookings.activeJob(managerId).filter(x -> x.getStatus() == BookingStatus.PAUSED).orElseThrow(ApiException::notFound);
        JobPause open = openPause(b.getId()).orElseThrow(ApiException::notFound);
        return new PausedJobResponse(b.getId(), KoreanFormat.sinceLabel(open.getPausedAt(), "부터 중단됨"), settings.get(SETTING_PAUSE_NOTE, String.class));
    }

    /** Job/Complete — 마지막 done 1건 (§6.3). 없으면 404 */
    @Transactional(readOnly = true)
    public CompletedJobResponse lastCompleted(UUID managerId) {
        Booking b = bookings.lastCompleted(managerId).orElseThrow(ApiException::notFound);
        List<JobChecklistItem> items = checklist.findByBookingIdOrderBySort(b.getId());
        long done = items.stream().filter(JobChecklistItem::isDone).count();
        String recording = live.recordingStatus(b.getId());
        // 예상 정산액: 정산 행이 있으면 그 최종 금액, 없으면 예약에 고정된 매니저 몫(managerPay). 둘 다 "85,000원" 모양
        String amount = settlements.findByBooking(b.getId()).map(Settlement::getFinalAmount).map(KoreanFormat::won).orElse(KoreanFormat.won(b.managerPay()));
        // 요약 4행: 청소 시간·총 소요(시작~끝, 중단 구간을 빼지 않는다)·체크리스트(전부 했으면 success)·촬영 기록
        // 주의: 진행 중 화면의 경과 시간(activeJob·elapsed)은 중단 구간을 뺀다 — 두 계산이 달라 통일할지는 PROGRESS.md '전체 검수 1차에서 나온 결정 항목' 에 남아 있다
        return new CompletedJobResponse(settings.get(SETTING_COMPLETE_TITLE, String.class),
                KoreanFormat.addressArea(b.getRoadAddress()) + " · " + catalog.requireCategory(b.getCategoryKey()).getLabel(),
                List.of(new CompletedJobResponse.SummaryRow("청소 시간", KoreanFormat.clockRange(b.getStartedAt(), b.getEndedAt()), "default"),
                        new CompletedJobResponse.SummaryRow("총 소요", KoreanFormat.durationKo(Duration.between(b.getStartedAt(), b.getEndedAt())), "default"),
                        new CompletedJobResponse.SummaryRow("체크리스트", items.size() + "개 중 " + done + "개 완료", done == items.size() ? "success" : "default"),
                        recordingRow(recording)),
                new CompletedJobResponse.Payout(LABEL_PAYOUT, amount, settings.get(SETTING_SETTLEMENT_NOTE, String.class)));
    }

    /**
     * 녹화 상태(LiveSessionService.recordingStatus) → 완료 화면 '촬영 기록' 행. tone 은 행 색(default·success·issue).
     * switch 식(→) = 값에 맞는 줄 하나를 골라 돌려준다. default 는 나열하지 않은 나머지 전부
     * ("none" = 세션이 없거나 녹화를 시작한 적 없음, 또는 녹화본을 지운 뒤)
     */
    static CompletedJobResponse.SummaryRow recordingRow(String status) {
        return switch (status) {
            case "ready" -> new CompletedJobResponse.SummaryRow("촬영 기록", "정상 업로드", "success");
            case "recording", "uploading" -> new CompletedJobResponse.SummaryRow("촬영 기록", "업로드 중", "default");
            case "failed" -> new CompletedJobResponse.SummaryRow("촬영 기록", "업로드 실패", "issue");
            default -> new CompletedJobResponse.SummaryRow("촬영 기록", "기록 없음", "issue"); // 바디캠이 한 번도 안 붙은 작업 (코드에서 정함)
        };
    }

    /** StatusBadge 값: scheduled | inProgress | paused | complete (매니저 앱 status.js) */
    public static String badge(BookingStatus s) {
        return switch (s) {
            case IN_PROGRESS -> "inProgress";
            case PAUSED -> "paused";
            case DONE -> "complete";
            default -> "scheduled";
        };
    }

    /** 요청사항 줄 — 사진 장수는 예약에 저장된 사진 경로 수로 센다 (진행 중 화면용. 서명 URL 을 만들지 않는다) */
    private List<String> requests(Booking b) {
        return requests(b, b.getRepairPhotos() == null ? 0 : b.getRepairPhotos().size());
    }

    /** 요청사항 줄: 카테고리 주의사항 라벨(예약 때 고른 key → 라벨) 다음에 Booking.requestLines 의 줄들 */
    private List<String> requests(Booking b, int photoCount) {
        List<String> out = new ArrayList<>(catalog.noticeLabels(b.getCategoryKey(), b.getNotices()));
        out.addAll(b.requestLines(photoCount)); // 수리 예약이면 가전·증상·상세·사진 장수도 (Booking.requestLines)
        return out;
    }

    /** 이 작업의 모든 중단 구간 길이 합(초). 아직 열린 구간은 now 까지 센다 */
    private long pausedSeconds(Booking b, Instant now) {
        return pauses.findByBookingIdOrderByPausedAt(b.getId()).stream().mapToLong(p -> p.length(now).getSeconds()).sum();
    }

    /** 아직 닫히지 않은 중단 구간. 여럿이면 가장 늦게 시작한 것 — reduce((a, c) -> c) 는 목록의 마지막 요소를 남긴다 */
    private Optional<JobPause> openPause(UUID bookingId) {
        return pauses.findByBookingIdOrderByPausedAt(bookingId).stream().filter(JobPause::isOpen).reduce((a, c) -> c);
    }
}
