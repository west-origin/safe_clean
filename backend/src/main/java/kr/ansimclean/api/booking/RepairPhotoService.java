package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.RepairPhotoResponse;
import kr.ansimclean.api.common.web.RateLimiter;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.profile.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 수리 예약 증상 사진 (Figma 1:483). Storage 비공개 버킷 `repair-photos` 에 `<customerId>/<uuid>.<ext>` 로 올리고 경로와 미리보기 서명 URL 을 돌려준다.
 * 예약 생성 때 경로가 이 고객 것인지 검사한다 (BookingService.ownPhotos). 5MB·이미지만 — 형식은 파일 내용(ImageBytes)으로 판정한다.
 * 올린 사진의 원장은 repair_photo_uploads 테이블(RepairPhotoUpload).
 * 이 클래스에는 트랜잭션 설정이 없어 묶음은 부르는 쪽이 정한다 (CODE_GUIDE.md 3-3):
 * upload(RepairPhotoController)·deleteUnattached(RepairPhotoCleaner)는 묶음 없이 불려 레포지토리 호출마다 따로 커밋된다.
 * attach 는 BookingService 의 예약 생성 묶음 안에서 불려, 여기서 던지면 예약도 같이 되돌려진다.
 * deleteAllOf 는 탈퇴 뒤 아웃박스 처리 묶음 안에서(live/AccountMediaDeletionHandler) 불려, 던지면 아웃박스가 나중에 다시 부른다.
 */
@Service
public class RepairPhotoService {
    private static final Logger log = LoggerFactory.getLogger(RepairPhotoService.class);
    public static final String BUCKET = "repair-photos"; // Supabase Storage 비공개 버킷 이름. job/JobService.REPAIR_PHOTO_BUCKET 이 같은 글자를 따로 들고 있어 바꾸면 둘 다
    public static final long MAX_BYTES = 5L * 1024 * 1024; // 5MB — application.yml 의 multipart 상한과 같다
    static final int PREVIEW_SECONDS = 24 * 60 * 60; // 예약 플로우를 하루 안에 끝낸다고 본다 — 만료되면 화면이 다시 올리게 한다
    // 고객 앱은 이 URL 을 새로 받지 않고, 사진마다 올린 시각을 초안에 두어 24시간이 지나면 초안에서 빼고 PHOTO_EXPIRED 와 같은 문구로
    // 다시 올리게 한다(apps/customer repairFlow.js PHOTO_TTL_MS — 이 값을 바꾸면 그쪽도). 그 사이 정리가 먼저 지웠으면 attach 의 PHOTO_EXPIRED 를 받고 사진을 비운다
    // 형식(Content-Type) → 저장할 파일 확장자. image/heif 는 선언 허용 목록(TYPES)용이다 —
    // 확장자는 내용 판정(ImageBytes.detect) 결과로 고르고 detect 는 HEIF 도 image/heic 로 돌려주므로 .heif 로 저장되지는 않는다
    private static final Map<String, String> EXT = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/heic", "heic", "image/heif", "heif");
    private static final Set<String> TYPES = EXT.keySet(); // 앱이 선언해도 되는 Content-Type 목록 (1차 거름망)
    static final String UNREADABLE = "사진 파일을 읽을 수 없어요. 다른 사진을 골라주세요"; // (코드에서 정함)
    /** 올린 뒤 미리보기 주소를 만들지 못했을 때 — Storage 업로드 실패(SupabaseStorageClient.upload)와 같은 문구 */
    static final String UPLOAD_FAILED = "사진을 올리지 못했어요. 잠시 후 다시 시도해주세요";

    private final SupabaseStorageClient storage; // Supabase Storage 에 파일을 올리고·지우고·서명 URL 을 받는 HTTP 호출 (외부 호출)

    /** 사용자당 시간당 업로드 상한 — 예약 한 건에 5장, 다시 고르는 것을 감안해도 넉넉하다. 남용(Storage 비용)만 막는다 (검수 3차) */
    static final int UPLOADS_PER_USER_PER_HOUR = 60;

    private final RateLimiter rateLimiter;
    private final RepairPhotoUploadRepository uploads; // 업로드 원장(repair_photo_uploads) — 어떤 사진이 예약에 붙었는지 기록
    private final AppClock clock;

    /** 예약에 붙지 않은 사진을 이 시간 뒤에 지운다 — 미리보기 URL(24시간)과 같다. 예약 플로우를 하루 안에 끝낸다고 본다 */
    static final Duration UNATTACHED_KEEP = Duration.ofSeconds(PREVIEW_SECONDS);
    static final int CLEANUP_BATCH = 200; // 정리 한 번에 지우는 최대 장수 — 남은 것은 다음 정리 때

    /** 예약에 붙이려는 사진이 그 사이 정리됐다 (코드에서 정함, 재감사 M-04) */
    static final String PHOTO_EXPIRED = "첨부한 사진이 만료됐어요. 사진을 다시 올려주세요";

