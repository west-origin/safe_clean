package kr.ansimclean.api.live;

import kr.ansimclean.api.common.events.CameraReleased;
import kr.ansimclean.api.common.outbox.OutboxHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

/**
 * 매니저가 방문을 취소한 뒤 (전체 재점검 J1): 그 매니저가 라이브를 시작해 둔 채였다면 바디캠 Ingress 가 고객 방을 가리킨 채 남는다.
 * 예약의 managerId 가 비어 재조정기도 그 기기를 찾지 못하므로, 옛 매니저가 바디캠을 켜면 **남의 집 방으로 송출**된다. 그 방에 묶여 있을 때만 대기 방으로 돌린다
 * (다른 작업 방으로 이미 옮겼으면 건드리지 않는다). 송출 중이면 pending_room 에 적어 두고 ingress_ended 때 적용된다. LiveKit 오류는 던져 아웃박스가 재시도한다.
 * 예외: 잠금 해제(unlockIngress) 실패는 던지지 않는다 — 재조정기(LiveReconciler)가 1분마다, 그 매니저에게 일시정지 중인 작업이 없으면 다시 연다.
 * 발행: job.JobService.doCancelByManager (CameraReleased).
 */
@Component
public class CameraReleaseHandler implements OutboxHandler {
    private static final Logger log = LoggerFactory.getLogger(CameraReleaseHandler.class);

    private final ManagerDeviceRepository devices;
    private final LiveKitGateway livekit;
    private final LiveService live;
    private final JsonMapper json = JsonMapper.builder().build(); // 아웃박스에 저장된 JSON 을 이벤트 record 로 다시 읽는 도구

    public CameraReleaseHandler(ManagerDeviceRepository devices, LiveKitGateway livekit, LiveService live) {
        this.devices = devices;
        this.livekit = livekit;
        this.live = live;
    }

    /** 맡는 이벤트 타입 — 이벤트 record 의 클래스 이름(CameraReleased)과 같은 글자다 */
    @Override
    public Set<String> types() {
        return Set.of("CameraReleased");
    }

    /**
     * 취소한 매니저의 바디캠이 이 예약 방에 묶여 있으면 잠금을 풀고 대기 방(parking-매니저 id)으로 돌린다.
     * 기기가 없거나, Ingress 가 없거나, 이미 다른 방을 가리키면 아무것도 하지 않는다
     */
    @Override
    public void handle(String type, String payloadJson) {
        CameraReleased e = json.readValue(payloadJson, CameraReleased.class);
        ManagerDevice device = devices.findById(e.managerId()).orElse(null);
        if (device == null || device.getIngressId() == null) return; // 등록된 바디캠이 없다 — 옮길 것이 없다
        String room = "booking-" + e.bookingId(); // 예약 방 이름 규칙 (LiveSession 이 방을 만들 때와 같다)
        // 묶여 있는가: 옮길 차례를 기다리는 방(pending_room)이 이 방이거나, LiveKit 에 물어본 Ingress 의 지금 방이 이 방이면.
        // LiveKit 에 그 Ingress 가 없으면 false. 조회가 실패하면 예외가 그대로 올라가 아웃박스가 나중에 다시 부른다
        boolean boundHere = room.equals(device.getPendingRoom())
                || livekit.getIngress(device.getIngressId()).map(i -> room.equals(i.roomName())).orElse(false);
        if (!boundHere) return;
        live.unlockIngress(device); // 일시정지 때 잠긴 채라면 연다. 실패해도 던지지 않는다 (재조정기의 잠금 정리나 다음 라이브 시작(LiveService.start)이 다시 연다)
        live.bindIngress(device, device.parkingRoom()); // 송출 중이면 false + pending_room(대기 방) — ingress_ended·재조정기가 적용
        log.info("방문 취소한 매니저의 바디캠을 대기 방으로 {} ← {}", device.getIngressId(), room); // pending 으로 미뤄진 경우에도 같은 로그가 남는다
    }
}
