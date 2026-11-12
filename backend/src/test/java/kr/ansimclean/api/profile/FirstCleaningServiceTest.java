package kr.ansimclean.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 첫 1회 청소를 다른 계정으로 다시 받으려는 사람이 주소를 살짝 바꿔 쓰는 경우 — 같은 집으로 보아야 막힌다 */
class FirstCleaningServiceTest {
    private static final String ROAD = "서울특별시 강남구 강남대로 396";

    @ParameterizedTest(name = "같은 집: {0} / {1}")
    @CsvSource(delimiter = '|', value = {
        "서울특별시 강남구 강남대로 396        | 101동 1001호",
        "서울 강남구 강남대로 396              | 101동 1001호",   // 시·도 줄임
        "서울시 강남구 강남대로396             | 101동 1001호",   // 띄어쓰기 없음
        "서울특별시 강남구 강남대로 396 (역삼동) | 101동 1001호",  // 괄호 동 이름
        "서울 강남대로 396                    | 101동 1001호",   // 구 생략
        "서울특별시 강남구 강남 대로 396        | 101동 1001호",   // 도로명 사이 공백 → 공백 제거로는 '강남대로' 와 같다
        "서울특별시 강남구 강남대로 0396       | 101동 1001호",   // 앞 0
        ROAD + "                             | 101동1001호",
        ROAD + "                             | 101 동 1001 호",
        ROAD + "                             | 101-1001",
        ROAD + "                             | 101도 1001오",    // 오타
        ROAD + "                             | 101dong 1001ho",
        ROAD + "                             | 1001호",          // 동 생략
        ROAD + "                             | 10층 101동 1001호", // 층 추가
        ROAD + "                             | 1001호 (101동)",  // 순서 바꿈
        ROAD + "                             | ''",              // 상세 생략
        ROAD + "                             | 0101동 01001호",
    })
    void sameHome(String road, String detail) {
        assertThat(FirstCleaningService.sameHome(ROAD, "101동 1001호", road, detail)).isTrue();
    }

    @ParameterizedTest(name = "다른 집: {0} / {1}")
    @CsvSource(delimiter = '|', value = {
        ROAD + "                        | 102동 1001호",  // 이웃 동
        ROAD + "                        | 101동 1002호",  // 이웃 호
        ROAD + "                        | 1011호",
        "서울특별시 강남구 강남대로 398   | 101동 1001호",  // 다른 건물
        "서울특별시 강남구 강남대로 396-1 | 101동 1001호",  // 부번이 다른 건물
        "서울특별시 서초구 강남대로 396   | 101동 1001호",  // 다른 구의 같은 도로명
        "부산광역시 강남대로 396         | 101동 1001호",  // 다른 시·도
        "서울특별시 강남구 테헤란로 396   | 101동 1001호",
    })
    void differentHome(String road, String detail) {
        assertThat(FirstCleaningService.sameHome(ROAD, "101동 1001호", road, detail)).isFalse();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "서울특별시 강남구 봉은사로 2길 10, 봉은사로2길10",
        "서울특별시 강남구 봉은사로2길 10, 봉은사로2길10",
        "경기도 성남시 분당구 중앙로 123번길 4-2, 중앙로123번길4-2",
        "서울특별시 종로구 종로 1, 종로1",
        "서울특별시 강남구 가로수길 10, 가로수길10",
    })
    void roadKey(String road, String key) {
        assertThat(FirstCleaningService.road(road).key()).isEqualTo(key);
    }
}
