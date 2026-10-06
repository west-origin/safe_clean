package kr.ansimclean.api.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// manager_hours 테이블(요일별 기본 근무시간) 창구. 다른 기능은 이것을 직접 쓰지 않고 AvailabilityService 를 부른다.
// 기본 키가 (매니저 id, 요일) 두 칸이라 findById 에는 ManagerHours.Key 를 넘긴다.
public interface ManagerHoursRepository extends JpaRepository<ManagerHours, ManagerHours.Key> {
    /** 한 매니저의 요일 행들, 월 → 일 순 */
    List<ManagerHours> findByManagerIdOrderByWeekday(UUID managerId);

    /** 탈퇴·파기 때 그 매니저의 행을 모두 지운다. 지운 행 수를 돌려준다 */
    long deleteByManagerId(UUID managerId);

    /** 기본 근무시간 행이 이미 있는가 — createDefaultHours 가 두 번 만들지 않게 */
    boolean existsByManagerId(UUID managerId);
}
