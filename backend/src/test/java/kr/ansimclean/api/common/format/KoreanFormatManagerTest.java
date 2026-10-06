package kr.ansimclean.api.common.format;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** B4 에서 추가한 매니저 화면 문자열 */
// KoreanFormat 의 매니저 앱용 표시 문자열(날짜·시각 요약, 주소 줄이기, 연월)을 입력 → 기대 문자열로 확인한다.
class KoreanFormatManagerTest {
    // dateTimeShort: 날짜 + 12시간제 시각. addressHead: 앞 3어절까지(건물번호·'지하' 앞에서 멈춤, 콜 카드의 대략 위치).
    // addressArea: 시·도를 뺀 2어절 (null 이면 빈 글자)
    @Test
    void 콜_요약과_주소_어절() {
        assertThat(KoreanFormat.dateTimeShort(LocalDate.of(2026, 9, 18), LocalTime.of(14, 0))).isEqualTo("9월 18일 오후 2:00");
        assertThat(KoreanFormat.dateTimeShort(LocalDate.of(2026, 9, 19), LocalTime.of(10, 30))).isEqualTo("9월 19일 오전 10:30");
        assertThat(KoreanFormat.addressHead("서울특별시 강남구 테헤란로 123", 3)).isEqualTo("서울특별시 강남구 테헤란로");
        assertThat(KoreanFormat.addressHead("서울", 3)).isEqualTo("서울");
        assertThat(KoreanFormat.addressHead("세종특별자치시 도움6로 11", 3)).isEqualTo("세종특별자치시 도움6로"); // 건물번호 앞에서 멈춘다 (자체 점검 #27)
        assertThat(KoreanFormat.addressHead("세종특별자치시 조치원읍 지하 12", 3)).isEqualTo("세종특별자치시 조치원읍");
        assertThat(KoreanFormat.addressArea("부산광역시 해운대구 우동 123-4")).isEqualTo("해운대구 우동");
        assertThat(KoreanFormat.addressArea("서울")).isEqualTo("서울");
        assertThat(KoreanFormat.addressArea(null)).isEqualTo("");
    }

    // yearMonth 는 '2024년 3월'(예: 매니저 프로필 '활동 시작'), yearMonthDot 은 월 두 자리 '2026.03'(예: 범죄경력 조회 날짜)
    @Test
    void 연월() {
        assertThat(KoreanFormat.yearMonth(LocalDate.of(2024, 3, 1))).isEqualTo("2024년 3월");
        assertThat(KoreanFormat.yearMonthDot(LocalDate.of(2026, 3, 5))).isEqualTo("2026.03");
    }
}
