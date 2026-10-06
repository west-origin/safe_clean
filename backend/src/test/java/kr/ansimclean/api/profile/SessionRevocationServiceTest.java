package kr.ansimclean.api.profile;

import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 끊은 세션 목록 (감사 M-03): 메모리 조회 + 1분마다 DB 재적재, 90일 뒤 만료 */
// SessionRevocationService(로그아웃·비밀번호 재설정으로 끊은 세션 목록) 단위 테스트. 스프링·DB 없이 가짜 저장소로 돌린다.
// 가짜 저장소의 '테이블' 은 rows 목록이고, MutableClock(옮길 수 있는 테스트 시계)으로 90일·1분이 지난 상황을 만든다
class SessionRevocationServiceTest {
    static final UUID USER = UUID.randomUUID();
    RevokedSessionRepository repo = mock(RevokedSessionRepository.class);
    List<RevokedSession> rows = new ArrayList<>(); // 가짜 DB 테이블 (JUnit 은 테스트마다 이 클래스를 새로 만들어 빈 목록으로 시작한다)

    // 준비: save 는 rows 에 쌓고, 만료 전 목록 조회는 rows 에서 만료 시각이 기준 뒤인 행만 돌려준다.
    // 확인: 끊은 세션 id 만 true, 다른 id·null 은 false. 보관 기간(KEEP, 90일)+2분 뒤에는 같은 id 도 false
    @Test
    void 끊은_세션만_거부하고_90일이_지나면_풀린다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-24T05:00:00Z"), AppClock.ZONE);
        when(repo.save(any())).thenAnswer(inv -> { rows.add(inv.getArgument(0)); return inv.getArgument(0); });
        when(repo.findByExpiresAtAfter(any())).thenAnswer(inv -> rows.stream().filter(r -> r.getExpiresAt().isAfter(inv.getArgument(0))).toList());
        SessionRevocationService service = new SessionRevocationService(repo, new AppClock(clock));

        service.revoke(USER, List.of("sid-1"));
        assertThat(service.isRevoked("sid-1")).isTrue();
        assertThat(service.isRevoked("sid-2")).isFalse();
        assertThat(service.isRevoked(null)).isFalse();

        clock.set(clock.instant().plus(SessionRevocationService.KEEP).plus(Duration.ofMinutes(2)));
        assertThat(service.isRevoked("sid-1")).isFalse();
    }

    // 서버가 여러 대일 때: 다른 서버가 DB 에 적은 행은 이 서버 메모리에 없다. rows 에 직접 넣어 그 상황을 만들고,
    // 61초 뒤(다시 읽는 간격 1분이 지난 뒤) 조회하면 DB 를 다시 읽어 true 가 되는지 본다
    @Test
    void 다른_인스턴스가_끊은_세션도_1분_안에_DB_에서_읽어_거부한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-24T05:00:00Z"), AppClock.ZONE);
        when(repo.findByExpiresAtAfter(any())).thenAnswer(inv -> rows.stream().toList());
        SessionRevocationService service = new SessionRevocationService(repo, new AppClock(clock));
        assertThat(service.isRevoked("sid-x")).isFalse(); // 처음 적재 — 아직 없다

        rows.add(RevokedSession.of("sid-x", USER, clock.instant(), clock.instant().plus(Duration.ofDays(90))));
        clock.set(clock.instant().plus(Duration.ofSeconds(61)));
        assertThat(service.isRevoked("sid-x")).isTrue();
    }
}
