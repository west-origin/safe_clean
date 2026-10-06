package kr.ansimclean.api.auth;

import kr.ansimclean.api.common.events.AccountDeleted;
import kr.ansimclean.api.common.outbox.OutboxHandler;
import kr.ansimclean.api.common.supabase.SupabaseAuthClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

/**
 * 탈퇴 뒤 Supabase 사용자 삭제 — 외부 효과라 트랜잭션 밖(아웃박스)에서, 실패하면 백오프 재시도·6회 뒤 failed 로 드러난다 (CLAUDE.md §11-1).
 * 이미 지워졌으면(404) SupabaseAuthClient.deleteUser 가 조용히 끝난다.
 * 흐름: AuthService.deleteAccount 가 탈퇴 트랜잭션 안에서 AccountDeleted 이벤트를 아웃박스에 적고 → 커밋 뒤 OutboxDispatcher 가 여기로 넘긴다.
 * AuthService 도 커밋 직후 한 번 바로 지워 보므로, 그게 성공했으면 여기서는 404 로 끝난다.
 */
@Component
public class SupabaseUserDeletionHandler implements OutboxHandler {
    private final SupabaseAuthClient supabase;
    private final JsonMapper json = JsonMapper.builder().build(); // 아웃박스에 저장된 이벤트 JSON 을 record 로 읽는다

    public SupabaseUserDeletionHandler(SupabaseAuthClient supabase) {
        this.supabase = supabase;
    }

    /** 이 핸들러가 맡는 아웃박스 이벤트 이름 (이벤트 record 의 클래스 이름) */
    @Override
    public Set<String> types() {
        return Set.of("AccountDeleted");
    }

    /** payload 의 userId 로 Supabase 사용자를 지운다. 예외가 나면 아웃박스가 실패로 적고 나중에 다시 부른다 */
    @Override
    public void handle(String type, String payloadJson) {
        supabase.deleteUser(json.readValue(payloadJson, AccountDeleted.class).userId());
    }
}
