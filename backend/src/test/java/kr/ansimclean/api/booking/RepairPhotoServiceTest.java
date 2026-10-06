package kr.ansimclean.api.booking;

import kr.ansimclean.api.common.web.RateLimiter;
import kr.ansimclean.api.booking.dto.RepairPhotoResponse;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 수리 사진 업로드: 고객 폴더 아래 uuid 경로, 이미지·5MB 만, 미리보기 서명 URL */
// RepairPhotoService 단위 테스트. 업로드 검사(시간당 한도·크기·형식·해상도), 저장 순서(원장 → Storage),
// 24시간 지난 미첨부 사진 정리(deleteUnattached), 예약 첨부(attach), 탈퇴 때 전체 삭제(deleteAllOf)를 본다.
// Storage·원장 레포지토리·ProfileService 는 Mockito 목이다 (CODE_GUIDE.md 3-12).
class RepairPhotoServiceTest {
    static final UUID CUSTOMER = UUID.randomUUID(); // 테스트용 고객 id (실행마다 새 무작위 값)
    SupabaseStorageClient storage = mock(SupabaseStorageClient.class);
    // RateLimiter 는 목이 아니라 진짜(메모리 한도)다. 시계가 고정돼 있어 1시간 창이 비지 않고 횟수가 그대로 쌓인다
    RateLimiter rateLimiter = new RateLimiter(new AppClock(Clock.fixed(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE)));
    RepairPhotoUploadRepository uploads = mock(RepairPhotoUploadRepository.class);
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 서비스가 보는 '지금' (서울 14:00)
    // holdLive 는 목 기본값(null 을 돌려주고 예외 없음)이라, 따로 정하지 않으면 탈퇴하지 않은 계정으로 통과한다
    kr.ansimclean.api.profile.ProfileService profiles = mock(kr.ansimclean.api.profile.ProfileService.class);
    RepairPhotoService service = new RepairPhotoService(storage, rateLimiter, uploads, profiles, new AppClock(Clock.fixed(NOW, AppClock.ZONE)));

    // 인스턴스 초기화 블록: 객체가 만들어질 때마다 돈다. JUnit 은 테스트 메서드마다 새 객체를 만들므로 테스트마다 다시 정해진다
    {
        when(uploads.existsById(any())).thenReturn(true); // 올린 뒤에도 원장이 있다(탈퇴 정리와 겹치지 않은 보통의 경우)
    }

    /** PNG 서명 + IHDR(가로·세로) — 헤더만 있는 최소 PNG */
    static byte[] png(int width, int height) {
        byte[] b = new byte[33];
        // 0~7번째: PNG 고정 서명 8바이트, 8~11번째: 조각 길이 13, 12~15번째: 조각 이름 'IHDR'
        byte[] head = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R' };
        System.arraycopy(head, 0, b, 0, head.length);
        // 16~19번째에 가로, 20~23번째에 세로를 4바이트(큰 자리 먼저)로 적는다 — ImageBytes.dimensions 가 읽는 자리.
        // -1 은 32비트가 모두 1 이라 네 바이트가 모두 FF 가 된다 — 부호 없이 읽으면 약 43억 (overflow 테스트가 쓴다)
        b[16] = (byte) (width >>> 24); b[17] = (byte) (width >>> 16); b[18] = (byte) (width >>> 8); b[19] = (byte) width;
        b[20] = (byte) (height >>> 24); b[21] = (byte) (height >>> 16); b[22] = (byte) (height >>> 8); b[23] = (byte) height;
        return b;
    }

    /** JPEG: SOI + APP0(길이 16) + SOF0(높이·너비) */
    static byte[] jpeg(int width, int height) {
        byte[] b = new byte[2 + 18 + 19];
        b[0] = (byte) 0xFF; b[1] = (byte) 0xD8; // 파일 시작(SOI)
        // APP0 세그먼트: 표식 FF E0 + 길이 16 (내용은 0). ImageBytes 는 길이만큼 건너뛰어 2 + 2 + 16 = 20번째에서 다음 세그먼트를 찾는다
        b[2] = (byte) 0xFF; b[3] = (byte) 0xE0; b[4] = 0; b[5] = 16;
        // 20번째부터 SOF0(프레임 헤더): 표식 FF C0, 길이 17, 정밀도 8, 그다음 높이 2바이트·너비 2바이트
        int i = 20;
        b[i] = (byte) 0xFF; b[i + 1] = (byte) 0xC0; b[i + 2] = 0; b[i + 3] = 17; b[i + 4] = 8;
        b[i + 5] = (byte) (height >>> 8); b[i + 6] = (byte) height; b[i + 7] = (byte) (width >>> 8); b[i + 8] = (byte) width;
        return b;
    }

