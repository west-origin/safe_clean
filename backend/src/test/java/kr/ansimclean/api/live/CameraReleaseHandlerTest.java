package kr.ansimclean.api.live;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static kr.ansimclean.api.live.LiveTestSupport.MANAGER;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 방문을 취소한 매니저의 바디캠 정리 (전체 재점검 J1) */
// 막아야 하는 문제: 방문을 취소한 매니저의 바디캠이 그 고객의 예약 방을 계속 가리켜, 다시 켜면 남의 집 방으로 송출되는 것.
// 단위 테스트: 기기 저장소·LiveKit·LiveService 는 가짜다. LiveKit 이 알려 주는 Ingress 의 지금 방(roomName)만 바꿔 가며
// 대기 방으로 옮기라는 호출(bindIngress)이 나가는지 본다. 기기는 LiveTestSupport.device()(Ingress "ing-1", 옮길 차례인 방 없음).
class CameraReleaseHandlerTest {
    ManagerDeviceRepository devices = mock(ManagerDeviceRepository.class);
    LiveKitGateway livekit = mock(LiveKitGateway.class);
    LiveService live = mock(LiveService.class);
    CameraReleaseHandler handler = new CameraReleaseHandler(devices, livekit, live);
    ManagerDevice device = LiveTestSupport.device();
    UUID booking = UUID.randomUUID();

    /** 아웃박스에 저장되는 CameraReleased 이벤트 JSON (취소된 예약 id, 취소한 매니저 id) */
    String payload() {
        return "{\"bookingId\":\"" + booking + "\",\"managerId\":\"" + MANAGER + "\"}";
    }

    // Ingress 가 지금 "booking-취소된 예약 id" 방을 가리킨다 → 대기 방(parking-매니저 id)으로 옮기라고 부른다
    @Test
    void 취소한_예약_방에_묶여_있으면_대기_방으로_돌린다() {
        when(devices.findById(MANAGER)).thenReturn(Optional.of(device));
        when(livekit.getIngress("ing-1")).thenReturn(Optional.of(new LiveKitGateway.IngressInfo("ing-1", "rtmp://x", "k", "booking-" + booking, false)));
        handler.handle("CameraReleased", payload());
        verify(live).bindIngress(device, device.parkingRoom());
    }

    // Ingress 가 이미 다른 예약 방을 가리킨다 → 이 취소와 상관없는 방이라 옮기지 않는다 (bindIngress 가 한 번도 불리지 않는다)
    @Test
    void 이미_다른_작업_방으로_옮겼으면_건드리지_않는다() {
        when(devices.findById(MANAGER)).thenReturn(Optional.of(device));
        when(livekit.getIngress("ing-1")).thenReturn(Optional.of(new LiveKitGateway.IngressInfo("ing-1", "rtmp://x", "k", "booking-" + UUID.randomUUID(), false)));
        handler.handle("CameraReleased", payload());
        verify(live, never()).bindIngress(org.mockito.ArgumentMatchers.any(), anyString());
    }
}
