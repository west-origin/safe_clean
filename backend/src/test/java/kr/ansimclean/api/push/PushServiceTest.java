package kr.ansimclean.api.push;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 푸시 토큰 등록(기기 공유 시 앞 사람 토큰 정리)·발송(무효 토큰 삭제, 일시 실패는 개수로 돌려줌 → PushHandler 가 예외 → 아웃박스 재시도) */
// PushService 단위 테스트. 토큰 저장소·발송기(PushSender)·ProfileService 는 Mockito 가짜라 DB·Firebase 없이 돈다.
// 발송(send)은 직접 던지지 않고 일시 실패 기기 수만 돌려준다 — 예외는 이 수를 받은 PushHandler 가 던진다.
// PushService 는 @Transactional 이라 안에서 던지면 그 이벤트의 아웃박스 트랜잭션이 rollback-only 가 되기 때문이다 (PushService 머리 설명)
class PushServiceTest {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 서비스 시계를 이 시각에 고정한다 (토큰 갱신 시각 확인용)
    static final UUID USER = UUID.randomUUID();
    PushTokenRepository tokens = mock(PushTokenRepository.class);
    PushSender sender = mock(PushSender.class);
    kr.ansimclean.api.profile.ProfileService profiles = mock(kr.ansimclean.api.profile.ProfileService.class);
    PushService service;

    // 각 테스트 전에 서비스를 새로 만들고, 토큰 save 는 받은 객체를 그대로 돌려주게 한다
    @BeforeEach
    void setUp() {
        service = new PushService(tokens, sender, new AppClock(Clock.fixed(NOW, AppClock.ZONE)), profiles);
        when(tokens.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // 탈퇴가 먼저 끝나 holdLive(탈퇴와 줄을 서는 조회)가 UNAUTHENTICATED 를 던지면, 등록도 같은 예외로 멈추고
    // 토큰 저장·다른 사용자 토큰 정리를 하나도 하지 않는다
    @Test
    void 탈퇴가_먼저_커밋됐으면_등록은_401_이고_토큰을_남기지_않는다() { // 자체 점검 #53
        when(profiles.holdLive(USER)).thenThrow(new kr.ansimclean.api.common.error.ApiException(kr.ansimclean.api.common.error.ErrorCode.UNAUTHENTICATED));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.register(USER, "ios", "tok")).isInstanceOf(kr.ansimclean.api.common.error.ApiException.class);
        verify(tokens, org.mockito.Mockito.never()).save(any());
        verify(tokens, org.mockito.Mockito.never()).deleteByTokenAndUserIdNot(any(), any());
    }

    // ① 처음 등록: 다른 사용자에게 남은 같은 토큰을 지우고 새 행을 저장한다.
    // ② 이미 있는 행: 새로 저장하지 않고 platform 과 갱신 시각만 바꾼다 (진짜 PushToken 객체라 필드를 바로 본다).
    // ③ 허용 목록(PushService.PLATFORMS) 밖의 platform(예: windows), 빈 토큰은 검증 오류
    @Test
    void 등록은_다른_사용자에게_남은_같은_토큰을_지우고_있으면_갱신_없으면_저장() {
        when(tokens.findById(new PushToken.Key(USER, "tok"))).thenReturn(Optional.empty());
        service.register(USER, "ios", "tok");
        verify(tokens).deleteByTokenAndUserIdNot("tok", USER); // 공용 기기: 앞 사람의 푸시가 이 기기로 오지 않게
        verify(tokens).save(any());

        PushToken existing = PushToken.of(USER, "ios", "tok", NOW.minusSeconds(100));
        when(tokens.findById(new PushToken.Key(USER, "tok"))).thenReturn(Optional.of(existing));
        service.register(USER, "android", "tok");
        assertThat(existing.getPlatform()).isEqualTo("android");
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);

        assertThatThrownBy(() -> service.register(USER, "windows", "tok")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.register(USER, "ios", " ")).isInstanceOf(ApiException.class);
    }

    // 기기 두 대(a: 성공, b: 무효 토큰) → b 행만 지우고 실패 0.
    // a 를 일시 실패로 바꾸면 결과가 1 이고 예외는 없다. 등록된 기기가 없으면 아무것도 보내지 않고 0
    @Test
    void 발송은_기기마다_보내고_무효_토큰은_지우고_일시_실패_수를_돌려준다_던지지_않는다() {
        PushToken a = PushToken.of(USER, "ios", "a", NOW);
        PushToken b = PushToken.of(USER, "android", "b", NOW);
        when(tokens.findByUserId(USER)).thenReturn(List.of(a, b));
        when(sender.send(eq("a"), any(), any(), any(), any())).thenReturn(PushSender.Result.SENT);
        when(sender.send(eq("b"), any(), any(), any(), any())).thenReturn(PushSender.Result.INVALID_TOKEN);

        assertThat(service.send(USER, "제목", "본문", Map.of("type", "call"), "k")).isZero();
        verify(tokens).delete(b);
        verify(tokens, never()).delete(a);

        when(sender.send(eq("a"), any(), any(), any(), any())).thenReturn(PushSender.Result.FAILED);
        assertThat(service.send(USER, "제목", "본문", Map.of(), "k")).isEqualTo(1); // @Transactional 안에서 던지면 이 이벤트의 아웃박스 트랜잭션이 rollback-only (예전에는 배치 전체) — 개수만 돌려준다

        when(tokens.findByUserId(USER)).thenReturn(List.of()); // 기기 없음 — 정상
        assertThat(service.send(USER, "제목", "본문", Map.of(), "k")).isZero();
    }

    // unregister 는 (사용자, 토큰) 키 한 행만, forget 은 그 사용자의 토큰 전부를 지우는지 본다
    @Test
    void 로그아웃은_그_기기_토큰만_탈퇴는_전부_지운다() {
        service.unregister(USER, "tok");
        verify(tokens).deleteById(new PushToken.Key(USER, "tok"));
        service.forget(USER);
        verify(tokens).deleteByUserId(USER);
    }
}
