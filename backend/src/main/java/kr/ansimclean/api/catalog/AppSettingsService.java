package kr.ansimclean.api.catalog;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * app_settings 읽기. 값은 jsonb 라 형에 맞게 꺼낸다. 없는 키는 설정 실수이므로 INTERNAL 로 드러낸다(조용히 기본값을 쓰지 않는다).
 * 여러 기능이 운영 값과 화면 문구를 이 클래스로 읽는다 (예: PricingService 의 수수료율, BookingService 의 안내 문구).
 * 별도 캐시가 없어 부를 때마다 DB 에서 읽는다 — DB 값을 바꾸면 다음 요청부터 반영된다.
 * 키가 없어 던지는 INTERNAL 은 부른 쪽의 쓰기 묶음(예: 예약 생성)까지 되돌린다 — 이 클래스의 readOnly 묶음이
 * 바깥 묶음에 합쳐지기 때문이다 (CODE_GUIDE.md 3-3). 부른 쪽이 예외를 잡아도 그 묶음은 커밋되지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class AppSettingsService {
    private static final Logger log = LoggerFactory.getLogger(AppSettingsService.class);
    private final AppSettingRepository repository;
    private final JsonMapper json = JsonMapper.builder().build(); // JSON 글자 ↔ JsonNode·자바 값 변환기 (Jackson)

    public AppSettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    /**
     * 키의 값을 JSON 트리(JsonNode)로 준다. 객체·배열 값(화면 문구 묶음, 시간대 목록 …)은 이걸로 읽는다.
     * 키가 없으면 로그를 남기고 ApiException(INTERNAL) 을 던진다.
     */
    public JsonNode node(String key) {
        AppSetting setting = repository.findById(key).orElseThrow(() -> {
            log.error("app_settings 에 없는 키: {}", key); // 키 이름은 로그에만 — INTERNAL 응답은 항상 일반 문구(§5.0)
            return new ApiException(ErrorCode.INTERNAL);
        });
        return json.readTree(setting.getValue());
    }

    /** 키의 값을 지정한 자바 형으로 바꿔 준다. 예: get("support_phone", String.class) → JSON 문자열의 글자 */
    public <T> T get(String key, Class<T> type) {
        return json.treeToValue(node(key), type);
    }

    /** 정수 값 (예: free_option_limit, cancel_deadline_hours) */
    public int integer(String key) {
        return node(key).asInt();
    }

    /** 소수 값 (예: platform_fee_rate 0.1 — PricingService 가 수수료 계산에 쓴다) */
    public double decimal(String key) {
        return node(key).asDouble();
    }
}
