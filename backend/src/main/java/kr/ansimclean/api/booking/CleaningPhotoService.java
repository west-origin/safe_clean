package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.CleaningPhotoResponse;
import kr.ansimclean.api.booking.dto.CustomerCleaningPhotosResponse;
import kr.ansimclean.api.booking.dto.JobPhotosResponse;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 청소 전·후 사진 (2026-10-23, docs/api.md "청소 전·후 사진 추가").
 * 매니저가 청소 진행 중(in_progress·paused)에 위치마다 '전' 사진을 찍고, 완료 직전에 같은 위치의 '후' 사진을 찍는다.
 * 고객은 청소가 끝난(done) 뒤에만 전·후가 다 있는 위치를 비교해 본다.
 * 파일은 Storage 비공개 버킷 `job-photos` 에 `<bookingId>/<photoId>-before.<ext>`, `<bookingId>/<photoId>-after-<무작위>.<ext>` 로 둔다.
 * '후' 경로에 무작위 글자를 붙이는 이유: 다시 찍은 사진이 같은 경로를 쓰면 앱·브라우저가 예전 사진을 캐시에서 보여 줄 수 있다.
 *
 * 이 클래스에는 클래스 단위 @Transactional 이 없다 — Storage 호출(외부 효과)을 DB 트랜잭션 안에서 하지 않기 위해서다 (CLAUDE.md §11-1).
 * 올리기 순서: 예약 확인(소유권·진행 중) → 한도·파일 검사 → Storage 업로드 → [트랜잭션: 예약 잠금 → 다시 확인 → 행 저장].
 * 행 저장이 실패하면 방금 올린 파일을 지운다(실패해도 경고만 — 남은 파일은 보존 기간 정리·탈퇴 정리가 예약 폴더 목록으로 찾아 지운다).
 * 바꾸기·지우기 순서: [트랜잭션: 행 변경] → 커밋 뒤 예전 파일 삭제(실패해도 경고만).
 * 라이브의 탈퇴 정리(live/AccountMediaDeletionHandler)가 deleteAllOfBookings 를, 매일 정리(CleaningPhotoRetention)가 deleteExpired 를 부른다.
 */
@Service
public class CleaningPhotoService {
    private static final Logger log = LoggerFactory.getLogger(CleaningPhotoService.class);

    public static final String BUCKET = "job-photos"; // Supabase Storage 비공개 버킷 이름 (docs/runbook.md 에 만드는 SQL)
    public static final int MAX_PHOTOS = 10; // 예약 한 건에 찍을 수 있는 위치 수 — 응답의 max 로도 내려 준다
    static final int MAX_LOCATION_LENGTH = 20; // 위치 이름 최대 글자 수 (V055 의 check 제약과 같다)
    static final int URL_SECONDS = 60 * 60; // 서명 URL 유효 시간 1시간 — 화면을 다시 열면 새로 받는다
    /** 매니저당 시간당 업로드 상한 — 10곳 × 전·후 2장에 다시 찍기를 넉넉히 더한 값. 남용(Storage 비용)만 막는다 */
    static final int UPLOADS_PER_MANAGER_PER_HOUR = 120;
    /** 보존 일수 app_settings 키 — 녹화본과 같은 값을 쓴다(live/RecordingRetention.SETTING_RETENTION_DAYS 와 같은 글자. booking 은 live 를 참조할 수 없어 따로 둔다) */
    static final String SETTING_RETENTION_DAYS = "recording_retention_days";
    static final int RETENTION_BATCH = 500; // 정리 한 번에 보는 최대 장수 — 남은 것은 다음 날

    // 화면에 그대로 보이는 문구 (코드에서 정함)
    static final String TOO_MANY = "사진은 위치 10곳까지 찍을 수 있어요";
    static final String BAD_LOCATION = "위치 이름을 1~20자로 적어주세요";
    static final String NOT_ACTIVE = "진행 중인 작업에서만 사진을 찍을 수 있어요";
    static final String NOT_VIEWABLE = "진행 중이거나 끝난 작업의 사진만 볼 수 있어요";

