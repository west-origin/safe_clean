package kr.ansimclean.api.profile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AddressRegion(도로명주소 → 시·도, 시·군·구) 단위 테스트. 스프링·DB 없이 정적 메서드만 부른다.
 * 확인하는 것: 정식 이름 주소 파싱, 축약·옛 이름을 정식 이름으로 맞추기, '광주시' 판별,
 * 저장 전 검사(requireMatchable)가 시·도 없는 주소를 막는지.
 * 시·도를 못 읽으면 매칭 후보가 0명이 돼 예약이 어느 매니저에게도 가지 않으므로 이 결과가 중요하다.
 * assertThat(a).isEqualTo(b) = AssertJ 검증. record 는 필드 값이 같으면 같은 것으로 본다.
 */
class AddressRegionTest {
    // @Test = JUnit 이 이 메서드를 테스트 하나로 실행한다.
    // 정식 시·도 이름으로 시작하는 주소 세 가지. '성남시 분당구' 처럼 두 단계면 앞의 '성남시' 만 잡힌다
    @Test
    void 시도와_시군구를_뽑는다() {
        assertThat(AddressRegion.parse("서울특별시 강남구 테헤란로 123")).isEqualTo(new AddressRegion("서울특별시", "강남구"));
        assertThat(AddressRegion.parse("경기도 성남시 분당구 판교로 1")).isEqualTo(new AddressRegion("경기도", "성남시"));
        assertThat(AddressRegion.parse("부산광역시 해운대구 우동 1")).isEqualTo(new AddressRegion("부산광역시", "해운대구"));
    }

    // 이름은 '구가 없는 주소' 지만 아래에 축약형·'광주시' 판별·시도 없는 주소·null 입력까지 함께 확인한다.
    // parse 는 못 읽은 쪽을 null 로 두고 예외를 던지지 않는다
    @Test
    void 구가_없는_주소는_시군구가_null_이다() {
        assertThat(AddressRegion.parse("부산광역시 중앙대로 2127-23")).isEqualTo(new AddressRegion("부산광역시", null));
        // 흔한 축약·옛 이름은 정식 이름으로 (안 맞추면 후보 0명으로 영영 매칭되지 않는다)
        assertThat(AddressRegion.parse("서울 강남구 테헤란로 123")).isEqualTo(new AddressRegion("서울특별시", "강남구"));
        assertThat(AddressRegion.parse("서울시 강남구 테헤란로 123")).isEqualTo(new AddressRegion("서울특별시", "강남구"));
        assertThat(AddressRegion.parse("경기 성남시 분당구 판교로 1")).isEqualTo(new AddressRegion("경기도", "성남시"));
        assertThat(AddressRegion.parse("강원도 춘천시 중앙로 1")).isEqualTo(new AddressRegion("강원특별자치도", "춘천시"));
        assertThat(AddressRegion.canonicalSido("서울")).isEqualTo("서울특별시");
        // '광주시' 는 경기도 광주시의 정식 이름이기도 하다 — 다음 토큰으로 판별 (무조건 광역시로 보면 경기 광주 예약이 광주광역시 매니저에게 간다)
        assertThat(AddressRegion.parse("광주 북구 용봉로 77")).isEqualTo(new AddressRegion("광주광역시", "북구"));
        assertThat(AddressRegion.parse("광주시 북구 용봉로 77")).isEqualTo(new AddressRegion("광주광역시", "북구"));
        assertThat(AddressRegion.parse("광주시 오포읍 양벌로 10")).isEqualTo(new AddressRegion("경기도", "광주시"));
        assertThat(AddressRegion.parse("경기도 광주시 오포읍 양벌로 10")).isEqualTo(new AddressRegion("경기도", "광주시"));
        assertThat(AddressRegion.parse("광주광역시 광산구 무진대로 1")).isEqualTo(new AddressRegion("광주광역시", "광산구"));
        assertThat(AddressRegion.parse("광주시 양벌로 10")).isEqualTo(new AddressRegion(null, "광주시")); // 판별 불가 → sido 없음 → 후보 0명 로그
        assertThat(AddressRegion.parse("테헤란로 123")).isEqualTo(new AddressRegion(null, null));
        assertThat(AddressRegion.parse(null)).isEqualTo(new AddressRegion(null, null));
    }

    // requireMatchable = 주소 저장·예약 생성 전에 부르는 검사. 시·도를 못 읽으면 MISSING_SIDO 문구로 예외(VALIDATION)를 던진다.
    // assertThatThrownBy(() -> …).hasMessage(…) = 괄호 안 코드가 예외를 던지고 그 메시지가 같은지 확인한다.
    // '강남구 테헤란로 123' 처럼 구만 있고 시·도가 없어도 막힌다. 시·군·구가 없는 '부산광역시 중앙대로' 는 통과한다
    @Test
    void 저장_전_검사는_시도가_없으면_막고_시군구는_없어도_된다() { // 직접 사용 점검 U1
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AddressRegion.requireMatchable("강남대로 396")).hasMessage(AddressRegion.MISSING_SIDO);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AddressRegion.requireMatchable("강남구 테헤란로 123")).hasMessage(AddressRegion.MISSING_SIDO);
        assertThat(AddressRegion.requireMatchable("서울 강남구 강남대로 396")).isEqualTo(new AddressRegion("서울특별시", "강남구"));
        assertThat(AddressRegion.requireMatchable("부산광역시 중앙대로 2127-23")).isEqualTo(new AddressRegion("부산광역시", null));
    }
}
