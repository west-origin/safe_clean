package kr.ansimclean.api.live;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.QueryHint;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * live_sessions 테이블(LiveSession)을 읽고 쓰는 곳 (CODE_GUIDE.md 3-1). 키는 예약 id(UUID). 쓰는 곳 예: LiveService, LiveReconciler.
 * pause·resume·stop·웹훅·재조정기는 lock… 메서드로 행을 잠그고 읽는다: 사용자 요청·웹훅은 앞 요청이 끝날 때까지 기다리고
 * (lockById·lockByRoomName), 재조정기는 남이 쥔 행을 기다리지 않고 건너뛴다(lockByIdIfFree).
 */
public interface LiveSessionRepository extends JpaRepository<LiveSession, UUID> {
    /** 방 이름으로 찾기(잠그지 않음). 지금은 부르는 곳이 없다 — 웹훅은 잠그고 읽는 lockByRoomName 을 쓴다 */
    Optional<LiveSession> findByRoomName(String roomName);

    /** 상태를 바꾸는 경로(pause/resume/stop·웹훅·재조정기)는 잠그고 읽는다 — 잠금 없이 읽고 쓰면 나중 커밋이 앞 커밋(mute 확인·카메라 이탈·시청자 수)을 통째로 덮는다 */
    // @Lock(PESSIMISTIC_WRITE) = SQL 의 FOR UPDATE 행 잠금 (CODE_GUIDE.md 3-4).
    // @Query 의 문장은 SQL 이 아니라 JPQL — 테이블·칸 대신 자바 클래스·필드 이름(LiveSession, bookingId)을 쓴다. :bookingId 에 인자가 들어간다.
    // 예: LiveService.pause·resume, LiveSessionService.mayBeStreaming(jobs/pause 가 카메라 재접속과 겹치지 않게)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LiveSession s where s.bookingId = :bookingId")
    Optional<LiveSession> lockById(UUID bookingId);

    /** 웹훅용: LiveKit 웹훅은 방 이름만 알려 주므로 방 이름으로 잠그고 읽는다 (LiveWebhookService) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LiveSession s where s.roomName = :roomName")
    Optional<LiveSession> lockByRoomName(String roomName);

    /** 재조정기용: 지금 다른 요청(pause·웹훅)이 쥔 행은 건너뛴다(SKIP LOCKED) — 다음 분에 본다. 배치가 남의 요청을 기다리게 하지 않는다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // 잠금 대기 시간 -2 는 Hibernate 에서 SKIP LOCKED 를 뜻한다: 남이 잠근 행은 기다리지 않고 없는 것처럼 건너뛴다(빈 Optional)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select s from LiveSession s where s.bookingId = :bookingId")
    Optional<LiveSession> lockByIdIfFree(UUID bookingId);

    /** 재조정기: 엔티티를 먼저 올리지 않고 id 만 (그 뒤 lockByIdIfFree 로 최신 상태를 잠근다) */
    @Query("select s.bookingId from LiveSession s where s.status in :statuses")
    List<UUID> findIdsByStatusIn(Collection<String> statuses);

    /** status 가 목록 안에 있는 세션 전체. 지금은 부르는 곳이 없다 — 재조정기는 id 만 읽는 findIdsByStatusIn 을 쓴다 */
    List<LiveSession> findByStatusIn(Collection<String> statuses);

    /** 예약 id 목록 중 녹화 상태가 이 값인 세션 — 예: LiveSessionService.readyBookingIds(내 예약 완료 탭의 '녹화 영상 보기') */
    List<LiveSession> findByBookingIdInAndRecordingStatus(Collection<UUID> bookingIds, String recordingStatus);

    /** 보존 기간이 지난 녹화본 (RecordingRetention) */
    List<LiveSession> findByRecordingStatusAndEndedAtBefore(String recordingStatus, Instant before);

    /** 끝난 세션인데 녹화가 아직 recording/uploading — egress_ended 웹훅이 유실됐을 때 재조정기가 마무리한다 */
    List<LiveSession> findByStatusAndRecordingStatusIn(String status, Collection<String> recordingStatuses);

    /** 방금 끝난 세션 — stop 때 방 삭제·Ingress 대기 방 복귀가 실패했으면 재조정기가 다시 한다(시청자에게 영상이 계속 가지 않게) */
    List<LiveSession> findByStatusAndEndedAtAfter(String status, Instant after);
}
