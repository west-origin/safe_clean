package kr.ansimclean.api.friend;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 친구 표(V069 friend_requests·friendships·friend_blocks·friend_invites)의 SQL 모음. FriendService 만 쓴다.
 * 상대 고객의 닉네임·태그·이름은 profiles 를 읽기만 한다(쓰기는 ProfileService). '살아 있는 고객' = 고객 역할 ∧ 가입 완료 ∧ 탈퇴 안 함.
 * 두 사람 사이의 쓰기는 부르는 쪽이 먼저 lockPair 로 줄을 세운다.
 */
@Component
public class FriendStore {
    /** 고객 한 명의 친구 화면용 정보 (이름은 마스킹 전 원문 — 응답에 내보내기 전에 FriendService 가 가린다) */
    public record UserRow(UUID id, String nickname, String tag, String name) {}

    /** 친구 요청 한 건 */
    public record RequestRow(UUID id, UUID requesterId, UUID addresseeId, String status, String source, Instant createdAt) {}

    /** 친구 목록·요청 목록 한 줄 */
    public record UserAt(UserRow user, UUID requestId, Instant at) {}

    /** 초대 코드 + 초대한 사람 */
    public record InviteRow(String code, UUID inviterId, Instant expiresAt) {}

    private static final String LIVE_CUSTOMER = "p.role = 'customer' and p.deleted_at is null and p.signup_completed_at is not null";

    private final JdbcClient jdbc;

    public FriendStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static UserRow user(java.sql.ResultSet rs, String prefix) throws java.sql.SQLException {
        return new UserRow(rs.getObject(prefix + "id", UUID.class), rs.getString(prefix + "nickname"), rs.getString(prefix + "friend_tag"), rs.getString(prefix + "name"));
    }

    /** 닉네임#태그 정확히 일치하는 살아 있는 고객 (닉네임 대소문자 무시) */
    public Optional<UserRow> findByHandle(String nickname, String tag) {
        return jdbc.sql("select p.id, p.nickname, p.friend_tag, p.name from profiles p where " + LIVE_CUSTOMER
                        + " and lower(p.nickname) = lower(:n) and p.friend_tag = :t")
                .param("n", nickname).param("t", tag)
                .query((rs, i) -> user(rs, "")).optional();
    }

    /** id 로 살아 있는 고객 하나 */
    public Optional<UserRow> findLiveCustomer(UUID id) {
        return jdbc.sql("select p.id, p.nickname, p.friend_tag, p.name from profiles p where " + LIVE_CUSTOMER + " and p.id = :id")
                .param("id", id).query((rs, i) -> user(rs, "")).optional();
    }

    /**
     * 두 사람 쌍의 트랜잭션 잠금. 순서와 상관없이 같은 키(작은 id·큰 id)라 A→B 요청과 B→A 요청이 동시에 와도 한 줄로 선다.
     * 트랜잭션이 끝나면 풀린다 (pg_advisory_xact_lock)
     */
    public void lockPair(UUID a, UUID b) {
        String key = a.compareTo(b) < 0 ? "friend:" + a + ":" + b : "friend:" + b + ":" + a;
        jdbc.sql("select count(*) from (select pg_advisory_xact_lock(hashtextextended(:k, 0))) x").param("k", key).query(Long.class).single();
    }

    public boolean areFriends(UUID a, UUID b) {
        return jdbc.sql("select exists(select 1 from friendships where user_a = least(:a, :b) and user_b = greatest(:a, :b))")
                .param("a", a).param("b", b).query(Boolean.class).single();
    }

    /** 둘 중 한 사람이라도 상대를 차단했나 */
    public boolean blockedEither(UUID a, UUID b) {
        return jdbc.sql("select exists(select 1 from friend_blocks where (blocker_id = :a and blocked_id = :b) or (blocker_id = :b and blocked_id = :a))")
                .param("a", a).param("b", b).query(Boolean.class).single();
    }

    private static RequestRow request(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new RequestRow(rs.getObject("id", UUID.class), rs.getObject("requester_id", UUID.class), rs.getObject("addressee_id", UUID.class),
                rs.getString("status"), rs.getString("source"), rs.getTimestamp("created_at").toInstant());
    }