    // 상한(UPLOADS_PER_USER_PER_HOUR)만큼 올려 한도를 채운 뒤 한 장 더 올리면 429(RATE_LIMITED) 인지 본다
    @Test
    void 시간당_상한을_넘기면_RATE_LIMITED() {
        // 미리보기 서명 URL 은 목이 가짜 주소를 돌려주게 한다 (정하지 않으면 null 이 된다. 아래 테스트들도 같다)
        when(storage.signedUrl(eq(RepairPhotoService.BUCKET), any(), anyInt())).thenReturn("https://signed");
        for (int i = 0; i < RepairPhotoService.UPLOADS_PER_USER_PER_HOUR; i++) service.upload(CUSTOMER, "image/png", png(10, 10));
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", png(10, 10))).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.RATE_LIMITED); // OTP 가 아닌 한도는 RATE_LIMITED
    }

    @Test
    void 이미지는_고객_폴더에_올리고_경로와_서명_URL_을_준다() {
        when(storage.signedUrl(eq(RepairPhotoService.BUCKET), any(), anyInt())).thenReturn("https://signed");
        // 선언 형식 뒤의 '; charset=binary' 는 떼고 허용 목록과 비교한다. Storage 에 올릴 형식(image/jpeg)은 파일 내용에서 정한다
        RepairPhotoResponse res = service.upload(CUSTOMER, "image/jpeg; charset=binary", jpeg(4032, 3024));
        // ArgumentCaptor: 목에 넘어간 인자를 잡아 두었다가 꺼내 보는 도구. 여기서는 Storage 에 올린 경로를 잡는다
        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(eq(RepairPhotoService.BUCKET), path.capture(), any(), eq("image/jpeg"));
        assertThat(path.getValue()).startsWith(CUSTOMER + "/").endsWith(".jpg");
        assertThat(res.path()).isEqualTo(path.getValue());
        assertThat(res.url()).isEqualTo("https://signed");
    }

