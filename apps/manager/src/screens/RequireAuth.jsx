// 로그인이 필요한 화면 묶음 (매니저 앱 전체). 로그인 전이면 로그인 화면으로, 가입이 덜 끝났으면 완료 화면으로, 로그인 후 원래 주소로 돌아온다.
import { useSyncExternalStore } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { isSignedIn, signupStep, subscribeSession, SIGNUP_STEP_PATHS } from '../api/manager.js';

/** 세션이 바뀌면(401 로 삭제 등) 다시 계산한다 — 오류 화면에 갇히지 않게 */
const snapshot = () => `${isSignedIn()}:${signupStep() ?? ''}`;

/**
 * routes.jsx 가 로그인이 필요한 화면 전체를 이 라우트의 children 으로 둔다 (경로 없는 감싸기 라우트).
 * 가입까지 끝났으면 자식 화면을, 가입 도중이면 그 단계 화면으로, 로그인 전이면 로그인 화면으로 보낸다.
 */
export default function RequireAuth() {
  const location = useLocation();
  // useSyncExternalStore = React 밖의 값(세션)을 구독한다. snapshot 문자열이 바뀌면 이 컴포넌트를 다시 그린다.
  // 반환값은 쓰지 않고, 아래에서 isSignedIn()·signupStep() 을 직접 다시 읽는다
  useSyncExternalStore(subscribeSession, snapshot);
  if (isSignedIn()) return <Outlet />; // Outlet = 자식 라우트의 화면이 그려지는 자리
  const step = signupStep();
  // 모르는 단계 값이면 완료 화면으로 (매니저 가입의 재개 화면은 완료 하나뿐이다 — api/manager.js 의 SIGNUP_STEP_PATHS)
  if (step) return <Navigate to={SIGNUP_STEP_PATHS[step] ?? '/auth/signup/complete'} replace />;
  // replace: 방문 기록의 지금 칸을 로그인 화면으로 바꾼다. state.from 은 로그인 성공 뒤 Login 이 돌아갈 원래 주소(쿼리 포함)
  return <Navigate to="/auth/login" replace state={{ from: location.pathname + location.search }} />;
}
