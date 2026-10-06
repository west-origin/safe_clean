package kr.ansimclean.api.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// notification_settings 테이블 저장소. ProfileService 만 쓴다 — 다른 기능(예: PushHandler)은 ProfileService 를 거쳐 읽는다.
// 키 타입이 NotificationSetting.Key(user_id + key 묶음)라서 findById·existsById 에 Key 객체를 넘긴다.
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, NotificationSetting.Key> {
    /** 이 사용자의 알림 설정 행 전부 (없는 키는 부른 쪽이 기본값으로 채운다) */
    List<NotificationSetting> findByUserId(UUID userId);

    /** 탈퇴 때 이 사용자의 설정 행을 모두 지운다 (ProfileService.anonymize). 결과 = 지운 행 수 */
    long deleteByUserId(UUID userId);
}
