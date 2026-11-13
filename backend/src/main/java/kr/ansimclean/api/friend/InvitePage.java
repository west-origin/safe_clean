package kr.ansimclean.api.friend;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 친구 초대 공개 페이지 HTML (InvitePageController). 스크립트 없이 링크 두 개뿐이다.
 * 닉네임은 사용자가 정한 값이라 HTML 이스케이프한다. 문구·모양은 Figma 에 없어 코드에서 정함 (PROGRESS 판단 항목)
 */
final class InvitePage {
    private InvitePage() {}

    static String render(String code, String nickname, InviteProperties props) {
        String appLink = "ansimclean://friend-invite/" + code;
        StringBuilder buttons = new StringBuilder();
        buttons.append(button(appLink, "앱에서 열기", true));
        if (!props.androidStoreUrl().isEmpty()) {
            String sep = props.androidStoreUrl().contains("?") ? "&" : "?";
            String url = props.androidStoreUrl() + sep + "referrer=" + URLEncoder.encode("invite=" + code, StandardCharsets.UTF_8);
            buttons.append(button(url, "Android 앱 설치하기", false));
        }
        if (!props.iosStoreUrl().isEmpty()) buttons.append(button(props.iosStoreUrl(), "iPhone 앱 설치하기", false));
        String title = escape(nickname) + "님이 안심클린 친구로 초대했어요";
        return page(title, title, "앱을 설치하고 가입하면 초대 코드 <b>" + code + "</b> 로 친구 요청을 이어서 받을 수 있어요.", buttons.toString());
    }

    static String expired() {
        return page("안심클린 친구 초대", "초대 링크가 만료됐어요", "친구에게 새 초대 링크를 받아주세요.", "");
    }

    private static String button(String href, String label, boolean primary) {
        return "<a class=\"" + (primary ? "btn primary" : "btn") + "\" href=\"" + escape(href) + "\">" + label + "</a>";
    }

    private static String page(String ogTitle, String heading, String body, String buttons) {
        // 색은 고객 앱 브랜드 파랑과 비슷한 값 — 서버 페이지라 디자인 토큰을 쓸 수 없다
        return """
                <!doctype html><html lang="ko"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta property="og:title" content="%s"><meta property="og:description" content="안심클린 앱에서 친구 요청을 확인해보세요">
                <title>안심클린 친구 초대</title>
                <style>body{font-family:system-ui,sans-serif;margin:0;padding:48px 24px;text-align:center;color:#1b1d1f}
                h1{font-size:20px;margin:0 0 12px}p{font-size:15px;color:#555;line-height:1.5}
                .btn{display:block;max-width:320px;margin:12px auto;padding:14px;border-radius:12px;border:1px solid #2f6bff;color:#2f6bff;text-decoration:none;font-weight:600}
                .primary{background:#2f6bff;color:#fff}</style></head>
                <body><h1>%s</h1><p>%s</p>%s</body></html>
                """.formatted(ogTitle, heading, body, buttons);
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
