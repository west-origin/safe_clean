// Figma: Auth/LoginPhone (80:508)
// 프로토타입 연결 없음 → "로그인하기" 성공 시 /home (PROGRESS.md 판단 항목). Figma 에 Button/Back 이 없어 뒤로가기도 없다
// 고객 앱 로그인 화면 (/auth/login). 예: 가입 첫 화면(SignUp)의 "로그인하기", 비밀번호 재설정(ResetPassword)의
// PASSWORD_CHANGED 가 여기로 보낸다 (전부 찾으려면 '/auth/login' 을 검색한다).
// 흐름: 번호 + 비밀번호 → loginWithPhone(POST /auth/login, 성공하면 세션 저장) →
//   가입이 덜 끝난 계정이면 남은 가입 화면, 아니면 원래 가려던 곳(없으면 /home).
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ScreenLayout, PhoneField, TextField, Button, InfoNote, ASSETS, cx, useAction } from '@ansimclean/ui';
import { loginWithPhone, SIGNUP_STEP_PATHS } from '../../api/customer.js';
import auth from './auth.module.css';
import styles from './LoginPhone.module.css';

/** RequireAuth 가 넘긴 목적지는 앱 안 경로('/…')만 믿는다 — '//host' 같은 값은 버린다 */
const safeReturnTo = (to) => (typeof to === 'string' && /^\/(?!\/)/.test(to) ? to : null); // 정규식: '/' 로 시작하고 바로 다음 글자는 '/' 가 아니다

/**
 * 로그인 화면. 번호·비밀번호는 이 화면 state 에만 두고, 요청과 화면 이동은 login(useAction) 이 한다.
 * 로그인 성공 뒤 이동은 모두 replace — 뒤로가기를 눌러도 로그인 화면으로 돌아오지 않는다
 * ("비밀번호를 잊으셨나요?" 이동은 replace 가 아니라 뒤로가기로 돌아온다).
 */
export default function LoginPhone() {
  const navigate = useNavigate();
  const { state } = useLocation(); // RequireAuth 가 넘긴 원래 목적지
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [failed, setFailed] = useState(''); // 실패 문구 (코드에서 정함). 요청 자체가 실패해도 로딩이 풀린다

  const login = useAction(async () => {
    setFailed('');
    const { ok, user } = await loginWithPhone({ phone, password });
    // ok:false 는 번호·비밀번호 불일치(LOGIN_FAILED)뿐이다. 그 밖의 오류는 던져져 login.error 로 간다
    if (!ok) return setFailed('휴대폰 번호 또는 비밀번호를 확인해주세요.');
    // 가입 도중 앱을 닫았던 계정이면 남은 가입 화면부터 (BACKEND_PLAN.md §7.3)
    if (!user.profileComplete) return navigate(SIGNUP_STEP_PATHS[user.signupStep] ?? '/auth/profile-address', { replace: true });
    // 가입까지 끝난 계정: RequireAuth → SignUp → 여기로 넘어온 원래 목적지, 없으면 홈
    navigate(safeReturnTo(state?.from) ?? '/home', { replace: true });
  });

  // 비밀번호 칸 아래 오류 문구 우선순위: 번호·비밀번호 불일치(failed) → 그 밖의 요청 실패(서버 문구, 없으면 기본 문구)
  return (
    <ScreenLayout noPadding gap="none" className={cx(auth.screen, styles.screen)}>
      <div className={cx(auth.body, styles.body)}>
        <div className={cx(auth.stack, styles.stack)}>
          <h1 className={cx('text-heading-l', auth.title)}>로그인하기</h1>
          {state?.notice && <InfoNote glyph="ⓘ">{state.notice}</InfoNote>}{/* 비밀번호 재설정은 됐는데 자동 로그인만 실패했을 때 (자체 점검 #22) */}
          <div className={cx(auth.form, styles.form)}>
            <PhoneField label="전화번호를 입력해주세요" labelStyle="heading" value={phone} onChange={(e) => setPhone(e.target.value)} />
            <TextField
              label="비밀번호"
              placeholder="비밀번호를 입력해주세요"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
          enterKeyHint="go"
          onKeyDown={(e) => { if (e.key === 'Enter' && phone && password && !login.loading) login.run(); }} // 키보드의 이동/완료로도 로그인 (직접 사용 점검 U7)
              error={failed || (login.error ? login.error.message || '로그인하지 못했어요. 잠시 후 다시 시도해주세요.' : undefined)}
            />
          </div>
        </div>

        {/* 비밀번호 재설정 링크 (Figma 에 없어 코드에서 정함) */}
        <p className={cx('text-body-s', styles.forgot)}>
          <button type="button" className={cx('text-title-small', styles.link)} onClick={() => navigate('/auth/reset-password')}>
            비밀번호를 잊으셨나요?
          </button>
        </p>

        <div className={auth.cta}>
          <Button variant="primary" chevron={false} loading={login.loading} disabled={!phone || !password} onClick={login.run}>
            로그인하기
          </Button>
        </div>
      </div>

      {/* HeroArt: Figma 절대 위치 (left 66, top 95). Figma 에서는 맨 위 레이어지만 사용자 결정(2026-09-21)대로 글·입력칸 뒤에 그린다
          (제목·입력칸 묶음과 비밀번호 재설정 링크가 z-index 로 위 — LoginPhone.module.css .stack·.hero). 탭은 통과 */}
      <img className={styles.hero} src={ASSETS.heroRoom.src} srcSet={ASSETS.heroRoom.srcSet} width={ASSETS.heroRoom.width} height={ASSETS.heroRoom.height} alt="" />
    </ScreenLayout>
  );
}
