// 매니저용 라이브 클라이언트. 앱 전체에서 하나만 있는 객체이며, 화면(Preparing → InProgress → Paused → Complete) 사이를 오가도 상태가 유지된다.
// 영상 출처는 서버 설정(source)을 따른다:
//  - app(기본, BACKEND_PLAN.md §8.0): **휴대폰 후면 카메라를 송출한다.** prepare() 가 청소 준비 화면에서 카메라를 켜 미리보기를 보이고,
//    start() 가 서버의 송출 토큰으로 방에 들어가 같은 트랙을 publish 한다. 영상 입력은 sources/ 만, LiveKit 은 roomConnector.js 만 안다.
//  - ingress(바디캠, 지금은 쓰지 않음): 프리뷰 토큰으로 방에 시청자로 들어가 바디캠 영상을 <video> 에 붙인다.
// 이 클라이언트는 서버 세션(start/pause/resume/stop)을 부르고, 상태는 3초마다 서버에서 다시 받는다.
// pause()/resume() 은 **영상만** 책임진다(§8.2.1) — 작업 상태는 화면이 pauseJob/resumeJob 을 따로 부른다.
// 순서가 정해져 있고 서버가 강제한다: 일시정지는 pause()(영상 차단) → pauseJob, 재개는 resumeJob → resume()(영상).
// 순서를 어기면 서버가 409(INVALID_STATE)로 거절한다 — 영상이 나가는데 작업은 '일시정지' 로 보이는 순간을 만들지 않으려는 것.
// 화면 코드는 useLiveSession() 훅을 거쳐 쓴다 (테스트는 이 객체를 직접 부른다).
import { startLiveSession, getLiveSessionStatus, pauseLiveSession, resumeLiveSession, stopLiveSession } from '../api/manager.js';
import { connectRoom, publishRoom } from './roomConnector.js';
import { createVideoSource } from './sources/index.js';
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
  /** 녹화는 카메라 송출과 별도로 서버 상태를 표시한다. */
  recordingStatus: 'none',
  recordingStartFailed: false,
  /** 서버가 '매니저가 영상을 멈추고 아직 재개하지 않음' 으로 안다 (video_held). 작업이 진행 중인데 true 면 진행 화면이 '작업 상태를 바꾸지 못했어요' 시트를 다시 띄운다 */
  held: false,
  /** @type {HTMLVideoElement|null} 프리뷰 <video> */
  videoEl: null,
  error: null, // start 가 실패했을 때의 오류 객체
  /** @type {'app'|'ingress'|null} 영상 출처 (서버 GET /live/sessions/{id} 의 source). 모르면 null */
  source: null,
  /** @type {'off'|'starting'|'ready'|'error'} 휴대폰 카메라 상태 (app 모드). ready 여야 청소를 시작할 수 있다 */
  camera: 'off',
  /** @type {'denied'|'notFound'|'busy'|'failed'|null} 카메라를 켜지 못한 이유 (sources/cameraVideoSource.js 의 CameraError.code) */
  cameraError: null,
  /** 송출 연결이 잠깐 끊겨 LiveKit 이 다시 붙는 중 (app 모드) */
  reconnecting: false,
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

// ── 휴대폰 송출(app 모드) ──
let videoSource = null; // 영상 입력 (sources/index.js 가 만든다). 처음 카메라를 켤 때 만든다
let stopEndedWatch = null; // 입력의 onEnded 구독 해제 함수
let cameraStarting = null; // 카메라를 켜는 중인 Promise — prepare 와 start 가 겹쳐도 카메라를 두 번 열지 않게
let cameraWanted = false; // 화면이 카메라를 원한다(prepare~stop/reset/releaseCamera) — 카메라가 저절로 꺼졌을 때 다시 켤지 정한다
let photoMode = false; // 전/후 사진은 라이브 세션을 열지 않고 휴대폰 카메라만 사용한다.
let publisher = null; // publishRoom 이 돌려준 송출 연결 { disconnect, replaceTrack, unmute }. 없으면 null
let publishInfo = null; // { url, token } — start 응답의 송출 접속 정보. 끊기면 폴링 때 이것으로 다시 붙는다
let publishConnecting = false; // 송출 연결 시도 중 — 폴링이 겹쳐도 두 번 붙지 않게

// ── 청소 전·후 사진 (capturePhoto) ──
/** 사진 비율 가로:세로 = 3:4 — ui 의 CameraFrame·BeforeAfterSlider 와 같다 (바꾸면 셋 다) */
const PHOTO_ASPECT = 3 / 4;
/** 저장 사진의 긴 변 최대 픽셀 (고객 앱 수리 사진 축소 image.js 와 같은 값) */
const PHOTO_MAX_EDGE = 1600;

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
    // 백그라운드에 다녀오면 WebView 가 카메라를 끊었을 수 있다 — 화면이 아직 카메라를 원하면 다시 켜고 송출 트랙을 바꿔 끼운다
    if (!hidden() && cameraWanted && state.camera !== 'ready') recoverCamera();
  });
}

