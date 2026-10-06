package kr.ansimclean.api.live;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** manager_devices — 바디캠 1대 = 매니저 1명 = Ingress 1개(영구, §8.1). 운영자가 /admin/devices 로 등록한다(§13-20) */
// 기본 키가 매니저 id 라 매니저당 한 행이다.
// 쓰는 곳 예: DeviceService(등록·키 회전), LiveWebhookService(online/offline·미룬 방 옮기기), LiveService(방 묶기·Ingress 잠금).
// DynamicUpdate = 저장할 때 바뀐 컬럼만 UPDATE 문에 넣는다
@Entity
@DynamicUpdate // 웹훅(stream_status)·start(pending_room) 가 겹칠 때 전체 컬럼 UPDATE 가 앞 커밋을 되돌리지 않게 (검수 3차)
@Table(name = "manager_devices")
public class ManagerDevice {
    // stream_status 값: 바디캠이 LiveKit 에 영상을 보내는 중(online)인지. ingress_started/ended 웹훅이 바꾸고, 웹훅을 놓치면 재조정기가 맞춘다
    public static final String ONLINE = "online";
    public static final String OFFLINE = "offline";

    @Id
    @Column(name = "manager_id", columnDefinition = "uuid")
    private UUID managerId;

    @Column(columnDefinition = "text", nullable = false)
    private String name; // 기기 표시 이름 (운영자가 등록, 장비 화면에 보인다)

    @Column(columnDefinition = "text")
    private String serial; // 기기 일련번호

    @Column(name = "ingress_id", columnDefinition = "text")
    private String ingressId; // 이 바디캠 전용 LiveKit Ingress id (영상 입구)

    @Column(name = "stream_url", columnDefinition = "text")
    private String streamUrl; // 기기에 한 번 설정하는 RTMP 주소

    @Column(name = "stream_key", columnDefinition = "text")
    private String streamKey; // 전체 스트림 키 — 앱 화면에는 끝 4자만 (LiveSessionService.maskKey)

    @Column(name = "stream_status", columnDefinition = "text", nullable = false)
    private String streamStatus;

    @Column(name = "last_online_at")
    private Instant lastOnlineAt;

    @Column(name = "pending_room", columnDefinition = "text")
    private String pendingRoom; // 송출 중이라 바로 못 옮긴 목적지 방. 송출이 끝나면(ingress_ended)·재조정기가 옮긴다

    // JdbcTypeCode(JSON) = Postgres jsonb 컬럼을 자바에서는 JSON 문자열로 읽고 쓴다. 모양 { battery, network }, 없으면 null
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_report", columnDefinition = "jsonb")
    private String lastReport;

    @Column(name = "reported_at")
    private Instant reportedAt; // last_report 를 받은 시각

    /** LiveKit Ingress 가 새 연결을 받는가 — 일시정지 중 카메라가 끊겨 있으면 false 로 잠근다 (감사 H-02, V025) */
    @Column(name = "ingress_enabled", nullable = false)
    private boolean ingressEnabled = true;

    protected ManagerDevice() {} // JPA 가 DB 행을 객체로 만들 때 쓰는 빈 생성자

    /** 새 기기 행 (DeviceService 가 처음 등록할 때). 처음에는 offline */
    public static ManagerDevice register(UUID managerId, String name, String serial, String ingressId, String streamUrl, String streamKey) {
        ManagerDevice d = new ManagerDevice();
        d.managerId = managerId;
        d.streamStatus = OFFLINE;
        d.update(name, serial, ingressId, streamUrl, streamKey);
        return d;
    }

    public UUID getManagerId() { return managerId; }
    public String getName() { return name; }
    public String getSerial() { return serial; }
    public String getIngressId() { return ingressId; }
    public String getStreamUrl() { return streamUrl; }
    public String getStreamKey() { return streamKey; }
    public String getStreamStatus() { return streamStatus; }
    public Instant getLastOnlineAt() { return lastOnlineAt; }
    public String getPendingRoom() { return pendingRoom; }
    public String getLastReport() { return lastReport; }
    public Instant getReportedAt() { return reportedAt; }

    public boolean isOnline() {
        // 바디캠이 지금 영상을 보내는 중인가 (대기 방이든 작업 방이든)
        return ONLINE.equals(streamStatus);
    }

    /** 대기 방 이름 (작업이 없을 때 Ingress 가 가리키는 곳 — 아무도 못 본다) */
    public String parkingRoom() {
        return "parking-" + managerId;
    }

    /** 바디캠 참가자 identity */
    public String cameraIdentity() {
        return "camera-" + managerId;
    }

    /** 등록 정보 덮어쓰기 (DeviceService 재등록·키 회전, register 도 이걸로 채운다). Ingress 가 바뀌면 잠금 표시를 연 상태로 되돌린다 */
    public void update(String name, String serial, String ingressId, String streamUrl, String streamKey) {
        if (this.ingressId == null || !this.ingressId.equals(ingressId)) this.ingressEnabled = true; // 새 Ingress(키 회전)는 열린 채 만들어진다
        this.name = name;
        this.serial = serial;
        this.ingressId = ingressId;
        this.streamUrl = streamUrl;
        this.streamKey = streamKey;
    }

    /** 송출 중으로 적는다(ingress_started 웹훅·카메라 붙음 처리·재조정기) — online + 마지막 연결 시각 */
    public void markOnline(Instant now) {
        streamStatus = ONLINE;
        lastOnlineAt = now;
    }

    /** 송출 끝으로 적는다(ingress_ended 웹훅·재조정기). 마지막 연결 시각은 남겨 둔다 */
    public void markOffline() {
        streamStatus = OFFLINE;
    }

    /** 송출 중이라 못 바꾼 방 — ingress_ended 때(또는 재조정기가) 적용 */
    public void setPendingRoom(String room) {
        pendingRoom = room;
    }

    /** 방 옮기기에 성공했다 — 미뤄 둔 방을 지운다 (LiveService.bindIngress) */
    public void clearPendingRoom() {
        pendingRoom = null;
    }

    // 잠금 표시 읽기·쓰기. LiveService.lockIngress/unlockIngress 가 LiveKit 호출이 성공한 뒤에 바꾼다
    public boolean isIngressEnabled() {
        return ingressEnabled;
    }

    public void setIngressEnabled(boolean enabled) {
        ingressEnabled = enabled;
    }

    /** 기기가 보낸 배터리·네트워크 상태(JSON)를 저장한다. 지금 main 코드에서 부르는 곳은 없다 (기기 상태 API 가 생기면 쓸 자리, V001 스키마 주석) */
    public void report(String json, Instant now) {
        lastReport = json;
        reportedAt = now;
    }
}
