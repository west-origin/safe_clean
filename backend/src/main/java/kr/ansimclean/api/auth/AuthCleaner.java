package kr.ansimclean.api.auth;

import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.common.web.RateLimiter;
import kr.ansimclean.api.profile.SessionRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/**
 * 매일 04:10 (Asia/Seoul, BACKEND_PLAN.md §2.5 IdempotencyCleaner·OtpCleaner): 24시간 지난 멱등 키, 1일 지난 인증 레코드 삭제.
 * 메모리 속도 제한 키, 기간이 지난 끊은 세션(revoked_sessions)도 같이 비운다. 스케줄러는 기능 패키지 안에 둔다(common 은 기능을 볼 수 없다).
 * 다른 코드가 부르는 곳은 없다 (스케줄러 전용).
 */
@Component
public class AuthCleaner {
    static final Duration IDEMPOTENCY_KEEP = Duration.ofHours(24); // 멱등 키(Idempotency-Key) 처리 기록을 남겨 두는 기간
    static final Duration VERIFICATION_KEEP = Duration.ofDays(1); // 인증번호(5분)·확인 토큰(15분)·시간당 발송 한도(1시간)가 보는 기간보다 넉넉하다
    private static final Logger log = LoggerFactory.getLogger(AuthCleaner.class);

    private final Idempotent idempotent;
    private final PhoneVerificationRepository verifications;
    private final RateLimiter rateLimiter;
    private final AppClock clock;
    private final TransactionTemplate tx;

    private final SessionRevocationService revocations;

    public AuthCleaner(Idempotent idempotent, PhoneVerificationRepository verifications, RateLimiter rateLimiter, AppClock clock, TransactionTemplate tx,
                       SessionRevocationService revocations) {
        this.revocations = revocations;
        this.idempotent = idempotent;
        this.verifications = verifications;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.tx = tx;
    }

    /**
     * 차례로 정리하고 건수를 로그로 남긴다: 멱등 키(DB) → 인증번호 기록(DB) → 속도 제한 키(메모리) → 끊은 세션(DB·메모리).
     * 실패해도 예외를 밖으로 던지지 않고 로그만 남긴다 — 다음 날 다시 돈다. 앞 단계가 실패하면 뒤 단계도 그날은 건너뛴다.
     */
    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "AuthCleaner", lockAtLeastFor = "PT1M")
    public void run() {
        try {
            long keys = idempotent.purgeOlderThan(clock.now().minus(IDEMPOTENCY_KEEP));
            // 멱등 키·끊은 세션 정리 메서드(Idempotent·SessionRevocationService)는 스스로 @Transactional 이지만,
            // 이것은 Repository 를 바로 부르므로 tx 로 감싼다 (이름으로 만든 deleteBy… 는 트랜잭션이 없으면 실패한다)
            long codes = tx.execute(status -> verifications.deleteByCreatedAtBefore(clock.now().minus(VERIFICATION_KEEP)));
            rateLimiter.cleanup();
            int sessions = revocations.purgeExpired(); // 90일 지난 끊은 세션 (감사 M-03)
            log.info("정리: 멱등 키 {}건, 인증 레코드 {}건, 끊은 세션 {}건", keys, codes, sessions);
        } catch (Exception e) {
            log.error("멱등 키·인증 레코드 정리 실패", e);
        }
    }
}
