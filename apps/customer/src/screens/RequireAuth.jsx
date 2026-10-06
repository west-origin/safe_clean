// 로그인이 필요한 화면 묶음. 로그인 전이면 가입 화면으로, 가입이 덜 끝난 계정이면 남은 가입 화면으로, 로그인 후 원래 가려던 곳으로 돌아온다.
// routes.jsx 가 로그인 후 화면들을 이 컴포넌트의 children 으로 둔다. 공개 화면(예: /signup·/auth/*·/terms/:id)은 그 밖에 있다.
import { useSyncExternalStore } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { isSignedIn, signupStep, subscribeSession, SIGNUP_STEP_PATHS } from '../api/customer.js';

/** 세션이 바뀌면(401 로 삭제 등) 다시 계산한다 — 오류 화면에 갇히지 않게 */
const snapshot = () => `${isSignedIn()}:${signupStep() ?? ''}`;

/**
 * 가입까지 끝난 로그인 상태면 자식 라우트(<Outlet />)를 그대로 그리고, 아니면 남은 가입 화면이나 /signup 으로 보낸다.
 * replace: 방문 기록을 바꿔치기해, 뒤로가기로 막힌 주소에 다시 오지 않는다.
 */
export default function RequireAuth() {
  const location = useLocation();
  useSyncExternalStore(subscribeSession, snapshot); // 값은 쓰지 않는다 — 세션이 바뀌면 다시 그리게 하려고 구독만 한다
  if (isSignedIn()) return <Outlet />;
  const step = signupStep();
  if (step) return <Navigate to={SIGNUP_STEP_PATHS[step] ?? '/auth/profile-address'} replace />; // 표에 없는 단계 값이면 주소 입력부터
  // state.from = 원래 가려던 주소. SignUp 이 로그인 화면으로 넘기고, LoginPhone 이 로그인 뒤 그리로 보낸다
  return <Navigate to="/signup" replace state={{ from: location.pathname + location.search }} />;
}
