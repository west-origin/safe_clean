package kr.ansimclean.api.friend;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.FriendRequested;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.common.web.RateLimiter;
import kr.ansimclean.api.friend.dto.FriendBlocksResponse;
import kr.ansimclean.api.friend.dto.FriendEntry;
import kr.ansimclean.api.friend.dto.FriendListResponse;
import kr.ansimclean.api.friend.dto.FriendRequestResult;
import kr.ansimclean.api.friend.dto.FriendRequestsResponse;
import kr.ansimclean.api.friend.dto.FriendSearchResponse;
import kr.ansimclean.api.friend.dto.FriendUser;
import kr.ansimclean.api.friend.dto.InvitePreviewResponse;
import kr.ansimclean.api.friend.dto.InviteResponse;
import kr.ansimclean.api.friend.dto.MyFriendProfileResponse;
import kr.ansimclean.api.friend.dto.OkResponse;
import kr.ansimclean.api.profile.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 고객 친구 (docs/domain.md '고객 친구·금액권 선물' › 친구 규칙). 모든 메서드는 토큰의 고객 id(me) 기준으로만 동작한다.
 * 같은 두 사람에 대한 쓰기는 FriendStore.lockPair 로 줄을 세운다 — 서로 동시에 요청해도 뒤에 온 쪽이 앞의 대기 요청을 보고 자동으로 친구가 된다.
 * 차단·탈퇴·남의 요청은 존재를 드러내지 않게 NOT_FOUND. 다른 기능(선물)은 areFriends 로 '친구인가' 만 묻는다.
 */
@Service
@Transactional
public class FriendService {
    public static final String RELATION_NONE = "none";
    public static final String RELATION_FRIEND = "friend";
    public static final String RELATION_SENT = "sent";
    public static final String RELATION_RECEIVED = "received";
    public static final String RELATION_SELF = "self";
    /** 초대 코드가 살아 있는 기간 (코드에서 정함) */
    static final Duration INVITE_TTL = Duration.ofDays(30);
    /** 남은 기간이 이보다 짧으면 새 코드를 만든다 — 공유한 링크가 금방 끊기지 않게 */
    static final Duration INVITE_REUSE_MIN = Duration.ofDays(7);
    static final String NICKNAME_REQUIRED = "친구 기능을 쓰려면 닉네임을 먼저 정해주세요";
    private static final Pattern HANDLE = Pattern.compile("^(.+)#([0-9]{4})$");
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final FriendStore store;
    private final ProfileService profiles;
    private final OutboxService outbox;
    private final Idempotent idempotent;
    private final RateLimiter rateLimiter;
    private final InviteProperties invite;
    private final AppClock clock;
    private final SecureRandom random = new SecureRandom();

    public FriendService(FriendStore store, ProfileService profiles, OutboxService outbox, Idempotent idempotent, RateLimiter rateLimiter,
                         InviteProperties invite, AppClock clock) {
        this.store = store;
        this.profiles = profiles;
        this.outbox = outbox;
        this.idempotent = idempotent;
        this.rateLimiter = rateLimiter;
        this.invite = invite;
        this.clock = clock;
    }

    // ── 내 닉네임#태그 ────────────────────────────────────────────────

    public MyFriendProfileResponse me(UUID me) {
        return toMine(profiles.friendHandle(me));
    }

    public MyFriendProfileResponse setNickname(UUID me, String nickname) {
        return toMine(profiles.setFriendNickname(me, nickname));
    }

    private static MyFriendProfileResponse toMine(ProfileService.FriendHandle h) {
        return h.ready() ? new MyFriendProfileResponse(h.nickname(), h.tag(), h.nickname() + "#" + h.tag(), false)
                : new MyFriendProfileResponse(null, null, null, true);
    }

