package kr.ansimclean.api.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// 한 행 = 청소 위치 하나(예: '거실 소파 앞')의 전·후 사진 한 쌍.
// 흐름: 매니저가 청소 중에 '전' 사진을 올리면 CleaningPhotoService.addBefore 가 of(...) 로 만든다(after 비어 있음)
// → 완료 직전에 같은 위치의 '후' 사진을 올리면 replaceAfter 로 after 경로가 채워진다(다시 찍으면 새 경로로 바뀐다)
// → 고객은 청소가 끝난(done) 뒤 전·후가 다 있는 행만 본다 → 보존 기간이 지나거나 고객이 탈퇴하면 파일과 행을 지운다.
/** cleaning_photos — 청소 전·후 사진 원장 (V055). 파일은 Storage 비공개 버킷 job-photos, 여기는 경로만 */
@Entity
@Table(name = "cleaning_photos")
public class CleaningPhoto {
    // 기본 키. 코드가 만든 uuid 라 DB 가 만들어 주지 않는다 — Storage 파일 이름에도 이 id 가 들어간다
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId; // 어느 예약(= 매니저 작업)의 사진인가

    @Column(columnDefinition = "text", nullable = false)
    private String location; // 매니저가 적은 위치 이름 (앞뒤 공백을 뺀 1~20자)

    @Column(nullable = false)
    private int sort; // 찍은 순서 — 목록은 이 순서로 보여 준다

    @Column(name = "before_path", columnDefinition = "text", nullable = false)
    private String beforePath; // '전' 사진의 Storage 경로

    @Column(name = "before_at", nullable = false)
    private Instant beforeAt;

    @Column(name = "after_path", columnDefinition = "text")
    private String afterPath; // '후' 사진의 Storage 경로. null = 아직 안 찍음

    @Column(name = "after_at")
    private Instant afterAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 아래 of(...) 로 만든다
    protected CleaningPhoto() {}

    /** 방금 올린 '전' 사진의 새 행. id 는 Storage 경로를 만들 때 이미 정했으므로 받아서 쓴다. 저장은 부르는 쪽이 */
    static CleaningPhoto of(UUID id, UUID bookingId, String location, int sort, String beforePath, Instant now) {
        CleaningPhoto p = new CleaningPhoto();
        p.id = id;
        p.bookingId = bookingId;
        p.location = location;
        p.sort = sort;
        p.beforePath = beforePath;
        p.beforeAt = now;
        p.createdAt = now;
        return p;
    }

    /** '후' 사진을 (다시) 붙인다. 돌려주는 값 = 바뀌기 전 경로(처음이면 null) — 부르는 쪽이 커밋 뒤 Storage 에서 지운다 */
    String replaceAfter(String path, Instant now) {
        String old = afterPath;
        afterPath = path;
        afterAt = now;
        return old;
    }

    /** 전·후 사진이 다 있는가 — 고객에게는 이런 행만 보인다 */
    public boolean isPaired() {
        return afterPath != null;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public String getLocation() { return location; }
    public int getSort() { return sort; }
    public String getBeforePath() { return beforePath; }
    public Instant getBeforeAt() { return beforeAt; }
    public String getAfterPath() { return afterPath; }
    public Instant getAfterAt() { return afterAt; }
    public Instant getCreatedAt() { return createdAt; }
}
