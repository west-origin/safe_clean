# 매니저용 라이브 (세션 제어 + 바디캠 프리뷰)

**휴대폰은 송출하지 않는다.** 영상은 별도 바디캠 → LiveKit Ingress(BACKEND_PLAN.md §8). 이 클라이언트는 서버 세션을 열고 닫으며, 프리뷰 토큰으로 방에 시청자로 들어가 바디캠 영상을 `<video>` 에 붙인다.
화면 4개(Job/Preparing → InProgress → Paused → Complete)는 `useLiveSession()` 만 쓴다.

```
live/
  README.md          이 문서
  liveClient.js      앱에 하나뿐인 클라이언트 (start/pause/resume/stop = 서버 세션, 3초 폴링, 프리뷰)
  roomConnector.js   livekit-client 를 부르는 유일한 파일 (시청 전용). wss:// 가 아니면 연결하지 않는다
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
- 카메라·마이크 권한은 필요 없다 (Info.plist·AndroidManifest 에서 제거, B5)