/** 휴대폰 카메라를 켠다(이미 켜져 있으면 그 트랙). 실패하면 camera='error' + cameraError 를 남기고 null */
async function startCamera() {
  const live = videoSource?.getTrack();
  if (live && live.readyState === 'live') return live;
  if (cameraStarting) return cameraStarting;
  videoSource ??= createVideoSource();
  stopEndedWatch ??= videoSource.onEnded(() => {
    set({ camera: 'off' });
    if (cameraWanted && !hidden()) recoverCamera(); // 보이는 중에 끊겼으면(다른 앱이 카메라를 가져갔다 돌려줌 등) 바로 다시 켠다
  });
  set({ camera: 'starting', cameraError: null });
  cameraStarting = (async () => {
    try {
      const track = await videoSource.start();
      set({ camera: 'ready' });
      showLocalPreview();
      return track;
    } catch (e) {
      set({ camera: 'error', cameraError: e?.code ?? 'failed' });
      return null;
    } finally {
      cameraStarting = null;
    }
  })();
  return cameraStarting;
}

/** 카메라를 다시 켜고, 송출 중이면 새 트랙으로 바꿔 끼운다 (고객 화면이 끊기지 않게) */
async function recoverCamera() {
  const track = await startCamera();
  if (track && publisher) await publisher.replaceTrack(track).catch(() => {});
}

/** 카메라를 끈다 (송출을 멈춘 뒤·청소 준비 화면을 떠날 때) */
function stopCamera() {
  cameraWanted = false;
  videoSource?.stop();
  if (state.videoEl && state.source === 'app') state.videoEl.srcObject = null;
  set({ camera: 'off', cameraError: null });
}

/** 휴대폰 카메라 트랙을 미리보기 <video> 에 붙인다 (app 모드). 같은 트랙이 송출에도 쓰인다 */
function showLocalPreview() {
  const track = videoSource?.getTrack();
  if (!state.videoEl || !track || typeof MediaStream === 'undefined') return; // MediaStream 이 없는 환경(테스트의 jsdom)에서는 붙이지 않는다
  if (state.videoEl.srcObject?.getVideoTracks?.()[0] === track) return; // 이미 붙어 있다
  state.videoEl.srcObject = new MediaStream([track]);
}

/** 송출하는 동안 화면이 꺼지지 않게(꺼지면 WebView 카메라가 멈춘다). 플러그인이 없는 환경(웹 테스트)에서는 조용히 넘어간다 */
async function keepAwake(on) {
  try {
    const { KeepAwake } = await import('@capacitor-community/keep-awake');
    if (on) await KeepAwake.keepAwake();
    else await KeepAwake.allowSleep();
  } catch {
    // 지원하지 않는 환경 — 화면 꺼짐 방지 없이 계속한다
  }
}

/** 송출 연결 (app 모드): 카메라를 켜고 송출 토큰으로 방에 들어가 트랙을 publish 한다. 실패하면 대기 시간을 늘리고 다음 폴링이 다시 시도한다 */
async function connectPublisher(mySeq) {
  if (!publishInfo || publisher || publishConnecting || Date.now() < previewRetryAt) return;
  publishConnecting = true;
  try {
    cameraWanted = true;
    const track = await startCamera();
    if (!track || mySeq !== seq) return; // 카메라를 못 켰다 — 화면이 cameraError 를 보여 주고, '카메라 다시 연결' 이 다시 부른다
    let connected = null;
    connected = await publishRoom({
      url: publishInfo.url,
      token: publishInfo.token,
      mediaTrack: track,
      onReconnecting: () => mySeq === seq && set({ reconnecting: true }),
      onReconnected: () => mySeq === seq && set({ reconnecting: false }),
      onDisconnected: () => {
        if (publisher !== connected) return;
        publisher = null; // 다음 폴링이 다시 붙는다
        set({ reconnecting: false });
      },
    });
    if (mySeq !== seq) {
      await connected?.disconnect();
      return;
    }
    publisher = connected; // wss:// 가 아니면(로컬 대역·테스트) null — 서버 폴링만으로 화면이 돈다
    previewBackoffMs = 0;
    if (connected) keepAwake(true);
  } catch {
    publisher = null;
    previewBackoffMs = Math.min(previewBackoffMs ? previewBackoffMs * 2 : PREVIEW_RETRY_MS, PREVIEW_RETRY_MAX_MS);
    previewRetryAt = Date.now() + previewBackoffMs;
  } finally {
    publishConnecting = false;
  }
}

