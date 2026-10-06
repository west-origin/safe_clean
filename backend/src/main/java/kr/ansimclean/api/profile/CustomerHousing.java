package kr.ansimclean.api.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// 고객 한 명에 한 행이고 기본 키가 곧 고객 id 다. 읽고 쓰는 곳은 ProfileService (예: 가입 저장 saveHousing,
// 가입 단계 판정 signupStep — 주소는 있고 이 행이 없으면 '주거 입력', 콜 카드·작업 상세의 한 줄 요약 housingSummary).
// 탈퇴하면 행을 지운다(ProfileService.anonymize).
/** customer_housing — Auth/ProfileHousing 입력 (거주형태·평수·방/화장실·반려동물) */
@Entity
@Table(name = "customer_housing")
public class CustomerHousing {
    @Id
    @Column(name = "customer_id", columnDefinition = "uuid")
    private UUID customerId;

    // 거주형태 키 — DB check 제약이 apartment·studio·house·other 만 허용한다 (표시 문구는 ProfileService.HOUSING_LABELS)
    @Column(name = "housing_type", columnDefinition = "text", nullable = false)
    private String housingType;

    // 평수 (문자열로 저장). '평수 모름' 이면 null
    @Column(columnDefinition = "text")
    private String size;

    @Column(name = "size_unknown", nullable = false)
    private boolean sizeUnknown;

    // 방·화장실 수 (문자열로 저장, 없을 수 있다)
    @Column(columnDefinition = "text")
    private String rooms;

    @Column(columnDefinition = "text")
    private String bathrooms;

    // 반려동물 — DB check 제약이 "yes"·"no" 만 허용한다
    @Column(columnDefinition = "text", nullable = false)
    private String pet;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자
    protected CustomerHousing() {}

    /** 키만 채운 새 객체. 필수 칸이 비어 있으니 저장 전에 update 로 값을 채워야 한다 (ProfileService.saveHousing 이 그렇게 한다) */
    public static CustomerHousing create(UUID customerId) {
        CustomerHousing h = new CustomerHousing();
        h.customerId = customerId;
        return h;
    }

    /** 가입 화면에서 받은 값으로 전부 덮어쓴다. '평수 모름' 이면 함께 온 평수는 버리고 null 로 둔다 */
    public void update(String housingType, String size, boolean sizeUnknown, String rooms, String bathrooms, String pet, Instant now) {
        this.housingType = housingType;
        this.size = sizeUnknown ? null : size;
        this.sizeUnknown = sizeUnknown;
        this.rooms = rooms;
        this.bathrooms = bathrooms;
        this.pet = pet;
        this.updatedAt = now;
    }

    public UUID getCustomerId() { return customerId; }
    public String getHousingType() { return housingType; }
    public String getSize() { return size; }
    public boolean isSizeUnknown() { return sizeUnknown; }
    public String getRooms() { return rooms; }
    public String getBathrooms() { return bathrooms; }
    public String getPet() { return pet; }
}
