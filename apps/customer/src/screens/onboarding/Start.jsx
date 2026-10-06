// '/' 진입점 (코드에서 정함): 로그인된 사용자는 온보딩을 다시 보지 않고 홈으로, 가입 중이면 남은 가입 화면으로, 그 외에는 온보딩.
// Capacitor 는 앱을 열 때마다 '/' 에서 시작하므로 이 분기가 없으면 매번 온보딩·재로그인을 반복한다.
import { Navigate } from 'react-router';
import { isSignedIn, signupStep, SIGNUP_STEP_PATHS } from '../../api/customer.js';
import Onboarding from './Onboarding.jsx';

/**
 * '/' 에서 세션 상태를 보고 갈 곳을 고른다. Navigate replace 는 지금 기록을 바꿔치기해, 뒤로가기로 '/' 에 돌아오지 않게 한다.
 */
export default function Start() {
  if (isSignedIn()) return <Navigate to="/home" replace />;
  const step = signupStep(); // 가입 도중이면 'address' | 'housing' | 'complete', 아니면 null
  if (step) return <Navigate to={SIGNUP_STEP_PATHS[step] ?? '/auth/profile-address'} replace />; // 모르는 단계 값이면 주소 입력 화면부터
  return <Onboarding />;
}
