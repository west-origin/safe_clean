package kr.ansimclean.api.live;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static kr.ansimclean.api.live.LiveTestSupport.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 매일 04:00 녹화본 보존 기간(recording_retention_days) 경과분 삭제 (BACKEND_PLAN.md §2.5·§8.2) */
// RecordingRetention 단위 테스트: run() 이 트랜잭션을 열고 purge() 를 부르는지, 보존 일수가 지난 ready 녹화본을
// Storage 에서 지우고 세션 녹화 상태를 none 으로 바꾸는지, Storage 삭제가 실패하면 ready 로 남기는지 확인한다.
// DB·Storage·설정은 모두 Mockito 목이고 시계는 LiveTestSupport.NOW 에 멈춰 있다.
class RecordingRetentionTest {
    LiveSessionRepository sessions = mock(LiveSessionRepository.class);
    SupabaseStorageClient storage = mock(SupabaseStorageClient.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    AppClock clock = new AppClock(Clock.fixed(NOW, AppClock.ZONE));
    // 트랜잭션 관리자 목: 실제 DB 없이 TransactionTemplate 이 트랜잭션을 열고(getTransaction) 커밋(commit)했는지만 기록한다
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    RecordingRetention retention;
    LiveSession session;

    // 테스트마다 먼저: 보존 일수를 90일로 두고, '끝난 지 90일이 지난 ready 녹화' 조회가
    // ready 녹화 세션 한 건을 돌려주게 준비한다 (세션 객체 자체의 종료 시각은 채우지 않았다 — 조회 결과를 목이 정한다)
    @BeforeEach
    void setUp() {
        // livekit_events 저장소는 돌려줄 값을 정하지 않은 목이라 '7일 지난 행 삭제' 수는 0(목의 기본값)이다
        retention = new RecordingRetention(sessions, mock(LivekitEventRepository.class), storage, settings, LiveTestSupport.props(), clock, new TransactionTemplate(txManager));
        when(settings.integer(RecordingRetention.SETTING_RETENTION_DAYS)).thenReturn(90);
        session = LiveSession.create(UUID.randomUUID());
        session.recordingReady("bookings/x/recording.mp4", 120);
        // purge 가 '지금 - 90일' 을 기준으로 물을 때만 이 세션을 돌려준다 (eq = 인자가 정확히 이 값일 때). 기준이 틀리면 빈 목록이 온다
        when(sessions.findByRecordingStatusAndEndedAtBefore(eq(LiveSession.REC_READY), eq(NOW.minus(Duration.ofDays(90))))).thenReturn(List.of(session));
    }

    // 스케줄 입구 run() 을 부르면 트랜잭션 관리자에 '열기 → 커밋' 이 한 번씩 기록되고, 그 안에서 파일 삭제까지 이어졌는지 본다
    @Test
    void run_은_트랜잭션_안에서_purge_한다() {
        // 같은 클래스의 purge() 를 직접 부르면 @Transactional 이 붙지 않아 파일은 지워지는데 recordingDeleted() 가 저장되지 않는다
        retention.run();

        verify(txManager).getTransaction(any());
        verify(txManager).commit(any());
        verify(storage).delete(any(), eq("bookings/x/recording.mp4"));
    }

    // purge() 를 직접 부른다: 버킷 "recordings"(LiveTestSupport.props 의 값)의 그 파일을 지우고, 세션은 녹화 없음(none)이 된다
    @Test
    void 보존_기간이_지난_녹화본은_Storage_에서_지우고_none_으로_바꾼다() {
        int n = retention.purge();

        assertThat(n).isEqualTo(1);
        verify(storage).delete(eq("recordings"), eq("bookings/x/recording.mp4"));
        assertThat(session.getRecordingStatus()).isEqualTo(LiveSession.REC_NONE);
    }

    // Storage 삭제가 ApiException 을 던지게 한다 → 지운 수는 0, 세션은 ready 그대로라 다음 날 04:00 에 다시 대상이 된다
    @Test
    void Storage_삭제가_실패하면_ready_로_남겨_내일_다시_시도한다() {
        // doThrow(...).when(목).메서드(...) = 돌려주는 값이 없는(void) 메서드가 불리면 예외를 던지게 한다
        Mockito.doThrow(new ApiException(ErrorCode.INTERNAL)).when(storage).delete(any(), any());

        int n = retention.purge();

        assertThat(n).isZero();
        assertThat(session.getRecordingStatus()).isEqualTo(LiveSession.REC_READY);
    }
}
