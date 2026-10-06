package kr.ansimclean.api.offer;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 콜 만료 스케줄러(30초): 만료 처리 뒤 정지·미승인·탈퇴 매니저의 대기 콜을 닫는다. 한 단계가 실패해도 던지지 않고 다음 단계는 돈다 (문제 목록 6번) */
// OfferService 는 가짜라 DB 없이 돈다. 순서와 '앞 단계의 예외가 뒤 단계를 막지 않는가' 만 본다
class OfferExpirerTest {
    OfferService offers = mock(OfferService.class);

    @Test
    void 만료_처리_뒤_정지_매니저_콜을_닫고_앞_단계가_실패해도_뒤_단계는_돈다() {
        when(offers.expireDue()).thenThrow(new IllegalStateException("db down"));
        new OfferExpirer(offers).run();
        InOrder order = inOrder(offers);
        order.verify(offers).expireDue();
        order.verify(offers).closeBlockedOffers();
    }
}
