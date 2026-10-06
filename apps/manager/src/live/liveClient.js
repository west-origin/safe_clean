// 매니저용 라이브 클라이언트. 앱 전체에서 하나만 있는 객체이며, 화면(Preparing → InProgress → Paused → Complete) 사이를 오가도 상태가 유지된다.
// 단계 5(B5) 에서 채움. **휴대폰은 송출하지 않는다** — 영상은 바디캠 → LiveKit Ingress(§8). 이 클라이언트는 서버 세션(start/pause/resume/stop)을 부르고,
// 프리뷰 토큰으로 방에 시청자로 들어가 <video> 에 붙인다. 상태는 3초마다 서버에서 다시 받는다.
// pause()/resume() 은 **영상만** 책임진다(§8.2.1) — 작업 상태는 화면이 pauseJob/resumeJob 을 따로 부른다.
// 순서가 정해져 있고 서버가 강제한다: 일시정지는 pause()(영상 차단) → pauseJob, 재개는 resumeJob → resume()(영상).
// 순서를 어기면 서버가 409(INVALID_STATE)로 거절한다 — 영상이 나가는데 작업은 '일시정지' 로 보이는 순간을 만들지 않으려는 것.
// 화면 코드는 useLiveSession() 훅을 거쳐 쓴다 (테스트는 이 객체를 직접 부른다).
import { startLiveSession, getLiveSessionStatus, pauseLiveSession, resumeLiveSession, stopLiveSession } from '../api/manager.js';
import { connectRoom } from './roomConnector.js';
import { session } from '../api/session.js';

/** @typedef {'idle'|'connecting'|'live'|'paused'|'ended'|'error'} LiveStatus */

const STATUS_POLL_MS = 3000; // 서버 상태 폴링 주기
/** cameraBusy 일 때 start 를 다시 부르는 최소 간격 */
const BUSY_START_RETRY_MS = 30_000;
let lastBusyStart = 0; // cameraBusy 때문에 start 를 마지막으로 다시 부른 시각(ms)
/** start 가 실패하면(순간 네트워크·LiveKit 장애) 이 간격으로 다시 시도한다 — 한 번 실패로 그 작업의 영상·녹화가 통째로 빠지지 않게 */
const START_RETRY_MS = 10000;

// 상태는 바뀔 때마다 새 객체로 만든다(useSyncExternalStore 가 참조 비교로 변경을 알아채기 때문)
let state = {
  /** @type {LiveStatus} connecting = 방은 열렸고 바디캠을 기다리는 중 */
  status: 'idle',
  /** 진행 중인 작업 id */
  jobId: null,
  /** 세션 시작 시각 (ms). 훅이 내보내지만, 지금 화면의 경과 시간은 작업 정보(job.startedAt)로 계산한다 (자체 점검 #43) */
  startedAt: null,
  /** 바디캠이 붙어 있는가 */
  cameraOnline: false,
  /** 바디캠이 대기 방에 송출 중이라 이 방으로 못 옮김 → "바디캠을 껐다가 다시 켜주세요" */
  cameraBusy: false,
  /** 서버가 '매니저가 영상을 멈추고 아직 재개하지 않음' 으로 안다 (video_held). 작업이 진행 중인데 true 면 진행 화면이 '작업 상태를 바꾸지 못했어요' 시트를 다시 띄운다 */
  held: false,
  /** @type {HTMLVideoElement|null} 프리뷰 <video> */
  videoEl: null,
  error: null, // start 가 실패했을 때의 오류 객체
};

const listeners = new Set(); // subscribe() 로 등록된 함수들 (useLiveSession)
let room = null; // 프리뷰로 들어간 LiveKit 방 (roomConnector 가 돌려준 Room). 없으면 null
let videoTrack = null; // 바디캠 영상 트랙 — <video> 가 나중에 등록돼도 붙일 수 있게 들고 있는다
let preview = null; // { url, token } — 서버가 start 응답으로 준 프리뷰 접속 정보. 연결이 끊기거나 처음에 실패하면 폴링 때 다시 붙는다 (외부 검토 4차)
let previewConnecting = false; // 연결 시도 중 — 폴링이 겹쳐도 두 번 붙지 않게
let previewRetryAt = 0; // 실패하면 이 시각 전에는 다시 붙지 않는다 (10초 → 최대 60초, 폴링마다 두드리지 않게)
let previewBackoffMs = 0;
const PREVIEW_RETRY_MS = 10_000;
const PREVIEW_RETRY_MAX_MS = 60_000;
let pollTimer = null; // 3초 폴링 setInterval 핸들
let retryTimer = null; // start 실패 뒤 재시도 setTimeout 핸들
/** start/stop/reset 마다 +1 — 늦게 도착한 start 응답·프리뷰 트랙이 끝난 세션을 되살리지 않게 */
let seq = 0;
/** pause/resume/stop 마다 +1 — 그 전에 나간 폴링 응답이 방금 바뀐 상태를 덮지 않게 (세션 자체는 그대로라 seq 와 분리한다) */
let pollSeq = 0;

