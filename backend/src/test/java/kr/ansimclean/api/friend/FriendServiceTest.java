package kr.ansimclean.api.friend;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.FriendRequested;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.common.web.RateLimiter;
import kr.ansimclean.api.friend.dto.FriendRequestResult;
import kr.ansimclean.api.friend.dto.FriendSearchResponse;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FriendService 규칙 (docs/domain.md '고객 친구·금액권 선물' › 친구 규칙). FriendStore(SQL)·ProfileService 는 가짜.
 * 실제 SQL·잠금·동시 요청은 FriendIT (Docker 필요).
 */
class FriendServiceTest {
    static final Instant NOW = Instant.parse("2026-11-13T03:00:00Z");
    final UUID me = UUID.randomUUID();
    final UUID other = UUID.randomUUID();
    final FriendStore.UserRow otherRow = new FriendStore.UserRow(other, "청소왕", "0420", "김철수");
    final FriendStore.UserRow meRow = new FriendStore.UserRow(me, "나", "0001", "이영희");

    FriendStore store = mock(FriendStore.class);
    ProfileService profiles = mock(ProfileService.class);
    OutboxService outbox = mock(OutboxService.class);
    Idempotent idempotent = mock(Idempotent.class);
    RateLimiter rateLimiter = mock(RateLimiter.class);
    FriendService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new FriendService(store, profiles, outbox, idempotent, rateLimiter, new InviteProperties("https://api.test/", "", ""),
                new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
        when(idempotent.run(any(), any(), any(), any(), any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(4)).get());
        when(profiles.friendHandle(any())).thenReturn(new ProfileService.FriendHandle("나", "0001"));
        when(store.findLiveCustomer(other)).thenReturn(Optional.of(otherRow));
        when(store.findLiveCustomer(me)).thenReturn(Optional.of(meRow));
        when(store.pendingBetween(any(), any())).thenReturn(Optional.empty());
    }

