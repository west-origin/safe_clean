package kr.ansimclean.api.push;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.time.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 푸시 토큰 등록·발송 (BACKEND_PLAN.md B6). 발송은 PushHandler(아웃박스) 가 부른다 — 업무 트랜잭션 안에서 부르지 않는다(§11-1).
 * 실패는 흐름을 막지 않는다: 토큰이 없으면 아무것도 안 하고, 유효하지 않은 토큰은 지우고, 일시 실패는 **개수로 돌려준다**.
 * 이 클래스는 @Transactional 이라 여기서 던지면 부른 쪽 트랜잭션이 rollback-only 가 돼(Spring 프록시) PushHandler 가 잡아도 커밋이 실패한다
 * (외부 검토 4차 — 그때는 아웃박스 배치 전체가 한 트랜잭션이라 같은 배치의 sent/failed 기록까지 사라졌다. 지금은 OutboxDispatcher 가
 * 이벤트마다 트랜잭션을 따로 열어 그 이벤트 하나만 되돌려진다). 예외는 트랜잭션 경계 밖의 PushHandler 가 던진다.
 * 부르는 곳 예: PushController(register·unregister), PushHandler(send), AuthService(forget — 탈퇴·비밀번호 재설정).
 * 바깥 트랜잭션에 합쳐지는 곳 (CODE_GUIDE.md 3-3): send 는 OutboxDispatcher 가 이벤트 한 건마다 연 트랜잭션,
 * forget 은 탈퇴(AuthService.deleteAccount)의 트랜잭션 — 탈퇴 묶음이 뒤에서 실패해 되돌려지면 토큰 삭제도 되돌려진다.
 */
@Service
@Transactional
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    static final Set<String> PLATFORMS = Set.of("ios", "android", "web"); // 앱이 보낼 수 있는 platform 값

    private final PushTokenRepository tokens;
    private final PushSender sender;
    private final AppClock clock;
    private final kr.ansimclean.api.profile.ProfileService profiles;

    public PushService(PushTokenRepository tokens, PushSender sender, AppClock clock, kr.ansimclean.api.profile.ProfileService profiles) {
        this.tokens = tokens;
        this.sender = sender;
        this.clock = clock;
        this.profiles = profiles;
    }

    /** PUT /push/token — 앱 시작·로그인 후. 같은 토큰이 다른 사용자에게 남아 있으면 그쪽에서 지운다(기기 공유) */
    public void register(UUID userId, String platform, String token) {
        // 값 검사 — ApiException.validation 은 앱에 검증 오류와 이 한국어 문구를 돌려준다
        if (platform == null || !PLATFORMS.contains(platform)) throw ApiException.validation("platform 은 ios·android·web 중 하나예요");
        if (token == null || token.isBlank() || token.length() > 4096) throw ApiException.validation("token 이 올바르지 않아요");
        // 탈퇴와 줄을 선다 — 탈퇴와 겹친 등록이 탈퇴 뒤에 커밋돼 지워지지 않는 토큰 행이 남았다 (자체 점검 #53). 탈퇴가 먼저면 401 → 앱이 로그아웃
        profiles.holdLive(userId);
        tokens.deleteByTokenAndUserIdNot(token, userId);
        // 이미 있는 행이면 platform·시각만 바꾼다(touch, save 없이 커밋 때 반영 — CODE_GUIDE.md 3-3), 없으면 새로 저장
        tokens.findById(new PushToken.Key(userId, token)).ifPresentOrElse(t -> t.touch(platform, clock.now()),
                () -> tokens.save(PushToken.of(userId, platform, token, clock.now())));
    }

    /** DELETE /push/token — 로그아웃 때 이 기기 토큰만 */
    public void unregister(UUID userId, String token) {
        // (userId, token) 키로 지우므로 다른 사용자에게 등록된 같은 토큰은 지우지 않는다
        if (token != null && !token.isBlank()) tokens.deleteById(new PushToken.Key(userId, token));
    }

    // AuthService 는 비밀번호 재설정 때도 부른다 — 옛 세션을 끊은 기기로 알림이 계속 가지 않게
    /** 탈퇴 — 그 사용자의 토큰 전부 */
    public void forget(UUID userId) {
        tokens.deleteByUserId(userId);
    }

    /**
     * 사용자의 모든 기기로. 유효하지 않은 토큰은 지운다.
     * @param collapseKey 이벤트마다 고정된 값 — PushHandler 가 이벤트 type 과 본문 해시로 만든다(outbox_events.id 가 아니다). 재시도해도 같다
     * @return 일시 실패한 기기 수 — 0 이 아니면 호출자(PushHandler)가 던져 아웃박스가 백오프 재시도한다(collapseKey 로 기기에 중복은 안 쌓인다)
     */
    public int send(UUID userId, String title, String body, Map<String, String> data, String collapseKey) {
        List<PushToken> targets = tokens.findByUserId(userId);
        if (targets.isEmpty()) return 0; // 기기 없음 — 정상 (웹·미로그인)
        int failed = 0;
        for (PushToken t : targets) {
            switch (sender.send(t.getToken(), title, body, data, collapseKey)) {
                // 무효 토큰 행 삭제는 호출한 쪽 트랜잭션에 묶인다 — 다른 기기 실패로 PushHandler 가 던지면 이 삭제도
                // 함께 되돌려진다 (토큰 행이 남아 다음 재시도 때 그 기기로 다시 보내 본다)
                case INVALID_TOKEN -> tokens.delete(t);
                case FAILED -> failed++;
                case SENT -> { } // 보냄 — 할 일 없음
            }
        }
        if (failed > 0) log.warn("푸시 {} {}/{} 기기 발송 실패 — 아웃박스가 재시도", title, failed, targets.size());
        else log.debug("푸시 {} → {}대", title, targets.size());
        return failed;
    }
}