    /** 친구 쓰기(요청·수락·초대)는 내 닉네임#태그가 있어야 한다 — 상대 화면에 누가 보냈는지 보여야 하므로 */
    private void requireHandle(UUID me) {
        if (!profiles.friendHandle(me).ready()) throw new ApiException(ErrorCode.INVALID_STATE, NICKNAME_REQUIRED);
    }

    // ── 검색 ─────────────────────────────────────────────────────────

    /** 닉네임#태그 정확히 일치할 때만. 없거나, 차단 관계면 user = null (차단 여부를 드러내지 않는다) */
    @Transactional(readOnly = true)
    public FriendSearchResponse search(UUID me, String q) {
        rateLimiter.check("friend-search", me.toString(), RateLimiter.FRIEND_SEARCH_PER_USER_PER_HOUR);
        Matcher m = HANDLE.matcher(q == null ? "" : q.strip());
        if (!m.matches() || m.group(1).isBlank()) throw ApiException.validation("닉네임#숫자4자리 형식으로 입력해주세요");
        Optional<FriendStore.UserRow> found = store.findByHandle(m.group(1).strip(), m.group(2));
        if (found.isEmpty()) return new FriendSearchResponse(null, null);
        FriendStore.UserRow u = found.get();
        if (u.id().equals(me)) return new FriendSearchResponse(view(u), RELATION_SELF);
        if (store.blockedEither(me, u.id())) return new FriendSearchResponse(null, null);
        return new FriendSearchResponse(view(u), relation(me, u.id()));
    }

    private String relation(UUID me, UUID other) {
        if (store.areFriends(me, other)) return RELATION_FRIEND;
        return store.pendingBetween(me, other).map(r -> r.requesterId().equals(me) ? RELATION_SENT : RELATION_RECEIVED).orElse(RELATION_NONE);
    }

    // ── 요청 ─────────────────────────────────────────────────────────

    /**
     * 친구 요청 보내기 (Idempotency-Key). 상대가 이미 나에게 보낸 대기 요청이 있으면 그 요청을 수락해 바로 친구(accepted).
     * 내가 보낸 대기 요청이 있으면 그것을 그대로 돌려준다(중복 없음). 이미 친구면 INVALID_STATE. 새 요청이면 상대에게 푸시
     */
    public FriendRequestResult sendRequest(UUID me, String idempotencyKey, UUID targetId) {
        return idempotent.run(idempotencyKey, me, List.of("friend-request", targetId), FriendRequestResult.class, () -> doSend(me, targetId));
    }

    private FriendRequestResult doSend(UUID me, UUID targetId) {
        rateLimiter.check("friend-request", me.toString(), RateLimiter.FRIEND_REQUEST_PER_USER_PER_HOUR);
        if (me.equals(targetId)) throw ApiException.validation("나에게는 친구 요청을 보낼 수 없어요");
        requireHandle(me);
        profiles.holdLive(me); // 탈퇴와 줄을 선다
        FriendStore.UserRow target = store.findLiveCustomer(targetId).orElseThrow(ApiException::notFound);
        store.lockPair(me, targetId);
        if (store.blockedEither(me, targetId)) throw ApiException.notFound();
        if (store.areFriends(me, targetId)) throw new ApiException(ErrorCode.INVALID_STATE, "이미 친구예요");
        Instant now = clock.now();
        Optional<FriendStore.RequestRow> pending = store.pendingBetween(me, targetId);
        if (pending.isPresent()) {
            FriendStore.RequestRow r = pending.get();
            if (r.requesterId().equals(me)) return new FriendRequestResult(r.id(), "pending", view(target));
            // 상대가 먼저 보낸 요청이 있다 = 서로 원한다 → 그 요청을 수락하고 친구로 (알림 없음)
            store.decide(r.id(), "accepted", now);
            store.addFriendship(me, targetId, now);
            return new FriendRequestResult(r.id(), "accepted", view(target));
        }
        UUID id = store.insertRequest(me, targetId, "pending", "search", now);
        outbox.publish(new FriendRequested(id, targetId));
        return new FriendRequestResult(id, "pending", view(target));
    }

