package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.CleaningPhotoResponse;
import kr.ansimclean.api.booking.dto.JobPhotosResponse;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

// 매니저 앱의 청소 전·후 사진 API: /api/v1/manager/jobs/{id}/photos…  (id = 작업 id = 예약 id)
// 매니저 앱 apps/manager/src/api 의 getJobPhotos·addBeforePhoto·addAfterPhoto·deleteJobPhoto 가 부른다.
// /manager/** 는 SecurityConfig 가 매니저 역할만 들여보내고, 자기 작업인지는 CleaningPhotoService 가 본다(남의 작업 404).
// 검사·저장은 전부 서비스에서 — 이 클래스는 multipart 칸을 꺼내 넘기기만 한다.
/** docs/api.md "청소 전·후 사진 추가 (2026-10-23)" — 매니저 4개 */
@RestController
@RequestMapping(SecurityConfig.API + "/manager/jobs/{id}/photos")
public class ManagerCleaningPhotoController {
    private final CleaningPhotoService service;
    private final JobMediaService media;

    public ManagerCleaningPhotoController(CleaningPhotoService service, JobMediaService media) {
        this.service = service;
        this.media = media;
    }

    @PostMapping("/confirm-before")
    public java.util.Map<String, Boolean> confirmBefore(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        media.confirmBefore(user.id(), id);
        return java.util.Map.of("ok", true);
    }

    /** 사진 목록 { photos, max } — 진행 중이거나 끝난 작업 */
    @GetMapping
    public JobPhotosResponse list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.managerList(user.id(), id);
    }

    /**
     * '전' 사진 올리기 — multipart 'file'(이미지) + 'location'(위치 이름 글자).
     * location 을 빠뜨려도 스프링 기본 400 대신 서비스의 한국어 문구(1~20자)가 나가도록 required=false 로 받는다.
     * 5MB 를 넘는 파일은 이 메서드에 닿기 전에 스프링이 막는다(application.yml multipart, RepairPhotoController 설명)
     */
    @PostMapping
    public CleaningPhotoResponse addBefore(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                           @RequestPart("file") MultipartFile file, @RequestParam(value = "location", required = false) String location) {
        return service.addBefore(user.id(), id, location, file.getContentType(), bytes(file));
    }

    /** 같은 위치의 '후' 사진 올리기(다시 찍으면 바꾼다) — multipart 'file' */
    @PutMapping("/{photoId}/after")
    public CleaningPhotoResponse addAfter(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @PathVariable UUID photoId,
                                          @RequestPart("file") MultipartFile file) {
        return service.addAfter(user.id(), id, photoId, file.getContentType(), bytes(file));
    }

    /** 사진 한 쌍 지우기 — 성공하면 본문 없이 204 */
    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @PathVariable UUID photoId) {
        service.delete(user.id(), id, photoId);
    }

    /** 파일 바이트 꺼내기 — 읽다 실패하면 400 VALIDATION (RepairPhotoController 와 같은 문구) */
    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("사진 파일을 읽을 수 없어요");
        }
    }
}
