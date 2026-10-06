package kr.ansimclean.api.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 옵션 검증 (§6.1): 같은 유료 옵션 key 를 두 번 보내도 한 번만 세어 금액이 두 번 합산되지 않는다 */
// CatalogService.requireOptions(견적·예약 생성 때 옵션 key 검증, BookingService 가 부른다) 단위 테스트와
// 확인 문구 응답(confirmCopy)의 정기청소 회차 수.
// 레포지토리·설정은 Mockito 목이고, 이 테스트가 쓰지 않는 레포지토리는 생성자를 채우려고 빈 목으로 넣는다.
class CatalogServiceTest {
    ServiceCategoryRepository categories = mock(ServiceCategoryRepository.class);
    CatalogOptionRepository options = mock(CatalogOptionRepository.class);
    CatalogFrequencyCopyRepository frequencyCopy = mock(CatalogFrequencyCopyRepository.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    CatalogService service = new CatalogService(categories, options, mock(CatalogNoticeChipRepository.class),
            frequencyCopy, mock(CatalogReasonRepository.class), mock(ChecklistTemplateRepository.class), settings);

    @Test
    void 같은_옵션_key_는_한_번만_고른_것으로_본다() {
        // 가정청소 옵션: 무료 dishes, 유료 fridge. 무료 옵션 한도는 2개
        CatalogOption paid = mock(CatalogOption.class);
        when(paid.getKey()).thenReturn("fridge");
        when(paid.isFree()).thenReturn(false);
        CatalogOption free = mock(CatalogOption.class);
        when(free.getKey()).thenReturn("dishes");
        when(free.isFree()).thenReturn(true);
        when(options.findByCategoryKeyOrderBySort("home")).thenReturn(List.of(free, paid));
        when(settings.integer(CatalogService.SETTING_FREE_OPTION_LIMIT)).thenReturn(2);

        // 무료 dishes 두 번, 유료 fridge 세 번을 보낸다
        List<CatalogOption> chosen = service.requireOptions("home", List.of("dishes", "dishes"), List.of("fridge", "fridge", "fridge"));

        // containsExactly: 정확히 이 원소들만, 이 순서로 — 무료 먼저, 유료 다음, 각각 한 번씩
        assertThat(chosen).containsExactly(free, paid);
    }

    // 확인 문구 응답(GET …/confirm-copy)에 정기청소 회차 수(= 개월 수, app_settings.series_term_months)를 싣는다 —
    // 주기 화면의 '6개월 동안 6회' 안내가 앱 고정 글자라, 운영자가 회차 수를 바꿔도 6회로 안내했다 (문제 목록 25번)
    @Test
    void 확인_문구_응답은_설정된_정기청소_회차_수를_싣는다() {
        when(categories.findById("home")).thenReturn(Optional.of(mock(ServiceCategory.class)));
        when(frequencyCopy.findByCategoryKey("home")).thenReturn(List.of());
        when(settings.integer(CatalogService.SETTING_SERIES_TERM_MONTHS)).thenReturn(3);
        assertThat(service.confirmCopy("home").seriesTermMonths()).isEqualTo(3);
    }
}
