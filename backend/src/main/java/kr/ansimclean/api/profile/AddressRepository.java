package kr.ansimclean.api.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * addresses 테이블을 읽고 쓰는 곳. ProfileService 만 쓴다.
 */
public interface AddressRepository extends JpaRepository<Address, UUID> {
    /** 한 고객의 주소 전부, 오래된 순 — 쓰임 예: 주소 관리 목록, 기본 주소를 지운 뒤 다음 기본 고르기 */
    List<Address> findByCustomerIdOrderByCreatedAt(UUID customerId);

    /** 기본 주소 (부분 유니크 인덱스라 많아야 하나). 없을 때는 부르는 쪽이 정한다 (예: defaultAddress 는 NOT_FOUND, saveDefaultAddress 는 새로 만든다) */
    Optional<Address> findByCustomerIdAndIsDefaultTrue(UUID customerId);

    /** 주소가 하나라도 있는가 — 가입 단계 판정(없으면 주소 입력 단계), 첫 주소면 기본으로 만들기 */
    boolean existsByCustomerId(UUID customerId);

    /** 저장한 주소 수 — 고객당 최대 개수(ProfileService.MAX_ADDRESSES) 검사 */
    long countByCustomerId(UUID customerId);

    /** 탈퇴 때 이 고객의 주소를 모두 지운다 (ProfileService.anonymize). 지운 행 수를 돌려준다 */
    long deleteByCustomerId(UUID customerId);
}
