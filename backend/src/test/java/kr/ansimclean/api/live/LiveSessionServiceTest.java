package kr.ansimclean.api.live;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.live.dto.DeviceResponse;
import kr.ansimclean.api.live.dto.DeviceStatusRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 라이브 세션 upsert 와 장비 화면 값 (B4 는 DB 쪽만, LiveKit 은 B5) */
// LiveSessionService 단위 테스트: 배정 때 세션 행 만들기·리셋(ensureCreated), 매니저 앱 장비 상태 줄(deviceStatus·activeJobEquipment),
// 장비 화면 응답(device)과 스트림 키 가리기(maskKey). DB 는 쓰지 않는다 — 저장소(Repository)를 Mockito 목으로 바꿔 끼운다.
class LiveSessionServiceTest {
    // 이 파일만 쓰는 매니저·예약 id (LiveTestSupport 를 쓰지 않는다)
    static final UUID MANAGER = UUID.randomUUID();
    static final UUID BOOKING = UUID.randomUUID();

    // 테스트마다 새 목과 새 서비스가 만들어진다 (JUnit 은 테스트 메서드마다 이 클래스의 객체를 새로 만든다)
    LiveSessionRepository sessions = mock(LiveSessionRepository.class);
    ManagerDeviceRepository devices = mock(ManagerDeviceRepository.class);
    LiveSessionService service = new LiveSessionService(sessions, devices);

    // 처음엔 행이 없어 새로 저장하고, 두 번째엔 있는 행을 그대로 돌려주는지(save 는 한 번뿐) 본다.
    // 덤으로 세션이 막 만들어진 상태면 '영상이 나가는 중' 이 아니고, 행이 없는 예약의 녹화 상태는 none 인지 확인한다
    @Test
    void 세션은_없으면_만들고_있으면_리셋한다() {
        when(sessions.findById(BOOKING)).thenReturn(Optional.empty());
        // thenAnswer: save 에 넘긴 객체를 그대로 돌려준다 (실제 JPA save 가 저장한 엔티티를 돌려주는 것을 흉내)
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        LiveSession created = service.ensureCreated(BOOKING);
        assertThat(created.getRoomName()).isEqualTo("booking-" + BOOKING);
        assertThat(created.getStatus()).isEqualTo(LiveSession.CREATED);

        when(sessions.findById(BOOKING)).thenReturn(Optional.of(created));
        assertThat(service.ensureCreated(BOOKING)).isSameAs(created); // isSameAs = 같은 객체(새로 만들지 않고 reset 만 했다)
        verify(sessions).save(any()); // save 는 첫 호출 때 한 번뿐 — 두 번째 호출은 reset 만 하고 저장은 커밋에 맡긴다
        assertThat(service.isStreaming(BOOKING)).isFalse();
        assertThat(service.recordingStatus(UUID.randomUUID())).isEqualTo("none");
    }

    // 바디캠이 등록되지 않은 매니저: 청소 준비 화면의 장비 4줄은 모두 issue(문제 있음)로 나오고, 장비 화면(device)은 NOT_FOUND 예외.
    // 값 문구는 줄마다 다르다(카메라 '연결 안 됨', 전송 '대기 중', 배터리·네트워크 '확인 필요'). 읽기만 하므로 save 는 없다
    @Test
    void 기기가_없으면_장비_상태는_전부_확인_필요_이고_장비_화면은_404() {
        when(devices.findById(MANAGER)).thenReturn(Optional.empty());
        List<DeviceStatusRow> rows = service.deviceStatus(MANAGER);
        // extracting = 목록의 각 줄에서 한 칸만 뽑아 비교한다. containsExactly = 값과 순서가 정확히 같다
        assertThat(rows).extracting(DeviceStatusRow::label).containsExactly("카메라", "배터리", "네트워크", "실시간 전송");
        assertThat(rows).extracting(DeviceStatusRow::value).containsExactly("연결 안 됨", "확인 필요", "확인 필요", "대기 중");
        assertThat(rows).allMatch(r -> r.status().equals("issue"));
        assertThatThrownBy(() -> service.device(MANAGER)).isInstanceOf(ApiException.class);
        verify(sessions, never()).save(any());
    }

    // 송출 중(online)이고 기기 보고(last_report JSON)에 배터리·네트워크가 있는 기기를 목으로 만든다.
    // 장비 화면은 배터리·네트워크·카메라·전송 순서로 보고 값·'정상' 이 나오고, 스트림 키는 끝 4자만 보인다.
    // 진행 중 화면(activeJobEquipment)은 카메라·배터리·네트워크 3줄이고 카메라 값이 '정상' 이다
    @Test
    void 온라인_기기는_정상_값과_마스킹된_키를_준다() {
        ManagerDevice d = mock(ManagerDevice.class); // 엔티티도 목으로 만들 수 있다 — 아래 when 으로 정한 getter 만 값을 준다
        when(d.isOnline()).thenReturn(true);
        when(d.getName()).thenReturn("안심캠 #AC-00231");
        when(d.getStreamUrl()).thenReturn("rtmp://example.livekit.cloud/x");
        when(d.getStreamKey()).thenReturn("abcd-ef-1234");
        when(d.getLastReport()).thenReturn("{\"battery\":\"82%\",\"network\":\"LTE / 양호\"}");
        when(devices.findById(MANAGER)).thenReturn(Optional.of(d));

        DeviceResponse res = service.device(MANAGER);
        assertThat(res.name()).isEqualTo("안심캠 #AC-00231");
        assertThat(res.connection()).isEqualTo("정상 연결");
        assertThat(res.status()).extracting(DeviceStatusRow::value).containsExactly("82%", "LTE / 양호", "정상", "정상");
        assertThat(res.stream().keyMasked()).isEqualTo("****-**-1234");

        List<DeviceStatusRow> active = service.activeJobEquipment(MANAGER);
        assertThat(active).extracting(DeviceStatusRow::value).containsExactly("정상", "82%", "LTE / 양호");
        assertThat(LiveSessionService.maskKey(null)).isNull(); // 키가 없으면 가린 키도 없다(null)
    }

    // 작업 재개(jobs/resume)는 매니저가 '촬영 다시 시작' 을 누른 것 — 세션을 잠그고 멈춘 표시(videoHeld)를 푼다.
    // 세션이 없으면(라이브를 시작한 적 없음) 아무것도 하지 않는다
    @Test
    void 작업_재개는_세션을_잠그고_멈춘_표시를_푼다() {
        LiveSession s = LiveSession.create(BOOKING);
        s.holdVideo();
        when(sessions.lockById(BOOKING)).thenReturn(Optional.of(s));
        service.jobResumed(BOOKING);
        assertThat(s.isVideoHeld()).isFalse();
        verify(sessions).lockById(BOOKING);

        UUID none = UUID.randomUUID();
        when(sessions.lockById(none)).thenReturn(Optional.empty());
        service.jobResumed(none); // 던지지 않는다
    }
}