    // 빈 파일·5MB 초과·이미지가 아닌 형식·형식 없음(null) 은 모두 거절되고, Storage 에는 아무것도 올라가지 않는다
    @Test
    void 빈_파일_큰_파일_이미지_아님은_VALIDATION() {
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", new byte[0])).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", new byte[(int) RepairPhotoService.MAX_BYTES + 1])).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.upload(CUSTOMER, "application/pdf", new byte[] { 1 })).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.upload(CUSTOMER, null, new byte[] { 1 })).isInstanceOf(ApiException.class);
        verify(storage, never()).upload(any(), any(), any(), any());
    }

    // 형식은 파일 내용으로 판정한다: 선언은 이미지여도 내용이 쓰레기 3바이트·HTML 이면 거절,
    // 헤더의 가로×세로가 상한(ImageBytes.MAX_PIXELS)을 넘거나 0 이어도 거절한다
    @Test
    void 이미지라고_선언해도_내용이_이미지가_아니거나_해상도가_비정상이면_VALIDATION() { // 감사 M-02
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/jpeg", new byte[] { 1, 2, 3 })).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", html)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", png(30_000, 30_000))).isInstanceOf(ApiException.class); // 9억 화소 — 압축 폭탄
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", png(0, 10))).isInstanceOf(ApiException.class);
        verify(storage, never()).upload(any(), any(), any(), any());
    }

    // png(-1, -1) 은 가로·세로 바이트가 모두 FF 라 부호 없이 읽으면 각각 약 43억이다. 둘을 곱하면 long 범위를 넘어
    // 음수가 될 수 있는데, 서비스는 곱하지 않고 나눗셈(MAX_PIXELS / 세로)으로 비교하므로 그래도 거절되는지 본다
    @Test
    void 부호없는_PNG_치수의_곱이_overflow_해도_거부한다() {
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", png(-1, -1)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        verify(storage, never()).upload(any(), any(), any(), any());
    }

    @Test
    void 형식은_선언이_아니라_내용_기준으로_저장한다() {
        when(storage.signedUrl(eq(RepairPhotoService.BUCKET), any(), anyInt())).thenReturn("https://signed");
        RepairPhotoResponse png = service.upload(CUSTOMER, "image/jpeg", png(10, 10)); // .jpg 로 왔지만 PNG
        verify(storage).upload(eq(RepairPhotoService.BUCKET), eq(png.path()), any(), eq("image/png"));
        assertThat(png.path()).endsWith(".png");
        // 가짜 HEIC: 앞 12바이트만 채운다 (상자 길이 24 + 'ftyp' + brand 'mif1'). HEIC 는 해상도 검사를 건너뛰므로 이것으로 충분하다.
        // image/heif 로 선언해도 서비스는 HEIF 를 image/heic 로 묶어 저장한다
        byte[] heic = new byte[32];
        System.arraycopy(new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'i', 'f', '1' }, 0, heic, 0, 12);
        RepairPhotoResponse res = service.upload(CUSTOMER, "image/heif", heic);
        verify(storage).upload(eq(RepairPhotoService.BUCKET), eq(res.path()), any(), eq("image/heic"));
        assertThat(res.path()).endsWith(".heic");
    }

    // 올린 뒤 미리보기 서명 URL 만들기가 실패하면(파일 없음 404 → NOT_FOUND, 빈 응답 → UPSTREAM) 녹화 영상 문구가 아니라
    // 사진 업로드 실패 문구로 답한다. 올라간 파일·원장 행은 예약에 붙지 않으므로 24시간 뒤 정리가 지운다 (문제 목록 21번)
    @Test
    void 미리보기_주소를_만들지_못하면_사진_업로드_실패_문구() {
        when(storage.signedUrl(eq(RepairPhotoService.BUCKET), any(), anyInt())).thenThrow(new ApiException(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", png(10, 10))).isInstanceOf(ApiException.class)
                .hasMessage("사진을 올리지 못했어요. 잠시 후 다시 시도해주세요").extracting("code").isEqualTo(ErrorCode.UPSTREAM);
        org.mockito.Mockito.reset(storage);
        when(storage.signedUrl(eq(RepairPhotoService.BUCKET), any(), anyInt())).thenThrow(new ApiException(ErrorCode.UPSTREAM));
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", png(10, 10))).isInstanceOf(ApiException.class)
                .hasMessage("사진을 올리지 못했어요. 잠시 후 다시 시도해주세요").extracting("code").isEqualTo(ErrorCode.UPSTREAM);
    }

    // 원장(repair_photo_uploads) 저장이 Storage 업로드보다 먼저인지 본다 — Storage 에 있는 파일이 원장에 없으면
    // 원장만 보는 미첨부 정리(deleteUnattached)가 그 파일을 찾지 못한다 (탈퇴 삭제는 Storage 폴더 목록도 본다)
    @Test
    void 올린_사진은_원장에_먼저_적는다() { // 감사 M-04
        when(storage.signedUrl(eq(RepairPhotoService.BUCKET), any(), anyInt())).thenReturn("https://signed");
        RepairPhotoResponse res = service.upload(CUSTOMER, "image/png", png(10, 10));
        // InOrder: 여러 목에 걸친 호출 순서를 확인하는 도구. 아래 verify 들이 적힌 순서대로 불렸어야 통과한다
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(uploads, storage);
        order.verify(uploads).save(org.mockito.ArgumentMatchers.argThat(u -> u.getPath().equals(res.path())));
        order.verify(storage).upload(eq(RepairPhotoService.BUCKET), eq(res.path()), any(), eq("image/png"));
    }

    // 24시간 1분 전에 올린 미첨부 행 하나를 정리 후보로 주고, claimForDeletion 이 1(잡기 성공)을 돌려주게 한다
    // → '삭제 중' 표시 → Storage 삭제 → 원장 행 삭제 순서로 도는지 본다
    @Test
    void 예약에_붙지_않고_24시간_지난_사진은_Storage_와_원장에서_지운다() {
        RepairPhotoUpload old = RepairPhotoUpload.of(CUSTOMER + "/old.jpg", CUSTOMER, NOW.minusSeconds(RepairPhotoService.PREVIEW_SECONDS + 60));
        when(uploads.findCleanupCandidates(eq(NOW.minus(RepairPhotoService.UNATTACHED_KEEP)), any())).thenReturn(java.util.List.of(old));
        when(uploads.claimForDeletion(eq(CUSTOMER + "/old.jpg"), any())).thenReturn(1);
        assertThat(service.deleteUnattached()).isEqualTo(1);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(uploads, storage);
        order.verify(uploads).claimForDeletion(CUSTOMER + "/old.jpg", NOW); // '삭제 중' 으로 잡고 (재감사 M-04·2차 #4)
        order.verify(storage).delete(RepairPhotoService.BUCKET, CUSTOMER + "/old.jpg");
        order.verify(uploads).deleteById(CUSTOMER + "/old.jpg"); // 파일을 지운 뒤에야 행을
        verify(uploads).findCleanupCandidates(NOW.minus(RepairPhotoService.UNATTACHED_KEEP), org.springframework.data.domain.PageRequest.ofSize(RepairPhotoService.CLEANUP_BATCH)); // 한 번에 최대 CLEANUP_BATCH 장
    }

    // claimForDeletion 이 0 = 후보를 읽은 뒤 예약 생성(attach)이 먼저 커밋해 정리가 잡지 못했다 → Storage 를 건드리지 않는다
    @Test
    void 정리_후보를_읽은_뒤_예약에_붙은_사진은_지우지_않는다() { // 재감사 M-04
        RepairPhotoUpload old = RepairPhotoUpload.of(CUSTOMER + "/old.jpg", CUSTOMER, NOW.minusSeconds(RepairPhotoService.PREVIEW_SECONDS + 60));
        when(uploads.findCleanupCandidates(any(), any())).thenReturn(java.util.List.of(old));
        when(uploads.claimForDeletion(any(), any())).thenReturn(0); // 그 사이 attach 가 먼저 커밋
        assertThat(service.deleteUnattached()).isZero();
        verify(storage, never()).delete(any(), any());
    }

    // 첫 정리: Storage 삭제가 예외를 던져도 정리는 밖으로 던지지 않고(지운 장 0) 원장 행은 지워지지 않는다.
    // 두 번째 정리: 같은 행이 다시 후보로 잡혀 끝까지 지워진다 (목이 같은 후보·잡기 성공을 계속 돌려준다)
    @Test
    void Storage_삭제가_실패하거나_중간에_멈추면_행이_삭제_중으로_남아_다음_정리가_다시_한다() { // 재감사 2차 #4
        RepairPhotoUpload old = RepairPhotoUpload.of(CUSTOMER + "/old.jpg", CUSTOMER, NOW.minusSeconds(RepairPhotoService.PREVIEW_SECONDS + 60));
        when(uploads.findCleanupCandidates(any(), any())).thenReturn(java.util.List.of(old));
        when(uploads.claimForDeletion(any(), any())).thenReturn(1);
        // doThrow(…).when(목).메서드(…): 돌려주는 값이 없는(void) 메서드가 예외를 던지게 하는 Mockito 문법
        org.mockito.Mockito.doThrow(new ApiException(ErrorCode.UPSTREAM)).when(storage).delete(any(), any());
        assertThat(service.deleteUnattached()).isZero(); // 실패한 장은 건너뛴다 — 정리 전체가 멈추지 않는다 (문제 목록 4번)
        verify(uploads, never()).deleteById(any()); // 행은 남는다(삭제 중 — 예약에 붙지도 않는다)

        org.mockito.Mockito.doNothing().when(storage).delete(any(), any());
        assertThat(service.deleteUnattached()).isEqualTo(1); // 다음 정리: 같은 행을 다시 잡아 끝낸다
        verify(uploads).deleteById(CUSTOMER + "/old.jpg");
    }

    // 후보가 오래된 순으로 세 장(bad → a → b)이고 맨 앞 bad 의 Storage 삭제만 실패한다.
    // 예전에는 bad 에서 멈춰 a·b 가 매일 남았다 — 이제 bad 만 건너뛰고 a·b 는 지운다 (문제 목록 4번)
    @Test
    void 한_장의_삭제가_실패해도_뒤의_사진은_계속_지운다() {
        Instant old = NOW.minusSeconds(RepairPhotoService.PREVIEW_SECONDS + 60);
        java.util.List<RepairPhotoUpload> candidates = java.util.List.of(RepairPhotoUpload.of(CUSTOMER + "/bad.jpg", CUSTOMER, old),
                RepairPhotoUpload.of(CUSTOMER + "/a.jpg", CUSTOMER, old.plusSeconds(1)), RepairPhotoUpload.of(CUSTOMER + "/b.jpg", CUSTOMER, old.plusSeconds(2)));
        when(uploads.findCleanupCandidates(any(), any())).thenReturn(candidates);
        when(uploads.claimForDeletion(any(), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new ApiException(ErrorCode.UPSTREAM)).when(storage).delete(RepairPhotoService.BUCKET, CUSTOMER + "/bad.jpg");

        assertThat(service.deleteUnattached()).isEqualTo(2);
        verify(uploads, never()).deleteById(CUSTOMER + "/bad.jpg"); // 실패한 장은 '삭제 중' 으로 남아 다음 정리가 다시 한다
        verify(uploads).deleteById(CUSTOMER + "/a.jpg");
        verify(uploads).deleteById(CUSTOMER + "/b.jpg");
    }

    // 서로 다른 경로는 a·b 두 장이다. attach 가 바뀐 행 1 을 돌려주면(한 장은 정리가 먼저 지웠다) 모자라서 PHOTO_EXPIRED,
    // 2 를 돌려주면 통과한다 — 같은 경로를 두 번 보내도 한 장으로 센다는 것도 함께 본다
    @Test
    void 정리가_먼저_지운_사진으로는_예약을_만들지_않는다() { // 재감사 M-04 — 없는 파일을 가리키는 예약
        when(uploads.attach(eq(CUSTOMER), any(), any())).thenReturn(1);
        assertThatThrownBy(() -> service.attach(CUSTOMER, java.util.List.of(CUSTOMER + "/a.jpg", CUSTOMER + "/b.jpg", CUSTOMER + "/a.jpg")))
                .isInstanceOf(ApiException.class).hasMessage(RepairPhotoService.PHOTO_EXPIRED);
        when(uploads.attach(eq(CUSTOMER), any(), any())).thenReturn(2);
        service.attach(CUSTOMER, java.util.List.of(CUSTOMER + "/a.jpg", CUSTOMER + "/b.jpg", CUSTOMER + "/a.jpg")); // 같은 경로 두 번은 한 장
    }

    // existsById 가 false = 업로드 직후 다시 봤을 때 원장 행이 없다(그 사이 탈퇴 정리가 지웠다) → 방금 올린 파일을 지우고 401
    @Test
    void 올리는_사이_탈퇴_정리가_원장을_지웠으면_늦게_올라간_파일도_지운다() { // 재감사 M-04
        when(uploads.existsById(any())).thenReturn(false);
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", png(10, 10)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(storage).delete(eq(RepairPhotoService.BUCKET), org.mockito.ArgumentMatchers.startsWith(CUSTOMER + "/"));
    }

    // holdLive 가 첫 호출(업로드 시작)에는 통과하고 두 번째(올린 뒤 재확인)에는 401 을 던지게 한다
    // (thenReturn(…).thenThrow(…) = 호출 순서마다 다른 답) → 올린 파일과 원장 행을 지우고 401
    @Test
    void 올리는_사이_탈퇴가_커밋됐으면_원장이_남아_있어도_파일과_행을_지운다() { // 재감사 2차 #4 — 정리(아웃박스)가 먼저 폴더를 읽었을 수 있다
        when(profiles.holdLive(CUSTOMER)).thenReturn(null).thenThrow(new ApiException(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", png(10, 10)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(storage).delete(eq(RepairPhotoService.BUCKET), org.mockito.ArgumentMatchers.startsWith(CUSTOMER + "/"));
        verify(uploads).deleteById(org.mockito.ArgumentMatchers.startsWith(CUSTOMER + "/"));
    }

    // 첫 확인(holdLive)부터 401 이면 Storage 업로드까지 가지 않는다
    @Test
    void 탈퇴한_계정은_사진을_올리지_못한다() {
        when(profiles.holdLive(CUSTOMER)).thenThrow(new ApiException(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> service.upload(CUSTOMER, "image/png", png(10, 10))).isInstanceOf(ApiException.class);
        verify(storage, never()).upload(any(), any(), any(), any());
    }

    // 원장에는 a.jpg 하나, Storage 폴더 목록에는 a.jpg 와 원장이 생기기 전에 올린 파일이 있다
    // → 겹친 a.jpg 는 한 번만 세어 두 장을 지우고 2 를 돌려준다
    @Test
    void 탈퇴하면_원장과_Storage_폴더의_사진을_전부_지운다() {
        when(uploads.findByCustomerId(CUSTOMER)).thenReturn(java.util.List.of(RepairPhotoUpload.of(CUSTOMER + "/a.jpg", CUSTOMER, NOW)));
        when(storage.list(RepairPhotoService.BUCKET, CUSTOMER.toString())).thenReturn(java.util.List.of(CUSTOMER + "/a.jpg", CUSTOMER + "/before-ledger.png"));
        assertThat(service.deleteAllOf(CUSTOMER)).isEqualTo(2);
        verify(storage).delete(RepairPhotoService.BUCKET, CUSTOMER + "/a.jpg");
        verify(storage).delete(RepairPhotoService.BUCKET, CUSTOMER + "/before-ledger.png");
    }
}
