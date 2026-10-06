package kr.ansimclean.api.settlement;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// settlements 테이블 창구. settlement 패키지 안(SettlementService·탈퇴 가드)만 쓰고, 다른 기능은 SettlementService 를 부른다.
// 여기 @Query 는 nativeQuery 가 아닌 JPQL — 테이블·칸 대신 엔티티(Settlement)·필드 이름(createdAt)을 쓰고,
// :managerId 같은 자리에는 같은 이름의 메서드 인자가 들어간다.
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
    /** 예약 한 건의 정산 — createFor 가 이미 있는지 볼 때, 작업 완료 화면이 예상 정산액을 보일 때 */
    Optional<Settlement> findByBookingId(UUID bookingId);

    /** 내 정산 한 건 — 남의 정산이면 빈 값(부르는 쪽이 404) */
    Optional<Settlement> findByIdAndManagerId(UUID id, UUID managerId);

    /** 탈퇴 가드 — 미지급(pending) 정산이 남았는가 */
    boolean existsByManagerIdAndStatus(UUID managerId, String status);

    // 한 매니저의 정산 전부, 최신순. 지금 main 코드에서는 부르는 곳이 없다 (목록은 아래 pageBefore 를 쓴다)
    List<Settlement> findByManagerIdOrderByCreatedAtDesc(UUID managerId);

    /** 목록 한 페이지 — (생성 시각, id) 키셋 커서, 최신순 (검증 검토: 50건 고정이라 51번째부터 영영 볼 수 없었다. 검수 4차: 시각만으로는 같은 시각의 정산이 다음 페이지에서 빠졌다) */
    // 커서(before, beforeId)보다 '앞선' 행만: 시각이 더 이르거나, 같은 시각이면 id 가 더 작은 것. Limit = 가져올 최대 건수(SQL LIMIT)
    @Query("select s from Settlement s where s.managerId = :managerId and (s.createdAt < :before or (s.createdAt = :before and s.id < :beforeId)) order by s.createdAt desc, s.id desc")
    List<Settlement> pageBefore(UUID managerId, Instant before, UUID beforeId, Limit limit);

    /** 합계는 DB 에서 — 이력이 쌓여도 전부 읽지 않는다. [완료 합, 예정 합] */
    // 칸 두 개짜리 한 행이라 Object[] 로 받는다(SettlementService.row 가 풀어 쓴다). coalesce(…, 0) = 정산이 없으면 null 대신 0
    @Query("select coalesce(sum(case when s.status = 'complete' then s.finalAmount else 0 end), 0), coalesce(sum(case when s.status <> 'complete' then s.finalAmount else 0 end), 0) from Settlement s where s.managerId = :managerId")
    Object[] sumsByManager(UUID managerId);

    /** 이번 달(생성 = 완료 시각 기준) [합, 건수] */
    // [from, to) 는 부르는 쪽이 서울 시각 기준 이번 달 1일 0시 ~ 다음 달 1일 0시로 넘긴다. 합은 지급 여부와 무관하게 모두 더한다
    @Query("select coalesce(sum(s.finalAmount), 0), count(s) from Settlement s where s.managerId = :managerId and s.createdAt >= :from and s.createdAt < :to")
    Object[] monthlyByManager(UUID managerId, Instant from, Instant to);
}
