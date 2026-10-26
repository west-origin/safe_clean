package kr.ansimclean.api.live;

import kr.ansimclean.api.booking.RecordingLookup;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.live.dto.DeviceResponse;
import kr.ansimclean.api.live.dto.DeviceStatusRow;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 라이브 세션·바디캠 기기의 DB 쪽 (B4). LiveKit 호출(Ingress·Egress·mute·웹훅)은 B5 가 LiveService 로 붙인다 (§8).
 * 다른 패키지(offer·job)는 이 서비스의 메서드로만 세션 행을 다룬다 — 읽기뿐 아니라 행 준비·잠금 읽기·작업 일시정지 시각 기록 같은
 * DB 안의 쓰기도 여기서 한다. 외부 효과는 이벤트로 (§2.4).
 * 부르는 곳 예: OfferService(콜 수락 때 세션 행 준비), JobService(jobs/pause 선행 조건·진행 중 화면).
 * offer·job 의 쓰기 묶음 안에서 부르면 그 묶음에 합쳐진다 — 여기서 건 행 잠금은 부른 쪽 묶음이 끝날 때 풀린다 (CODE_GUIDE.md 3-3).
 * 클래스에 붙은 Primary 표시 = RecordingLookup 구현이 NoopRecordingLookup 에도 있어 스프링이 주입할 때 이쪽을 고르게 한다.
 */
@Service
@Primary
@Transactional
public class LiveSessionService implements RecordingLookup {
    @Override
    public boolean isVideoEnded(UUID bookingId) {
        return sessions.findById(bookingId).filter(LiveSession::isEnded).isPresent();
    }
    // 장비 상태 줄의 라벨·값. 매니저 앱 Job/Preparing·Job/InProgress·Profile/Equipment 의 장비 상태 줄에 그대로 나온다
    static final String CAMERA = "카메라";
    static final String BATTERY = "배터리";
    static final String NETWORK = "네트워크";
    static final String STREAMING = "실시간 전송";
    static final String UNKNOWN = "확인 필요";

    private final LiveSessionRepository sessions;
    private final ManagerDeviceRepository devices;
    private final JsonMapper json = JsonMapper.builder().build(); // 기기 last_report(jsonb 문자열)를 JSON 트리로 읽는 Jackson 파서

    public LiveSessionService(LiveSessionRepository sessions, ManagerDeviceRepository devices) {
        this.sessions = sessions;
        this.devices = devices;
    }

    /** 배정·재배정 때 upsert: 없으면 만들고 있으면 created 로 리셋 (§6.1 assign) */
    public LiveSession ensureCreated(UUID bookingId) {
        // 예: OfferService 콜 수락(assign)·일정 변경(onRescheduled).
        // 있으면 reset() 만 한다(저장은 커밋 때, CODE_GUIDE.md 3-3). 없으면 새 행을 저장한다
        return sessions.findById(bookingId).map(s -> {
            s.reset();
            return s;
        }).orElseGet(() -> sessions.save(LiveSession.create(bookingId)));
    }

    // 예약 id로 읽는다. 고객 체크리스트(JobService)가 실제 녹화 시작 시각·길이를 조회한다.
    @Transactional(readOnly = true)
    public Optional<LiveSession> find(UUID bookingId) {
        return sessions.findById(bookingId);
    }

    /** 영상이 고객에게 나가는 중인가(live). 작업 일시정지의 선행 조건은 이것이 아니라 아래 mayBeStreaming 이다 (§8.2.1: 영상이 나가는데 상태가 일시정지인 순간은 없다) */
    @Transactional(readOnly = true)
    public boolean isStreaming(UUID bookingId) {
        // JobService.activeJob 이 진행 중 화면의 '실시간 촬영 중 / 카메라 연결 대기 중' 문구를 고를 때 쓴다. 세션이 없으면 false
        return sessions.findById(bookingId).map(LiveSession::isStreaming).orElse(false);
    }

