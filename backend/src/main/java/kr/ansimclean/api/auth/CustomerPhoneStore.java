package kr.ansimclean.api.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 고객 휴대폰 인증(첫 1회 청소)의 SQL 모음. 테이블은 V061(customer_phone_proofs·customer_phone_challenges) + V062(failed_attempts).
 * auth.users 는 읽기만 한다 (Supabase 소유) — 인증번호가 맞았는지의 최종 근거는 Supabase 가 채운 phone·phone_confirmed_at 이다.
 * 부르는 곳: CustomerPhoneService (발송·확인), SmsHookService (실제 문자 발송 허용 여부).
 */
@Component
public class CustomerPhoneStore {
    /** 진행 중인 인증 요청: 요청한 번호(+82…), 요청 시각, 틀린 횟수 */
    public record Challenge(String phone, Instant requestedAt, int failedAttempts) {}

    private final JdbcClient jdbc;

    public CustomerPhoneStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static OffsetDateTime utc(Instant t) {
        return OffsetDateTime.ofInstant(t, ZoneOffset.UTC);
    }

    public Optional<Challenge> challenge(UUID customerId) {
        return jdbc.sql("select phone, requested_at, failed_attempts from customer_phone_challenges where customer_id = :id")
                .param("id", customerId)
                .query((rs, n) -> new Challenge(rs.getString("phone"),
                        rs.getObject("requested_at", OffsetDateTime.class).toInstant(), rs.getInt("failed_attempts")))
                .optional();
    }

    /** 새 요청을 기록한다 (같은 고객의 이전 요청은 덮어쓰고 틀린 횟수도 0 으로) */
    public void startChallenge(UUID customerId, String phone, Instant now) {
        jdbc.sql("""
                insert into customer_phone_challenges(customer_id, phone, requested_at, failed_attempts) values(:id, :phone, :now, 0)
                on conflict(customer_id) do update set phone = excluded.phone, requested_at = excluded.requested_at, failed_attempts = 0
                """).param("id", customerId).param("phone", phone).param("now", utc(now)).update();
    }

    /** 이 요청(requestedAt)이 아직 최신이면 지운다 — 발송 실패한 요청이 60초 재발송 간격에 헛되이 잡히지 않게 */
    public void dropChallenge(UUID customerId, Instant requestedAt) {
        jdbc.sql("delete from customer_phone_challenges where customer_id = :id and requested_at = :at")
                .param("id", customerId).param("at", utc(requestedAt)).update();
    }

    public void dropChallenge(UUID customerId) {
        jdbc.sql("delete from customer_phone_challenges where customer_id = :id").param("id", customerId).update();
    }

    /** 틀린 횟수 +1 하고 새 값을 돌려준다 (요청이 없으면 0) */
    public int countFailure(UUID customerId) {
        return jdbc.sql("update customer_phone_challenges set failed_attempts = failed_attempts + 1 where customer_id = :id returning failed_attempts")
                .param("id", customerId).query(Integer.class).optional().orElse(0);
    }

    public boolean hasProof(UUID customerId) {
        return jdbc.sql("select exists(select 1 from customer_phone_proofs where customer_id = :id)")
                .param("id", customerId).query(Boolean.class).single();
    }

    public void saveProof(UUID customerId, String phone, Instant now) {
        jdbc.sql("""
                insert into customer_phone_proofs(customer_id, phone, verified_at, source) values(:id, :phone, :now, 'sms')
                on conflict(customer_id) do nothing
                """).param("id", customerId).param("phone", phone).param("now", utc(now)).update();
    }

    /**
     * Supabase 가 이 계정에 확인해 둔 휴대폰 번호(+82…). since 이후에 확인된 것만.
     * GoTrue 는 번호를 '+' 없이 저장한다 (821012345678).
     */
    public Optional<String> confirmedPhone(UUID userId, Instant since) {
        return jdbc.sql("""
                select '+' || phone from auth.users
                where id = :id and deleted_at is null and phone is not null and phone <> ''
                  and phone_confirmed_at is not null and phone_confirmed_at >= :since
                """).param("id", userId).param("since", utc(since))
                .query(String.class).optional();
    }
}
