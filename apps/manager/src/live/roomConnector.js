// LiveKit 방 연결 — 앱에서 livekit-client 를 import 하는 유일한 파일 (CLAUDE.md §1: 화면·훅은 WebRTC API 를 모른다).
// 두 앱 모두 **시청 전용**이다(영상은 바디캠 → LiveKit Ingress). 발행(publishTrack)은 어디에도 없다.
// wss:// 가 아닌 주소(로컬 대역 ws://localhost, 테스트의 가짜 서버)에는 연결하지 않고 null 을 돌려준다 — 서버 상태 폴링만으로 화면이 돈다.
// 매니저 앱에서는 liveClient.js 의 connectPreview 가 부른다 (바디캠 프리뷰).

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
