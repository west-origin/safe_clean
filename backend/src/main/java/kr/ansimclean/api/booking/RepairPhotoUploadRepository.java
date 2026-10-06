package kr.ansimclean.api.booking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 수리 사진 업로드 원장(repair_photo_uploads)을 읽고 쓰는 곳 (예: RepairPhotoService). id = Storage 파일 경로 문자열.
// @Query 안의 글은 SQL 이 아니라 JPQL(자바 엔티티 이름·필드 이름으로 쓰는 쿼리)이다.
// UPDATE 쿼리가 돌려주는 int 는 바뀐 행 수 — 부르는 쪽이 이 수로 '붙었나·잡았나' 를 판단한다.
// flushAutomatically = 쿼리 전에 아직 DB 에 안 보낸 변경을 먼저 보낸다.
public interface RepairPhotoUploadRepository extends JpaRepository<RepairPhotoUpload, String> {
    /** 예약에 붙인다 (예약 생성 트랜잭션 안) */
    // 아래 claimForDeletion 과 달리 @Transactional 이 없다 — 늘 예약 생성 묶음 안에서 불려 거기에 합쳐진다(예약이 되돌려지면 '붙음' 도 되돌려진다).
    // 이 고객 것이고, 아직 안 붙었고, 정리가 지우려고 잡지 않은 행만 바뀐다. 바뀐 수가 모자라면 RepairPhotoService.attach 가 '만료' 로 막는다
    @Modifying(flushAutomatically = true)
    @Query("update RepairPhotoUpload u set u.attachedAt = :now where u.path in :paths and u.customerId = :customerId and u.attachedAt is null and u.deletingAt is null")
    int attach(@Param("customerId") UUID customerId, @Param("paths") Collection<String> paths, @Param("now") Instant now);

    /**
     * 정리가 지우기 시작한다: 아직 예약에 붙지 않았을 때만 deleting_at 을 적어 붙을 수 없게 잡는다 — 후보를 읽은 뒤 그 사진이 예약에 붙었으면 0 (재감사 M-04).
     * 예약 쪽 attach(UPDATE)와 같은 행을 두고 Postgres 가 줄을 세워, 먼저 커밋한 쪽만 이긴다. 행을 지우지 않으므로 Storage 삭제 전에 멈춰도 다음 정리가 다시 한다 (재감사 2차 #4)
     */
    // @Transactional: 이 UPDATE 를 한 묶음으로 바로 커밋한다. 부르는 deleteUnattached 에는 트랜잭션이 없어서 여기서 연다
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update RepairPhotoUpload u set u.deletingAt = :now where u.path = :path and u.attachedAt is null")
    int claimForDeletion(@Param("path") String path, @Param("now") Instant now);

    /**
     * 정리 후보: 붙지 않은 채 before 보다 먼저 올린 업로드 (RepairPhotoCleaner). 지우다 멈춘('삭제 중') 행도 후보라 다음 정리가 마저 지운다.
     * 순서: 아직 한 번도 잡지 않은 행(deleting_at 없음)을 오래된 순으로 먼저, 그 뒤에 '삭제 중' 행을 잡은 지 오래된 순으로 (문제 목록 4번).
     * 삭제가 계속 실패하는 행이 매일 한 번에 읽는 개수(pageable, CLEANUP_BATCH)를 차지해 새로 기한이 지난 사진을 밀어내지 않게 한다 —
     * 다시 잡을 때마다 claimForDeletion 이 deleting_at 을 새로 적으므로 계속 실패하는 행은 점점 뒤로 간다
     */
    // nulls first = deleting_at 이 비어 있는 행을 맨 앞에 (Postgres 오름차순의 기본은 비어 있는 값이 맨 뒤다)
    @Query("select u from RepairPhotoUpload u where u.attachedAt is null and u.createdAt < :before order by u.deletingAt asc nulls first, u.createdAt asc")
    List<RepairPhotoUpload> findCleanupCandidates(@Param("before") Instant before, Pageable pageable);

    /** 이 고객이 올린 모든 원장 행 — 탈퇴 정리(RepairPhotoService.deleteAllOf) */
    List<RepairPhotoUpload> findByCustomerId(UUID customerId);
}
