// 고객용 라이브 클라이언트 (시청·녹화본 재생). 앱 전체에서 하나만 있는 객체이며, 화면 사이를 오가도 연결 상태가 유지된다.
// 단계 5(B5) 에서 채움: 서버 세션 → 시청 토큰 → LiveKit 방(roomConnector) → <video>. 녹화본은 서명 URL 을 <video src> 로 재생한다.
// 상태 문구·시청자 수는 30초마다 서버 세션을 다시 받아 맞춘다 (BACKEND_PLAN.md §13-13 폴링). 소리는 없다(§13-23) — muted 토글은 남겨 두되 화면은 비활성.
// 끝난 뒤 녹화본이 아직 업로드 중이면 같은 간격으로 준비 여부를 계속 묻는다. 화면이 사라지면(release) 잠깐 뒤 나간다 — 세로↔가로 전환은 그 사이에 다시 join 하므로 연결이 유지된다.
// 화면은 이 객체를 직접 부르지 않고 useLiveSession 훅을 거친다 (예: ServiceLivePortrait·ServiceLiveLandscape).
// 서버 호출은 api/customer.js 의 getLiveSession(세션)·getLiveViewerToken(시청 토큰)·getRecording(녹화본 서명 URL) 뿐이다.
import { getLiveSession, getLiveViewerToken, getRecording } from '../api/customer.js';
import { connectRoom } from './roomConnector.js';

/** @typedef {'idle'|'connecting'|'live'|'paused'|'ended'|'error'} LiveStatus */

const SESSION_POLL_MS = 30000; // 서버 세션을 다시 받는 간격 (30초)
const SEEK_STEP_SECONDS = 15; // back15/forward15 한 번에 옮기는 초
/** 화면이 사라진 뒤 이 시간 안에 같은 세션으로 다시 join 하면(세로↔가로) 나가지 않는다 */
const RELEASE_GRACE_MS = 2000; // 느린 망에서 가로 화면 청크를 받는 데 0.5초를 넘기면 방을 나갔다 다시 붙었다 (검증 검토)

// 상태는 바뀔 때마다 새 객체로 만든다(useSyncExternalStore 가 참조 비교로 변경을 알아채기 때문)
let state = {
  /** @type {LiveStatus} */
  status: 'idle',
  /** 'live' = 실시간 시청, 'replay' = 녹화본 재생 */
  mode: 'live',
  /** getLiveSession() 결과 (매니저·시작 시각·상태 문구·live 상태) */
  session: null,
  muted: true,
  /** 들어가 있는 예약 id. undefined 면 DEV 에서만 mock 세션이고, 프로덕션은 getLiveSession 이 404 를 던져 error 가 된다 */
  bookingId: undefined,
  /** @type {HTMLVideoElement|null} 트랙 또는 녹화본을 붙일 <video> */
  videoEl: null,
  error: null,
};

