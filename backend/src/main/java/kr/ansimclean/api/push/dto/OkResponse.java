package kr.ansimclean.api.push.dto;

// 성공만 알리는 응답 본문 { "ok": true } — 필드 이름(ok)이 그대로 JSON 키가 된다. PushController 의 토큰 등록·해제가 돌려준다.
// auth·live 패키지에도 같은 이름의 OkResponse 가 따로 있다.
public record OkResponse(boolean ok) {
    public static final OkResponse OK = new OkResponse(true); // 매번 새로 만들지 않고 이 하나를 돌려준다
}