/** 송출을 멈춘다: 트랙을 내리고 방을 나간다. 카메라는 끄지 않는다(stopCamera 가 따로) */
async function disconnectPublisher() {
  const p = publisher;
  publisher = null;
  publishInfo = null;
  previewRetryAt = 0;
  previewBackoffMs = 0;
  if (p) await p.disconnect().catch(() => {});
  keepAwake(false);
}

/** 상태 일부를 바꾸고(새 객체) 구독자를 깨운다 */


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
    if (!videoEl) return;
    if (photoMode || state.source === 'app') showLocalPreview(); // 사진 촬영 또는 휴대폰 송출 미리보기
    else if (videoTrack) videoTrack.attach(videoEl); // 바디캠 프리뷰 트랙이 먼저 와 있었으면 바로 붙인다
  },

  /**
   * 청소 준비 화면(Job/Preparing)이 들어올 때 부른다: 서버에 영상 출처를 묻고, 휴대폰 송출(app)이면 카메라를 켜 미리보기를 보인다.
   * 카메라 권한은 여기서 처음 묻는다. 실패하면 던지지 않고 camera='error' + cameraError 로 남긴다 (화면이 '카메라 연결 실패' 를 그린다).
   * '카메라 다시 연결' 도 이것을 다시 부른다. 바디캠 모드면 아무것도 하지 않는다
   */
  async prepare(jobId) {
    photoMode = false;
    let source = state.source;
    if (!source) {
      try {
        const res = await getLiveSessionStatus(jobId);
        source = res.source === 'app' ? 'app' : 'ingress'; // source 가 없는 옛 서버는 바디캠으로 본다
        set({ source });
      } catch {
        return; // 출처를 모른다 — 화면은 서버 장비 상태로 그린다. 다시 들어오거나 '카메라 다시 연결' 때 다시 묻는다
      }
    }
    if (source !== 'app') return;
    cameraWanted = true;
    await startCamera();
  },

  /** 청소를 시작하지 않고 청소 준비 화면을 떠날 때: 송출 중이 아니면 카메라를 끈다 (배터리·카메라 표시등) */
  releaseCamera() {
    if (publisher || publishInfo || ['connecting', 'live', 'paused'].includes(state.status)) return;
    stopCamera();
  },

  /**
   * 라이브 세션 시작 (Job/Preparing › 청소 시작, startJob 다음에). 서버가 방을 만들고 바디캠 Ingress 를 묶는다.
   * 같은 작업으로 다시 불러도 세션은 하나다. 서버 오류는 던지지 않고 status='error' + error 로 남긴다(작업은 이미 시작됐으므로 화면은 진행한다)
   */
  async start(jobId) {
    photoMode = false;
    // 같은 작업의 세션이 이미 살아 있으면(연결 대기·송출·일시정지) 다시 열지 않는다. idle·error·ended 면 새로 시작한다
    if (state.jobId === jobId && ['connecting', 'live', 'paused'].includes(state.status)) return;
    // 옛 폴링·프리뷰를 정리하고 새 세션 번호(seq)를 받는다
    stopPolling();
    disconnectPreview();
    await disconnectPublisher(); // 옛 송출 연결만 끊는다 — 카메라(미리보기)는 그대로 이어 쓴다
    const mySeq = ++seq;
    const startedAt = state.jobId === jobId && state.startedAt ? state.startedAt : null; // 같은 작업의 재시도만 시작 시각을 잇는다 (검수 4차: 앞 작업의 시각이 다음 작업으로 넘어갔다)
    set({ status: 'connecting', jobId, error: null, cameraBusy: false, cameraOnline: false, startedAt, recordingStatus: 'none', recordingStartFailed: false });
    try {
      const res = await startLiveSession(jobId);
      if (mySeq !== seq) return; // 그 사이 stop/reset 됐다
      set({ status: toStatus(res.status), cameraOnline: res.cameraOnline, cameraBusy: res.cameraBusy, startedAt: startedAt ?? Date.now() });
      preview = res.preview ?? null;
      publishInfo = res.publish ?? null; // 휴대폰 송출 모드면 송출 토큰이 온다 (§8.0)
      if (publishInfo) {
        set({ source: 'app' });
        await connectPublisher(mySeq);
      } else {
        stopCamera();
        await connectPreview(mySeq);
      }
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
      if (res.publish) publishInfo = res.publish;
      set({ status: toStatus(res.status), cameraOnline: res.cameraOnline, cameraBusy: Boolean(res.cameraBusy), held: Boolean(res.held), recordingStatus: res.recordingStatus ?? 'none', recordingStartFailed: Boolean(res.recordingStartFailed) });
      if (res.status === 'ended') stopPolling();
      else if (publishInfo) connectPublisher(mySeq); // 송출이 끊겼으면 다시 붙는다 (기다리지 않는다 — 폴링 주기를 막지 않게)
      else if (!room) connectPreview(mySeq);
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
    let res;
    try {
      res = await resumeLiveSession(jobId);
    } catch (e) {
      // 휴대폰 송출: 서버의 원격 unmute 가 LiveKit 프로젝트 설정으로 막혀 있으면 확인 실패(502)가 난다.
      // 작업은 이미 재개됐으므로(화면이 resumeJob 을 먼저 부른다) 휴대폰이 스스로 mute 를 풀고 한 번 더 확인을 받는다 (§8.0)
      if (e?.code !== 'LIVE_PAUSE_UNCONFIRMED' || !publisher) throw e;
      await publisher.unmute();
      res = await resumeLiveSession(jobId);
    }
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
      await disconnectPublisher();
      stopCamera();
      set({ status: 'ended', cameraOnline: false, cameraBusy: false, startedAt: null, reconnecting: false }); // jobId 는 남긴다 — 진행 중 화면이 끝난 작업에 start 를 다시 보내지 않게
    }
  },

  /**
   * 청소 전·후 사진 한 장 (2026-10-23, 코드에서 정함): 지금 미리보기 <video>(attachVideo 로 등록된 것)의 한 장면을 JPEG 파일로 만든다.
   * 휴대폰 송출 중인 카메라를 그대로 쓴다 — 카메라를 한 번 더 열면 Android 에서 '사용 중' 으로 실패한다.
   * 화면의 CameraFrame(3:4, cover)이 보여 주는 모양 그대로 가운데를 3:4 로 잘라 저장한다 — 청소 전 사진을 겹쳐 맞춘 각도가 저장본과 같고,
   * 고객 앱 BeforeAfterSlider(3:4)에서도 같은 자리가 겹친다. 긴 변은 PHOTO_MAX_EDGE 로 줄인다(업로드 5MB 한도·느린 망).
   * 영상이 아직 한 장면도 없으면(카메라 연결 전) Error('photo: notReady') 를 던진다 — 화면이 '카메라가 준비되지 않았어요' 문구로 바꾼다
   * @returns {Promise<File>} image/jpeg
   */
  async preparePhoto() {
    photoMode = true;
    cameraWanted = true;
    await startCamera();
    showLocalPreview();
  },

  releasePhoto() {
    photoMode = false;
    if (!publisher) stopCamera();
  },

  async capturePhoto() {
    const video = state.videoEl;
    const width = video?.videoWidth ?? 0;
    const height = video?.videoHeight ?? 0;
    if (!width || !height || typeof document === 'undefined') throw new Error('photo: notReady');
    // 가운데 3:4 영역 (가로가 넓으면 양옆을, 세로가 길면 위아래를 잘라 낸다)
    const cropWidth = Math.min(width, height * PHOTO_ASPECT);
    const cropHeight = Math.min(height, width / PHOTO_ASPECT);
    const scale = Math.min(1, PHOTO_MAX_EDGE / Math.max(cropWidth, cropHeight)); // 작은 영상은 키우지 않는다
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(cropWidth * scale);
    canvas.height = Math.round(cropHeight * scale);
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('photo: notReady');
    ctx.drawImage(video, (width - cropWidth) / 2, (height - cropHeight) / 2, cropWidth, cropHeight, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85));
    if (!blob) throw new Error('photo: notReady');
    return new File([blob], 'photo.jpg', { type: 'image/jpeg' });
  },

  /** 로그아웃·탈퇴: 서버는 부르지 않고 폴링·프리뷰·상태를 비운다 (토큰 없는 폴링이 계속 나가지 않게) */
  reset() {
    photoMode = false;
    seq++;
    pollSeq++;
    stopPolling();
    disconnectPreview();
    disconnectPublisher();
    stopCamera();
    set({ status: 'idle', jobId: null, startedAt: null, cameraOnline: false, cameraBusy: false, held: false, error: null, source: null, reconnecting: false, recordingStatus: 'none', recordingStartFailed: false });
  },
};

// 로그아웃·탈퇴·401 로 세션이 사라지면 폴링·프리뷰·상태를 비운다 (토큰 없는 폴링이 3초마다 계속 나가지 않게)
session.subscribe(() => {
  if (!session.accessToken() && state.status !== 'idle') liveClient.reset();
});
