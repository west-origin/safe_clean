package kr.ansimclean.api.profile.dto;

import java.util.UUID;

/** DELETE 응답 { id } */
// 지금은 주소 삭제(DELETE /customer/addresses/{id})가 지운 주소 id 를 돌려줄 때 쓴다
public record IdResponse(UUID id) {}