/** 상태 일부를 바꾸고(새 객체) 구독자를 깨운다 */
function set(patch) {
  state = { ...state, ...patch };
  listeners.forEach((fn) => fn());
}

/** 서버 세션 status → 화면 status */
// 방만 열렸거나(created) 바디캠을 기다리면(waiting_camera) 둘 다 connecting. 모르는 값도 connecting 으로 본다
const toStatus = (s) => ({ created: 'connecting', waiting_camera: 'connecting', live: 'live', paused: 'paused', ended: 'ended' })[s] ?? 'connecting';

/** 폴링 타이머와 start 재시도 타이머를 모두 멈춘다 (start 새로 시작·세션 끝·stop·reset 때) */
function stopPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = null;
  lastPoll = null;
  if (retryTimer) clearTimeout(retryTimer);
  retryTimer = null;
}
/** 화면이 안 보이면(앱 전환·백그라운드) 폴링은 쉬고, 돌아오면 바로 한 번 받는다 (검증 검토 — 백그라운드에서도 계속 돌았다) */
const hidden = () => typeof document !== 'undefined' && document.visibilityState === 'hidden';
let lastPoll = null; // 마지막으로 시작한 폴링 (돌아왔을 때 즉시 갱신용)
// document 가 없는 환경(브라우저 밖)에서도 이 파일을 불러올 수 있게 확인한다
if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (!hidden() && pollTimer && lastPoll) lastPoll();
  });
}


/** 프리뷰(LiveKit 방) 연결. 실패해도 세션은 유효하다 — room 을 null 로 두면 다음 폴링이 다시 시도한다 */
async function connectPreview(mySeq) {
  // 접속 정보가 없거나, 이미 붙었거나, 붙는 중이거나, 실패 뒤 대기 시간이면 아무것도 안 한다
  if (!preview || room || previewConnecting || Date.now() < previewRetryAt) return;
  previewConnecting = true;
  try {
    const connected = await connectRoom({
      url: preview.url,
      token: preview.token,
      onVideoTrack: (track) => {
        if (mySeq !== seq) return; // 그 사이 stop/reset·새 start 가 있었다 — 옛 세션의 트랙은 버린다
        videoTrack = track;
        if (state.videoEl) track.attach(state.videoEl); // <video> 가 이미 있으면 바로 붙인다 (없으면 attachVideo 가 붙인다)
      },
      onDisconnected: () => {
        if (room === connected) room = null; // 다음 폴링이 다시 붙는다
      },
    });
    if (mySeq !== seq) connected?.disconnect(); // 붙는 사이 세션이 바뀌었으면 바로 끊는다
    else room = connected; // wss:// 가 아니면 connectRoom 이 null 을 준다 — room 은 null 로 남는다
    previewBackoffMs = 0;
  } catch {
    // 실패: 다음 시도까지 10초 → 20초 → 40초 → 60초(최대)로 기다리는 시간을 늘린다
    room = null;
    previewBackoffMs = Math.min(previewBackoffMs ? previewBackoffMs * 2 : PREVIEW_RETRY_MS, PREVIEW_RETRY_MAX_MS);
    previewRetryAt = Date.now() + previewBackoffMs;
  } finally {
    previewConnecting = false;
  }
}

/** 프리뷰 방을 끊고 트랙·접속 정보·재시도 대기값을 비운다 (start 새로 시작·stop·reset 때) */
function disconnectPreview() {
  if (room) room.disconnect();
  room = null;
  preview = null; // stop/reset 뒤 폴링이 옛 토큰으로 다시 붙지 않게
  previewRetryAt = 0;
  previewBackoffMs = 0;
  if (videoTrack) videoTrack.detach();
  videoTrack = null;
}

