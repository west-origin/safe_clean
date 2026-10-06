package kr.ansimclean.api.live;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.live.LiveKitGateway.IngressInfo;
import kr.ansimclean.api.live.dto.RegisterDeviceRequest;
import kr.ansimclean.api.live.dto.RegisterDeviceResponse;
import kr.ansimclean.api.profile.Profile;
import kr.ansimclean.api.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 바디캠 등록 (§8.1): 매니저 1명 = 기기 1대 = Ingress 1개. 재등록은 있는 Ingress 를 다시 쓴다 */
// 단위 테스트: 기기 저장소·LiveKit·프로필 서비스는 가짜다. Ingress =LiveKit 이 바디캠의 RTMP 송출을 받는 입구(주소 + 스트림 키).
// LiveKit 에 어떤 Ingress 를 만들거나 지우라고 불렀는지와 응답의 Ingress id·스트림 키를 확인한다.
class DeviceServiceTest {
    static final UUID MANAGER = UUID.randomUUID();
    ManagerDeviceRepository devices = mock(ManagerDeviceRepository.class);
    LiveKitGateway livekit = mock(LiveKitGateway.class);
    ProfileService profiles = mock(ProfileService.class);
    DeviceService service = new DeviceService(devices, livekit, profiles);

    // 테스트마다: MANAGER 는 탈퇴하지 않은 매니저 프로필로 찾히고(탈퇴와 줄을 서는 공유 잠금 조회), 기기 저장은 받은 객체를 그대로 돌려준다
    @BeforeEach
    void setUp() {
        when(profiles.holdLiveIfPresent(MANAGER)).thenReturn(Optional.of(Profile.create(MANAGER, "manager", "김미영", null, "+821011112222")));
        when(devices.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // 매니저 탈퇴와 겹침을 단위로 흉내 낸다: 탈퇴가 먼저 잠그고 커밋하면 기다리던 등록의 공유 잠금 조회는 빈 값을 받는다 →
    // 운영자 API 에 맞게 401 이 아니라 기존 VALIDATION "매니저를 찾을 수 없어요" 로 끝나고, Ingress·기기 행을 만들지 않는다.
    // 예전에는 잠그지 않는 조회(findLive)라 탈퇴 미디어 정리가 끝난 뒤에 스트림 키가 든 기기 행이 남을 수 있었다 (문제 목록 29번)
    @Test
    void 매니저_탈퇴가_먼저_커밋됐으면_Ingress_도_기기_행도_만들지_않는다() {
        UUID gone = UUID.randomUUID();
        when(profiles.findLive(gone)).thenReturn(Optional.of(Profile.create(gone, "manager", "탈퇴 직전", null, "+821055556666"))); // 잠그지 않은 조회는 옛 값을 볼 수 있다
        when(profiles.holdLiveIfPresent(gone)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.register(new RegisterDeviceRequest(gone, "안심캠", "AC-9", false))).isInstanceOf(ApiException.class)
                .hasMessage("매니저를 찾을 수 없어요").extracting("code").isEqualTo(ErrorCode.VALIDATION);
        verify(livekit, never()).createIngress(anyString(), anyString(), anyString(), anyString());
        verify(devices, never()).save(any());
    }

    // 기기 행이 없을 때: 매니저 단위 잠금 → Ingress 를 정확히 이 인자(이름 "device-일련번호", 대기 방, 참가자 "camera-매니저 id",
    // 표시 이름 "이름 바디캠")로 만들 때만 가짜 LiveKit 이 응답한다 → 응답에 그 Ingress id·키가 나오고 기기 행이 저장된다
    @Test
    void 첫_등록은_매니저를_잠그고_주차_방으로_Ingress_를_만든다() {
        when(devices.findById(MANAGER)).thenReturn(Optional.empty());
        when(livekit.createIngress("device-AC-1", "parking-" + MANAGER, "camera-" + MANAGER, "김미영 바디캠"))
                .thenReturn(new IngressInfo("ing-1", "rtmp://lk/x", "key-1", "parking-" + MANAGER, false));

        RegisterDeviceResponse res = service.register(new RegisterDeviceRequest(MANAGER, "안심캠 #AC-1", "AC-1", false));

        verify(devices).lockManager(MANAGER); // 동시 등록 → Ingress 둘
        assertThat(res.ingressId()).isEqualTo("ing-1");
        assertThat(res.streamKey()).isEqualTo("key-1");
        verify(devices).save(any());
    }

    // 기기 행이 있고 LiveKit 에도 그 Ingress 가 살아 있을 때: 새로 만들지 않고 이름·일련번호만 바꾼다.
    // 이미 있던 행은 값만 바꾸고 save 는 부르지 않는다 (커밋 때 반영, CODE_GUIDE.md 3-3)
    @Test
    void 재등록은_있는_Ingress_를_다시_쓰고_이름_시리얼만_바꾼다() {
        ManagerDevice existing = ManagerDevice.register(MANAGER, "옛 이름", "OLD", "ing-1", "rtmp://lk/x", "key-1");
        when(devices.findById(MANAGER)).thenReturn(Optional.of(existing));
        when(livekit.getIngress("ing-1")).thenReturn(Optional.of(new IngressInfo("ing-1", "rtmp://lk/x", "key-1", "parking-" + MANAGER, false)));

        RegisterDeviceResponse res = service.register(new RegisterDeviceRequest(MANAGER, "안심캠 #AC-2", "AC-2", false));

        verify(livekit, never()).createIngress(anyString(), anyString(), anyString(), anyString());
        assertThat(res.ingressId()).isEqualTo("ing-1");
        assertThat(existing.getName()).isEqualTo("안심캠 #AC-2");
        assertThat(existing.getSerial()).isEqualTo("AC-2");
        verify(devices, never()).save(any());

        // rotate=true: 옛 Ingress 를 지우고 새 스트림 키를 만든다 (분실·도난)
        when(livekit.createIngress(anyString(), anyString(), anyString(), anyString())).thenReturn(new IngressInfo("ing-2", "rtmp://lk/y", "key-2", "parking-" + MANAGER, false));
        RegisterDeviceResponse rotated = service.register(new RegisterDeviceRequest(MANAGER, "안심캠 #AC-2", "AC-2", true));
        verify(livekit).deleteIngress("ing-1");
        assertThat(rotated.ingressId()).isEqualTo("ing-2");
        assertThat(rotated.streamKey()).isEqualTo("key-2");
    }

    // 1) 기기 행의 Ingress 를 LiveKit 에서 못 찾으면(getIngress 가 빈 값) 새로 만든다
    // 2) 역할이 고객인 사용자 → VALIDATION 3) 프로필이 없는 id(가짜 holdLiveIfPresent 의 기본 반환 = 빈 Optional) → 오류
    @Test
    void LiveKit_에서_Ingress_가_사라졌으면_새로_만들고_고객이나_없는_사용자는_VALIDATION() {
        ManagerDevice existing = ManagerDevice.register(MANAGER, "옛 이름", "OLD", "ing-gone", "rtmp://lk/x", "key-1");
        when(devices.findById(MANAGER)).thenReturn(Optional.of(existing));
        when(livekit.getIngress("ing-gone")).thenReturn(Optional.empty());
        when(livekit.createIngress(anyString(), anyString(), anyString(), anyString())).thenReturn(new IngressInfo("ing-2", "rtmp://lk/x", "key-2", "parking-" + MANAGER, false));
        assertThat(service.register(new RegisterDeviceRequest(MANAGER, "안심캠", "AC-3", false)).ingressId()).isEqualTo("ing-2");

        UUID customer = UUID.randomUUID();
        when(profiles.holdLiveIfPresent(customer)).thenReturn(Optional.of(Profile.create(customer, "customer", "김하늘", null, "+821033334444")));
        assertThatThrownBy(() -> service.register(new RegisterDeviceRequest(customer, "안심캠", "AC-4", false))).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.register(new RegisterDeviceRequest(UUID.randomUUID(), "안심캠", "AC-5", false))).isInstanceOf(ApiException.class);
    }
}
