package kr.ansimclean.api.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** cleaning_photos 읽기·쓰기. booking 패키지 안(CleaningPhotoService)에서만 쓴다 (ArchitectureTest) */
public interface CleaningPhotoRepository extends JpaRepository<CleaningPhoto, UUID> {
    /** 예약 한 건의 사진, 찍은 순서대로 */
    List<CleaningPhoto> findByBookingIdOrderBySortAsc(UUID bookingId);

    /** 예약 한 건의 사진 수 — 10곳 상한 검사 */
    long countByBookingId(UUID bookingId);

    /** 이 예약의 사진 하나 (다른 예약의 사진 id 면 빈 값 → NOT_FOUND) */
    Optional<CleaningPhoto> findByIdAndBookingId(UUID id, UUID bookingId);

    /** 지금까지 쓴 가장 큰 순서 번호 (없으면 0) — 새 사진은 그다음 번호 */
    @Query("select coalesce(max(p.sort), 0) from CleaningPhoto p where p.bookingId = :bookingId")
    int maxSort(@Param("bookingId") UUID bookingId);

    /** 여러 예약의 사진 전부 — 고객 탈퇴 정리용 */
    List<CleaningPhoto> findByBookingIdIn(Collection<UUID> bookingIds);

    /**
     * 보존 기간 정리 후보: 완료(done)된 지 cutoff 보다 오래된 예약의 사진, 최대 limit 장.
     * bookings.status 는 DB 문자열('done')로 비교한다 — 두 표를 잇는 쿼리라 SQL 로 직접 쓴다
     */
    @Query(value = "select p.* from cleaning_photos p join bookings b on b.id = p.booking_id " +
            "where b.status = 'done' and b.ended_at < :cutoff order by b.ended_at, p.id limit :limit", nativeQuery = true)
    List<CleaningPhoto> findExpired(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