    /** 영상이 나가고 있을 수 있는가(live 또는 paused+unmute_pending) — jobs/pause 는 이것이 false 여야 한다 */
    @Transactional // 클래스 기본과 같은 쓰기 트랜잭션 — 행 잠금(lockById)을 쓰므로 readOnly 로 두지 않는다
    public boolean mayBeStreaming(UUID bookingId) {
        // jobs/pause 는 예약을 잠근 뒤 이걸 부른다. 세션도 잠가야 카메라 재접속(웹훅)이 그 사이에 live 로 바꾸지 못한다 (§8.2.1)
        // lockById = 행 잠금(FOR UPDATE, CODE_GUIDE.md 3-4). JobService.pause 의 묶음에 합쳐지므로 작업 일시정지가 저장될 때까지 잠겨 있다.
        // JobService.pause 는 이 값이 true 면 INVALID_STATE("영상을 먼저 중단해주세요")로 거절한다
        return sessions.lockById(bookingId).map(LiveSession::mayBeStreaming).orElse(false);
    }

    /** jobs/pause 가 작업을 일시정지했다 — 시각을 세션에 기록만 남긴다 (재개 판단은 videoHeld 와 jobResumed 가 한다) */
    public void jobPaused(UUID bookingId, java.time.Instant now) {
        // JobService.pause 가 작업 상태를 paused 로 바꾼 직후 부른다. 세션이 없으면(라이브를 시작한 적 없음) 아무것도 하지 않는다.
        sessions.findById(bookingId).ifPresent(s -> s.jobPaused(now));
    }

    /**
     * jobs/resume 이 작업을 재개했다 = 매니저가 '촬영 다시 시작' 을 눌렀다 — 영상을 멈춰 둔 표시(videoHeld)를 푼다.
     * 이어지는 live/resume 이 서버에 닿지 않아도 재조정기가 영상을 다시 켤 수 있게 (계획 §9.2).
     * 재조정기·웹훅과 같은 행을 바꾸므로 잠그고 바꾼다 — JobService.resume 이 예약 → 세션 순서로 잠근다 (JobService.pause 와 같은 순서)
     */
    public void jobResumed(UUID bookingId) {
        sessions.lockById(bookingId).ifPresent(LiveSession::releaseVideo);
    }

    // 바디캠이 이 예약 방에 붙어 있는가(세션 live 또는 paused). 세션이 없으면 false. 지금 main 코드에서 부르는 곳은 없다
    @Transactional(readOnly = true)
    public boolean isCameraOnline(UUID bookingId) {
        return sessions.findById(bookingId).map(LiveSession::isCameraOnline).orElse(false);
    }

    /** 내 예약 완료 탭 "녹화 영상 보기" (booking/RecordingLookup) */
    @Override
    @Transactional(readOnly = true)
    public Set<UUID> readyBookingIds(Collection<UUID> bookingIds) {
        if (bookingIds.isEmpty()) return Set.of();
        // BookingService 가 고객 예약 카드(목록 rows·한 건 toReservation)에 녹화본 준비 여부를 넣을 때 부른다
        return sessions.findByBookingIdInAndRecordingStatus(bookingIds, LiveSession.REC_READY).stream().map(LiveSession::getBookingId).collect(Collectors.toSet());
    }

    /** 완료 화면 "촬영 기록" 에 쓰는 녹화 상태. 세션이 없으면 none */
    @Transactional(readOnly = true)
    public String recordingStatus(UUID bookingId) {
        // 값: none | recording | uploading | ready | failed (LiveSession.REC_*). JobService.lastCompleted(Job/Complete)가 부른다
        return sessions.findById(bookingId).map(LiveSession::getRecordingStatus).orElse("none");
    }