    private final ProfileService profiles; // 탈퇴하지 않은 계정인지 확인(holdLive)

    public RepairPhotoService(SupabaseStorageClient storage, RateLimiter rateLimiter, RepairPhotoUploadRepository uploads, ProfileService profiles, AppClock clock) {
        this.storage = storage;
        this.rateLimiter = rateLimiter;
        this.uploads = uploads;
        this.profiles = profiles;
        this.clock = clock;
    }

    /**
     * 사진 한 장 올리기: 계정 확인 → 시간당 한도 → 크기·형식·해상도 검사 → 원장 행 저장 → Storage 업로드 → 탈퇴 재확인 → { path, 미리보기 URL }.
     * 검사에 걸리면 ApiException: 탈퇴한 계정 401(UNAUTHENTICATED), 한도 초과 429(RATE_LIMITED), 파일 문제 400(VALIDATION, 화면에 보일 문구).
     * 묶음 없이 불려 첫 holdLive 의 공유 잠금은 그 호출이 끝나면 바로 풀린다 — 그래서 올린 뒤 원장·계정을 한 번 더 확인한다.
     */
    public RepairPhotoResponse upload(UUID customerId, String contentType, byte[] bytes) {
        profiles.holdLive(customerId); // 탈퇴한 계정의 남은 토큰으로는 올리지 못한다 (재감사 M-04)
        rateLimiter.check("repair-photo", customerId.toString(), UPLOADS_PER_USER_PER_HOUR);
        if (bytes == null || bytes.length == 0) throw ApiException.validation("사진 파일이 비어 있어요");
        if (bytes.length > MAX_BYTES) throw ApiException.validation("사진은 5MB 이하로 올려주세요");
        // "image/jpeg; charset=…" 처럼 뒤에 붙은 값을 떼고 소문자로 맞춘다
        String type = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase();
        if (!TYPES.contains(type)) throw ApiException.validation("이미지 파일(JPG·PNG·WEBP·HEIC)만 올릴 수 있어요");
        // 선언한 형식이 아니라 파일 내용으로 판정한다 — image/jpeg 라고 보낸 HTML·임의 바이트를 Storage 에 두지 않게 (감사 M-02)
        // 선언과 내용이 서로 다른 이미지 형식이면(확장자만 .jpg 인 HEIC 등) 내용을 따른다 — 둘 다 이미지라 위험하지 않고, 폰이 붙인 확장자가 틀리는 일이 있다
        String actual = ImageBytes.detect(bytes).orElseThrow(() -> ApiException.validation(UNREADABLE));
        // HEIC 는 헤더에서 가로·세로를 읽지 않으므로(ImageBytes 설명) 해상도 검사를 건너뛴다
        if (!actual.equals("image/heic")) {
            long[] size = ImageBytes.dimensions(bytes, actual).orElseThrow(() -> ApiException.validation(UNREADABLE));
            if (size[0] <= 0 || size[1] <= 0) throw ApiException.validation(UNREADABLE);
            if (size[0] > ImageBytes.MAX_PIXELS / size[1]) throw ApiException.validation("사진 해상도가 너무 커요. 다른 사진을 골라주세요"); // unsigned PNG 치수의 곱이 long을 넘지 않게
        }
        // 저장 경로 = 고객 id 폴더 / 무작위 uuid . 확장자. 첫 폴더가 고객 id 라 BookingService.ownPhotos 가 남의 사진을 가려내고,
        // 탈퇴 때 deleteAllOf 가 폴더째 목록을 뽑을 수 있다. 확장자는 선언이 아니라 실제 내용(actual)에서 정한다
        String path = customerId + "/" + UUID.randomUUID() + "." + EXT.get(actual);
        uploads.save(RepairPhotoUpload.of(path, customerId, clock.now())); // 원장을 먼저 — Storage 에 올라간 파일은 항상 원장에 있다 (감사 M-04)
        storage.upload(BUCKET, path, bytes, actual);
        // 올리는 사이 탈퇴했으면 늦게 도착한 파일을 남기지 않는다 (재감사 M-04·2차 #4). 탈퇴 커밋 뒤에 도는 정리(아웃박스)가 이 파일을 목록에서 놓쳤을 수 있다 —
        // 탈퇴가 여기보다 먼저 커밋됐으면 아래 확인이 잡고, 나중이면 파일이 이미 올라가 있어 정리의 폴더 목록에 잡힌다
        // 원장 행이 없어졌으면 그 사이 탈퇴 정리(deleteAllOf)가 이 고객 행을 지운 것이다. 둘 중 하나라도 걸리면 방금 올린 파일을 지우고 401
        if (!uploads.existsById(path) || !stillLive(customerId)) {
            storage.delete(BUCKET, path);
            uploads.deleteById(path);
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        // 미리보기용 서명 URL(24시간). 버킷이 비공개라 이 URL 없이는 앱이 사진을 볼 수 없다.
        // 만들지 못하면(Storage 의 중립 NOT_FOUND·UPSTREAM) 업로드 실패 문구로 바꿔 던진다 — 앱은 한 장이면 이 문구를 그대로 보인다 (문제 목록 21번).
        // 올라간 파일과 원장 행은 그대로 둔다: 앱이 경로를 받지 못해 예약에 붙지 않으므로 24시간 뒤 정리(deleteUnattached)가 지운다
        try {
            return new RepairPhotoResponse(path, storage.signedUrl(BUCKET, path, PREVIEW_SECONDS));
        } catch (ApiException e) {
            throw new ApiException(ErrorCode.UPSTREAM, UPLOAD_FAILED);
        }
    }

    /** 아직 탈퇴하지 않은 계정인가 — holdLive 가 401 을 던지면 false 로 바꿔 준다 */
    private boolean stillLive(UUID customerId) {
        try {
            profiles.holdLive(customerId);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    /** 예약 생성 트랜잭션 안에서: 이 사진들이 예약에 붙었다 (24시간 정리 대상에서 빠진다) */
    public void attach(UUID customerId, List<String> paths) {
        if (paths.isEmpty()) return;
        // LinkedHashSet = 중복 없는 집합(넣은 순서 유지). 같은 경로가 두 번 와도 한 번으로 세야 아래 개수 비교가 맞는다
        Set<String> distinct = new java.util.LinkedHashSet<>(paths);
        // 정리가 먼저 지운 사진이면 붙은 수가 모자란다 — 없는 파일을 가리키는 예약을 만들지 않는다 (재감사 M-04)
        if (uploads.attach(customerId, distinct, clock.now()) != distinct.size()) throw ApiException.validation(PHOTO_EXPIRED);
    }

    /**
     * RepairPhotoCleaner(매일): 24시간 동안 예약에 붙지 않은 사진을 Storage 와 원장에서 지운다. @return 이번에 지운 장수.
     * 한 장의 삭제가 실패하면 그 장만 경고 로그를 남기고 건너뛴다(녹화본 정리 live/RecordingRetention.purge 와 같은 방식, 문제 목록 4번) —
     * 예전에는 첫 실패에서 멈춰, 매일 실패하는 한 장 뒤의 모든 고객 사진이 약속한 24시간이 지나도 남았다. 건너뛴 장은 '삭제 중' 으로 남아 다음 정리가 다시 한다
     */
    public int deleteUnattached() {
        int n = 0;
        // 후보(24시간 넘게 안 붙은 것, 최대 CLEANUP_BATCH 장, 순서는 findCleanupCandidates 설명)는 잠금 없이 읽는다 — 그래서 한 장씩 claimForDeletion 으로 다시 확인한다
        for (RepairPhotoUpload u : uploads.findCleanupCandidates(clock.now().minus(UNATTACHED_KEEP), PageRequest.ofSize(CLEANUP_BATCH))) {
            try {
                // '삭제 중' 으로 먼저 잡는다(아직 안 붙었을 때만) → Storage → 행. 어디서 멈추든 행이 남아 다음 정리가 다시 한다 (재감사 M-04·2차 #4)
                if (uploads.claimForDeletion(u.getPath(), clock.now()) == 0) continue; // 그 사이 예약에 붙었다
                storage.delete(BUCKET, u.getPath()); // 없으면(업로드 실패) 그대로. 실패하면 던진다 — 행은 '삭제 중' 으로 남는다
                uploads.deleteById(u.getPath());
                n++;
            } catch (RuntimeException e) {
                // Storage 오류(ApiException)만이 아니라 원장 쓰기 실패도 이 장에서 끝낸다 — 레포지토리 호출마다 따로 커밋되므로 다음 장에 번지지 않는다
                log.warn("수리 사진 삭제 실패 {} — 다음 정리 때 다시: {}", u.getPath(), e.getMessage());
            }
        }
        return n;
    }

    /**
     * 탈퇴: 이 고객이 올린 사진 전부 — 예약에 붙은 것·안 붙은 것·원장 이전에 올린 것(Storage 폴더 목록)까지 (감사 M-04).
     * 외부 삭제가 실패하면 던진다(아웃박스가 다시 시도). 지운 개수
     */
    public int deleteAllOf(UUID customerId) {
        // 원장 경로 + Storage 의 고객 폴더 목록을 합친다. 집합이라 양쪽에 다 있는 경로는 한 번만 지운다
        java.util.Set<String> paths = new java.util.LinkedHashSet<>();
        uploads.findByCustomerId(customerId).forEach(u -> paths.add(u.getPath()));
        paths.addAll(storage.list(BUCKET, customerId.toString()));
        for (String path : paths) storage.delete(BUCKET, path);
        // 원장 행은 Storage 를 다 지운 뒤에 지운다 — 중간에 실패하면 행이 남아 다시 시도할 때 목록에 또 잡힌다
        uploads.deleteAll(uploads.findByCustomerId(customerId));
        return paths.size();
    }
}
