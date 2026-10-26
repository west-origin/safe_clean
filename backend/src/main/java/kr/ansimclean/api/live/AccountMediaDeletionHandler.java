package kr.ansimclean.api.live;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.CleaningPhotoService;
import kr.ansimclean.api.booking.RepairPhotoService;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.events.AccountMediaDeleted;
import kr.ansimclean.api.common.outbox.OutboxHandler;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 탈퇴 뒤 남는 미디어·기기 정리 (완결성 검토 — 개인정보 약관 '보유 기간: 회원 탈퇴 시' 인데 집 안 녹화본·수리 사진·바디캠 스트림 키가 남았다):
 * 고객 → 그 고객 예약의 녹화본(Storage) 삭제 + repair-photos/<customerId>/ 사진 전부 삭제(예약에 안 붙은 것 포함, RepairPhotoService.deleteAllOf)
 *   + 그 고객 예약의 청소 전·후 사진 job-photos/<bookingId>/ 전부(CleaningPhotoService.deleteAllOfBookings). 매니저 → LiveKit Ingress 삭제(스트림 키 폐기) + manager_devices 삭제.
 * 예약·정산 행(법정 보존)은 그대로. 아웃박스라 실패하면 백오프 재시도.
 * 발행: auth.AuthService 의 탈퇴 처리가 AccountMediaDeleted 이벤트를 남긴다. 이 핸들러는 탈퇴 트랜잭션이 커밋된 뒤 아웃박스에서 따로 돈다 (CODE_GUIDE.md 3-6).
 */
@Component
public class AccountMediaDeletionHandler implements OutboxHandler {
    private static final Logger log = LoggerFactory.getLogger(AccountMediaDeletionHandler.class);

    private final BookingService bookings;
    private final LiveSessionRepository sessions;
    private final ManagerDeviceRepository devices;
    private final LiveKitGateway livekit;
    private final SupabaseStorageClient storage;
    private final AppProperties props;
    private final JsonMapper json = JsonMapper.builder().build(); // 아웃박스에 저장된 JSON 을 이벤트 record 로 다시 읽는 도구

    private final RepairPhotoService repairPhotos;
    private final CleaningPhotoService cleaningPhotos; // 청소 전·후 사진 (2026-10-23)

    public AccountMediaDeletionHandler(BookingService bookings, LiveSessionRepository sessions, ManagerDeviceRepository devices, LiveKitGateway livekit, SupabaseStorageClient storage, AppProperties props,
                                       RepairPhotoService repairPhotos, CleaningPhotoService cleaningPhotos) {
        this.repairPhotos = repairPhotos;
        this.cleaningPhotos = cleaningPhotos;
        this.bookings = bookings;
        this.sessions = sessions;
        this.devices = devices;
        this.livekit = livekit;
        this.storage = storage;
        this.props = props;
    }

    /** 맡는 이벤트 타입 — 이벤트 record 의 클래스 이름(AccountMediaDeleted)과 같은 글자다 */
    @Override
    public Set<String> types() {
        return Set.of("AccountMediaDeleted");
    }

    /**
     * 탈퇴한 사용자 id 하나로 ① 그 사용자 폴더의 수리 사진 전부 ② 고객으로서 예약한 건마다 준비된 녹화본 파일
     * ③ 매니저 바디캠 Ingress 와 기기 행을 지운다. 고객·매니저를 가리지 않고 셋 다 해 본다 — 해당 없는 쪽은 찾는 것이 없어 지나간다.
     * 던지면(예: Ingress 삭제 실패) 이 이벤트의 DB 변경은 되돌려지고 아웃박스가 나중에 다시 부른다.
     * 다시 불려도 안전하다 — 앞 시도가 되돌려졌으면 원장 행·녹화 상태(ready)도 되살아나 이미 지운 파일을 다시 찾지만,
     * Storage 파일·Ingress 가 이미 없으면(404) 성공으로 치고 넘어간다
     */
    @Override
    public void handle(String type, String payloadJson) { // 아웃박스가 이벤트마다 연 트랜잭션 안에서 돈다 — recordingDeleted() 가 같이 저장된다
        UUID userId = json.readValue(payloadJson, AccountMediaDeleted.class).userId();
        int recordings = 0;
        // 예약에 붙은 사진만이 아니라 예약 없이 올리고 그만둔 사진·원장 이전 사진(고객 폴더 전체)까지 (감사 M-04)
        int photos = repairPhotos.deleteAllOf(userId);
        List<Booking> customerBookings = bookings.customerBookings(userId);
        // 청소 전·후 사진(집 안 사진)도 지운다. 사진은 진행 중에만 찍을 수 있어 한 번이라도 시작한(startedAt 있는) 예약만 넘긴다 —
        // 시작도 안 한 예약마다 Storage 폴더 목록을 부르지 않으려고. Storage 실패는 던진다 → 아웃박스가 다시 부른다 (수리 사진과 같다)
        photos += cleaningPhotos.deleteAllOfBookings(customerBookings.stream().filter(b -> b.getStartedAt() != null).map(Booking::getId).toList());
        for (Booking b : customerBookings) {
            LiveSession s = sessions.findById(b.getId()).orElse(null); // 라이브 세션 행은 예약 id 를 키로 쓴다
            if (s != null && s.isRecordingReady()) { // 업로드가 끝난(ready) 녹화본만 지운다
                storage.delete(props.storage().recordingsBucket(), s.getRecordingPath()); // Supabase Storage 에서 파일 삭제 (바깥 호출)
                s.recordingDeleted(); // 세션 행의 녹화 상태를 none 으로, 경로·길이를 비운다 (행 자체는 남는다)
                recordings++;
            }
        }
        // 매니저 기기 행은 매니저 id 가 키다. 고객이면 없다
        ManagerDevice device = devices.findById(userId).orElse(null);
        if (device != null) {
            if (device.getIngressId() != null) {
                try {
                    livekit.deleteIngress(device.getIngressId());
                } catch (ApiException e) {
                    throw e; // 아웃박스 재시도 — 스트림 키가 살아 있으면 안 된다
                }
            }
            devices.delete(device); // manager_devices 행 삭제 (스트림 URL·키도 함께 사라진다)
        }
        log.info("탈퇴 미디어 정리 완료 user={} 녹화본={} 사진={} 기기={}", userId, recordings, photos, device != null); // 로그에는 id 와 개수만 남긴다
    }
}
