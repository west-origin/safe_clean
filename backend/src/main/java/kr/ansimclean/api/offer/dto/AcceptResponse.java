package kr.ansimclean.api.offer.dto;

import java.util.UUID;

// 콜 수락 성공 응답. OfferService.doAccept 가 수락한 예약의 id 를 담아 만든다.
// 작업(job)에 따로 id 가 있는 게 아니라 예약 id 를 그대로 작업 id 로 쓴다. 멱등 키로 다시 요청하면 저장해 둔 이 응답을 그대로 돌려준다.
/** POST /manager/offers/{id}/accept → { jobId } (= booking id). 앱은 /job/{jobId} 로 */
public record AcceptResponse(UUID jobId) {}
