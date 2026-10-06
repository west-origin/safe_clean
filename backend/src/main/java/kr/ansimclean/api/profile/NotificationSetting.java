package kr.ansimclean.api.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// 알림 토글 한 칸 = 한 행 (사용자 id + 알림 키). 역할별 키 목록은 ProfileService 에 있다 (매니저: MANAGER_NOTIFICATION_KEYS).
// 행은 가입 마지막 단계(ProfileService.completeSignup)에서 기본값으로 만들고, 설정 화면의 토글(updateNotification)이 바꾼다.
// 행이 없으면 ProfileService.notifications 가 기본값(marketing 만 꺼짐)으로 채운다.
// PushHandler 는 설정을 따르는 푸시를 보내기 전에 ProfileService.notifications 로 이 값을 본다
// (일부 거래성 푸시는 설정과 무관하게 보낸다 — 예: 고객의 매니저 미배정 취소(BookingUnassigned), 매니저의 미방문 취소 안내.
// 문의 답변(InquiryAnswered)은 고객·매니저 모두 설정과 무관하다).
// 탈퇴 때 ProfileService.anonymize 가 이 사용자의 행을 모두 지운다.
/** notification_settings — 고객: push·marketing / 매니저: push·call·settlement·marketing */
@Entity
@Table(name = "notification_settings")
// @IdClass = 기본 키가 칸 두 개(user_id + key)라서, 두 값을 묶은 Key 클래스를 키로 쓴다
@IdClass(NotificationSetting.Key.class)
public class NotificationSetting {
    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Id
    @Column(name = "key", columnDefinition = "text")
    private String key; // 알림 종류 (예: push, marketing, call, settlement)

    @Column(nullable = false)
    private boolean enabled;

    /** JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자. 코드에서는 of 를 쓴다 */
    protected NotificationSetting() {}

    /** 새 설정 행을 만든다 (저장은 부른 쪽이 한다) */
    public static NotificationSetting of(UUID userId, String key, boolean enabled) {
        NotificationSetting n = new NotificationSetting();
        n.userId = userId;
        n.key = key;
        n.enabled = enabled;
        return n;
    }

    public UUID getUserId() { return userId; }
    public String getKey() { return key; }
    public boolean isEnabled() { return enabled; }

    /** 토글을 켜고 끈다 (ProfileService.updateNotification) */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 기본 키 묶음 (user_id + key). findById·existsById 에 이 객체를 넘긴다.
     * JPA 규칙상 필드 이름이 엔티티의 @Id 필드와 같아야 하고, 빈 생성자·equals·hashCode 가 있어야 한다.
     */
    public static class Key implements Serializable {
        private UUID userId;
        private String key;

        /** JPA 가 쓰는 빈 생성자 */
        public Key() {}

        /** 코드에서 키를 만들 때 */
        public Key(UUID userId, String key) {
            this.userId = userId;
            this.key = key;
        }

        // 두 키는 userId 와 key 가 모두 같으면 같은 키다 (JPA 가 같은 행인지 판단할 때 쓴다)
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(key, k.key);
        }

        // equals 와 짝을 맞춘 해시값 (같은 키면 같은 값)
        @Override
        public int hashCode() {
            return Objects.hash(userId, key);
        }
    }
}
