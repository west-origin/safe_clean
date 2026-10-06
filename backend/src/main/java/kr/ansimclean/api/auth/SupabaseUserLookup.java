package kr.ansimclean.api.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * auth.users 읽기 전용 조회 (같은 Postgres 라 SQL 로 본다. 쓰기는 절대 하지 않는다 — Supabase 소유).
 * 용도: 가입 도중 실패로 Supabase 사용자만 남고 profiles 가 없는 잔재를 찾아 재사용한다 (BACKEND_PLAN.md §5.1 signup (a)).
 * 비밀번호 재설정 뒤 끊을 다른 세션 목록(auth.sessions)도 여기서 읽는다.
 * 부르는 곳: AuthService (signup, resetPassword).
 * JdbcClient: 엔티티 없이 SQL 문자열을 바로 실행하는 스프링 도구. SQL 안의 :id 같은 자리에 param 으로 값을 넣는다.
 */
@Component
public class SupabaseUserLookup {
    private final JdbcClient jdbc;

    public SupabaseUserLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 이 사용자의 살아 있는 Supabase 세션 id (비밀번호 재설정 때 새 세션 말고 전부 끊으려고, 감사 M-03). 읽기만 한다 */
    // 조건: 비밀번호를 바꾸기 **전에** 부른다 — Supabase 가 비밀번호 변경 때 세션 행을 지워, 뒤에 읽으면 비어 있다 (AuthService.resetPassword)
    public java.util.List<String> sessionIds(UUID userId) {
        return jdbc.sql("select id::text from auth.sessions where user_id = :id").param("id", userId).query(String.class).list();
    }

    /**
     * 이 번호의 Supabase 사용자 id (삭제 표시된 사용자는 뺀다). 없으면 빈 Optional.
     * 가입 때 이전 시도의 잔재나 탈퇴 뒤 못 지운 사용자를 찾는 데 쓴다 (AuthService.signup).
     */
    public Optional<UUID> findIdByPhone(String e164) {
        return jdbc.sql("select id from auth.users where phone = :phone and deleted_at is null limit 1")
                .param("phone", e164.substring(1)) // GoTrue 는 '+' 없이 저장한다 (821012345678)
                .query(UUID.class).optional();
    }
}