    /** 받은 요청 수락 — 받은 사람만. 이미 수락했으면 같은 결과 */
    public FriendRequestResult accept(UUID me, UUID requestId) {
        requireHandle(me);
        profiles.holdLive(me);
        FriendStore.RequestRow r = lockedRequest(requestId, me, true);
        FriendStore.UserRow other = store.findLiveCustomer(r.requesterId()).orElseThrow(ApiException::notFound); // 보낸 사람이 탈퇴했으면 없는 요청처럼
        if ("accepted".equals(r.status())) return new FriendRequestResult(r.id(), "accepted", view(other));
        requirePending(r);
        Instant now = clock.now();
        store.decide(r.id(), "accepted", now);
        store.addFriendship(me, r.requesterId(), now);
        return new FriendRequestResult(r.id(), "accepted", view(other));
    }

    /** 받은 요청 거절 — 받은 사람만. 상대에게 알리지 않는다 */
    public FriendRequestResult reject(UUID me, UUID requestId) {
        FriendStore.RequestRow r = lockedRequest(requestId, me, true);
        if (!"rejected".equals(r.status())) {
            requirePending(r);
            store.decide(r.id(), "rejected", clock.now());
        }
        return new FriendRequestResult(r.id(), "rejected", store.findLiveCustomer(r.requesterId()).map(FriendService::view).orElse(null));
    }

    /** 보낸 요청 취소 — 보낸 사람만 */
    public FriendRequestResult cancel(UUID me, UUID requestId) {
        FriendStore.RequestRow r = lockedRequest(requestId, me, false);
        if (!"cancelled".equals(r.status())) {
            requirePending(r);
            store.decide(r.id(), "cancelled", clock.now());
        }
        return new FriendRequestResult(r.id(), "cancelled", store.findLiveCustomer(r.addresseeId()).map(FriendService::view).orElse(null));
    }

    /**
     * 요청을 두 사람 쌍 잠금 뒤에 다시 읽는다. asAddressee = true 면 받은 사람만, false 면 보낸 사람만 — 아니면 NOT_FOUND (남의 요청).
     * 잠금 전 읽기는 쌍을 알아내는 데만 쓰고, 상태는 잠금 뒤 다시 읽은 값으로 판단한다
     */
    private FriendStore.RequestRow lockedRequest(UUID requestId, UUID me, boolean asAddressee) {
        FriendStore.RequestRow first = store.request(requestId).orElseThrow(ApiException::notFound);
        UUID owner = asAddressee ? first.addresseeId() : first.requesterId();
        if (!owner.equals(me)) throw ApiException.notFound();
        store.lockPair(first.requesterId(), first.addresseeId());
        return store.request(requestId).orElseThrow(ApiException::notFound);
    }

    private static void requirePending(FriendStore.RequestRow r) {
        if (!"pending".equals(r.status())) throw new ApiException(ErrorCode.INVALID_STATE, "이미 처리된 요청이에요");
    }

