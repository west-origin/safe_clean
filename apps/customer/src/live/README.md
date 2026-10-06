# 고객용 라이브 (시청·녹화본 재생)

화면(`screens/service/*`)은 `useLiveSession(bookingId, { replay })` 의 반환값만 쓴다. LiveKit 은 `roomConnector.js` 에서만 import 한다 (CLAUDE.md §1).

```
live/
  README.md          이 문서
  liveClient.js      앱에 하나뿐인 시청 클라이언트 (서버 세션 → 시청 토큰 → 방 → <video>, 녹화본 재생, 30초 폴링)
  roomConnector.js   livekit-client 를 부르는 유일한 파일. wss:// 가 아니면(로컬 대역·가짜 서버) 연결하지 않고 null
  useLiveSession.js  liveClient 의 상태를 React 에 묶는 훅. 화면은 이것만 import
```

## 흐름 (BACKEND_PLAN.md §5.2·§5.4·§9.3)
1. `join(bookingId)` → `GET /customer/bookings/{id}/live` (매니저·상태 문구·`live.state`) → `state` 에 따라 `status`: waiting → `connecting`, live → `live`, paused → `paused`, ended → `ended`
2. 진행 중이면 `POST /live/sessions/{id}/token` → `roomConnector.connectRoom` → `TrackSubscribed` 에서 `<video>` 에 붙임. 아직 시작 전(`LIVE_NOT_AVAILABLE`)이면 상태 문구만 보여주고 다음 폴링에서 다시 시도
3. 30초마다 세션을 다시 받아 문구·시청자 수·상태를 맞춘다(§13-13). 끝났고 녹화본이 있으면 재생 모드로
4. `replay=true` 또는 끝난 예약: `GET /live/sessions/{id}/recording` 의 서명 URL 을 `<video src>` 로. `seek/back15/forward15/togglePause` 는 이 모드에서만 동작한다
5. `leave()`: 방에서 나가고 `ended`

## 훅 반환값
| 이름 | 설명 |
|---|---|
| `status` | `'idle' \| 'connecting' \| 'live' \| 'paused' \| 'ended' \| 'error'` — 재생 모드에서는 live/paused 가 재생/멈춤 |
| `mode` | `'live' \| 'replay'` |
| `session` | 서버 응답. 재생 모드에서는 `playback` 이 현재 위치로 갱신된다 |
| `videoRef` | `<VideoSurface ref={videoRef}>` |
| `muted` / `toggleMute` | 바디캠은 영상만 보내므로(§13-23) 화면의 음소거 컨트롤은 비활성 |
| `capture` | 현재 프레임 PNG 저장 (사진첩 저장은 B7 Capacitor) |
| `seek` / `back15` / `forward15` / `togglePause` | 녹화본에서만 |
| `leave` | 방 나가기 |

테스트는 `src/test/fakeHttp.js` 로 서버를 대체한다 (`LIVE_BOOKING_ID`, `READY_BOOKING_ID`).