    // 형식(Content-Type) → 저장할 파일 확장자. RepairPhotoService 와 같은 규칙(이미지 4종, 내용으로 판정)
    private static final Map<String, String> EXT = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/heic", "heic", "image/heif", "heif");

    private final SupabaseStorageClient storage; // Storage 업로드·삭제·서명 URL (외부 호출)
    private final RateLimiter rateLimiter;
    private final BookingService bookings; // 예약 소유권·상태 확인과 잠금
    private final CleaningPhotoRepository photos; // cleaning_photos 원장
    private final AppSettingsService settings; // 보존 일수
    private final AppClock clock;
    private final RecordingLookup recordings;
    private final TransactionTemplate tx; // 코드로 트랜잭션을 여는 도구 — Storage 호출과 DB 저장을 나누려고 쓴다

    public CleaningPhotoService(SupabaseStorageClient storage, RateLimiter rateLimiter, BookingService bookings, CleaningPhotoRepository photos,
                                AppSettingsService settings, AppClock clock, TransactionTemplate tx, RecordingLookup recordings) {
        this.recordings = recordings;
        this.storage = storage;
        this.rateLimiter = rateLimiter;
        this.bookings = bookings;
        this.photos = photos;
        this.settings = settings;
        this.clock = clock;
        this.tx = tx;
    }

    // ── 매니저 ──

    /**
     * 작업의 사진 목록 (찍은 순서). 진행 중(in_progress·paused)이거나 끝난(done) 작업만 — 그 밖의 상태는 409 INVALID_STATE.
     * 남의 작업이면 404 NOT_FOUND (존재를 알리지 않는다)
     */
    public JobPhotosResponse managerList(UUID managerId, UUID bookingId) {
        Booking b = bookings.requireManagerBooking(managerId, bookingId);
        if (!BookingStatus.ACTIVE.contains(b.getStatus()) && b.getStatus() != BookingStatus.DONE) throw new ApiException(ErrorCode.INVALID_STATE, NOT_VIEWABLE);
        return new JobPhotosResponse(photos.findByBookingIdOrderBySortAsc(bookingId).stream().map(this::toResponse).toList(), MAX_PHOTOS, b.getRequiredPhotoRooms(), b.getPhotoPhase());
    }

    /**
     * '전' 사진 한 장 + 위치 이름 → 새 사진 한 쌍(afterUrl 은 null).
     * 400 VALIDATION: 위치 이름·파일 문제·10곳 초과. 409 INVALID_STATE: 진행 중이 아님. 404: 남의 작업. 429: 시간당 상한. 502: Storage 실패
     */
    public CleaningPhotoResponse addBefore(UUID managerId, UUID bookingId, String location, String contentType, byte[] bytes) {
        String loc = normalizeLocation(location);
        requireBefore(bookings.requireManagerBooking(managerId, bookingId), loc);
        rateLimiter.check("job-photo", managerId.toString(), UPLOADS_PER_MANAGER_PER_HOUR);
        String type = checkImage(contentType, bytes);
        if (photos.countByBookingId(bookingId) >= MAX_PHOTOS) throw ApiException.validation(TOO_MANY);
        UUID id = UUID.randomUUID();
        String path = bookingId + "/" + id + "-before." + EXT.get(type);
        storage.upload(BUCKET, path, bytes, type); // 트랜잭션 밖에서 (실패하면 502 를 던지고 아무 행도 남지 않는다)
        CleaningPhoto saved;
        try {
            // 예약 행을 잠가(select … for update) 같은 작업의 동시 업로드·완료 처리와 줄을 세운 뒤 다시 확인한다.
            // 올리는 사이 작업이 완료됐거나 다른 요청이 10장째를 채웠으면 여기서 걸린다
            saved = tx.execute(status -> {
                requireBefore(bookings.lockManagerBooking(managerId, bookingId), loc);
                if (photos.countByBookingId(bookingId) >= MAX_PHOTOS) throw ApiException.validation(TOO_MANY);
                return photos.save(CleaningPhoto.of(id, bookingId, loc, photos.maxSort(bookingId) + 1, path, clock.now()));
            });
        } catch (RuntimeException e) {
            deleteQuietly(path); // 행이 없는 파일을 남기지 않는다
            throw e;
        }
        return toResponse(saved);
    }

