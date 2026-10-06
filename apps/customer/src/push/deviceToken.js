// 이 기기의 푸시 토큰. pushClient 가 채우고, api 의 logout 이 로그아웃 직전 서버에서 지울 때 읽는다.
// 로그아웃(세션 삭제 — 의도한 로그아웃·401 강제 로그아웃 모두) 뒤에는 pushClient 가 기기 등록을 풀고 비운다.
// (api → push → api 순환을 피하려고 의존성 없는 이 파일에 둔다)
let token = null; // 푸시 플러그인이 준 이 기기의 토큰. 아직 없거나 등록 실패면 null
let platform = null; // 'ios' | 'android' — pushClient 가 Capacitor.getPlatform() 값을 넣는다. 아직 없으면 null
let suspended = false; // 로그아웃 진행 중 — 그 사이 토큰 갱신(session.renew)이 구독을 깨워 옛 사용자에게 다시 등록하지 않게 (검수 3차)

export const deviceToken = {
  /** 토큰·플랫폼을 바꾼다. pushClient 의 'registration'(받음)·'registrationError'(실패 → null) 에서 부른다 */
  set(next, nextPlatform) {
    token = next;
    platform = nextPlatform;
  },
  get: () => token, // 예: syncToken(PUT /push/token)·logout(DELETE /push/token) 이 읽는다
  platform: () => platform,
  /** 로그아웃이 시작~끝 사이에 켠다. 켜져 있으면 pushClient.syncToken 은 아무것도 하지 않는다 */
  suspend(on) {
    suspended = Boolean(on);
  },
  suspended: () => suspended, // pushClient.syncToken 이 등록 전에 본다
};
