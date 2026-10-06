package kr.ansimclean.api.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * customer_housing 테이블을 읽고 쓰는 곳. ProfileService 만 쓴다.
 * 기본 키가 고객 id 라 물려받은 기본 메서드(예: findById·deleteById)만으로 충분해 따로 선언한 메서드가 없다.
 */
public interface CustomerHousingRepository extends JpaRepository<CustomerHousing, UUID> {}
