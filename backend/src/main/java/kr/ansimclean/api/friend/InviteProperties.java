package kr.ansimclean.api.friend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 친구 초대 링크 설정 (application.yml app.invite.*). baseUrl 은 초대 페이지를 여는 서버 주소(예: https://api.example.com) —
 * 비면 초대 링크를 만들 수 없다(INVALID_STATE). 스토어 주소가 비면 초대 페이지에 설치 버튼을 그리지 않는다.
 * androidStoreUrl 에는 Play 스토어 주소를 넣는다 — 페이지가 &referrer=invite%3D{code} 를 붙여 설치 뒤 앱이 코드를 이어받는다(설치 리퍼러)
 */
@Component
public record InviteProperties(String baseUrl, String androidStoreUrl, String iosStoreUrl) {
    public InviteProperties(@Value("${app.invite.base-url:}") String baseUrl,
                            @Value("${app.invite.android-store-url:}") String androidStoreUrl,
                            @Value("${app.invite.ios-store-url:}") String iosStoreUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.androidStoreUrl = androidStoreUrl == null ? "" : androidStoreUrl;
        this.iosStoreUrl = iosStoreUrl == null ? "" : iosStoreUrl;
    }
}
