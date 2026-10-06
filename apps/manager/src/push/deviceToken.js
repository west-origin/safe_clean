// 이 기기의 푸시 토큰. pushClient 가 채우고, api 의 logout 이 로그아웃 직전 서버에서 지울 때 읽는다.
// 로그아웃(세션 삭제 — 의도한 로그아웃·401 강제 로그아웃 모두) 뒤에는 pushClient 가 기기 등록을 풀고 비운다.
// (api → push → api 순환을 피하려고 의존성 없는 이 파일에 둔다)
// 아래 변수들은 이 모듈 안에만 있고, 밖에서는 deviceToken 객체의 함수로만 읽고 쓴다.
let token = null; // OS 가 발급한 기기 토큰 문자열. 아직 없거나 등록 실패면 null
let platform = null; // 'ios' | 'android' (pushClient 가 Capacitor.getPlatform() 값을 넣는다)
let suspended = false; // 로그아웃 진행 중 — 그 사이 토큰 갱신(session.renew)이 구독을 깨워 옛 사용자에게 다시 등록하지 않게 (검수 3차)

/** 앱 전체에서 하나뿐인 기기 토큰 보관함 (모듈 변수라 import 한 곳 모두가 같은 값을 본다) */
export const deviceToken = {
  /** 새 토큰과 플랫폼을 저장한다. 'registration' 이벤트 때 채우고, 'registrationError' 때 null 로 비운다 */
  set(next, nextPlatform) {
    token = next;
    platform = nextPlatform;
  },
  get: () => token, // 지금 기기 토큰 (없으면 null)
  platform: () => platform, // 'ios' | 'android' | null
  /** 로그아웃이 시작~끝 사이에 켠다. 켜져 있으면 pushClient.syncToken 은 아무것도 하지 않는다 */
  suspend(on) {
    suspended = Boolean(on);
  },
  suspended: () => suspended, // 로그아웃 진행 중이면 true
};
