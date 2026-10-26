package kr.ansimclean.api.auth;

import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.profile.ManagerPenaltyService;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.schedule.AvailabilityService;
import kr.ansimclean.api.support.SupportService;
import kr.ansimclean.api.support.TermsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * 매일 04:20 (Asia/Seoul): 탈퇴한 지 5년이 지난 계정에 남은 개인정보를 파기한다 (BACKEND_PLAN §13-22 결정 5년, 구현은 사용자 결정 2026-09-27).
 * 5년 = 전자상거래법의 계약·대금결제 기록 보존 기간. 탈퇴 때 이름·번호·주소록·주거·알림·푸시·녹화·사진은 이미 지웠고
 * (매니저는 근무시간·근무 불가·패널티 원장과 신원 확인·교육·보험 기록도 — AuthService.deleteAccount·ManagerProfile.withdraw),
 * 여기서는 그 뒤에 남은 것을 비운다:
 *  - 고객: 예약·정기청소의 주소(시도·시군구만 남김)·요청·주의사항·수리 상세·취소 사유 자유 입력, 결제 수단 (BookingAccountPurge·PaymentAccountPurge)
 *  - 매니저: 근무시간·근무 불가(사유 자유 입력)·패널티 원장, 신원 확인·교육·보험 기록 — 지금은 탈퇴 때 지우므로 보통은 비어 있고,
 *    그렇게 바뀌기 전에 탈퇴한 계정에 남은 것을 한 번 더 비운다
 *  - 공통: 약관 동의 기록, 남아 있을 수 있는 1:1 문의(보통은 3년에 지워졌다)
 * 예약·정산·결제 행 자체는 남긴다 — 상대방(매니저·고객)의 이력이 가리키고, 개인정보를 비운 뒤에는 누구인지 알 수 없다.
 * 계정마다 트랜잭션 하나(하나가 실패해도 다른 계정은 계속), 끝나면 profiles.purged_at 을 적어 다시 보지 않는다. 한 번에 최대 100건(남으면 다음 날)
 * 부르는 곳: 아래 run(스케줄러). purgeDue 는 테스트(예: DeletedAccountPurgerTest)도 직접 부른다.
 * 예약·결제 쪽 파기는 AccountPurge 구현체(스프링이 List 로 모아 넣어 준다)가, 나머지는 각 기능의 Service 가 맡는다.
 */
@Component
public class DeletedAccountPurger {
    static final int RETENTION_YEARS = 5; // 탈퇴 뒤 남은 개인정보를 보관하는 햇수
    private static final Logger log = LoggerFactory.getLogger(DeletedAccountPurger.class);

    private final ProfileService profiles;
    private final ManagerPenaltyService penalties;
    private final AvailabilityService availability;
    private final TermsService terms;
    private final SupportService support;
    private final List<AccountPurge> purges;
    private final AppClock clock;
    private final TransactionTemplate tx; // 계정마다 따로 열고 커밋한다 — 한 계정이 실패해도 앞 계정의 파기는 남는다

    public DeletedAccountPurger(ProfileService profiles, ManagerPenaltyService penalties, AvailabilityService availability, TermsService terms, SupportService support,
                                List<AccountPurge> purges, AppClock clock, TransactionTemplate tx) {
        this.profiles = profiles;
        this.penalties = penalties;
        this.availability = availability;
        this.terms = terms;
        this.support = support;
        this.purges = purges;
        this.clock = clock;
        this.tx = tx;
    }

    /** 스케줄러 입구. 실패해도 예외를 밖으로 던지지 않고 로그만 남긴다 — 다음 날 다시 돈다 */
    @Scheduled(cron = "0 20 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "DeletedAccountPurger", lockAtLeastFor = "PT1M")
    public void run() {
        try {
            int n = purgeDue();
            if (n > 0) log.info("탈퇴 5년 지난 계정 {}건 파기", n);
        } catch (Exception e) {
            log.error("탈퇴 계정 파기 실패", e); // 다음 날 다시
        }
    }

    /** 파기한 계정 수. 탈퇴 시각이 서울 달력으로 5년 전보다 이른 계정 */
    public int purgeDue() {
        // 지금을 서울 시각으로 바꿔 달력에서 5년을 뺀 시각 — 이보다 먼저 탈퇴한 계정이 대상
        Instant cutoff = clock.now().atZone(AppClock.ZONE).minusYears(RETENTION_YEARS).toInstant();
        int count = 0;
        // 대상은 아직 파기하지 않은 계정 중 오래된 순 최대 100건 (id·역할만)
        for (ProfileService.PurgeTarget t : profiles.dueForPurge(cutoff)) {
            try {
                tx.executeWithoutResult(status -> purgeOne(t));
                count++;
            } catch (RuntimeException e) {
                log.error("탈퇴 계정 파기 실패 user={} — 다음 날 다시", t.id(), e);
            }
        }
        return count;
    }

    /**
     * 한 계정의 남은 개인정보를 지운다 (purgeDue 가 연 트랜잭션 안). 순서: 예약·결제(AccountPurge) → 매니저 기록 → 약관 동의 → 문의 → 파기 표시.
     * 매니저의 근무시간·패널티는 지금은 탈퇴 때도 지운다(AuthService.deleteAccount) — 여기서는 남아 있는 것이 있으면 지운다.
     * 신원·교육·보험 기록도 마찬가지로 탈퇴 때 비우고(ManagerProfile.withdraw), markPurged 가 한 번 더 비운다(ManagerProfile.purge).
     */
    private void purgeOne(ProfileService.PurgeTarget t) {
        for (AccountPurge p : purges) p.purge(t.id(), t.role());
        if (Roles.MANAGER.equals(t.role())) {
            availability.forget(t.id());
            penalties.forget(t.id());
        }
        terms.forgetAgreements(t.id());
        support.forgetInquiries(t.id());
        profiles.markPurged(t.id()); // 마지막 — 중간에 실패하면 다음 날 처음부터 다시(모두 다시 해도 결과가 같다)
    }
}
