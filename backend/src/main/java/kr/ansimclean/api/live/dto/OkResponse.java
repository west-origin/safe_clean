package kr.ansimclean.api.live.dto;

// 돌려줄 데이터가 없는 요청의 성공 응답 { "ok": true }. POST /live/sessions/{id}/stop 이 쓴다 (LiveService.stop).
// auth·push 패키지에는 같은 이름의 별도 OkResponse 가 따로 있다
public record OkResponse(boolean ok) {}