    /**
     * 같은 위치의 '후' 사진을 붙인다(다시 찍으면 바꾼다) → 바뀐 사진 한 쌍.
     * 없는 사진 id(또는 다른 작업의 사진) → 404. 나머지 오류는 addBefore 와 같다
     */
    public CleaningPhotoResponse addAfter(UUID managerId, UUID bookingId, UUID photoId, String contentType, byte[] bytes) {
        requireAfter(bookings.requireManagerBooking(managerId, bookingId));
        photos.findByIdAndBookingId(photoId, bookingId).orElseThrow(ApiException::notFound);
        rateLimiter.check("job-photo", managerId.toString(), UPLOADS_PER_MANAGER_PER_HOUR);
        String type = checkImage(contentType, bytes);
        // 무작위 8글자 — 다시 찍을 때마다 다른 경로가 되어 캐시된 예전 사진과 겹치지 않는다
        String path = bookingId + "/" + photoId + "-after-" + UUID.randomUUID().toString().substring(0, 8) + "." + EXT.get(type);
        storage.upload(BUCKET, path, bytes, type);
        String[] old = new String[1]; // 트랜잭션 안에서 꺼낸 예전 '후' 경로 (람다 안에서 바꾸려고 배열에 담는다)
        CleaningPhoto saved;
        try {
            saved = tx.execute(status -> {
                requireAfter(bookings.lockManagerBooking(managerId, bookingId));
                CleaningPhoto p = photos.findByIdAndBookingId(photoId, bookingId).orElseThrow(ApiException::notFound); // 그 사이 지워졌으면 404
                old[0] = p.replaceAfter(path, clock.now()); // 변경은 save 없이 커밋 때 저장된다 (CODE_GUIDE.md 3-3)
                return p;
            });
        } catch (RuntimeException e) {
            deleteQuietly(path);
            throw e;
        }
        if (old[0] != null) deleteQuietly(old[0]); // 커밋 뒤 예전 '후' 파일 삭제. 실패하면 경고만 — 보존 기간 정리·탈퇴 정리가 나중에 지운다
        return toResponse(saved);
    }

    /** 사진 한 쌍을 지운다 (진행 중 작업만). 행을 먼저 지우고 커밋 뒤 파일을 지운다. 없는 사진이면 404 */
    public void delete(UUID managerId, UUID bookingId, UUID photoId) {
        CleaningPhoto removed = tx.execute(status -> {
            requireActive(bookings.lockManagerBooking(managerId, bookingId));
            if (bookings.requireManagerBooking(managerId, bookingId).getBeforePhotosConfirmedAt() != null)
                throw ApiException.validation("영상 시작 후에는 전 사진을 삭제할 수 없어요");
            CleaningPhoto p = photos.findByIdAndBookingId(photoId, bookingId).orElseThrow(ApiException::notFound);
            photos.delete(p);
            return p;
        });
        deleteQuietly(removed.getBeforePath());
        if (removed.getAfterPath() != null) deleteQuietly(removed.getAfterPath());
    }

    // ── 고객 ──

    /**
     * 고객 예약의 전·후 사진. ready = 청소가 끝났는가. 끝나기 전에는 photos 가 빈 목록,
     * 끝난 뒤에는 전·후가 다 있는 위치만 (한쪽만 있으면 비교할 수 없어 뺀다). 남의 예약이면 404
     */
    public CustomerCleaningPhotosResponse customerList(UUID customerId, UUID bookingId) {
        Booking b = bookings.requireCustomerBooking(customerId, bookingId);
        if (b.getStatus() != BookingStatus.DONE) return new CustomerCleaningPhotosResponse(false, List.of());
        return new CustomerCleaningPhotosResponse(true, photos.findByBookingIdOrderBySortAsc(bookingId).stream()
                .filter(CleaningPhoto::isPaired).map(this::toResponse).toList());
    }