// 아래는 state 밖의 내부 값이다 — 바꿔도 구독자에게 알리지 않고, 화면은 읽지 못한다
const listeners = new Set(); // subscribe 로 등록한 함수들 — set() 이 상태를 바꿀 때마다 부른다
let room = null; // 지금 붙어 있는 LiveKit 방 (connectRoom 결과). 없으면 null
let videoTrack = null; // 받은 실시간 영상 트랙 — <video> 가 바뀌면 새 <video> 에 다시 붙인다
let replayUrl = null; // 재생 중인 녹화본의 서명 URL
/** 녹화본 재생 위치 — <video> 가 바뀌어도(세로↔가로) 이어서 본다 */
let replayTime = 0;
/** 녹화본을 재생 중인 예약 id (없으면 null). onReplayError 가 이 id 로 서명 URL 을 다시 받는다 */
let replayBookingId = null;
let pollTimer = null; // 30초 폴링 setInterval 번호
let releaseTimer = null; // release() 가 건 '잠깐 뒤 leave' 타이머 — 그 사이 join 이 오면 취소한다
/** join 마다 +1. leave 뒤에 늦게 도착한 응답이 연결·폴링을 되살리지 않게 각 단계에서 비교한다 */
let joinSeq = 0;
/** connectLive 가 진행 중이면 그 Promise — 폴링·join 이 겹쳐 방을 두 개 만들지 않게 (검수 3차) */
let connecting = null;
let connectingSeq = 0; // connecting 이 어느 join 의 시도인가 — 다른 예약의 붙는 중 시도를 새 join 이 기다리지 않게 (재감사 2차 #2)
/** 트랙 이벤트(음소거·영상 도착)마다 +1. 서버가 아직 그 변화를 커밋하기 전에 온 응답이 트랙으로 정한 상태를 덮지 않게, 가장 최근 이벤트의 확인만 이어 간다 (자체 점검 #34·#38) */
let trackEventSeq = 0;
/** 확인이 아직 끝나지 않은 트랙 이벤트 번호 (0 = 없음). 그동안 보통 폴링은 상태를 덮지 않는다 — 서버가 그 이벤트를 커밋하기 전의 옛 상태일 수 있다 */
let confirmingEvent = 0;
const TRACK_CONFIRM_TRIES = 4; // 트랙 이벤트 뒤 서버 상태가 따라오는지 묻는 최대 횟수
const TRACK_CONFIRM_MS = 1000; // 그 사이 간격
/** 서명 URL 재발급 연속 실패 횟수 — 무한 재시도 대신 몇 번 뒤 오류로 끝낸다 */
let replayRetries = 0;
const REPLAY_RETRY_MAX = 3;
const REPLAY_RETRY_BACKOFF_MS = [1000, 3000, 8000]; // n번째 재시도 전에 기다리는 시간 (점점 길게)

/** 상태를 새 객체로 바꾸고 구독자(useLiveSession)에게 알린다. 이 파일에서 구독자를 부르는 곳은 여기뿐이다 */
function set(patch) {
  state = { ...state, ...patch };
  listeners.forEach((fn) => fn());
}

/** 서버 live.state → 화면 status */
const toStatus = (liveState) => ({ waiting: 'connecting', live: 'live', paused: 'paused', ended: 'ended' })[liveState] ?? 'connecting'; // 표에 없는 값·빈 값은 대기(connecting)로 본다

