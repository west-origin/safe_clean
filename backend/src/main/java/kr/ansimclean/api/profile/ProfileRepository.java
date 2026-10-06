package kr.ansimclean.api.profile;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// profiles 테이블 저장소. 서버 코드에서 쓰는 곳: ProfileService (다른 기능은 ProfileService 를 거쳐 쓴다).
// 'Live' = 탈퇴하지 않은(deleted_at 이 비어 있는) 계정.
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    /** 살아 있는 계정 하나 (탈퇴했거나 없으면 비어 온다) */
    Optional<Profile> findByIdAndDeletedAtIsNull(UUID id);

    /** 탈퇴: 가드 검사~익명화 사이에 수락·예약이 끼어들지 못하게 잠근다 */
    // PESSIMISTIC_WRITE = FOR UPDATE, 아래 PESSIMISTIC_READ = FOR SHARE (CODE_GUIDE.md 3-4).
    // 둘은 부딪쳐, 늦게 온 쪽이 먼저 잡은 쪽의 트랜잭션이 끝날 때까지 기다린다.
    // 부르는 곳: ProfileService.lockLive ← AuthService.deleteAccount·completeSignup
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profile p where p.id = :id and p.deletedAt is null")
    Optional<Profile> lockLive(UUID id);

    /** 예약 생성·개인정보 쓰기: 탈퇴(lockLive)와만 부딪치는 공유 잠금(FOR SHARE). 탈퇴가 먼저 커밋했으면 행이 조건에서 빠져 비어 온다 (감사 M-06) */
    // 공유 잠금끼리는 서로 막지 않아 같은 사용자의 쓰기 여러 개가 함께 지나간다. 부르는 곳: ProfileService.holdLive·holdLiveIfPresent
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Profile p where p.id = :id and p.deletedAt is null")
    Optional<Profile> holdLive(UUID id);

    /** 이 번호의 살아 있는 계정 (가입 때 번호 중복 검사, 비밀번호 재설정). 살아 있는 계정끼리는 번호가 겹치지 않는다 */
    Optional<Profile> findByPhoneAndDeletedAtIsNull(String phone);

    /** 탈퇴 시각이 cutoff 이전이고 아직 파기하지 않은 계정 (DeletedAccountPurger) */
    // 이름 풀이: findTop100 = 최대 100개, …OrderByDeletedAt = 오래 전에 탈퇴한 것부터.
    // 하루 한 번 도는 파기 스케줄러가 한 번에 최대 100건만 처리하고, 남은 계정은 다음 날 이어서 한다
    List<Profile> findTop100ByDeletedAtBeforeAndPurgedAtIsNullOrderByDeletedAt(java.time.Instant cutoff);
}
