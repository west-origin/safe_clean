package kr.ansimclean.api.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// 한 행 = 고객이 올린 수리 사진 한 장.
// 한 장의 흐름: 올릴 때 RepairPhotoService.upload 가 of(...) 로 만든다(attached_at 비어 있음)
// → 이 사진을 넣어 예약을 만들면 attached_at 이 채워진다 → 24시간 넘게 안 붙으면 정리가 deleting_at 을 채운 뒤 행을 지운다. 탈퇴하면 전부 지운다.
// attached_at·deleting_at 은 이 클래스의 메서드가 아니라 RepairPhotoUploadRepository 의 UPDATE 쿼리(attach·claimForDeletion)로만 바뀐다.
/** repair_photo_uploads — 수리 사진 업로드 원장 (감사 M-04, V024). 예약에 붙지 않은 사진을 찾아 지우려고 둔다 */
@Entity
@Table(name = "repair_photo_uploads")
public class RepairPhotoUpload {
    // 기본 키 = Storage 안의 파일 경로(고객 id 폴더/uuid.확장자). 코드가 정한 값이라 DB 가 만들어 주지 않는다
    @Id
    private String path;

    @Column(name = "customer_id", columnDefinition = "uuid", nullable = false)
    private UUID customerId; // 올린 고객

    @Column(name = "created_at", nullable = false)
    private Instant createdAt; // 올린 시각 — 정리는 이 시각에서 24시간이 지났는지 본다

    @Column(name = "attached_at")
    private Instant attachedAt; // 예약에 붙은 시각. null = 아직 안 붙음

    /** 정리가 지우기 시작한 시각 — 이 행은 더 이상 예약에 붙지 않는다 (V031, 재감사 2차 #4) */
    @Column(name = "deleting_at")
    private Instant deletingAt;

    // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자. 코드에서는 아래 of(...) 로 만든다
    protected RepairPhotoUpload() {}

    /** 방금 올린 사진의 새 원장 행 (아직 예약에 안 붙음). 저장은 부르는 쪽이 uploads.save 로 */
    public static RepairPhotoUpload of(String path, UUID customerId, Instant now) {
        RepairPhotoUpload u = new RepairPhotoUpload();
        u.path = path;
        u.customerId = customerId;
        u.createdAt = now;
        return u;
    }

    public String getPath() {
        return path;
    }

    public Instant getAttachedAt() {
        return attachedAt;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
