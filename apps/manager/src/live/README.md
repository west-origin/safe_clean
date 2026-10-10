# 매니저용 라이브 (세션 제어 + 휴대폰 카메라 송출)

**휴대폰 후면 카메라로 송출한다** (2026-10-10, BACKEND_PLAN.md §8.0 — 서버 `LIVE_SOURCE=app`, 기본). 청소 준비 화면에서 `prepare()` 가 카메라를 켜 미리보기를 보이고, `start()` 가 서버의 송출 토큰으로 같은 트랙을 publish 한다. 바디캠 모드(`ingress`)면 예전처럼 프리뷰 토큰으로 시청만 한다.
화면 4개(Job/Preparing → InProgress → Paused → Complete)는 `useLiveSession()` 만 쓴다.

```
live/
  README.md          이 문서
  liveClient.js      앱에 하나뿐인 클라이언트 (prepare/start/pause/resume/stop = 서버 세션, 3초 폴링, 송출·미리보기·재연결)
  sources/           영상 입력만 (cameraVideoSource.js = 휴대폰 후면 카메라, index.js 가 고른다). LiveKit 을 모른다 — 바디캠 SDK 등은 여기에 파일을 더한다
  roomConnector.js   livekit-client 를 부르는 유일한 파일 (publishRoom = 송출, connectRoom = 바디캠 프리뷰 시청). wss:// 가 아니면 연결하지 않는다
  useLiveSession.js  liveClient 의 상태를 React 에 묶는 훅
```

## 화면 ↔ 훅 ↔ 서버 (§8.2.1 순서)
| 화면 | 버튼 | 순서 | status |
|---|---|---|---|
| Job/Preparing | 청소 시작 | `startJob(id)` → `start(id)` = `POST /live/sessions/{id}/start` | `connecting`(바디캠 대기) → 바디캠이 붙으면 `live` |
| Job/InProgress | 촬영 일시정지 | `pause()` = `POST …/pause`(mute 확인 뒤 200) → `pauseJob()` | `paused` (실패하면 화면 이동 없음) |
| Job/Paused | 촬영 다시 시작 | `resumeJob()` → `resume()` = `POST …/resume` | `live` |
| Job/InProgress | 청소 완료 | `completeJob()` → `stop(jobId)` = `POST …/stop` (서버는 진행 중 작업의 stop 을 거절한다. 실패는 재조정기가 정리) | `ended` |

- `cameraBusy`: 바디캠이 대기 방에 송출 중이라 이 방으로 못 옮김 → "바디캠을 껐다가 다시 켜주세요". 폴링 때 `start` 를 다시 불러 서버가 재시도한다
- `start` 의 서버 오류(기기 미등록 등)는 던지지 않고 `status='error'` 로 남긴다 — 작업은 이미 시작됐으므로 화면은 진행 화면으로 간다
- 카메라·마이크 권한을 Info.plist·AndroidManifest 에 둔다 (제거 금지 — 2026-10-10 사용자 지시). 지금은 영상만 송출하므로 마이크는 요청하지 않는다
- 일시정지는 서버가 휴대폰 트랙을 mute 하고 확인한다. 재개 때 원격 unmute 가 막혀 있으면(LiveKit 프로젝트 설정) 앱이 스스로 풀고 서버 확인을 한 번 더 받는다
- 송출 중에는 화면 꺼짐 방지(keep-awake). 앱이 백그라운드에 다녀오면 카메라를 다시 켜 송출 트랙을 바꿔 끼운다
