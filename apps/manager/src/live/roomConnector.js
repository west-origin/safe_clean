// LiveKit 방 연결 — 앱에서 livekit-client 를 import 하는 유일한 파일 (CLAUDE.md §1: 화면·훅은 WebRTC API 를 모른다).
// 매니저 앱의 두 길:
//  - publishRoom: 휴대폰 송출(LIVE_SOURCE=app, 기본 — BACKEND_PLAN.md §8.0). 영상 입력(sources/)이 준 트랙을 방에 publish 한다.
//    입력이 무엇이든(카메라·나중의 바디캠 SDK) MediaStreamTrack 하나만 받으므로 이 함수는 바뀌지 않는다.
//  - connectRoom: 바디캠(ingress) 모드의 프리뷰 시청. 지금은 쓰지 않는다.
// wss:// 가 아닌 주소(로컬 대역 ws://localhost, 테스트의 가짜 서버)에는 연결하지 않고 null 을 돌려준다 — 서버 상태 폴링만으로 화면이 돈다.

/**
 * 송출용 방 연결: 서버가 준 송출 토큰(identity camera-매니저id, 카메라만 publish)으로 들어가 영상 트랙을 올린다.
 * 연결이 잠깐 끊기면 livekit-client 가 스스로 다시 붙는다(onReconnecting → onReconnected). 완전히 끊기면 onDisconnected — liveClient 가 다시 붙인다.
 * 서버가 일시정지로 트랙을 mute 하면 SDK 가 보내기를 멈추고 onMuted(true) 를 부른다.
 * @param {{ url: string, token: string, mediaTrack: MediaStreamTrack,
 *   onReconnecting?: () => void, onReconnected?: () => void, onDisconnected?: () => void, onMuted?: (muted: boolean) => void }} opts
 * @returns {Promise<{ disconnect: () => Promise<void>, replaceTrack: (t: MediaStreamTrack) => Promise<void>, unmute: () => Promise<void> } | null>}
 */
export async function publishRoom({ url, token, mediaTrack, onReconnecting, onReconnected, onDisconnected, onMuted }) {
  if (!/^wss:\/\//.test(url ?? '')) return null;
  const { Room, RoomEvent, Track, LocalVideoTrack } = await import('livekit-client');
  const room = new Room({ adaptiveStream: false, dynacast: true }); // dynacast: 보는 사람이 없으면 해상도 층을 끄고 데이터를 아낀다
  room.on(RoomEvent.Reconnecting, () => onReconnecting?.());
  room.on(RoomEvent.Reconnected, () => onReconnected?.());
  room.on(RoomEvent.Disconnected, () => onDisconnected?.());
  room.on(RoomEvent.TrackMuted, (publication, participant) => {
    if (participant === room.localParticipant && publication.kind === Track.Kind.Video) onMuted?.(true);
  });
  room.on(RoomEvent.TrackUnmuted, (publication, participant) => {
    if (participant === room.localParticipant && publication.kind === Track.Kind.Video) onMuted?.(false);
  });
  await room.connect(url, token); // 실패하면 throw — liveClient 가 받아 다시 시도한다
  // userProvidedTrack=true: 이 트랙은 입력(sources/)이 관리한다 — 내리거나 끊어도 SDK 가 카메라를 끄지 않는다
  const local = new LocalVideoTrack(mediaTrack, undefined, true);
  try {
    await room.localParticipant.publishTrack(local, { source: Track.Source.Camera, name: 'camera', simulcast: true });
  } catch (e) {
    await room.disconnect();
    throw e;
  }
  return {
    // 트랙을 내리고(카메라는 끄지 않는다) 방을 나간다
    async disconnect() {
      try {
        await room.localParticipant.unpublishTrack(local, false);
      } catch {
        // 이미 끊긴 방 — 나가기만 한다
      }
      await room.disconnect();
    },
    // 입력이 새 트랙을 줬을 때(카메라가 끊겼다 다시 켜짐) 같은 발행 자리에 바꿔 끼운다 — 고객 화면이 끊기지 않게
    async replaceTrack(next) {
      await local.replaceTrack(next, { userProvidedTrack: true });
    },
    // 재개 때 스스로 mute 를 푼다 — 서버의 원격 unmute 가 프로젝트 설정으로 막혀 있어도 영상이 다시 나가게 (§8.0)
    async unmute() {
      if (local.isMuted) await local.unmute();
    },
  };
}

// LiveKit 방에 시청자로 들어가, 바디캠 영상 트랙이 보이면 onVideoTrack 으로 넘긴다. 끊을 때는 돌려받은 room.disconnect()
/**
 * @param {{ url: string, token: string, onVideoTrack?: (track: import('livekit-client').RemoteTrack) => void,
 *   onMuted?: (muted: boolean) => void, onDisconnected?: () => void }} opts
 * @returns {Promise<import('livekit-client').Room | null>}
 */
export async function connectRoom({ url, token, onVideoTrack, onMuted, onDisconnected }) {
  if (!/^wss:\/\//.test(url ?? '')) return null;
  // 동적 import: livekit-client 는 처음 연결할 때 불러온다 (앱 시작 때 읽지 않는다)
  const { Room, RoomEvent, Track } = await import('livekit-client');
  const room = new Room({ adaptiveStream: true }); // adaptiveStream: 붙인 <video> 크기·보임 여부에 맞춰 받는 화질을 조절한다
  // 방에 들어온 트랙을 구독하게 되면 영상만 넘긴다 (오디오는 무시)
  room.on(RoomEvent.TrackSubscribed, (track) => {
    if (track.kind === Track.Kind.Video) onVideoTrack?.(track);
  });
  // 영상이 막히거나(mute) 다시 나오면 알린다. 매니저 liveClient 는 onMuted 를 넘기지 않는다
  room.on(RoomEvent.TrackMuted, (publication) => {
    if (publication.kind === Track.Kind.Video) onMuted?.(true);
  });
  room.on(RoomEvent.TrackUnmuted, (publication) => {
    if (publication.kind === Track.Kind.Video) onMuted?.(false);
  });
  room.on(RoomEvent.Disconnected, () => onDisconnected?.()); // 끊기면 알린다 — liveClient 는 room 을 비워 다음 폴링이 다시 붙게 한다
  await room.connect(url, token); // 실패하면 throw — liveClient 가 받아 대기 시간을 늘린다
  // 들어가기 전에 이미 발행돼 있던 트랙
  room.remoteParticipants.forEach((participant) => {
    participant.videoTrackPublications.forEach((publication) => {
      if (publication.track) onVideoTrack?.(publication.track);
    });
  });
  return room;
}
