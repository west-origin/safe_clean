package kr.ansimclean.api.customer;

import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.catalog.CatalogService;
import kr.ansimclean.api.customer.dto.HomeResponse;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 고객 홈 응답 조합 (docs/api.md §5.2 GET /customer/home) */
// CustomerScreenService 단위 테스트 (CODE_GUIDE.md 3-12). 협력자(설정·예약·카탈로그)는 모두 Mockito 가짜다.
class CustomerScreenServiceTest {
    AppSettingsService settings = mock(AppSettingsService.class);
    BookingService bookings = mock(BookingService.class);
    CatalogService catalog = mock(CatalogService.class);
    CustomerScreenService service = new CustomerScreenService(settings, mock(ProfileService.class), bookings, catalog);

    // 운영자가 변경·취소 기한을 48시간으로 바꿨다 — 홈 응답이 그 값을 싣는다. 홈 '다가오는 방문' 카드가 잠겼을 때
    // '방문 N시간 전이 지나…' 안내가 이 값을 쓴다(예전에는 앱 고정 24) (문제 목록 25번). 다가오는 방문이 없으면 upcoming 은 null
    @Test
    void 홈_응답은_설정된_변경_취소_기한_시간을_싣는다() {
        UUID customer = UUID.randomUUID();
        when(bookings.nextVisit(customer)).thenReturn(Optional.empty());
        when(catalog.cancelDeadlineHours()).thenReturn(48);
        HomeResponse res = service.home(customer);
        assertThat(res.changeDeadlineHours()).isEqualTo(48);
        assertThat(res.upcoming()).isNull();
    }
}
