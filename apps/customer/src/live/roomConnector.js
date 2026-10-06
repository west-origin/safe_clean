// LiveKit 방 연결 — 앱에서 livekit-client 를 import 하는 유일한 파일 (CLAUDE.md §1: 화면·훅은 WebRTC API 를 모른다).
// 두 앱 모두 **시청 전용**이다(영상은 바디캠 → LiveKit Ingress). 발행(publishTrack)은 어디에도 없다.
// wss:// 가 아닌 주소(로컬 대역 ws://localhost, 테스트의 가짜 서버)에는 연결하지 않고 null 을 돌려준다 — 서버 상태 폴링만으로 화면이 돈다.

/**
 * @param {{ url: string, token: string, onVideoTrack?: (track: import('livekit-client').RemoteTrack) => void,
 *   onMuted?: (muted: boolean) => void, onDisconnected?: () => void }} opts
 * @returns {Promise<import('livekit-client').Room | null>}
 * 방에 들어가 콜백으로 알린다: 영상 트랙을 받으면 onVideoTrack, 영상 트랙이 음소거·해제되면 onMuted(true/false),
 * 연결이 끊기면 onDisconnected. liveClient._connectLive 가 시청 토큰을 받은 뒤 부른다. 방을 나가는 것은 돌려준 Room 의 disconnect().
 */
export async function connectRoom({ url, token, onVideoTrack, onMuted, onDisconnected }) {
  if (!/^wss:\/\//.test(url ?? '')) return null;
  // 동적 import: livekit-client 는 별도 청크로 나뉘어 여기까지 왔을 때 처음 내려받는다 (wss 가 아니면 불러오지도 않는다)
  const { Room, RoomEvent, Track } = await import('livekit-client');
  const room = new Room({ adaptiveStream: true }); // adaptiveStream: 붙인 <video> 의 크기·보임 여부에 맞춰 받는 화질을 조절한다
  // 이벤트를 먼저 달고 connect 한다. connect 가 끝나면 아래에서 이미 발행돼 있던 트랙을 한 번 더 훑는다
  room.on(RoomEvent.TrackSubscribed, (track) => {
    if (track.kind === Track.Kind.Video) onVideoTrack?.(track);
  });
  // 영상 트랙의 음소거·해제만 알린다 (liveClient 는 음소거를 '일시정지' 로 보여 준다). 소리 트랙은 무시한다
  room.on(RoomEvent.TrackMuted, (publication) => {
    if (publication.kind === Track.Kind.Video) onMuted?.(true);
  });
  room.on(RoomEvent.TrackUnmuted, (publication) => {
    if (publication.kind === Track.Kind.Video) onMuted?.(false);
  });
  room.on(RoomEvent.Disconnected, () => onDisconnected?.());
  await room.connect(url, token);
  // 들어가기 전에 이미 발행돼 있던 트랙
  room.remoteParticipants.forEach((participant) => {
    participant.videoTrackPublications.forEach((publication) => {
      if (publication.track) onVideoTrack?.(publication.track);
    });
  });
  return room;
}
