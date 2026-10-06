package kr.ansimclean.api.profile;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// manager_profiles 테이블을 읽고 쓰는 저장소. profile 패키지 안(ProfileService·ManagerPenaltyService)에서만 쓴다 —
// 다른 기능(예: OfferService)은 ProfileService 를 거친다.
public interface ManagerProfileRepository extends JpaRepository<ManagerProfile, UUID> {
    /** 승인 ∧ 탈퇴하지 않은 매니저 — 탈퇴 뒤 운영자가 승인해도 콜이 유령 계정으로 가지 않게 (자체 점검 #55) */
    // @Query 의 글은 SQL 이 아니라 JPQL(엔티티 이름·필드 이름으로 쓰는 쿼리)이다. profiles 와 id 로 맞춰 탈퇴 여부를 본다.
    // 정지 중인 매니저는 여기서 거르지 않는다 — ProfileService.approvedManagers 가 isSuspended 로 한 번 더 거른다
    @org.springframework.data.jpa.repository.Query("select m from ManagerProfile m, Profile p where p.id = m.managerId and m.approved = true and p.deletedAt is null")
    List<ManagerProfile> findApprovedLive();

    /**
     * 이 매니저가 지금 매칭 후보인가를 DB 의 지금 값으로 센다(1 이면 후보, 0 이면 아님): 승인 ∧ 탈퇴 아님 ∧ 오늘 정지 아님(정지 종료일이 없거나 오늘보다 앞).
     * 엔티티가 아니라 숫자를 돌려주는 조회라, 같은 트랜잭션에서 findApprovedLive 등으로 먼저 올려 둔 ManagerProfile 객체(1차 캐시 —
     * 같은 id 를 다시 찾으면 DB 대신 돌려주는 메모리 사본)의 옛 값을 보지 않는다 (합친 38건 검토 R3). ProfileService.isApprovedManagerNow 가 부른다
     */
    @Query("select count(m) from ManagerProfile m, Profile p where p.id = m.managerId and m.managerId = :managerId and m.approved = true"
            + " and p.deletedAt is null and (m.suspendedUntil is null or m.suspendedUntil < :today)")
    long countCallableNow(UUID managerId, java.time.LocalDate today);

    /** 콜 수락의 첫 잠금 — 같은 매니저의 동시 수락을 직렬화한다 (§6.2-3) */
    // PESSIMISTIC_WRITE = SELECT … FOR UPDATE (CODE_GUIDE.md 3-4). 콜 수락은 ProfileService.lockManager 로 이것부터 잡는다.
    // 탈퇴(AuthService.deleteAccount)도 ProfileService.lockManagerIfExists 로 같은 잠금을 잡아 콜 수락과 줄을 선다
    // 운영자 패널티 면제(ManagerPenaltyService.waive)도 패널티 잠금 뒤 원장 행보다 먼저 이 행을 잡는다 — 탈퇴와 같은 '매니저 행 → 원장 행' 순서 (합친 38건 검토 R5)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ManagerProfile m where m.managerId = :managerId")
    Optional<ManagerProfile> findForUpdate(UUID managerId);
}