/** 녹화본 재생 위치를 세션의 playback 에 반영한다 (세로·가로 화면의 녹화본 진행 막대) */
function syncPlayback() {
  const v = state.videoEl;
  if (!v || state.mode !== 'replay' || !state.session) return;
  const total = Number.isFinite(v.duration) ? v.duration : 0;
  const label = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`; // 초 → 'm:ss'
  set({ session: { ...state.session, playback: { currentLabel: label(v.currentTime), totalLabel: label(total), ratio: total ? v.currentTime / total : 0, currentSeconds: v.currentTime, totalSeconds: total } } });
}

/** 녹화본의 길이 등 메타데이터를 읽은 뒤(loadedmetadata): 기억해 둔 위치로 옮기고 진행 막대를 맞춘다 */
function onReplayLoaded() {
  const v = state.videoEl;
  if (!v) return;
  if (replayTime > 0) v.currentTime = replayTime; // 화면 전환 전 위치부터
  syncPlayback();
}

/**
 * 녹화본 <video> 오류(예: 서명 URL 만료 — 유효 시간은 서버 LiveService 의 RECORDING_URL_TTL_SECONDS) 때
 * 새 URL 을 받아 같은 위치에서 잇는다. 복구할 수 없는 오류이거나 재시도가 다하면 status 'error' 로 끝낸다
 */
async function onReplayError() {
  const v = state.videoEl;
  if (!v || !replayBookingId || !replayUrl) return;
  const seq = joinSeq;
  replayTime = v.currentTime || replayTime; // 새 URL 을 넣으면 loadedmetadata → onReplayLoaded 가 이 위치로 되돌린다
  // 파일 자체가 깨졌거나(DECODE) 형식을 못 읽는(SRC_NOT_SUPPORTED) 경우는 URL 을 다시 받아도 같다 — 바로 오류로 끝낸다
  const code = v.error?.code;
  const unrecoverable = code === 3 /* MEDIA_ERR_DECODE */ || code === 4 /* MEDIA_ERR_SRC_NOT_SUPPORTED */;
  if (unrecoverable || replayRetries >= REPLAY_RETRY_MAX) {
    set({ status: 'error', error: new Error('녹화 영상을 재생할 수 없어요') });
    return;
  }
  // 1초·3초·8초 기다린 뒤 서명 URL 을 새로 받는다. 기다리는 사이 leave·새 join 이 있었으면(seq 가 바뀜) 그만둔다
  const wait = REPLAY_RETRY_BACKOFF_MS[replayRetries] ?? REPLAY_RETRY_BACKOFF_MS.at(-1);
  replayRetries += 1;
  await new Promise((r) => setTimeout(r, wait));
  if (seq !== joinSeq) return;
  try {
    const recording = await getRecording(replayBookingId);
    if (seq !== joinSeq || !recording) return;
    replayUrl = recording.url;
    v.src = replayUrl;
  } catch {
    // 재발급 요청 자체가 실패 — status 는 그대로 두고 오류 문구만 띄운다
    set({ error: new Error('녹화 영상을 불러오지 못했어요') });
  }
}
/** 재생이 실제로 이어지면 재시도 횟수를 비운다 */
function onReplayPlaying() {
  replayRetries = 0;
}

/** <video> 가 실제로 재생 중인지가 status 를 정한다 (자동 재생·화면 전환·브라우저 정책과 어긋나지 않게) */
function onReplayPlay() {
  if (state.mode === 'replay' && state.status !== 'live') set({ status: 'live' });
}
/** 멈추면 paused. 끝까지 재생돼 멈춘 경우(v.ended)는 상태를 바꾸지 않는다 */
function onReplayPause() {
  const v = state.videoEl;
  if (state.mode === 'replay' && state.status !== 'paused' && !(v && v.ended)) set({ status: 'paused' });
}

/** bindVideo 가 붙인 녹화본 이벤트 리스너를 뗀다 (<video> 가 사라질 때·leave 때) */
function unbindReplay(videoEl) {
  videoEl.removeEventListener('timeupdate', syncPlayback);
  videoEl.removeEventListener('loadedmetadata', onReplayLoaded);
  videoEl.removeEventListener('error', onReplayError);
  videoEl.removeEventListener('play', onReplayPlay);
  videoEl.removeEventListener('pause', onReplayPause);
  videoEl.removeEventListener('playing', onReplayPlaying);
}

/**
 * <video> 에 지금 가진 영상을 붙인다: 실시간 트랙이 있으면 트랙을, 녹화본 URL 이 있으면 src 와 재생 이벤트 리스너를.
 * <video> 와 영상 중 늦게 준비된 쪽에서 부른다 — 예: attachVideo(새 <video> 등록), startReplay(녹화본 URL 을 받은 뒤)
 */
function bindVideo(videoEl) {
  if (videoTrack) videoTrack.attach(videoEl); // livekit-client 트랙의 attach: 그 <video> 에 스트림을 연결한다
  if (replayUrl && videoEl.src !== replayUrl) { // 같은 URL 이 이미 붙어 있으면 리스너를 두 번 달지 않는다
    videoEl.srcObject = null; // 실시간 트랙이 붙어 있던 자리를 비운다
    videoEl.crossOrigin = 'anonymous'; // Storage 서명 URL(다른 origin) 프레임을 캡처(canvas)할 수 있게
    videoEl.addEventListener('timeupdate', syncPlayback);
    videoEl.addEventListener('loadedmetadata', onReplayLoaded);
    videoEl.addEventListener('error', onReplayError);
    videoEl.addEventListener('play', onReplayPlay);
    videoEl.addEventListener('pause', onReplayPause);
    videoEl.addEventListener('playing', onReplayPlaying);
    videoEl.src = replayUrl; // <video autoPlay muted> 라 src 를 넣으면 바로 재생된다. 화면 전환 뒤엔 onReplayLoaded 가 replayTime 부터 잇는다
  }
}

/** 30초 폴링을 멈춘다 (앱 복귀 때 바로 받는 lastPoll 도 비운다) */
function stopPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = null;
  lastPoll = null;
}
/** 화면이 안 보이면(앱 전환·백그라운드) 폴링은 쉬고, 돌아오면 바로 한 번 받는다 (검증 검토 — 백그라운드에서도 계속 돌았다) */
const hidden = () => typeof document !== 'undefined' && document.visibilityState === 'hidden';
let lastPoll = null; // 마지막으로 시작한 폴링 (돌아왔을 때 즉시 갱신용)
if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (!hidden() && pollTimer && lastPoll) lastPoll();
  });
}


/** 30초마다 refresh 를 부른다 (앱이 백그라운드면 그 회차는 건너뛴다). seq 를 넘겨 leave 뒤의 응답은 refresh 가 버리게 한다 */
function startPolling(bookingId, seq) {
  stopPolling(); // 폴링이 두 개 겹치지 않게 먼저 끈다
  lastPoll = () => liveClient.refresh(bookingId, seq);
  pollTimer = setInterval(() => !hidden() && lastPoll(), SESSION_POLL_MS);
}

/** 실시간 연결(방·트랙)만 끊는다. 녹화본 재생으로 넘어갈 때와 leave 때 */
function disconnectLive() {
  if (room) room.disconnect();
  room = null;
  if (videoTrack) videoTrack.detach();
  videoTrack = null;
}

/** 앱 전체에서 하나뿐인 라이브 클라이언트. 메서드는 this 를 쓰지 않고 liveClient.xxx 로 서로 부른다 — 훅이 메서드를 떼어 넘겨도 동작한다 */
export const liveClient = {
  /** 현재 상태 스냅샷 (useSyncExternalStore 용) */
  getState: () => state,
  /** 상태 변화 구독. 반환값은 구독 해제 함수 (useSyncExternalStore 용) */
  subscribe(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },

  /** <video> 엘리먼트 등록. 트랙·녹화본이 있으면 바로 붙인다 */
  attachVideo(videoEl) {
    const prev = state.videoEl;
    // null 은 <video> 가 화면에서 빠질 때(ref 콜백의 언마운트). 녹화본 재생 중이면 위치를 기억하고 리스너를 뗀다
    if (!videoEl && prev && replayUrl) {
      replayTime = prev.currentTime || replayTime; // 떼기 전 위치를 기억한다
      unbindReplay(prev);
    }
    set({ videoEl });
    if (!videoEl) return;
    videoEl.muted = state.muted;
    bindVideo(videoEl);
  },

  /**
   * 세션에 들어간다. replay=true 이거나 이미 끝난 예약이면 녹화본 재생 모드.
   * @param {string} [bookingId]
   * @param {{ replay?: boolean }} [opts]
   * 흐름: 앞 세션 leave → 서버 세션 조회 → (끝났거나 replay 면) startReplay, 아니면 시청 토큰으로 방 연결 → 30초 폴링.
   * 같은 세션으로 다시 오면(세로↔가로 전환) 아래 same 조건에 맞는 한 그대로 둔다 (예: 실시간 모드에서 error 였으면 다시 들어간다).
   * 세션 조회가 실패하면 status 'error' 로 두고, 예약 id 가 있으면 폴링을 걸어 다음 회차가 다시 받게 한다.
   * 매 await 뒤에 seq 를 비교해, 그 사이 나갔으면 이후 단계를 하지 않는다.
   */
  async join(bookingId, { replay = false } = {}) {
    if (releaseTimer) clearTimeout(releaseTimer); // 화면 전환 중 — 나가지 않는다
    releaseTimer = null;
    // 화면(세로↔가로)이 바뀌어도 같은 세션이면 그대로 둔다
    const same = state.session && state.bookingId === bookingId;
    if (same && !replay && ['connecting', 'idle', 'live', 'paused'].includes(state.status)) return;
    if (same && replay && state.mode === 'replay') return; // 녹화본 요청은 녹화본 모드일 때만 그대로 — 실시간을 보던 중 '녹화본 보기' 로 오면 다시 들어간다 (검수 3차)
    if (same && !replay && state.mode === 'replay' && state.status === 'ended') return;
    liveClient.leave();
    const seq = ++joinSeq;
    confirmingEvent = 0; // 지난 세션에서 끝나지 않은 확인이 새 세션의 보통 폴링을 막지 않게
    set({ status: 'connecting', error: null, bookingId, mode: 'live' });
    try {
      const session = await getLiveSession(bookingId);
      if (seq !== joinSeq) return; // 그 사이 leave 됐다
      set({ session });
      // 예약 id 없이 여기까지 오는 것은 DEV 뿐이다 — 프로덕션의 getLiveSession 은 404 를 던져 아래 catch 로 간다
      if (!bookingId) {
        set({ status: 'idle' }); // 예약 없이 열린 화면(mock)
        return;
      }
      const state0 = session.live?.state;
      if (replay || state0 === 'ended') {
        await liveClient.startReplay(bookingId, session, seq);
        return;
      }
      set({ status: toStatus(state0) });
      await liveClient.connectLive(bookingId, seq);
      if (seq !== joinSeq) return;
      startPolling(bookingId, seq);
    } catch (error) {
      if (seq !== joinSeq) return;
      set({ status: 'error', error });
      if (bookingId) startPolling(bookingId, seq); // 순간 장애면 다음 폴링이 세션을 다시 받아 잇는다
    }
  },

  /** 시청 토큰 → 방 연결 (wss 가 아니면 roomConnector 가 건너뛴다) */
  async connectLive(bookingId, seq = joinSeq) {
    if (connecting && connectingSeq === seq) return connecting; // 같은 join 이 이미 붙는 중 — 그 시도를 기다린다
    if (room) return; // 이미 붙어 있다
    const attempt = liveClient._connectLive(bookingId, seq).finally(() => {
      if (connecting === attempt) connecting = null; // 뒤에 시작한 시도의 표시를 지우지 않는다
    });
    connecting = attempt;
    connectingSeq = seq;
    return attempt;
  },
  /**
   * connectLive 의 실제 작업: 시청 토큰 받기 → connectRoom 으로 방 연결 → room 에 기록. 던지지 않고 실패는 state.error 로만 알린다.
   * 단, 아직 볼 수 없는 상태(LIVE_NOT_AVAILABLE)는 오류로 띄우지 않는다 — 다음 폴링이 다시 붙는다
   */
  async _connectLive(bookingId, seq) {
    try {
      const { url, token } = await getLiveViewerToken(bookingId);
      if (seq !== joinSeq) return; // leave 뒤 늦게 온 토큰 — 연결하지 않는다
      // 아래 onDisconnected 가 '끊긴 방이 지금의 room 인가' 를 비교하려고 변수를 먼저 만든다
      let connected = null;
      connected = await connectRoom({
        url,
        token,
        // 연결이 끝나기 전에 나가 다른 예약에 들어갔으면, 옛 방의 늦은 트랙·음소거 이벤트가 새 예약의 <video>·상태를 바꾸지 않게 (재감사 2차 #2)
        onVideoTrack: (track) => {
          if (seq !== joinSeq) return;
          videoTrack = track;
          if (state.videoEl) track.attach(state.videoEl);
          // 카메라 대기 중 바디캠이 붙었다 — 30초 폴링을 기다리지 않고 '실시간' 문구·배지로 (자체 점검 #38)
          if (state.status === 'connecting') liveClient.refresh(bookingId, seq, { expect: 'live', event: ++trackEventSeq });
        },
        onMuted: (muted) => {
          if (seq !== joinSeq) return;
          set({ status: muted ? 'paused' : 'live' });
          // 상태 카드 문구·시청자 수도 바로 (30초 폴링을 기다리면 배지와 카드가 최대 30초 어긋났다, 검증 검토).
          // 서버가 아직 커밋 전이면 옛 상태를 주므로, 같아질 때까지 잠깐 다시 묻는다 (자체 점검 #34)
          liveClient.refresh(bookingId, seq, { expect: muted ? 'paused' : 'live', event: ++trackEventSeq });
        },
        // 매니저 휴대폰이 트랙을 내렸다 — 멈춘 마지막 화면을 떼고 '연결 대기' 로, 서버 상태를 곧 다시 받는다 (§8.0)
        onVideoEnded: () => {
          if (seq !== joinSeq) return;
          if (videoTrack) videoTrack.detach();
          videoTrack = null;
          if (state.status === 'live') set({ status: 'connecting' });
          liveClient.refresh(bookingId, seq);
        },
        // 네트워크가 잠깐 끊겨 LiveKit 이 다시 붙는 중 — 'LIVE' 배지를 내리고(연결 대기), 붙으면 서버 상태로 되돌린다. 새로고침 없이 이어진다
        onReconnecting: () => {
          if (seq === joinSeq && state.status === 'live') set({ status: 'connecting' });
        },
        onReconnected: () => {
          if (seq === joinSeq) liveClient.refresh(bookingId, seq);
        },
        onDisconnected: () => {
          if (room !== connected) return; // 다른(새) 방의 참조를 지우지 않는다
          room = null;
          if (seq === joinSeq && state.mode === 'live') setTimeout(() => liveClient.refresh(bookingId, seq), 1000); // 30초 폴링을 기다리지 않고 곧 다시 붙는다 (검수 3차)
        },
      });
      if (seq !== joinSeq || room) {
        connected?.disconnect(); // 연결되는 사이 leave 됐거나 다른 연결이 먼저 붙었다
        return;
      }
      room = connected;
      if (state.error) set({ error: null }); // 이전 실패는 지난 일
    } catch (error) {
      // 아직 시청할 수 없는 상태(LIVE_NOT_AVAILABLE)면 서버 상태만 보여준다 — 다음 폴링에서 다시 시도
      if (seq === joinSeq && error?.code !== 'LIVE_NOT_AVAILABLE') set({ error });
    }
  },

  /**
   * 30초마다: 상태 문구·시청자 수·상태 갱신. 끝났으면 녹화본이 준비될 때까지 계속 묻고, 준비되면 재생 모드로.
   * confirm = { expect, event, tries }: 트랙 이벤트 직후의 확인. 서버 상태가 expect 와 다르면(아직 커밋 전) 상태를 덮지 않고 1초 뒤 다시 묻는다.
   * 몇 번 뒤에도 다르면 서버를 따른다. 그 사이 새 트랙 이벤트가 오면 옛 확인은 멈춘다.
   * 보통 폴링도 요청을 보낸 뒤에 트랙 이벤트(음소거·영상 도착)가 왔으면 그 응답을 버린다 — 이벤트 전의 서버 상태라서,
   * 늦게 도착하면 더 새 '일시정지' 를 '실시간' 으로 덮었다. 이벤트 뒤의 상태는 그 이벤트의 확인 요청이 가져온다.
   * 이벤트의 확인이 아직 진행 중이면(서버가 커밋 전일 수 있다) 이벤트 뒤에 나간 보통 폴링도 상태를 덮지 않는다
   */
  async refresh(bookingId, seq = joinSeq, confirm = null) {
    const eventAtRequest = trackEventSeq; // 요청을 보낼 때의 트랙 이벤트 번호
    if (confirm) confirmingEvent = confirm.event; // 이 이벤트의 확인이 진행 중
    const endConfirm = () => { // 확인이 끝났다(반영·포기·실패) — 보통 폴링이 다시 상태를 맞춘다
      if (confirm && confirmingEvent === confirm.event) confirmingEvent = 0;
    };
    try {
      const session = await getLiveSession(bookingId);
      if (seq !== joinSeq) return endConfirm();
      if (confirm && confirm.event !== trackEventSeq) return; // 더 새 트랙 이벤트의 확인이 진행 중 (그 확인이 confirmingEvent 를 맡는다)
      if (!confirm && (eventAtRequest !== trackEventSeq || (confirmingEvent !== 0 && confirmingEvent === trackEventSeq))) return; // 이벤트 전의 상태이거나, 이벤트의 확인이 아직 진행 중
      const serverStatus = toStatus(session.live?.state);
      const tries = confirm?.tries ?? TRACK_CONFIRM_TRIES;
      // 서버가 아직 트랙 이벤트를 반영하지 못했다 — 트랙으로 정한 상태를 덮지 않고 1초 뒤 다시 묻는다 (ended 는 바로 따른다)
      if (confirm && serverStatus !== 'ended' && serverStatus !== confirm.expect && tries > 1) {
        setTimeout(() => liveClient.refresh(bookingId, seq, { ...confirm, tries: tries - 1 }), TRACK_CONFIRM_MS);
        return;
      }
      endConfirm(); // 서버가 따라왔거나 횟수가 다했다 — 아래에서 서버 값을 반영하고 확인을 끝낸다
      if (state.mode === 'replay' && replayUrl) return; // 이미 녹화본을 재생 중
      const status = toStatus(session.live?.state);
      if (status === 'ended') {
        if (session.live?.recordingReady) await liveClient.startReplay(bookingId, session, seq);
        else {
          set({ session, status, mode: 'replay', error: null });
          if (!session.live?.recordingPending) stopPolling(); // 녹화본이 생길 여지가 없다(취소·세션 없음·실패) — 더 묻지 않는다
        }
      } else {
        set({ session, status, error: null });
        if (!room) await liveClient.connectLive(bookingId, seq); // 카메라 대기 중에도 시청 토큰이 나온다 — 대기 중 연결에 실패했으면 여기서 다시 붙는다 (자체 점검 #38)
      }
    } catch {
      endConfirm(); // 잠깐의 실패는 다음 폴링이 메운다 — 확인이 끊겼으니 보통 폴링을 막아 두지 않는다
    }
  },

  /** 녹화본: 실시간 연결을 끊고 서명 URL 을 <video src> 로. 준비 전이면 ended 상태로 안내 문구만 두고 준비될 때까지 폴링한다 */
  async startReplay(bookingId, session, seq = joinSeq) {
    disconnectLive(); // 실시간 트랙·방은 여기서 끝 — 녹화본과 겹치지 않게
    set({ mode: 'replay', status: 'ended', session, error: null }); // URL 을 받는 동안에도 화면은 '종료' 안내를 보여준다 (검수 3차)
    let recording = null;
    if (session.live?.recordingReady) {
      try {
        recording = await getRecording(bookingId);
      } catch (error) {
        if (seq !== joinSeq) return;
        set({ error });
        if (!pollTimer) startPolling(bookingId, seq); // 다음 폴링이 다시 받는다
        return;
      }
    }
    if (seq !== joinSeq) return;
    if (!recording) {
      set({ mode: 'replay', status: 'ended', session });
      if (session.live?.recordingPending) {
        if (!pollTimer) startPolling(bookingId, seq); // 업로드가 끝나면 refresh 가 여기로 다시 온다
      } else stopPolling(); // 녹화본은 없다
      return;
    }
    stopPolling();
    replayRetries = 0;
    replayUrl = recording.url;
    replayBookingId = bookingId;
    set({ mode: 'replay', status: 'paused', session }); // 재생이 시작되면 play 이벤트가 live 로 바꾼다
    if (state.videoEl) bindVideo(state.videoEl);
  },

  /** 화면이 사라질 때(훅 정리). 바로 나가지 않고 잠깐 기다린다 — 세로↔가로 전환이면 곧 같은 세션으로 join 이 온다 */
  release() {
    if (releaseTimer) clearTimeout(releaseTimer);
    releaseTimer = setTimeout(() => {
      releaseTimer = null;
      liveClient.leave();
    }, RELEASE_GRACE_MS);
  },

  /** 세션에서 바로 나간다: 폴링·방·녹화본 재생을 정리하고 status 를 'ended' 로. 등록된 <video>(videoEl)는 그대로 둔다 */
  leave() {
    joinSeq++; // 진행 중이던 join·토큰·폴링 응답은 전부 버린다
    confirmingEvent = 0;
    if (releaseTimer) clearTimeout(releaseTimer);
    releaseTimer = null;
    stopPolling();
    disconnectLive();
    replayRetries = 0;
    if (state.videoEl && replayUrl) {
      // 같은 <video> 가 다음 세션에도 쓰일 수 있어 녹화본 리스너와 주소를 떼어 둔다
      unbindReplay(state.videoEl);
      state.videoEl.removeAttribute('src');
    }
    replayUrl = null;
    replayBookingId = null;
    replayTime = 0;
    set({ status: 'ended', session: null, bookingId: undefined });
  },

  /** 소리 켜기/끄기: <video>.muted 와 상태를 함께 바꾼다 (지금 두 화면의 음소거 버튼은 비활성) */
  toggleMute() {
    const muted = !state.muted;
    if (state.videoEl) state.videoEl.muted = muted;
    set({ muted });
  },

  /** 현재 프레임을 PNG 로 저장 (canvas). 사진첩 저장(Capacitor, §13-15)은 B7 에서 */
  capture() {
    const v = state.videoEl;
    if (!v || !v.videoWidth) return;
    const canvas = document.createElement('canvas');
    canvas.width = v.videoWidth;
    canvas.height = v.videoHeight;
    canvas.getContext('2d').drawImage(v, 0, 0);
    let dataUrl;
    try {
      dataUrl = canvas.toDataURL('image/png');
    } catch {
      set({ error: new Error('이 영상은 캡처할 수 없어요') }); // 교차 출처 영상이 CORS 없이 로드된 경우(canvas 오염)
      return;
    }
    // 화면에 넣지 않은 <a download> 를 눌러 PNG 파일로 내려받게 한다
    const a = document.createElement('a');
    a.href = dataUrl;
    a.download = `ansimclean-${Date.now()}.png`;
    a.click();
  },

  /** 녹화본 탐색: 초 단위 delta(back15/forward15). 실시간에는 동작하지 않는다 */
  seek(deltaSeconds) {
    const v = state.videoEl;
    if (!v || state.mode !== 'replay') return;
    const total = Number.isFinite(v.duration) ? v.duration : 0;
    v.currentTime = Math.max(0, Math.min(total || Infinity, v.currentTime + deltaSeconds)); // 0초~끝 사이로 자른다. 길이를 아직 모르면(0) 끝 제한 없이
    syncPlayback();
  },
  /** 녹화본 탐색: 진행 막대의 0~1 비율 (0·1 이 정수라 delta 와 섞이지 않게 별도 함수) */
  seekRatio(ratio) {
    const v = state.videoEl;
    if (!v || state.mode !== 'replay') return;
    const total = Number.isFinite(v.duration) ? v.duration : 0;
    v.currentTime = Math.max(0, Math.min(total, Math.max(0, Math.min(1, ratio)) * total));
    syncPlayback();
  },
  /** 녹화본 15초 뒤로 (아래 forward15 는 앞으로) */
  back15() {
    liveClient.seek(-SEEK_STEP_SECONDS);
  },
  forward15() {
    liveClient.seek(SEEK_STEP_SECONDS);
  },
  /** 녹화본 재생/일시정지. 실시간에는 동작하지 않는다(서버가 상태를 정한다) */
  togglePause() {
    const v = state.videoEl;
    if (state.mode !== 'replay' || !v) return;
    // status 는 <video> 의 play/pause 이벤트가 맞춰 두므로(자동 재생·화면 전환 포함) 그 값을 기준으로 뒤집는다
    if (state.status === 'live') {
      v.pause?.();
      set({ status: 'paused' });
    } else {
      v.play?.()?.catch?.(() => {});
      set({ status: 'live' });
    }
  },
};
