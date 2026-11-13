// 친구 초대 링크 (코드에서 정함, 2026-11-13). 서버 초대 페이지 https://<API>/i/{code} 의 '앱에서 열기' 가 ansimclean://friend-invite/{code} 로 앱을 연다.
// 하는 일: 링크에서 코드 꺼내기, 로그인 전에 열린 초대를 기억해 두었다가 가입·로그인 뒤 이어서 보여 주기.
// 앱(Capacitor)에서는 App.jsx 가 appUrlOpen·getLaunchUrl 로 받은 주소를 openInviteUrl 에 넘긴다.

const KEY = 'ansimclean.pendingFriendInvite';
const CODE = /^[A-Za-z0-9]{10}$/;

/** 링크·코드 문자열 → 초대 코드 (아니면 null). ansimclean://friend-invite/X · https://…/i/X · X 를 모두 받는다 */
export function inviteCodeOf(text) {
  if (!text) return null;
  const s = String(text).trim();
  if (CODE.test(s)) return s;
  const m = s.match(/(?:friend-invite|\/i)\/([A-Za-z0-9]{10})(?:[/?#]|$)/);
  return m ? m[1] : null;
}

/** 로그인 전에 열린 초대 코드를 기억한다 (가입·로그인 뒤 홈이 꺼내 초대 화면으로 보낸다) */
export function rememberInvite(code) {
  try { localStorage.setItem(KEY, code); } catch { /* 저장할 수 없는 환경이면 기억하지 않는다 */ }
}

/** 기억해 둔 초대 코드를 꺼내고 지운다 (없으면 null) */
export function takePendingInvite() {
  try {
    const code = localStorage.getItem(KEY);
    localStorage.removeItem(KEY);
    return code && CODE.test(code) ? code : null;
  } catch {
    return null;
  }
}

/** 초대 화면이 열리면 기억한 코드를 지운다 (같은 초대가 다시 뜨지 않게) */
export function clearPendingInvite() {
  try { localStorage.removeItem(KEY); } catch { /* 무시 */ }
}

/** 앱이 링크로 열렸을 때: 초대 링크면 코드를 기억하고 초대 화면 경로를 돌려준다 (로그인 전이면 RequireAuth 가 가입으로 보낸다) */
export function openInviteUrl(url) {
  const code = inviteCodeOf(url);
  if (!code || !String(url).match(/friend-invite|\/i\//)) return null;
  rememberInvite(code);
  return `/friends/invite/${code}`;
}
