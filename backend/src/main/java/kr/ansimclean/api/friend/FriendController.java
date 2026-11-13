package kr.ansimclean.api.friend;

import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.friend.dto.FriendBlocksResponse;
import kr.ansimclean.api.friend.dto.FriendListResponse;
import kr.ansimclean.api.friend.dto.FriendRequestResult;
import kr.ansimclean.api.friend.dto.FriendRequestsResponse;
import kr.ansimclean.api.friend.dto.FriendSearchResponse;
import kr.ansimclean.api.friend.dto.InvitePreviewResponse;
import kr.ansimclean.api.friend.dto.InviteResponse;
import kr.ansimclean.api.friend.dto.MyFriendProfileResponse;
import kr.ansimclean.api.friend.dto.NicknameRequest;
import kr.ansimclean.api.friend.dto.OkResponse;
import kr.ansimclean.api.friend.dto.UserIdRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 고객 친구 API (docs/api.md '고객 친구'). 가입을 끝낸 고객만(SecurityConfig /customer/**).
 * 모든 메서드가 토큰의 사용자 id 로만 FriendService 를 부른다 — 남의 요청 id 를 보내면 NOT_FOUND.
 */
@RestController
@RequestMapping(SecurityConfig.API + "/customer/friends")
public class FriendController {
    private final FriendService service;

    public FriendController(FriendService service) {
        this.service = service;
    }

    /** 내 닉네임#태그 (앱 getMyFriendProfile) */
    @GetMapping("/me")
    public MyFriendProfileResponse me(@AuthenticationPrincipal CurrentUser user) {
        return service.me(user.id());
    }

    /** 닉네임 정하기 (앱 setFriendNickname) — 태그는 서버가 붙인다 */
    @PutMapping("/me")
    public MyFriendProfileResponse setNickname(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody NicknameRequest req) {
        return service.setNickname(user.id(), req.nickname());
    }

    /** 닉네임#태그 검색 (앱 searchFriend) — 정확히 일치할 때만 */
    @GetMapping("/search")
    public FriendSearchResponse search(@AuthenticationPrincipal CurrentUser user, @RequestParam("q") String q) {
        return service.search(user.id(), q);
    }

    /** 친구 목록 (앱 getFriends) */
    @GetMapping
    public FriendListResponse friends(@AuthenticationPrincipal CurrentUser user) {
        return service.friends(user.id());
    }

    /** 친구 삭제 (앱 removeFriend) — 양쪽 해제, 알림 없음 */
    @DeleteMapping("/{userId}")
    public OkResponse unfriend(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID userId) {
        return service.unfriend(user.id(), userId);
    }

    /** 받은·보낸 대기 요청 (앱 getFriendRequests) */
    @GetMapping("/requests")
    public FriendRequestsResponse requests(@AuthenticationPrincipal CurrentUser user, @RequestParam("box") String box) {
        return service.requests(user.id(), box);
    }

    /** 친구 요청 보내기 (앱 sendFriendRequest). Idempotency-Key 선택 */
    @PostMapping("/requests")
    public FriendRequestResult send(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody UserIdRequest req,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.sendRequest(user.id(), key, req.userId());
    }

    @PostMapping("/requests/{id}/accept")
    public FriendRequestResult accept(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.accept(user.id(), id);
    }

    @PostMapping("/requests/{id}/reject")
    public FriendRequestResult reject(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.reject(user.id(), id);
    }

    @PostMapping("/requests/{id}/cancel")
    public FriendRequestResult cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.cancel(user.id(), id);
    }

    /** 내가 차단한 사람 (앱 getBlockedFriends) */
    @GetMapping("/blocks")
    public FriendBlocksResponse blocks(@AuthenticationPrincipal CurrentUser user) {
        return service.blocks(user.id());
    }

    /** 차단 (앱 blockUser) — 친구 해제 + 대기 요청 취소 */
    @PostMapping("/blocks")
    public OkResponse block(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody UserIdRequest req) {
        return service.block(user.id(), req.userId());
    }

    @DeleteMapping("/blocks/{userId}")
    public OkResponse unblock(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID userId) {
        return service.unblock(user.id(), userId);
    }

    /** 공유할 초대 링크 (앱 createFriendInvite) */
    @PostMapping("/invites")
    public InviteResponse createInvite(@AuthenticationPrincipal CurrentUser user) {
        return service.createInvite(user.id());
    }

    /** 초대 링크로 앱을 열었을 때 (앱 getFriendInvite) */
    @GetMapping("/invites/{code}")
    public InvitePreviewResponse preview(@AuthenticationPrincipal CurrentUser user, @PathVariable String code) {
        return service.previewInvite(user.id(), code);
    }

    /** 초대 수락 → 바로 친구 (앱 acceptFriendInvite). Idempotency-Key 선택 */
    @PostMapping("/invites/{code}/accept")
    public FriendRequestResult acceptInvite(@AuthenticationPrincipal CurrentUser user, @PathVariable String code,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.acceptInvite(user.id(), key, code);
    }
}