/** 앱에 하나뿐인 라이브 클라이언트. 메서드는 this 를 쓰지 않아 떼어서 넘겨도 된다 (useLiveSession 이 그렇게 쓴다) */
export const liveClient = {
  getState: () => state, // 지금 상태 (바뀔 때마다 새 객체)
  /** 상태 변화 구독. 해제 함수를 돌려준다 (useSyncExternalStore 가 쓰는 형식) */
  subscribe(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },

  /** 프리뷰 <video> 등록 */
  // 화면의 ref 로 쓰여 <video> 가 생길 때 요소를, 사라질 때 null 을 받는다
  attachVideo(videoEl) {
    set({ videoEl });
    if (videoEl && videoTrack) videoTrack.attach(videoEl); // 트랙이 먼저 와 있었으면 바로 붙인다
  },

  /**
   * 라이브 세션 시작 (Job/Preparing › 청소 시작, startJob 다음에). 서버가 방을 만들고 바디캠 Ingress 를 묶는다.
   * 같은 작업으로 다시 불러도 세션은 하나다. 서버 오류는 던지지 않고 status='error' + error 로 남긴다(작업은 이미 시작됐으므로 화면은 진행한다)
   */
  async start(jobId) {
    // 같은 작업의 세션이 이미 살아 있으면(연결 대기·송출·일시정지) 다시 열지 않는다. idle·error·ended 면 새로 시작한다
    if (state.jobId === jobId && ['connecting', 'live', 'paused'].includes(state.status)) return;
    // 옛 폴링·프리뷰를 정리하고 새 세션 번호(seq)를 받는다
    stopPolling();
    disconnectPreview();
    const mySeq = ++seq;
    const startedAt = state.jobId === jobId && state.startedAt ? state.startedAt : null; // 같은 작업의 재시도만 시작 시각을 잇는다 (검수 4차: 앞 작업의 시각이 다음 작업으로 넘어갔다)
    set({ status: 'connecting', jobId, error: null, cameraBusy: false, cameraOnline: false, startedAt });
    try {
      const res = await startLiveSession(jobId);
      if (mySeq !== seq) return; // 그 사이 stop/reset 됐다
      set({ status: toStatus(res.status), cameraOnline: res.cameraOnline, cameraBusy: res.cameraBusy, startedAt: startedAt ?? Date.now() });
      preview = res.preview ?? null;
      await connectPreview(mySeq);
      if (mySeq !== seq) return;
      // 3초마다 refresh. 화면이 숨겨져 있으면 그 차례는 건너뛴다
      lastPoll = () => liveClient.refresh(jobId, mySeq);
      pollTimer = setInterval(() => !hidden() && lastPoll(), STATUS_POLL_MS);
    } catch (error) {
      if (mySeq !== seq) return;
      set({ status: 'error', error });
      // 순간 장애면 다음 시도가 붙는다. 서버가 거절하는 경우(끝난 작업·바디캠 미등록 등 409, 내 작업이 아님 404)는 재시도하지 않는다
      if (error?.status !== 409 && error?.status !== 404) retryTimer = setTimeout(() => liveClient.start(jobId), START_RETRY_MS);
    }
  },

  /** 3초마다 서버 상태. 바디캠이 대기 방에 붙어 있으면(cameraBusy) start 를 다시 불러 서버가 방 묶기를 재시도하게 한다 (§8.1).
   *  프리뷰 연결이 없으면(처음 실패·끊김) 여기서 다시 붙는다 */
  async refresh(jobId, mySeq = seq) {
    const myPoll = pollSeq; // 이 폴링이 나갈 때의 번호 — 응답이 왔을 때 바뀌었으면 버린다
    try {
      // 바디캠이 대기 방에 붙어 있으면(cameraBusy) 서버가 ingress_ended 웹훅·재조정기로 방을 다시 묶는다 — 앱의 start 재호출은 30초에 한 번만 (3초마다 LiveKit API 를 두드리던 것, 검증 검토)
      const retryStart = state.cameraBusy && Date.now() - lastBusyStart >= BUSY_START_RETRY_MS;
      if (retryStart) lastBusyStart = Date.now();
      const res = retryStart ? await startLiveSession(jobId) : await getLiveSessionStatus(jobId);
      if (mySeq !== seq || myPoll !== pollSeq) return; // stop 뒤·pause/resume 전에 나간 응답은 버린다
      if (res.preview) preview = res.preview; // start 응답에만 있다 — 새 프리뷰 접속 정보로 바꾼다
      set({ status: toStatus(res.status), cameraOnline: res.cameraOnline, cameraBusy: Boolean(res.cameraBusy), held: Boolean(res.held) });
      if (res.status === 'ended') stopPolling();
      else if (!room) connectPreview(mySeq); // 기다리지 않는다 — 폴링 주기를 막지 않게
    } catch {
      // 잠깐의 실패는 다음 폴링이 메운다
    }
  },

  /** 촬영 일시정지 ① — 서버가 바디캠 트랙 mute 를 확인한 뒤에만 돌아온다. 실패하면 throw (화면은 일시정지 화면으로 가지 않는다).
   *  jobId 는 화면이 서버에서 받은 작업 id — 앱을 다시 열어 세션 상태가 비어 있어도(재진입) 맞는 작업에 보낸다 */
  async pause(jobId = state.jobId) {
    if (!jobId) throw new Error('liveClient.pause: 작업 id 가 없어요');
    if (state.jobId !== jobId) set({ jobId }); // 재진입으로 상태가 비어 있었으면 작업 id 를 채운다
    let res;
    try {
      res = await pauseLiveSession(jobId);
    } catch (e) {
      // 502(확인 실패) = 서버가 '계속 전송' 으로 보고 멈춤 표시를 지웠다. 통신 오류(결과 모름)는 다음 폴링이 서버 값을 가져온다
      if (e?.code === 'LIVE_PAUSE_UNCONFIRMED') set({ held: false });
      throw e;
    }
    pollSeq++; // 이 응답 전에 나간 폴링이 되돌리지 못하게 (프리뷰 트랙 콜백은 seq 를 보므로 카메라 재접속은 그대로 붙는다)
    // 바디캠이 아직 안 붙었으면 서버는 막을 영상이 없어 created/waiting_camera 상태로 200 을 준다 (LiveService.pause) — 이때도 화면은 일시정지로 본다
    set({ status: toStatus(res.status) === 'connecting' ? 'paused' : toStatus(res.status), held: true }); // 200 = 서버가 매니저가 재개할 때까지 막아 둔다
  },

  /** 촬영 다시 시작 ② — unmute 확인 뒤 live. 실패하면 throw.
   *  작업 재개(resumeJob, ①)가 먼저 끝나 있어야 한다 — 작업이 아직 일시정지면 서버가 409. 바디캠이 안 붙어 있으면 connecting 으로 남는다 */
  async resume(jobId = state.jobId) {
    if (!jobId) throw new Error('liveClient.resume: 작업 id 가 없어요');
    if (state.jobId !== jobId) set({ jobId });
    const res = await resumeLiveSession(jobId);
    pollSeq++;
    set({ status: toStatus(res.status), held: false }); // 재개 요청이 닿았다 — 서버도 멈춤 표시를 지웠다
  },

  /** 세션 종료 (Job/InProgress › 청소 완료 뒤). 서버가 녹화를 멈추고 방을 닫는다.
   *  작업이 완료(또는 취소)되기 전이면 서버가 409 로 거절한다 — 그래서 화면은 completeJob 다음에 부른다 */
  async stop(jobId = state.jobId) {
    seq++;
    pollSeq++;
    stopPolling();
    // 서버 호출이 실패해도(throw 는 그대로 부른 쪽으로 간다) 앱 쪽 프리뷰·상태는 정리한다
    try {
      if (jobId) await stopLiveSession(jobId);
    } finally {
      disconnectPreview();
      set({ status: 'ended', cameraOnline: false, cameraBusy: false, startedAt: null }); // jobId 는 남긴다 — 진행 중 화면이 끝난 작업에 start 를 다시 보내지 않게
    }
  },

  /** 로그아웃·탈퇴: 서버는 부르지 않고 폴링·프리뷰·상태를 비운다 (토큰 없는 폴링이 계속 나가지 않게) */
  reset() {
    seq++;
    pollSeq++;
    stopPolling();
    disconnectPreview();
    set({ status: 'idle', jobId: null, startedAt: null, cameraOnline: false, cameraBusy: false, held: false, error: null });
  },
};

// 로그아웃·탈퇴·401 로 세션이 사라지면 폴링·프리뷰·상태를 비운다 (토큰 없는 폴링이 3초마다 계속 나가지 않게)
session.subscribe(() => {
  if (!session.accessToken() && state.status !== 'idle') liveClient.reset();
});
