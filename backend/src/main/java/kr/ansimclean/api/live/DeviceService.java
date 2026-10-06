package kr.ansimclean.api.live;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.live.dto.RegisterDeviceRequest;
import kr.ansimclean.api.live.dto.RegisterDeviceResponse;
import kr.ansimclean.api.profile.Profile;
import kr.ansimclean.api.profile.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 바디캠 등록 (§8.1·§13-20): 매니저 1명 = 기기 1대 = Ingress 1개(영구). 처음 등록할 때 대기 방(parking-<managerId>)을 가리키는 RTMP Ingress 를 만들고
 * 주소·키를 저장한다. 다시 등록하면 Ingress 는 재사용하고 이름·일련번호만 바꾼다. 응답의 전체 키는 운영자가 기기에 설정한다.
 * 부르는 곳: AdminDeviceController(POST /admin/devices, 운영자 토큰 검사 뒤).
 * 예외: rotate=true(키 회전)면 옛 Ingress 를 지우고 새로 만든다 — 스트림 주소·키가 바뀐다.
 * LiveKit 에서 옛 Ingress 가 이미 사라졌으면(조회 결과 없음) 역시 새로 만든다.
 */
@Service
// 트랜잭션은 DB 만 되돌린다(CODE_GUIDE.md 3-3). 주의: LiveKit 호출(Ingress 삭제·생성)은 되돌려지지 않는다 —
// 새 Ingress 를 만든 뒤 저장이 실패하면 LiveKit 에 쓰이지 않는 Ingress 가 남고, rotate 로 옛 것을 지운 뒤 생성이 실패하면
// 행은 지워진 Ingress 를 가리킨 채 남는다(다시 등록하면 조회 결과가 없어 새로 만든다)
@Transactional
public class DeviceService {
    private final ManagerDeviceRepository devices;
    private final LiveKitGateway livekit;
    private final ProfileService profiles;

    public DeviceService(ManagerDeviceRepository devices, LiveKitGateway livekit, ProfileService profiles) {
        this.devices = devices;
        this.livekit = livekit;
        this.profiles = profiles;
    }

    /**
     * 매니저에게 바디캠을 붙이고(또는 다시 붙이고) 기기에 넣을 RTMP 주소·전체 스트림 키를 돌려준다.
     * 부작용: LiveKit Ingress 생성·삭제(rotate), manager_devices 행 저장·갱신.
     * 탈퇴했거나 매니저가 아닌 사용자면 VALIDATION 오류.
     * 매니저 확인은 탈퇴와 줄을 서는 공유 잠금 조회(holdLiveIfPresent, FOR SHARE)다 (문제 목록 29번) — 그 매니저의 탈퇴와 겹치면:
     * 등록이 먼저면 탈퇴가 이 트랜잭션이 끝날 때까지 기다리므로, 탈퇴 미디어 정리(AccountMediaDeletionHandler)가 방금 만든 기기 행을 보고 Ingress 와 함께 지운다.
     * 탈퇴가 먼저면 여기서 빈 값을 받아 Ingress 를 만들기 전에 VALIDATION 으로 끝난다. 예전의 잠그지 않는 조회(findLive)로는 탈퇴자의 스트림 키가 남을 수 있었다.
     */
    public RegisterDeviceResponse register(RegisterDeviceRequest req) {
        // 탈퇴하지 않은 프로필만 — 운영자 API 라 401(UNAUTHENTICATED, '운영자 토큰 문제' 로 읽힌다)이 아니라 빈 값을 받아 아래 VALIDATION 으로 바꾼다.
        // filter 로 역할이 매니저인지까지 본다. 잠금은 이 트랜잭션(클래스 @Transactional)이 끝날 때 풀린다
        Profile manager = profiles.holdLiveIfPresent(req.managerId()).filter(p -> Roles.MANAGER.equals(p.getRole()))
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION, "매니저를 찾을 수 없어요"));
        // Postgres 권고 잠금(pg_advisory_xact_lock): 같은 매니저 id 로 들어온 요청을 한 줄로 세우고, 트랜잭션이 끝나면 저절로 풀린다
        devices.lockManager(req.managerId()); // 동시 등록 → 두 번째는 여기서 기다렸다가 기존 기기를 본다
        Optional<ManagerDevice> existing = devices.findById(req.managerId());
        String parking = "parking-" + req.managerId(); // 작업이 없을 때 바디캠이 묶여 있는 대기 방 (ManagerDevice.parkingRoom 과 같은 이름)
        if (req.rotate()) existing.map(ManagerDevice::getIngressId).ifPresent(livekit::deleteIngress); // 키 회전: 옛 Ingress 삭제 → 아래에서 새로 만든다
        // 쓸 Ingress 고르기: rotate 면 무조건 새로 만든다. 아니면 기존 기기의 Ingress 를 LiveKit 에서 찾아 쓰고, 없으면(첫 등록·LiveKit 에서 사라짐) 새로 만든다.
        // 참가자 identity "camera-<managerId>" 는 ManagerDevice.cameraIdentity 와 같아야 재조정기·웹훅이 바디캠을 알아본다
        LiveKitGateway.IngressInfo ingress = (req.rotate() ? Optional.<LiveKitGateway.IngressInfo>empty() : existing.map(ManagerDevice::getIngressId).flatMap(livekit::getIngress))
                .orElseGet(() -> livekit.createIngress("device-" + req.serial(), parking, "camera-" + req.managerId(), manager.getName() + " 바디캠"));
        // 기존 행이 있으면 값만 바꾼다(save 없이 커밋 때 반영, CODE_GUIDE.md 3-3). 없으면 새 행을 저장한다
        ManagerDevice device = existing.map(d -> {
            d.update(req.name(), req.serial(), ingress.ingressId(), ingress.url(), ingress.streamKey());
            return d;
        }).orElseGet(() -> devices.save(ManagerDevice.register(req.managerId(), req.name(), req.serial(), ingress.ingressId(), ingress.url(), ingress.streamKey())));
        // 전체 스트림 키가 나가는 곳은 이 응답뿐이다 (매니저 앱의 기기 조회는 가린 키만 받는다 — RegisterDeviceResponse 설명)
        return new RegisterDeviceResponse(device.getManagerId(), device.getName(), device.getSerial(), device.getIngressId(), device.getStreamUrl(), device.getStreamKey());
    }
}
