package kr.ansimclean.api.offer.dto;

import java.util.List;
import java.util.UUID;

// 매니저 앱 콜 상세 화면의 응답 모양. OfferService.call 이 만든다. 필드 뜻은 CallItem 과 같고 customerRequest 만 더해진다.
// customerRequest = 고객 요청사항 줄들(Booking.requestLines)을 줄바꿈으로 이어 붙인 한 문자열
/** GET /manager/offers/{id} — CallItem + customerRequest */
public record CallDetailResponse(UUID id, String expiry, String expiresAt, String title, String location, String pay, String eta, String note, List<String> details, String customerRequest) {
    /** 목록 카드(CallItem)의 값을 그대로 옮기고 customerRequest 만 붙인다. CallItem 에 필드를 더하면 여기도 같이 더해야 한다 */
    public static CallDetailResponse of(CallItem c, String customerRequest) {
        return new CallDetailResponse(c.id(), c.expiry(), c.expiresAt(), c.title(), c.location(), c.pay(), c.eta(), c.note(), c.details(), customerRequest);
    }
}