    private static void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).code()).isEqualTo(code);
    }

    @Test
    void 새_요청은_대기로_만들고_받는_사람에게_푸시한다_잠금이_먼저다() {
        UUID id = UUID.randomUUID();
        when(store.insertRequest(me, other, "pending", "search", NOW)).thenReturn(id);
        FriendRequestResult r = service.sendRequest(me, "k1", other);
        assertThat(r.status()).isEqualTo("pending");
        assertThat(r.user().handle()).isEqualTo("청소왕#0420");
        assertThat(r.user().maskedName()).isEqualTo("김*수");
        var order = inOrder(store, outbox);
        order.verify(store).lockPair(me, other);
        order.verify(store).insertRequest(me, other, "pending", "search", NOW);
        order.verify(outbox).publish(new FriendRequested(id, other));
    }

    @Test
    void 상대가_먼저_보낸_대기_요청이_있으면_자동으로_친구가_된다() {
        UUID theirs = UUID.randomUUID();
        when(store.pendingBetween(me, other)).thenReturn(Optional.of(new FriendStore.RequestRow(theirs, other, me, "pending", "search", NOW)));
        FriendRequestResult r = service.sendRequest(me, null, other);
        assertThat(r.status()).isEqualTo("accepted");
        assertThat(r.id()).isEqualTo(theirs);
        verify(store).decide(theirs, "accepted", NOW);
        verify(store).addFriendship(me, other, NOW);
        verify(store, never()).insertRequest(any(), any(), any(), any(), any());
        verify(outbox, never()).publish(any());
    }

    @Test
    void 내가_보낸_대기_요청이_있으면_새로_만들지_않는다() {
        UUID mine = UUID.randomUUID();
        when(store.pendingBetween(me, other)).thenReturn(Optional.of(new FriendStore.RequestRow(mine, me, other, "pending", "search", NOW)));
        FriendRequestResult r = service.sendRequest(me, null, other);
        assertThat(r.id()).isEqualTo(mine);
        assertThat(r.status()).isEqualTo("pending");
        verify(store, never()).insertRequest(any(), any(), any(), any(), any());
        verify(outbox, never()).publish(any());
    }

    @Test
    void 이미_친구거나_차단_관계거나_나_자신이거나_닉네임이_없으면_막는다() {
        when(store.areFriends(me, other)).thenReturn(true);
        assertCode(() -> service.sendRequest(me, null, other), ErrorCode.INVALID_STATE);

        when(store.areFriends(me, other)).thenReturn(false);
        when(store.blockedEither(me, other)).thenReturn(true);
        assertCode(() -> service.sendRequest(me, null, other), ErrorCode.NOT_FOUND); // 차단 여부를 드러내지 않는다

        assertCode(() -> service.sendRequest(me, null, me), ErrorCode.VALIDATION);

        UUID ghost = UUID.randomUUID();
        when(store.findLiveCustomer(ghost)).thenReturn(Optional.empty()); // 탈퇴·매니저·없는 사람
        when(store.blockedEither(me, ghost)).thenReturn(false);
        assertCode(() -> service.sendRequest(me, null, ghost), ErrorCode.NOT_FOUND);

        when(profiles.friendHandle(me)).thenReturn(new ProfileService.FriendHandle(null, null));
        when(store.blockedEither(me, other)).thenReturn(false);
        assertCode(() -> service.sendRequest(me, null, other), ErrorCode.INVALID_STATE);
        verify(store, never()).insertRequest(any(), any(), any(), any(), any());
    }

    @Test
    void 요청_한도를_넘으면_보내지_않는다() {
        doThrow(new ApiException(ErrorCode.RATE_LIMITED)).when(rateLimiter).check(eq("friend-request"), anyString(), anyInt());
        assertCode(() -> service.sendRequest(me, null, other), ErrorCode.RATE_LIMITED);
        verify(store, never()).insertRequest(any(), any(), any(), any(), any());
    }

    @Test
    void 수락은_받은_사람만_대기에서만_다시_눌러도_같다() {
        UUID id = UUID.randomUUID();
        FriendStore.RequestRow pending = new FriendStore.RequestRow(id, other, me, "pending", "search", NOW);
        when(store.request(id)).thenReturn(Optional.of(pending));
        assertThat(service.accept(me, id).status()).isEqualTo("accepted");
        verify(store).lockPair(other, me);
        verify(store).decide(id, "accepted", NOW);
        verify(store).addFriendship(me, other, NOW);

        // 보낸 사람이 수락을 누르면 남의 요청처럼 404
        assertCode(() -> service.accept(other, id), ErrorCode.NOT_FOUND);

        // 이미 수락된 요청 = 같은 결과
        when(store.request(id)).thenReturn(Optional.of(new FriendStore.RequestRow(id, other, me, "accepted", "search", NOW)));
        assertThat(service.accept(me, id).status()).isEqualTo("accepted");

        // 취소된 요청은 수락할 수 없다
        when(store.request(id)).thenReturn(Optional.of(new FriendStore.RequestRow(id, other, me, "cancelled", "search", NOW)));
        assertCode(() -> service.accept(me, id), ErrorCode.INVALID_STATE);
    }

    @Test
    void 거절은_받은_사람만_취소는_보낸_사람만() {
        UUID id = UUID.randomUUID();
        when(store.request(id)).thenReturn(Optional.of(new FriendStore.RequestRow(id, other, me, "pending", "search", NOW)));
        assertCode(() -> service.reject(other, id), ErrorCode.NOT_FOUND);
        assertCode(() -> service.cancel(me, id), ErrorCode.NOT_FOUND);
        assertThat(service.reject(me, id).status()).isEqualTo("rejected");
        verify(store).decide(id, "rejected", NOW);

        UUID sent = UUID.randomUUID();
        when(store.request(sent)).thenReturn(Optional.of(new FriendStore.RequestRow(sent, me, other, "pending", "search", NOW)));
        assertThat(service.cancel(me, sent).status()).isEqualTo("cancelled");
        verify(store).decide(sent, "cancelled", NOW);

        // 상대가 이미 수락했으면 취소할 수 없다
        when(store.request(sent)).thenReturn(Optional.of(new FriendStore.RequestRow(sent, me, other, "accepted", "search", NOW)));
        assertCode(() -> service.cancel(me, sent), ErrorCode.INVALID_STATE);
    }

    @Test
    void 차단하면_친구_해제와_대기_요청_취소를_같이_한다() {
        service.block(me, other);
        var order = inOrder(store);
        order.verify(store).lockPair(me, other);
        order.verify(store).deleteFriendship(me, other);
        order.verify(store).cancelPendingBetween(me, other, NOW);
        order.verify(store).block(me, other, NOW);
        assertCode(() -> service.block(me, me), ErrorCode.VALIDATION);
    }

    @Test
    void 친구_삭제는_친구_행만_지우고_알리지_않는다() {
        service.unfriend(me, other);
        verify(store).lockPair(me, other);
        verify(store).deleteFriendship(me, other);
        verify(outbox, never()).publish(any());
    }

    @Test
    void 검색은_닉네임_태그_정확히_일치만_차단_관계는_결과가_없다() {
        assertCode(() -> service.search(me, "청소왕"), ErrorCode.VALIDATION);
        assertCode(() -> service.search(me, "청소왕#12"), ErrorCode.VALIDATION);

        when(store.findByHandle("청소왕", "0420")).thenReturn(Optional.of(otherRow));
        FriendSearchResponse found = service.search(me, " 청소왕#0420 ");
        assertThat(found.user().userId()).isEqualTo(other);
        assertThat(found.relation()).isEqualTo("none");

        when(store.pendingBetween(me, other)).thenReturn(Optional.of(new FriendStore.RequestRow(UUID.randomUUID(), other, me, "pending", "search", NOW)));
        assertThat(service.search(me, "청소왕#0420").relation()).isEqualTo("received");

        when(store.blockedEither(me, other)).thenReturn(true);
        assertThat(service.search(me, "청소왕#0420").user()).isNull();

        when(store.findByHandle("나", "0001")).thenReturn(Optional.of(meRow));
        assertThat(service.search(me, "나#0001").relation()).isEqualTo("self");

        when(store.findByHandle("없음", "9999")).thenReturn(Optional.empty());
        assertThat(service.search(me, "없음#9999").user()).isNull();
        verify(rateLimiter, org.mockito.Mockito.atLeastOnce()).check("friend-search", me.toString(), RateLimiter.FRIEND_SEARCH_PER_USER_PER_HOUR);
    }

    @Test
    void 초대_수락은_바로_친구_자기_초대와_차단은_막는다() {
        String code = "Abcdef2345";
        when(store.invite(code, NOW)).thenReturn(Optional.of(new FriendStore.InviteRow(code, other, NOW.plusSeconds(60))));
        UUID id = UUID.randomUUID();
        when(store.insertRequest(other, me, "accepted", "invite", NOW)).thenReturn(id);
        FriendRequestResult r = service.acceptInvite(me, null, code);
        assertThat(r.status()).isEqualTo("accepted");
        assertThat(r.id()).isEqualTo(id);
        verify(store).addFriendship(me, other, NOW);
        verify(outbox, never()).publish(any());

        assertCode(() -> service.acceptInvite(other, null, code), ErrorCode.VALIDATION); // 내 초대
        when(store.blockedEither(me, other)).thenReturn(true);
        assertCode(() -> service.previewInvite(me, code), ErrorCode.NOT_FOUND);
        assertCode(() -> service.acceptInvite(me, null, code), ErrorCode.NOT_FOUND);
        assertCode(() -> service.previewInvite(me, "short"), ErrorCode.NOT_FOUND);
    }

    @Test
    void 초대_코드는_7일_넘게_남은_것을_다시_쓰고_없으면_30일짜리를_만든다() {
        when(store.liveInviteOf(me, NOW.plus(FriendService.INVITE_REUSE_MIN)))
                .thenReturn(Optional.of(new FriendStore.InviteRow("Keep123456", me, NOW.plus(java.time.Duration.ofDays(20)))));
        assertThat(service.createInvite(me).url()).isEqualTo("https://api.test/i/Keep123456");
        verify(store, never()).insertInvite(any(), any(), any(), any());

        when(store.liveInviteOf(any(), any())).thenReturn(Optional.empty());
        var created = service.createInvite(me);
        assertThat(created.code()).matches("[A-Za-z0-9]{10}");
        assertThat(created.expiresAt()).isEqualTo(NOW.plus(FriendService.INVITE_TTL).toString());
        verify(store).insertInvite(created.code(), me, NOW, NOW.plus(FriendService.INVITE_TTL));
    }

    @Test
    void 초대_링크_주소가_설정되지_않으면_만들지_않는다() {
        service = new FriendService(store, profiles, outbox, idempotent, rateLimiter, new InviteProperties("", "", ""), new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
        assertCode(() -> service.createInvite(me), ErrorCode.INVALID_STATE);
    }

    @Test
    void 선물이_묻는_친구_여부는_차단이면_false() {
        when(store.areFriends(me, other)).thenReturn(true);
        assertThat(service.areFriends(me, other)).isTrue();
        when(store.blockedEither(me, other)).thenReturn(true);
        assertThat(service.areFriends(me, other)).isFalse();
    }

    @Test
    void 이름_마스킹() {
        assertThat(FriendService.maskName("김철수")).isEqualTo("김*수");
        assertThat(FriendService.maskName("김수")).isEqualTo("김*");
        assertThat(FriendService.maskName("남궁민수")).isEqualTo("남**수");
        assertThat(FriendService.maskName("김")).isEqualTo("*");
        assertThat(FriendService.maskName(null)).isEqualTo("*");
    }

    @Test
    void 초대_페이지는_닉네임을_이스케이프한다() {
        String html = InvitePage.render("Abcdef2345", "<script>x</script>", new InviteProperties("https://api.test", "https://play.google.com/store/apps/details?id=kr.ansimclean.customer", ""));
        assertThat(html).doesNotContain("<script>x").contains("&lt;script&gt;x");
        assertThat(html).contains("ansimclean://friend-invite/Abcdef2345");
        assertThat(html).contains("&amp;referrer=invite%3DAbcdef2345");
        assertThat(html).doesNotContain("iPhone"); // 주소가 없는 스토어 버튼은 그리지 않는다
    }
}
