package kr.ansimclean.api.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// manager_unavailable 테이블(근무 불가) 창구. 다른 기능은 이것을 직접 쓰지 않고 AvailabilityService 를 부른다.
public interface ManagerUnavailableRepository extends JpaRepository<ManagerUnavailable, UUID> {
    /** 한 매니저의 근무 불가 전부, 날짜 순 (지난 것 포함 — 거르는 것은 부르는 쪽) */
    List<ManagerUnavailable> findByManagerIdOrderByDate(UUID managerId);

    /** 탈퇴·파기 때 그 매니저의 근무 불가를 모두 지운다. 지운 행 수를 돌려준다 */
    long deleteByManagerId(UUID managerId);
}
