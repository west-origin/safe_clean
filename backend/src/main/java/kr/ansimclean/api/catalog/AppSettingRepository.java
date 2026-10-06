package kr.ansimclean.api.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * app_settings 테이블 접근 (기본키 = 설정 키 글자). AppSettingsService 가 findById(키) 로만 읽는다.
 * 다른 기능은 이 저장소를 직접 쓰지 않고 AppSettingsService 를 부른다.
 */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {}
