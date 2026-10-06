package kr.ansimclean.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

// 체크리스트 한 항목 (예: 카테고리 home 의 "거실" 묶음 안 한 줄). CatalogService.checklistTemplate 이 카테고리별로 읽고
// JobService.createChecklist 가 배정·재배정 때 예약마다 복사한다. 작업은 복사본을 쓰므로 템플릿을 바꿔도
// 이미 복사된 목록은 바뀌지 않는다.
// 기본키가 (category_key, group_name, label) 세 칸이라 @IdClass 로 아래 Key 클래스를 지정한다.
/** checklist_templates — 작업 체크리스트 템플릿. 배정 시 job_checklist_items 로 복사된다 (§6.1 assign) */
@Entity
@Table(name = "checklist_templates")
@IdClass(ChecklistTemplate.Key.class)
public class ChecklistTemplate {
    @Id
    @Column(name = "category_key", columnDefinition = "text")
    private String categoryKey;

    @Id
    @Column(name = "group_name", columnDefinition = "text")
    private String groupName; // 묶음 이름 (예: '거실'). 매니저 앱 진행 중 작업 화면이 이 이름으로 항목을 묶어 보인다

    @Id
    @Column(columnDefinition = "text")
    private String label;

    @Column(nullable = false)
    private int sort; // 카테고리 안에서의 순서 (작은 값이 먼저)

    /** JPA 가 DB 행으로 객체를 만들 때 쓰는 빈 생성자 */
    protected ChecklistTemplate() {}

    public String getCategoryKey() { return categoryKey; }
    public String getGroupName() { return groupName; }
    public String getLabel() { return label; }
    public int getSort() { return sort; }

    /** 복합 기본키 (category_key, group_name, label). 필드 이름이 위 @Id 필드와 같아야 하고, equals·hashCode 는 세 칸 모두로 비교한다 */
    public static class Key implements Serializable {
        private String categoryKey;
        private String groupName;
        private String label;

        public Key() {} // JPA 가 쓰는 빈 생성자

        public Key(String categoryKey, String groupName, String label) {
            this.categoryKey = categoryKey;
            this.groupName = groupName;
            this.label = label;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(categoryKey, k.categoryKey) && Objects.equals(groupName, k.groupName) && Objects.equals(label, k.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categoryKey, groupName, label);
        }
    }
}
