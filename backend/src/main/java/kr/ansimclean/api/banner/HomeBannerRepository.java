package kr.ansimclean.api.banner;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

// home_banners 저장소. banner 패키지 안에서만 쓴다
public interface HomeBannerRepository extends JpaRepository<HomeBanner, UUID> {
    /** 순서대로 (같으면 만든 순서) */
    List<HomeBanner> findAllByOrderBySortOrderAscCreatedAtAsc();

    /** 순서를 바꾸거나 새 배너 자리를 정할 때 전체를 잠근다 — 두 어드민이 동시에 바꿔 순서가 꼬이지 않게 (배너는 몇 장뿐) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from HomeBanner b order by b.sortOrder, b.createdAt")
    List<HomeBanner> lockAllInOrder();
}
