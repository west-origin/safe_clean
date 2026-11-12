package kr.ansimclean.api.banner;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.image.ImageBytes;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 배너 이미지 파일 (비공개 버킷 banners). 검사·올리기·서명 URL·조용한 삭제를 한곳에 둔다.
 * 규칙(코드에서 정함, rbac.md §15-22): JPG·PNG·WebP, 5MB 이하 — HEIC 는 고객 앱(웹·안드로이드)이 그리지 못해 받지 않는다.
 * 권장 크기 1080×324(360:108)는 안내만 하고 막지 않는다 — 고객 앱이 비율에 맞춰 잘라 보여 준다(object-fit: cover).
 */
@Component
public class BannerImages {
    public static final String BUCKET = "banners"; // runbook.md 의 SQL 로 만든다
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXT = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
    private static final Logger log = LoggerFactory.getLogger(BannerImages.class);

    private final SupabaseStorageClient storage;

    public BannerImages(SupabaseStorageClient storage) {
        this.storage = storage;
    }

    /** 검사를 통과하면 파일 내용으로 판정한 형식(image/…)을 돌려준다. 어기면 VALIDATION(화면에 그대로 보이는 문구) */
    static String check(String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw ApiException.validation("배너 이미지를 골라주세요");
        if (bytes.length > MAX_BYTES) throw ApiException.validation("이미지는 5MB 이하로 올려주세요");
        String declared = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase();
        if (!EXT.containsKey(declared)) throw ApiException.validation("JPG·PNG·WebP 이미지만 올릴 수 있어요");
        String actual = ImageBytes.detect(bytes).filter(EXT::containsKey)
                .orElseThrow(() -> ApiException.validation("이미지 파일을 읽을 수 없어요. 다른 이미지를 골라주세요"));
        long[] size = ImageBytes.dimensions(bytes, actual).orElseThrow(() -> ApiException.validation("이미지 파일을 읽을 수 없어요. 다른 이미지를 골라주세요"));
        if (size[0] <= 0 || size[1] <= 0) throw ApiException.validation("이미지 파일을 읽을 수 없어요. 다른 이미지를 골라주세요");
        if (size[0] > ImageBytes.MAX_PIXELS / size[1]) throw ApiException.validation("이미지 해상도가 너무 커요. 다른 이미지를 골라주세요");
        return actual;
    }

    /** 새 경로로 올린다(덮어쓰지 않는다 — 바꿀 때마다 새 파일) → 경로. 실패하면 UPSTREAM. 트랜잭션 밖에서 부른다 */
    String upload(UUID bannerId, String type, byte[] bytes) {
        String path = bannerId + "/" + UUID.randomUUID() + "." + EXT.get(type);
        storage.upload(BUCKET, path, bytes, type);
        return path;
    }

    /** 서명 URL. 실패하면 null (목록 전체를 실패시키지 않는다) */
    String url(String path, int seconds) {
        try {
            return storage.signedUrl(BUCKET, path, seconds);
        } catch (RuntimeException e) {
            log.warn("배너 서명 URL 실패 {}: {}", path, e.getMessage());
            return null;
        }
    }

    /** 지운다. 이미 없으면 그대로(404). 실패하면 던진다 — 아웃박스 핸들러가 다시 시도한다 */
    void delete(String path) {
        storage.delete(BUCKET, path);
    }

    /** 올린 뒤 DB 저장이 실패했을 때 고아 파일을 치운다 (실패해도 던지지 않는다) */
    void deleteQuietly(String path) {
        try {
            storage.delete(BUCKET, path);
        } catch (RuntimeException e) {
            log.warn("배너 이미지 정리 실패 {}: {}", path, e.getMessage());
        }
    }
}
