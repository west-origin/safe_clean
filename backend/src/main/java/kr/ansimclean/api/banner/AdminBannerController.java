package kr.ansimclean.api.banner;

import kr.ansimclean.api.banner.dto.AdminBannerResponse;
import kr.ansimclean.api.banner.dto.AdminBannerTextRequest;
import kr.ansimclean.api.banner.dto.BannerDeleted;
import kr.ansimclean.api.banner.dto.BannerOrderRequest;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * /admin/banners — 고객 앱 홈 배너 관리 (매니저 앱 관리자 화면, rbac.md §15-22). 실제 경로 /api/v1/admin/banners.
 * SecurityConfig 가 /admin/** 에 admin + MFA 를 요구하고, AdminBannerService 가 AccessPolicy 로 한 번 더 본다.
 * 응답 모양은 backend/docs/api.md "어드민 배너".
 */
@RestController
@RequestMapping(SecurityConfig.API + "/admin/banners")
public class AdminBannerController {
    private final AdminBannerService service;

    public AdminBannerController(AdminBannerService service) {
        this.service = service;
    }

    /** GET /admin/banners → 모든 배너(순서대로) */
    @GetMapping
    public List<AdminBannerResponse> list(@AuthenticationPrincipal CurrentUser actor) {
        return service.list(actor);
    }

    /** GET /admin/banners/{id} → 배너 한 장 */
    @GetMapping("/{id}")
    public AdminBannerResponse get(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id) {
        return service.get(actor, id);
    }

    /** POST /admin/banners (multipart: file + title·description·startsOn·endsOn) → 새 배너(맨 뒤) */
    @PostMapping
    public AdminBannerResponse create(@AuthenticationPrincipal CurrentUser actor, @RequestPart(value = "file", required = false) MultipartFile file,
                                      @RequestParam(required = false) String title, @RequestParam(required = false) String description,
                                      @RequestParam(required = false) String startsOn, @RequestParam(required = false) String endsOn) {
        return service.create(actor, file == null ? null : file.getContentType(), bytes(file), new AdminBannerTextRequest(title, description, startsOn, endsOn));
    }

    /** PUT /admin/banners/{id} { title, description, startsOn, endsOn } → 고친 배너 */
    @PutMapping("/{id}")
    public AdminBannerResponse update(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id, @RequestBody AdminBannerTextRequest req) {
        return service.update(actor, id, req);
    }

    /** PUT /admin/banners/{id}/image (multipart: file) → 이미지를 바꾼 배너 */
    @PutMapping("/{id}/image")
    public AdminBannerResponse replaceImage(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id,
                                            @RequestPart(value = "file", required = false) MultipartFile file) {
        return service.replaceImage(actor, id, file == null ? null : file.getContentType(), bytes(file));
    }

    /** PUT /admin/banners/order { ids: [...] } → 새 순서의 전체 목록 */
    @PutMapping("/order")
    public List<AdminBannerResponse> reorder(@AuthenticationPrincipal CurrentUser actor, @RequestBody BannerOrderRequest req) {
        return service.reorder(actor, req == null ? null : req.ids());
    }

    /** DELETE /admin/banners/{id} → { ok: true } */
    @DeleteMapping("/{id}")
    public BannerDeleted delete(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id) {
        service.delete(actor, id);
        return BannerDeleted.OK;
    }

    // 파일이 없으면 null — 서비스가 권한 검사 뒤 '배너 이미지를 골라주세요' 로 답한다
    private static byte[] bytes(MultipartFile file) {
        if (file == null) return null;
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("이미지 파일을 읽을 수 없어요. 다른 이미지를 골라주세요");
        }
    }
}
