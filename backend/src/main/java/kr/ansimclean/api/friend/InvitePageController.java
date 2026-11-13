package kr.ansimclean.api.friend;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * GET /i/{code} — 카카오톡 등으로 공유한 친구 초대 링크가 여는 공개 페이지(로그인 없음, SecurityConfig permitAll).
 * 앱이 깔려 있으면 '앱에서 열기' 가 ansimclean://friend-invite/{code} 로 앱을 연다. 없으면 스토어로 — Android 는 설치 리퍼러에
 * invite={code} 를 실어 설치 뒤 앱이 같은 코드로 이어간다(디퍼드 딥링크). 문구는 Figma 에 없어 코드에서 정함 (InvitePage)
 */
@RestController
public class InvitePageController {
    private final FriendService service;
    private final InviteProperties props;

    public InvitePageController(FriendService service, InviteProperties props) {
        this.service = service;
        this.props = props;
    }

    @GetMapping(value = "/i/{code}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable String code) {
        Optional<String> nickname = service.inviterNickname(code);
        String html = nickname.map(n -> InvitePage.render(code, n, props)).orElseGet(InvitePage::expired);
        return ResponseEntity.status(nickname.isPresent() ? HttpStatus.OK : HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .body(html);
    }
}