    // ── 정리 ──

    /**
     * 고객 탈퇴: 그 고객 예약들의 사진 파일 전부(원장 경로 + Storage 의 예약 폴더 목록)를 지우고 행도 지운다.
     * 탈퇴 아웃박스 처리 묶음 안에서 불린다(live/AccountMediaDeletionHandler). Storage 실패는 그대로 던진다 — 아웃박스가 다시 부른다.
     * 지운 파일 수를 돌려준다
     */
    public int deleteAllOfBookings(List<UUID> bookingIds) {
        if (bookingIds.isEmpty()) return 0;
        List<CleaningPhoto> rows = photos.findByBookingIdIn(bookingIds);
        // 집합이라 원장과 폴더 목록에 다 있는 경로는 한 번만 지운다. 폴더 목록은 행 저장에 실패해 남은 파일·바뀌기 전 '후' 파일까지 잡는다
        Set<String> paths = new LinkedHashSet<>();
        for (CleaningPhoto p : rows) {
            paths.add(p.getBeforePath());
            if (p.getAfterPath() != null) paths.add(p.getAfterPath());
        }
        for (UUID id : bookingIds) paths.addAll(storage.list(BUCKET, id.toString()));
        for (String path : paths) storage.delete(BUCKET, path);
        // 행은 파일을 다 지운 뒤에 — 중간에 실패하면 행이 남아 다시 시도할 때 또 잡힌다
        photos.deleteAll(rows);
        return paths.size();
    }

    /**
     * CleaningPhotoRetention(매일): 완료된 지 보존 일수(app_settings recording_retention_days, 기본 90)가 지난 예약의 사진을 지운다.
     * 예약 한 건씩: 그 예약의 행 경로 + Storage 예약 폴더 목록(행 저장 실패·다시 찍기로 남은 파일까지)을 지운 뒤 행을 지운다.
     * 한 건이 실패하면 경고만 남기고 다음 건으로 (RepairPhotoService.deleteUnattached 와 같은 방식) — 남은 행은 내일 다시.
     * 묶음 없이 불려 행 삭제는 레포지토리 호출마다 따로 커밋된다. 지운 사진 행 수를 돌려준다
     */
    public int deleteExpired() {
        int days = settings.integer(SETTING_RETENTION_DAYS);
        // 후보 행을 예약별로 묶는다 (LinkedHashMap = 넣은 순서 유지 — 오래 전에 끝난 예약부터)
        Map<UUID, List<CleaningPhoto>> byBooking = new java.util.LinkedHashMap<>();
        for (CleaningPhoto p : photos.findExpired(clock.now().minus(Duration.ofDays(days)), RETENTION_BATCH)) {
            byBooking.computeIfAbsent(p.getBookingId(), k -> new java.util.ArrayList<>()).add(p);
        }
        int n = 0;
        for (Map.Entry<UUID, List<CleaningPhoto>> e : byBooking.entrySet()) {
            try {
                Set<String> paths = new LinkedHashSet<>();
                for (CleaningPhoto p : e.getValue()) {
                    paths.add(p.getBeforePath());
                    if (p.getAfterPath() != null) paths.add(p.getAfterPath());
                }
                paths.addAll(storage.list(BUCKET, e.getKey().toString()));
                for (String path : paths) storage.delete(BUCKET, path); // 이미 없으면(404) 성공으로 친다
                photos.deleteAll(e.getValue());
                n += e.getValue().size();
            } catch (RuntimeException ex) {
                log.warn("청소 사진 삭제 실패 booking={} — 내일 다시: {}", e.getKey(), ex.getMessage());
            }
        }
        return n;
    }

    // ── 도구 ──

    /** 진행 중(in_progress·paused)이 아니면 409 */
    private void requireBefore(Booking b, String location) {
        requireActive(b);
        if (b.getBeforePhotosConfirmedAt() != null) throw ApiException.validation("영상 시작 후에는 전 사진을 바꿀 수 없어요");
        if (!b.getRequiredPhotoRooms().contains(location)) throw ApiException.validation("등록된 촬영 공간을 선택해주세요");
        if (photos.findByBookingIdOrderBySortAsc(b.getId()).stream().anyMatch(p -> location.equals(p.getLocation())))
            throw ApiException.validation("이미 이 공간의 전 사진이 있어요. 삭제 후 다시 찍어주세요");
    }

