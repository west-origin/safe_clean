package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.RepairPhotoResponse;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

// 수리 예약의 증상 사진 올리기 API: POST /api/v1/customer/repair-photos.
// 고객 앱의 수리 예약 상세 화면(RepairDetail)이 사진을 줄여서 api/customer.js 의 uploadRepairPhoto 로 올린다.
// 받은 { path, url } 중 path 는 나중에 예약 생성 본문(repair.photos)에 넣고, url 은 미리보기로 쓴다.
// 검사·저장은 전부 RepairPhotoService 에서 한다 — 이 클래스는 파일 바이트를 꺼내 넘기기만 한다.
/** docs/api.md §5.2 `uploadRepairPhoto(file)` — multipart 'file' */
@RestController
@RequestMapping(SecurityConfig.API + "/customer/repair-photos")
public class RepairPhotoController {
    private final RepairPhotoService service;

    public RepairPhotoController(RepairPhotoService service) {
        this.service = service;
    }

    /**
     * 사진 한 장을 받아 Storage 에 올리고 { path, url } 을 돌려준다.
     * user = 요청을 보낸 로그인 사용자(스프링 시큐리티가 인증 단계에서 채워 준다), file = multipart 본문의 'file' 칸.
     * 파일 크기 상한 5MB 는 application.yml 의 multipart 설정과 RepairPhotoService.MAX_BYTES 두 곳에 있다.
     * 5MB 를 넘는 파일은 이 메서드에 닿기 전에 스프링이 막고, GlobalExceptionHandler 가 같은 "5MB 이하" 문구의 400 으로 바꾼다.
     * 상한을 바꾸면 두 곳과 그 문구를 같이 바꾼다.
     */
    @PostMapping
    public RepairPhotoResponse upload(@AuthenticationPrincipal CurrentUser user, @RequestPart("file") MultipartFile file) {
        // 앱이 보낸 Content-Type 은 서비스가 '이미지 종류인가' 만 먼저 보고, 실제 형식은 파일 내용으로 다시 판정한다
        // 파일을 읽다 실패하면(IOException) 400 VALIDATION 문구로 바꿔 돌려준다
        try {
            return service.upload(user.id(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw ApiException.validation("사진 파일을 읽을 수 없어요");
        }
    }
}