    /** 두 사람 사이의 대기 요청 (방향 무관, 많아야 하나 — friend_requests_one_pending) */
    public Optional<RequestRow> pendingBetween(UUID a, UUID b) {
        return jdbc.sql("""
                select id, requester_id, addressee_id, status, source, created_at from friend_requests
                where status = 'pending' and least(requester_id, addressee_id) = least(:a, :b) and greatest(requester_id, addressee_id) = greatest(:a, :b)
                """).param("a", a).param("b", b).query(FriendStore::request).optional();
    }

    public Optional<RequestRow> request(UUID id) {
        return jdbc.sql("select id, requester_id, addressee_id, status, source, created_at from friend_requests where id = :id")
                .param("id", id).query(FriendStore::request).optional();
    }

    public UUID insertRequest(UUID requester, UUID addressee, String status, String source, Instant now) {
        return jdbc.sql("""
                insert into friend_requests(requester_id, addressee_id, status, source, created_at, decided_at)
                values (:r, :a, :s, :src, :now, :decided) returning id
                """).param("r", requester).param("a", addressee).param("s", status).param("src", source).param("now", java.sql.Timestamp.from(now))
                .param("decided", "pending".equals(status) ? null : java.sql.Timestamp.from(now), java.sql.Types.TIMESTAMP)
                .query(UUID.class).single();
    }

    /** 대기 요청의 결과를 적는다. 대기가 아니면 바꾸지 않는다 — 바꾼 행 수(0|1) */
    public int decide(UUID id, String status, Instant now) {
        return jdbc.sql("update friend_requests set status = :s, decided_at = :now where id = :id and status = 'pending'")
                .param("s", status).param("now", java.sql.Timestamp.from(now)).param("id", id).update();
    }

    /** 두 사람 사이 대기 요청을 모두 취소 (차단할 때) */
    public void cancelPendingBetween(UUID a, UUID b, Instant now) {
        jdbc.sql("""
                update friend_requests set status = 'cancelled', decided_at = :now
                where status = 'pending' and least(requester_id, addressee_id) = least(:a, :b) and greatest(requester_id, addressee_id) = greatest(:a, :b)
                """).param("a", a).param("b", b).param("now", java.sql.Timestamp.from(now)).update();
    }

    public void addFriendship(UUID a, UUID b, Instant now) {
        jdbc.sql("insert into friendships(user_a, user_b, created_at) values (least(:a, :b), greatest(:a, :b), :now) on conflict do nothing")
                .param("a", a).param("b", b).param("now", java.sql.Timestamp.from(now)).update();
    }

    public int deleteFriendship(UUID a, UUID b) {
        return jdbc.sql("delete from friendships where user_a = least(:a, :b) and user_b = greatest(:a, :b)").param("a", a).param("b", b).update();
    }

    public void block(UUID blocker, UUID blocked, Instant now) {
        jdbc.sql("insert into friend_blocks(blocker_id, blocked_id, created_at) values (:a, :b, :now) on conflict do nothing")
                .param("a", blocker).param("b", blocked).param("now", java.sql.Timestamp.from(now)).update();
    }

    public int unblock(UUID blocker, UUID blocked) {
        return jdbc.sql("delete from friend_blocks where blocker_id = :a and blocked_id = :b").param("a", blocker).param("b", blocked).update();
    }

    /** 내 친구 (탈퇴한 사람 제외), 닉네임 순 */
    public List<UserAt> friends(UUID me) {
        return jdbc.sql("""
                select p.id, p.nickname, p.friend_tag, p.name, f.created_at from friendships f
                join profiles p on p.id = case when f.user_a = :me then f.user_b else f.user_a end
                where (f.user_a = :me or f.user_b = :me) and %s
                order by lower(p.nickname), p.friend_tag
                """.formatted(LIVE_CUSTOMER)).param("me", me)
                .query((rs, i) -> new UserAt(user(rs, ""), null, rs.getTimestamp("created_at").toInstant())).list();
    }