    /** Job/Preparing 장비 4행 (§5.3 getEquipmentStatus). 카메라·전송 = stream_status, 배터리·네트워크 = last_report(없으면 "확인 필요") */
    @Transactional(readOnly = true)
    public List<DeviceStatusRow> deviceStatus(UUID managerId) {
        // DeviceController GET /manager/device/status → 매니저 앱 getEquipmentStatus (Job/Preparing).
        // online = manager_devices.stream_status. ingress_started 웹훅이 대기 방(parking)에 붙어도 켜므로, 작업 시작 전에도 '연결됨' 이 될 수 있다.
        // 기기가 없거나 last_report 가 비어 있으면 report 는 null → 배터리·네트워크는 '확인 필요'
        Optional<ManagerDevice> device = devices.findById(managerId);
        boolean online = device.map(ManagerDevice::isOnline).orElse(false);
        JsonNode report = device.map(ManagerDevice::getLastReport).map(json::readTree).orElse(null);
        return List.of(
                online ? DeviceStatusRow.ok(CAMERA, "연결됨") : DeviceStatusRow.issue(CAMERA, "연결 안 됨"),
                reportRow(BATTERY, report, "battery"),
                reportRow(NETWORK, report, "network"),
                online ? DeviceStatusRow.ok(STREAMING, "준비 완료") : DeviceStatusRow.issue(STREAMING, "대기 중"));
    }

    /** Job/InProgress 촬영 상태 3행 (카메라 정상/연결 안 됨 · 배터리 · 네트워크) */
    @Transactional(readOnly = true)
    public List<DeviceStatusRow> activeJobEquipment(UUID managerId) {
        // JobService.activeJob 이 부른다. deviceStatus 의 앞 3줄(카메라·배터리·네트워크)을 쓰고, 카메라가 ok 면 값만 '정상' 으로 바꾼다
        List<DeviceStatusRow> rows = deviceStatus(managerId);
        DeviceStatusRow camera = rows.get(0);
        return List.of(new DeviceStatusRow(CAMERA, "ok".equals(camera.status()) ? "정상" : camera.value(), camera.status()), rows.get(1), rows.get(2));
    }

    /** Profile/Equipment. 기기 미등록이면 404 (운영자가 등록, §13-8) */
    @Transactional(readOnly = true)
    public DeviceResponse device(UUID managerId) {
        // DeviceController GET /manager/device → 매니저 앱 getEquipmentDevice. 이 화면은 배터리·네트워크가 먼저다 (deviceStatus 와 순서가 다르다).
        // 스트림 키는 끝 4자만 보이고, 마지막 연결 시각은 서울 시간 "9월 15일 14:32" 모양. 한 번도 붙은 적 없으면 null
        ManagerDevice d = devices.findById(managerId).orElseThrow(ApiException::notFound);
        JsonNode report = d.getLastReport() == null ? null : json.readTree(d.getLastReport());
        List<DeviceStatusRow> status = List.of(
                reportRow(BATTERY, report, "battery"),
                reportRow(NETWORK, report, "network"),
                d.isOnline() ? DeviceStatusRow.ok(CAMERA, "정상") : DeviceStatusRow.issue(CAMERA, "연결 안 됨"),
                d.isOnline() ? DeviceStatusRow.ok(STREAMING, "정상") : DeviceStatusRow.issue(STREAMING, "대기 중"));
        return new DeviceResponse(d.getName(), d.isOnline() ? "정상 연결" : "연결 안 됨", d.isOnline(), status, new DeviceResponse.Stream(d.getStreamUrl(), maskKey(d.getStreamKey())),
                d.getLastOnlineAt() == null ? null : KoreanFormat.dateShort(kr.ansimclean.api.common.time.AppClock.toSeoul(d.getLastOnlineAt()).toLocalDate()) + " " + KoreanFormat.clock(d.getLastOnlineAt()));
    }

    /** last_report JSON 의 field(battery·network) 값을 한 줄로 만든다. 값이 없거나 null 이면 "확인 필요"(issue) */
    private static DeviceStatusRow reportRow(String label, JsonNode report, String field) {
        JsonNode v = report == null ? null : report.get(field);
        if (v == null || v.isNull()) return DeviceStatusRow.issue(label, UNKNOWN);
        return DeviceStatusRow.ok(label, v.asString());
    }

    /** 스트림 키 마스킹: 끝 4자만 ("****-**-1234"). 없으면 null */
    static String maskKey(String key) {
        if (key == null || key.isBlank()) return null;
        String tail = key.length() >= 4 ? key.substring(key.length() - 4) : key;
        return "****-**-" + tail;
    }
}