    // ── 목록 ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FriendListResponse friends(UUID me) {
        return new FriendListResponse(store.friends(me).stream().map(this::entry).toList());
    }

    /** box = received | sent (그 밖이면 400) */
    @Transactional(readOnly = true)
    public FriendRequestsResponse requests(UUID me, String box) {
        List<FriendStore.UserAt> rows = switch (box == null ? "" : box) {
            case "received" -> store.received(me);
            case "sent" -> store.sent(me);
            default -> throw ApiException.validation("box 는 received 또는 sent 예요");
        };
        return new FriendRequestsResponse(box, rows.stream().map(this::entry).toList());
    }

    @Transactional(readOnly = true)
    public FriendBlocksResponse blocks(UUID me) {
        return new FriendBlocksResponse(store.blocks(me).stream().map(this::entry).toList());
    }

    /** 다른 기능(선물)이 묻는다: 두 고객이 지금 친구인가. 차단하면 친구가 풀리므로 차단 관계는 false */
    @Transactional(readOnly = true)
    public boolean areFriends(UUID a, UUID b) {
        return store.areFriends(a, b) && !store.blockedEither(a, b);
    }

    /**
     * 선물처럼 두 사람 사이 쓰기를 하는 다른 기능이 같은 트랜잭션에서 친구 관계를 잠그고 확인한다.
     * 잠금 뒤 친구가 아니거나(삭제·차단이 먼저 커밋됨) 상대가 탈퇴했으면 false — 이 트랜잭션이 끝날 때까지 삭제·차단이 끼어들지 못한다
     */
    public boolean lockFriendship(UUID a, UUID b) {
        store.lockPair(a, b);
        return areFriends(a, b) && store.findLiveCustomer(a).isPresent() && store.findLiveCustomer(b).isPresent();
    }

    // ── 삭제·차단 ────────────────────────────────────────────────────

    /** 친구 삭제 — 양쪽 모두 해제, 상대에게 알림 없음. 친구가 아니어도 성공(같은 결과) */
    public OkResponse unfriend(UUID me, UUID other) {
        store.lockPair(me, other);
        store.deleteFriendship(me, other);
        return OkResponse.OK;
    }

    /** 차단 — 친구 해제 + 두 사람 사이 대기 요청 취소. 차단된 사람은 나를 검색·요청·초대 수락·선물할 수 없다 */
    public OkResponse block(UUID me, UUID other) {
        if (me.equals(other)) throw ApiException.validation("나를 차단할 수 없어요");
        profiles.holdLive(me);
        store.findLiveCustomer(other).orElseThrow(ApiException::notFound);
        store.lockPair(me, other);
        Instant now = clock.now();
        store.deleteFriendship(me, other);
        store.cancelPendingBetween(me, other, now);
        store.block(me, other, now);
        return OkResponse.OK;
    }

    /** 차단 해제 — 친구로 되돌리지는 않는다 */
    public OkResponse unblock(UUID me, UUID other) {
        store.lockPair(me, other);
        store.unblock(me, other);
        return OkResponse.OK;
    }

    // ── 초대 ─────────────────────────────────────────────────────────

    /** 공유할 초대 링크. 7일 넘게 남은 내 코드가 있으면 그것을, 없으면 새로 (30일) */
    public InviteResponse createInvite(UUID me) {
        if (invite.baseUrl().isEmpty()) throw new ApiException(ErrorCode.INVALID_STATE, "초대 링크를 준비하고 있어요");
        requireHandle(me);
        profiles.holdLive(me);
        Instant now = clock.now();
        FriendStore.InviteRow row = store.liveInviteOf(me, now.plus(INVITE_REUSE_MIN)).orElseGet(() -> {
            String code = newCode();
            Instant exp = now.plus(INVITE_TTL);
            store.insertInvite(code, me, now, exp);
            return new FriendStore.InviteRow(code, me, exp);
        });
        return new InviteResponse(row.code(), inviteUrl(row.code()), row.expiresAt().toString());
    }

    public String inviteUrl(String code) {
        return invite.baseUrl() + "/i/" + code;
    }

    private String newCode() {
        StringBuilder b = new StringBuilder(10);
        for (int i = 0; i < 10; i++) b.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        return b.toString();
    }

    /** 앱이 초대 링크를 열었을 때 — 초대한 사람과 나의 관계. 만료·없음·차단·초대한 사람 탈퇴면 NOT_FOUND */
    @Transactional(readOnly = true)
    public InvitePreviewResponse previewInvite(UUID me, String code) {
        rateLimiter.check("friend-invite", me.toString(), RateLimiter.FRIEND_INVITE_PER_USER_PER_HOUR);
        FriendStore.UserRow inviter = liveInviter(code);
        if (inviter.id().equals(me)) return new InvitePreviewResponse(view(inviter), RELATION_SELF);
        if (store.blockedEither(me, inviter.id())) throw ApiException.notFound();
        return new InvitePreviewResponse(view(inviter), relation(me, inviter.id()));
    }

    /**
     * 초대 수락 (Idempotency-Key) — 초대한 사람이 이미 원한 관계라 바로 친구. 요청 이력은 source = invite, accepted 로 남긴다.
     * 두 사람 사이 대기 요청이 있으면 그것을 수락으로 바꾼다. 이미 친구면 그대로 성공
     */
    public FriendRequestResult acceptInvite(UUID me, String idempotencyKey, String code) {
        return idempotent.run(idempotencyKey, me, List.of("friend-invite", code), FriendRequestResult.class, () -> doAcceptInvite(me, code));
    }

    private FriendRequestResult doAcceptInvite(UUID me, String code) {
        rateLimiter.check("friend-invite", me.toString(), RateLimiter.FRIEND_INVITE_PER_USER_PER_HOUR);
        FriendStore.UserRow inviter = liveInviter(code);
        if (inviter.id().equals(me)) throw ApiException.validation("내가 보낸 초대 링크예요");
        requireHandle(me);
        profiles.holdLive(me);
        store.lockPair(me, inviter.id());
        if (store.blockedEither(me, inviter.id())) throw ApiException.notFound();
        Instant now = clock.now();
        Optional<FriendStore.RequestRow> pending = store.pendingBetween(me, inviter.id());
        if (store.areFriends(me, inviter.id())) {
            return new FriendRequestResult(pending.map(FriendStore.RequestRow::id).orElse(null), "accepted", view(inviter));
        }
        UUID id;
        if (pending.isPresent()) {
            id = pending.get().id();
            store.decide(id, "accepted", now);
        } else {
            id = store.insertRequest(inviter.id(), me, "accepted", "invite", now);
        }
        store.addFriendship(me, inviter.id(), now);
        return new FriendRequestResult(id, "accepted", view(inviter));
    }

    private FriendStore.UserRow liveInviter(String code) {
        if (code == null || !code.matches("[A-Za-z0-9]{10}")) throw ApiException.notFound();
        FriendStore.InviteRow row = store.invite(code, clock.now()).orElseThrow(ApiException::notFound);
        return store.findLiveCustomer(row.inviterId()).orElseThrow(ApiException::notFound);
    }

    /** 공개 초대 페이지(/i/{code})용: 초대한 사람의 닉네임 (없거나 만료면 빈 값) */
    @Transactional(readOnly = true)
    public Optional<String> inviterNickname(String code) {
        if (code == null || !code.matches("[A-Za-z0-9]{10}")) return Optional.empty();
        return store.invite(code, clock.now()).flatMap(r -> store.findLiveCustomer(r.inviterId())).map(FriendStore.UserRow::nickname);
    }

    // ── 표시 ─────────────────────────────────────────────────────────

    private FriendEntry entry(FriendStore.UserAt row) {
        return new FriendEntry(view(row.user()), row.requestId(), row.at().toString(), KoreanFormat.dateShort(AppClock.toSeoul(row.at()).toLocalDate()));
    }

    static FriendUser view(FriendStore.UserRow u) {
        String handle = u.nickname() == null || u.tag() == null ? null : u.nickname() + "#" + u.tag();
        return new FriendUser(u.id(), u.nickname(), u.tag(), handle, maskName(u.name()));
    }

    /** 이름 가운데를 가린다: 김철수 → 김*수, 김수 → 김*, 남궁민수 → 남**수. 한 글자·빈 값은 * */
    static String maskName(String name) {
        if (name == null || name.isBlank()) return "*";
        int[] cps = name.strip().codePoints().toArray();
        if (cps.length == 1) return "*";
        StringBuilder b = new StringBuilder().appendCodePoint(cps[0]);
        if (cps.length == 2) return b.append('*').toString();
        b.append("*".repeat(cps.length - 2));
        return b.appendCodePoint(cps[cps.length - 1]).toString();
    }
}
