package kr.ansimclean.api.live;

import kr.ansimclean.api.booking.Booking;
import kr.ansimclean.api.booking.BookingService;
import kr.ansimclean.api.booking.TestBookings;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 뒤 미디어 정리 핸들러(AccountMediaDeletionHandler) 단위 테스트. 아웃박스가 넘겨줄 이벤트 JSON 을 handle 에 직접 넣는다.
 * 저장소·LiveKit·Storage·수리 사진 서비스는 가짜(mock)라, 무엇을 지우라고 불렀는지를 verify 로 확인한다.
 */
class AccountMediaDeletionHandlerTest {
    BookingService bookings = mock(BookingService.class);
    LiveSessionRepository sessions = mock(LiveSessionRepository.class);
    ManagerDeviceRepository devices = mock(ManagerDeviceRepository.class);
    LiveKitGateway livekit = mock(LiveKitGateway.class);
    SupabaseStorageClient storage = mock(SupabaseStorageClient.class);
    kr.ansimclean.api.booking.RepairPhotoService repairPhotos = mock(kr.ansimclean.api.booking.RepairPhotoService.class);
    kr.ansimclean.api.booking.CleaningPhotoService cleaningPhotos = mock(kr.ansimclean.api.booking.CleaningPhotoService.class);
    AccountMediaDeletionHandler handler = new AccountMediaDeletionHandler(bookings, sessions, devices, livekit, storage, LiveTestSupport.props(), repairPhotos, cleaningPhotos);

    // 1) 고객: 수리 사진이 붙고 녹화본이 준비된(ready) 예약 하나 → 사진 폴더 삭제·녹화 파일 삭제·세션의 녹화 상태 해제, 바디캠 기기는 없어 Ingress 는 안 건드린다
    // 2) 매니저: 고객 예약은 없고 기기가 있다 → LiveKit Ingress(스트림 키) 삭제 + 기기 행 삭제
    @Test
    void 고객_탈퇴는_녹화본과_수리_사진을_지우고_매니저_탈퇴는_Ingress_와_기기를_지운다() {
        UUID customer = UUID.randomUUID();
        Booking b = TestBookings.scheduled(customer, UUID.randomUUID(), LocalDate.of(2026, 9, 17), LocalTime.of(10, 0));
        b.describeRepair("냉장고", null, null, List.of(customer + "/a.jpg")); // 수리 사진 경로 = "고객 id/파일명"
        LiveSession s = LiveSession.create(b.getId());
        s.recordingReady(b.getId() + ".mp4", 600); // 업로드가 끝난 녹화본 (파일 경로, 길이 초)
        when(bookings.customerBookings(customer)).thenReturn(List.of(b));
        when(sessions.findById(b.getId())).thenReturn(Optional.of(s));
        when(devices.findById(customer)).thenReturn(Optional.empty());

        handler.handle("AccountMediaDeleted", "{\"userId\":\"" + customer + "\"}"); // 아웃박스에 저장되는 이벤트 JSON 모양 그대로

        verify(repairPhotos).deleteAllOf(customer); // 예약에 붙은 사진·안 붙은 사진·폴더 전체 (감사 M-04, RepairPhotoServiceTest)
        verify(storage).delete(LiveTestSupport.props().storage().recordingsBucket(), b.getId() + ".mp4");
        assertThat(s.isRecordingReady()).isFalse();
        verify(livekit, never()).deleteIngress(any());

        UUID manager = UUID.randomUUID();
        ManagerDevice device = ManagerDevice.register(manager, "안심캠", "AC-1", "ing-1", "rtmp://x", "key");
        when(bookings.customerBookings(manager)).thenReturn(List.of());
        when(devices.findById(manager)).thenReturn(Optional.of(device));
        handler.handle("AccountMediaDeleted", "{\"userId\":\"" + manager + "\"}");
        verify(livekit).deleteIngress("ing-1");
        verify(devices).delete(device);
    }

    // 청소 전·후 사진: 시작한 적 있는 예약(startedAt 있음)만 넘긴다 — 시작 전 예약에는 사진이 있을 수 없다
    @Test
    void 고객_탈퇴는_시작했던_예약의_청소_전후_사진을_지운다() {
        UUID customer = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 9, 17);
        Booking started = TestBookings.inProgress(customer, manager, today, LocalTime.of(10, 0), Instant.parse("2026-09-17T01:00:00Z"));
        Booking notStarted = TestBookings.scheduled(customer, manager, today.plusDays(1), LocalTime.of(10, 0));
        when(bookings.customerBookings(customer)).thenReturn(List.of(started, notStarted));
        when(devices.findById(customer)).thenReturn(Optional.empty());

        handler.handle("AccountMediaDeleted", "{\"userId\":\"" + customer + "\"}");

        verify(cleaningPhotos).deleteAllOfBookings(List.of(started.getId()));
    }

    // 청소 사진 Storage 삭제가 실패하면 그대로 던진다 — 아웃박스가 이 이벤트를 나중에 다시 처리한다
    @Test
    void 청소_사진_삭제_실패는_던져서_재시도한다() {
        UUID customer = UUID.randomUUID();
        when(bookings.customerBookings(customer)).thenReturn(List.of());
        when(cleaningPhotos.deleteAllOfBookings(any())).thenThrow(new kr.ansimclean.api.common.error.ApiException(kr.ansimclean.api.common.error.ErrorCode.UPSTREAM));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle("AccountMediaDeleted", "{\"userId\":\"" + customer + "\"}"))
                .isInstanceOf(kr.ansimclean.api.common.error.ApiException.class);
    }
}
