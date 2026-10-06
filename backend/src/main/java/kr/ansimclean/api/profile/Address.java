package kr.ansimclean.api.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// 만들고 바꾸는 곳은 ProfileService (예: 가입 주소 saveDefaultAddress — 별칭 '집', 설정 › 주소 추가 addAddress).
// 탈퇴하면 고객의 주소 행을 모두 지운다(ProfileService.anonymize).
// 부분 유니크 인덱스(addresses_one_default) = is_default 가 true 인 행은 고객마다 하나만 허용하는 DB 제약.
// 그래서 기본 주소를 옮기거나 지울 때 ProfileService 가 옛 기본을 먼저 내리거나 지우고 flush(바로 DB 에 반영)한 뒤 새 기본을 켠다.
// flush 없이 한꺼번에 반영하면 새 기본이 먼저 저장될 수 있어, 한순간 기본이 둘이 되어 DB 가 거절한다(요청 전체가 되돌려진다).
/** addresses — 고객 주소. is_default 는 고객당 하나(부분 유니크 인덱스) */
@Entity
@Table(name = "addresses")
public class Address {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "customer_id", columnDefinition = "uuid", nullable = false)
    private UUID customerId;

    // 별칭. 가입 주소는 '집', 비어 오면 '기타' (create)
    @Column(columnDefinition = "text", nullable = false)
    private String label;

    // 도로명주소 (주소 검색 API 전이라 고객이 직접 쓴 문자열)
    @Column(name = "road_address", columnDefinition = "text", nullable = false)
    private String roadAddress;

    // 동·호수 같은 상세 주소. 없으면 null
    @Column(name = "detail_address", columnDefinition = "text")
    private String detailAddress;

    // 도로명주소에서 뽑은 시·군·구와 시·도 (AddressRegion.parse). 못 읽으면 null.
    // 이 두 칸을 읽는 main 코드는 지금 없다 — 예약은 도로명주소에서 다시 뽑아 자기 행에 저장한다(BookingService)
    @Column(columnDefinition = "text")
    private String sigungu;

    @Column(columnDefinition = "text")
    private String sido;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자
    protected Address() {}

    /** 새 주소 객체 (저장은 부르는 쪽이 한다). 별칭이 비었으면 '기타', 시·도와 시·군·구는 도로명주소에서 뽑아 채운다 */
    public static Address create(UUID customerId, String label, String roadAddress, String detailAddress, boolean isDefault, Instant now) {
        Address a = new Address();
        a.id = UUID.randomUUID();
        a.customerId = customerId;
        a.label = label == null || label.isBlank() ? "기타" : label;
        a.roadAddress = roadAddress;
        a.detailAddress = detailAddress;
        a.isDefault = isDefault;
        a.createdAt = now;
        AddressRegion region = AddressRegion.parse(roadAddress);
        a.sido = region.sido();
        a.sigungu = region.sigungu();
        return a;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getLabel() { return label; }
    public String getRoadAddress() { return roadAddress; }
    public String getDetailAddress() { return detailAddress; }
    public String getSigungu() { return sigungu; }
    public String getSido() { return sido; }
    public boolean isDefault() { return isDefault; }

    /** 주소 내용을 바꾸고 시·도와 시·군·구도 다시 뽑는다. 부르는 곳: ProfileService.saveDefaultAddress (가입 주소 다시 저장) */
    public void update(String roadAddress, String detailAddress) {
        this.roadAddress = roadAddress;
        this.detailAddress = detailAddress;
        AddressRegion region = AddressRegion.parse(roadAddress);
        this.sido = region.sido();
        this.sigungu = region.sigungu();
    }

    /** 기본 주소 표시를 켜고 끈다. 고객당 하나만 켤 수 있어(위 설명) 켜기 전에 옛 기본을 먼저 꺼야 한다 */
    public void makeDefault(boolean value) {
        this.isDefault = value;
    }
}
