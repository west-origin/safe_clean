package kr.ansimclean.api.auth.dto;

/**
 * 돌려줄 데이터가 없는 인증 API 의 응답 { "ok": true } — AuthController 의 /logout·DELETE /account 가 쓴다.
 * push·live 패키지의 dto 에도 같은 이름·같은 모양의 record 가 따로 있다.
 */
public record OkResponse(boolean ok) {
    // 매번 새로 만들지 않고 이 하나를 돌려준다 (값이 바뀌지 않으므로 공유해도 된다)
    public static final OkResponse OK = new OkResponse(true);
}