    private void requireAfter(Booking b) {
        requireActive(b);
        if (b.getVideoFinishedAt() == null) throw ApiException.validation("실시간 영상을 먼저 종료해주세요");
        if (!recordings.isVideoEnded(b.getId())) throw ApiException.validation("실시간 영상 종료를 다시 시도해주세요");
    }

    private static void requireActive(Booking b) {
        if (!BookingStatus.ACTIVE.contains(b.getStatus())) throw new ApiException(ErrorCode.INVALID_STATE, NOT_ACTIVE);
    }

    /** 앞뒤 공백을 빼고 1~20자인지 본다. 글자 수는 DB(char_length)와 같게 '글자' 단위(이모지도 1자)로 센다 */
    static String normalizeLocation(String location) {
        String loc = location == null ? "" : location.strip();
        int length = loc.codePointCount(0, loc.length());
        if (length < 1 || length > MAX_LOCATION_LENGTH) throw ApiException.validation(BAD_LOCATION);
        return loc;
    }

    /**
     * 크기·형식·해상도 검사 (RepairPhotoService.upload 와 같은 규칙과 문구). 통과하면 파일 내용으로 판정한 형식(image/…)을 돌려준다.
     * 앱이 선언한 Content-Type 은 1차 거름망일 뿐이고, 실제 형식은 ImageBytes 가 파일 앞부분으로 고른다
     */
    private static String checkImage(String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw ApiException.validation("사진 파일이 비어 있어요");
        if (bytes.length > RepairPhotoService.MAX_BYTES) throw ApiException.validation("사진은 5MB 이하로 올려주세요");
        String type = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase();
        if (!EXT.containsKey(type)) throw ApiException.validation("이미지 파일(JPG·PNG·WEBP·HEIC)만 올릴 수 있어요");
        String actual = ImageBytes.detect(bytes).orElseThrow(() -> ApiException.validation(RepairPhotoService.UNREADABLE));
        if (!actual.equals("image/heic")) { // HEIC 는 헤더에서 크기를 읽지 않는다 (ImageBytes 설명)
            long[] size = ImageBytes.dimensions(bytes, actual).orElseThrow(() -> ApiException.validation(RepairPhotoService.UNREADABLE));
            if (size[0] <= 0 || size[1] <= 0) throw ApiException.validation(RepairPhotoService.UNREADABLE);
            if (size[0] > ImageBytes.MAX_PIXELS / size[1]) throw ApiException.validation("사진 해상도가 너무 커요. 다른 사진을 골라주세요");
        }
        return actual;
    }

    /** 행 → 응답. 서명 URL 은 장마다 1시간짜리 */
    private CleaningPhotoResponse toResponse(CleaningPhoto p) {
        return new CleaningPhotoResponse(p.getId(), p.getLocation(), url(p.getBeforePath()), p.getAfterPath() == null ? null : url(p.getAfterPath()));
    }

    /** 서명 URL. 한 장이 실패해도 목록 전체를 실패시키지 않고 그 칸만 null (경고 로그) */
    private String url(String path) {
        try {
            return storage.signedUrl(BUCKET, path, URL_SECONDS);
        } catch (RuntimeException e) {
            log.warn("청소 사진 서명 URL 실패 {}: {}", path, e.getMessage());
            return null;
        }
    }

    /** 파일 삭제 — 실패해도 던지지 않는다(경고만). 남은 파일은 보존 기간 정리·탈퇴 정리의 폴더 목록이 지운다 */
    private void deleteQuietly(String path) {
        try {
            storage.delete(BUCKET, path);
        } catch (RuntimeException e) {
            log.warn("청소 사진 파일 삭제 실패 {} — 나중 정리에 맡긴다: {}", path, e.getMessage());
        }
    }
}
