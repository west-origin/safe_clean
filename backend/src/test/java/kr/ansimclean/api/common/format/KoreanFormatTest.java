package kr.ansimclean.api.common.format;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 예시 값은 BACKEND_PLAN.md §6.7 과 앱 mock.js 의 문자열 그대로다. 하나라도 다르면 화면이 달라진다. */
// KoreanFormat(서버가 화면에 보낼 한국어 표시 문자열을 만드는 곳)의 함수마다 입력 → 기대 문자열을 확인한다.
// 시각(Instant)을 받는 함수는 서울 시간으로 바꿔 표시하므로 기대값이 UTC 보다 9시간 뒤다.
class KoreanFormatTest {
    // 2026-09-15 는 화요일, 2026-11-01 은 일요일, 2026-09-18 은 금요일
    @Test
    void 날짜() {
        assertThat(KoreanFormat.dateLabel(LocalDate.of(2026, 9, 15))).isEqualTo("9월 15일 (화)");
        assertThat(KoreanFormat.dateShort(LocalDate.of(2026, 9, 14))).isEqualTo("9월 14일");
        assertThat(KoreanFormat.dateLong(LocalDate.of(2026, 9, 15))).isEqualTo("2026년 9월 15일");
        assertThat(KoreanFormat.dateTimeLong(LocalDate.of(2026, 11, 1), LocalTime.of(9, 0))).isEqualTo("2026년 11월 1일 (일) 오전 9시");
        assertThat(KoreanFormat.dateTimeLong(LocalDate.of(2026, 11, 14), LocalTime.of(14, 0))).isEqualTo("2026년 11월 14일 (토) 오후 2시");
        assertThat(KoreanFormat.dateTimeCall(LocalDate.of(2026, 9, 18), LocalTime.of(14, 0))).isEqualTo("9월 18일 금요일 오후 2:00");
        assertThat(KoreanFormat.dateTimeCall(LocalDate.of(2026, 9, 19), LocalTime.of(10, 0))).isEqualTo("9월 19일 토요일 오전 10:00");
    }

    // 12시간제 경계(0시 → 오전 12시, 12시 → 오후 12시), 음수 경과 시간은 00:00:00, 딱 떨어지는 시간은 '분' 을 뺀다
    @Test
    void 시각() {
        assertThat(KoreanFormat.timeKo(LocalTime.of(14, 0))).isEqualTo("오후 2시");
        assertThat(KoreanFormat.timeKo(LocalTime.of(14, 30))).isEqualTo("오후 2시 30분");
        assertThat(KoreanFormat.timeKo(LocalTime.of(0, 0))).isEqualTo("오전 12시");
        assertThat(KoreanFormat.timeKo(LocalTime.of(12, 0))).isEqualTo("오후 12시");
        assertThat(KoreanFormat.timeRange(LocalTime.of(9, 0), LocalTime.of(18, 0))).isEqualTo("09:00 - 18:00");
        assertThat(KoreanFormat.hms(Duration.ofSeconds(3600 + 23 * 60 + 42))).isEqualTo("01:23:42");
        assertThat(KoreanFormat.hms(Duration.ofSeconds(-5))).isEqualTo("00:00:00");
        assertThat(KoreanFormat.durationKo(Duration.ofMinutes(3 * 60 + 23))).isEqualTo("3시간 23분");
        assertThat(KoreanFormat.durationKo(Duration.ofMinutes(120))).isEqualTo("2시간");
        assertThat(KoreanFormat.durationKo(Duration.ofMinutes(45))).isEqualTo("45분");
    }

    // 천 단위 쉼표 + '원'. wonSigned 는 양수에 +, 음수에 - 를 붙인다
    @Test
    void 금액() {
        assertThat(KoreanFormat.won(85000)).isEqualTo("85,000원");
        assertThat(KoreanFormat.won(1240000)).isEqualTo("1,240,000원");
        assertThat(KoreanFormat.won(0)).isEqualTo("0원");
        assertThat(KoreanFormat.wonSigned(3000)).isEqualTo("+3,000원");
        assertThat(KoreanFormat.wonSigned(-15000)).isEqualTo("-15,000원");
    }

    // untilExpiry: 남은 시간을 '분:초 후 만료', 지났으면 '만료됨'. 나머지는 UTC 시각을 서울 시각으로 바꿔 표시한다
    @Test
    void 만료와_시각_라벨() {
        Instant now = Instant.parse("2026-09-15T05:00:00Z"); // 서울 14:00
        assertThat(KoreanFormat.untilExpiry(now.plusSeconds(4 * 60 + 32), now)).isEqualTo("4:32 후 만료");
        assertThat(KoreanFormat.untilExpiry(now.plusSeconds(65), now)).isEqualTo("1:05 후 만료");
        assertThat(KoreanFormat.untilExpiry(now.minusSeconds(1), now)).isEqualTo("만료됨");
        assertThat(KoreanFormat.sinceLabel(Instant.parse("2026-09-15T05:32:00Z"), "부터 중단됨")).isEqualTo("14:32부터 중단됨");
        assertThat(KoreanFormat.clockRange(Instant.parse("2026-09-15T05:02:00Z"), Instant.parse("2026-09-15T08:25:00Z"))).isEqualTo("14:02 - 17:25");
        assertThat(KoreanFormat.noticeDate(Instant.parse("2026-09-09T15:00:00Z"))).isEqualTo("2026.09.10"); // 서울 자정
        assertThat(KoreanFormat.liveStartedAt(Instant.parse("2025-09-11T05:32:00Z"))).isEqualTo("2025. 09. 11. 14:32");
    }

    // 앱 입력(010 뺀 8자리, 또는 하이픈 섞인 11자리) ↔ 국제 형식(+8210…) 변환, 화면 표시, 로그용 가림.
    // 형식이 맞지 않으면(7자리, 숫자 없음) toE164 가 IllegalArgumentException 을 던진다. 번호는 모두 예시 값이다
    @Test
    void 휴대폰_번호() {
        assertThat(KoreanFormat.toE164("12345678")).isEqualTo("+821012345678");
        assertThat(KoreanFormat.toE164("010-1234-5678")).isEqualTo("+821012345678");
        assertThat(KoreanFormat.phoneDisplay("+821012345678")).isEqualTo("010-1234-5678");
        assertThat(KoreanFormat.phoneLocal("+821012345678")).isEqualTo("12345678");
        assertThat(KoreanFormat.maskPhone("+821012345678")).isEqualTo("+8210****5678");
        assertThat(KoreanFormat.isE164KrMobile("+821012345678")).isTrue();
        assertThat(KoreanFormat.isE164KrMobile("+821112345678")).isFalse();
        assertThatThrownBy(() -> KoreanFormat.toE164("1234567")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KoreanFormat.toE164("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    // 매니저에게 보이는 고객 이름: 첫 글자만 남기고 ○ 로 가린다. 한 글자 이름도 ○ 하나를 붙인다
    @Test
    void 이름_마스킹() {
        assertThat(KoreanFormat.maskName("김하늘")).isEqualTo("김○○");
        assertThat(KoreanFormat.maskName("이")).isEqualTo("이○");
        assertThat(KoreanFormat.maskName("")).isEqualTo("");
    }
}
