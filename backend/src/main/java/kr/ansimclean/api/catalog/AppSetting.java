package kr.ansimclean.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 운영 설정 한 행 (키 → JSON 값). 읽는 곳은 AppSettingsService 하나이고, 서버 코드에는 값을 쓰는 곳이 없다.
// 처음 값은 Flyway 마이그레이션(예: V005__seed_settings.sql)으로 넣고, 운영 중에는 DB 에서 직접 update 한다
// (예: backend/docs/runbook.md 의 support_phone). 캐시가 없어 바꾼 값은 다음 요청부터 쓰인다.
/** app_settings — 운영 값 (수수료율·오퍼 만료·슬롯·문구 …). 코드에 상수로 두지 않는다. value 는 jsonb 원문 */
@Entity
@Table(name = "app_settings")
public class AppSetting {
    @Id
    @Column(name = "key", columnDefinition = "text")
    private String key;

    // jsonb 칸을 JSON 글자 그대로(String) 받는다. 숫자·문자열·객체 중 무엇인지는 키마다 달라서
    // 여기서 형을 정하지 않고, AppSettingsService 가 읽을 때 JsonNode 로 풀어 형에 맞게 꺼낸다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String value;

    /** JPA(Hibernate)가 DB 행으로 객체를 만들 때 쓰는 빈 생성자. 코드에서 직접 만들지 않는다 */
    protected AppSetting() {}

    public String getKey() { return key; }
    public String getValue() { return value; }
}
