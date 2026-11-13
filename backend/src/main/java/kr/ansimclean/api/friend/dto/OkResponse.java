package kr.ansimclean.api.friend.dto;

/** 친구 삭제·차단·차단 해제 — { ok: true } */
public record OkResponse(boolean ok) {
    public static final OkResponse OK = new OkResponse(true);
}