    /** 받은 대기 요청 (보낸 사람이 탈퇴했으면 빠진다), 최근 순 */
    public List<UserAt> received(UUID me) {
        return jdbc.sql("""
                select p.id, p.nickname, p.friend_tag, p.name, r.id as request_id, r.created_at from friend_requests r
                join profiles p on p.id = r.requester_id
                where r.addressee_id = :me and r.status = 'pending' and %s order by r.created_at desc
                """.formatted(LIVE_CUSTOMER)).param("me", me)
                .query((rs, i) -> new UserAt(user(rs, ""), rs.getObject("request_id", UUID.class), rs.getTimestamp("created_at").toInstant())).list();
    }

    /** 보낸 대기 요청 (받는 사람이 탈퇴했으면 빠진다), 최근 순 */
    public List<UserAt> sent(UUID me) {
        return jdbc.sql("""
                select p.id, p.nickname, p.friend_tag, p.name, r.id as request_id, r.created_at from friend_requests r
                join profiles p on p.id = r.addressee_id
                where r.requester_id = :me and r.status = 'pending' and %s order by r.created_at desc
                """.formatted(LIVE_CUSTOMER)).param("me", me)
                .query((rs, i) -> new UserAt(user(rs, ""), rs.getObject("request_id", UUID.class), rs.getTimestamp("created_at").toInstant())).list();
    }

    /** 내가 차단한 사람 (탈퇴한 사람 제외), 최근 순 */
    public List<UserAt> blocks(UUID me) {
        return jdbc.sql("""
                select p.id, p.nickname, p.friend_tag, p.name, b.created_at from friend_blocks b
                join profiles p on p.id = b.blocked_id
                where b.blocker_id = :me and %s order by b.created_at desc
                """.formatted(LIVE_CUSTOMER)).param("me", me)
                .query((rs, i) -> new UserAt(user(rs, ""), null, rs.getTimestamp("created_at").toInstant())).list();
    }

    /** 이 고객의 아직 쓸 수 있는 초대 코드 중 가장 늦게 끝나는 것 (after 이후까지 살아 있는 것만) */
    public Optional<InviteRow> liveInviteOf(UUID inviter, Instant after) {
        return jdbc.sql("select code, inviter_id, expires_at from friend_invites where inviter_id = :id and expires_at > :t order by expires_at desc limit 1")
                .param("id", inviter).param("t", java.sql.Timestamp.from(after))
                .query((rs, i) -> new InviteRow(rs.getString("code"), rs.getObject("inviter_id", UUID.class), rs.getTimestamp("expires_at").toInstant())).optional();
    }

    public void insertInvite(String code, UUID inviter, Instant now, Instant expiresAt) {
        jdbc.sql("insert into friend_invites(code, inviter_id, created_at, expires_at) values (:c, :id, :now, :exp)")
                .param("c", code).param("id", inviter).param("now", java.sql.Timestamp.from(now)).param("exp", java.sql.Timestamp.from(expiresAt)).update();
    }

    /** 아직 살아 있는 초대 코드 */
    public Optional<InviteRow> invite(String code, Instant now) {
        return jdbc.sql("select code, inviter_id, expires_at from friend_invites where code = :c and expires_at > :now")
                .param("c", code).param("now", java.sql.Timestamp.from(now))
                .query((rs, i) -> new InviteRow(rs.getString("code"), rs.getObject("inviter_id", UUID.class), rs.getTimestamp("expires_at").toInstant())).optional();
    }

    /** 탈퇴 + 5년 파기: 이 계정의 친구 관계·요청·차단·초대를 지운다 (FriendAccountPurge) */
    public void purge(UUID userId) {
        jdbc.sql("delete from friendships where user_a = :id or user_b = :id").param("id", userId).update();
        jdbc.sql("delete from friend_requests where requester_id = :id or addressee_id = :id").param("id", userId).update();
        jdbc.sql("delete from friend_blocks where blocker_id = :id or blocked_id = :id").param("id", userId).update();
        jdbc.sql("delete from friend_invites where inviter_id = :id").param("id", userId).update();
    }
}
