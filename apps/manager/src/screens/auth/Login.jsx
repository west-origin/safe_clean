// Manager/Auth/Login (코드에서 정함) — Figma 매니저 페이지에 로그인 화면이 없다. 고객용 Auth/LoginPhone 과 같은 입력(휴대폰 + 비밀번호)을 매니저 앱 스타일(NavBar 없이 제목 + 폼 + StickyCTA)로.
// 성공 → RequireAuth 가 넘긴 state.from 또는 홈(/). "회원가입" → /auth/signup
// 흐름: 번호·비밀번호 → loginWithPhone(서버 POST /auth/login, 성공하면 세션 저장) → 가입이 덜 끝난 계정이면 가입 완료 화면,
// 끝난 계정이면 원래 주소나 홈. "비밀번호를 잊으셨나요?" 는 /auth/reset-password 로 간다.
// 이 라우트는 로그인 없이 열리는 공개 화면이다 (routes.jsx 의 publicRoutes).
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ScreenLayout, PhoneField, TextField, Button, InfoNote, ScreenError, ScreenLoading, useAsync, useAction, cx } from '@ansimclean/ui';
import { getAuthCopy, loginWithPhone, SIGNUP_STEP_PATHS } from '../../api/manager.js';
import styles from './auth.module.css';

/** RequireAuth 가 넘긴 목적지는 앱 안 경로('/…')만 믿는다 — '//host' 같은 값은 버린다 */
const safeReturnTo = (to) => (typeof to === 'string' && /^\/(?!\/)/.test(to) ? to : null); // 정규식 뜻: 첫 글자가 '/' 이고 둘째 글자는 '/' 가 아니다. 아니면 null → 홈

/**
 * 매니저 로그인 화면 (/auth/login). 문구는 getAuthCopy('login') = api/mock.js 의 AUTH_COPY.login.
 * 입력값(번호·비밀번호)은 이 화면의 state 에만 둔다. 로그인에 성공하면 replace 로 이동해 뒤로 가기로 이 화면에 돌아오지 않는다.
 */
export default function Login() {
  const navigate = useNavigate();
  const { state } = useLocation(); // 이 화면으로 올 때 함께 넘어온 값: from(RequireAuth 가 넘긴 원래 주소), notice(ResetPassword 가 넘긴 안내)
  const { data: copy, error, reload } = useAsync(() => getAuthCopy('login'), []);
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [failed, setFailed] = useState(false); // 서버가 '번호·비밀번호 불일치'(LOGIN_FAILED)로 답했을 때 true
  // 번호·비밀번호 불일치는 copy.failed, 그 밖(네트워크·429 등)은 서버 문구 — 원인이 다른데 같은 안내를 하지 않는다
  const login = useAction(async () => {
    setFailed(false);
    const { ok, user } = await loginWithPhone({ phone, password });
    if (!ok) return setFailed(true);
    // 계정은 만들었지만 가입 완료(약관 동의 전송)를 못 한 사람: 서버가 준 signupStep 으로 이어갈 화면을 고른다. 모르는 값이면 완료 화면
    if (!user.profileComplete) return navigate(SIGNUP_STEP_PATHS[user.signupStep] ?? '/auth/signup/complete', { replace: true });
    navigate(safeReturnTo(state?.from) ?? '/', { replace: true });
  });
  // 훅을 모두 부른 뒤에 early return 한다 (React 훅은 렌더마다 같은 순서로 불려야 한다). onBack: 오류 화면에서 이전 화면으로
  if (error) return <ScreenError error={error} onRetry={reload} onBack={() => navigate(-1)} />;
  if (!copy) return <ScreenLoading />;

  return (
    <ScreenLayout
      topPadding="lg"
      stickyCta={
        <Button chevron={false} loading={login.loading} disabled={!phone || !password} onClick={login.run}>
          {copy.submit}
        </Button>
      }
    >
      <header className={styles.header}>
        <h1 className={cx('text-heading-l', styles.title)}>{copy.title}</h1>
        {state?.notice && <InfoNote glyph="ⓘ">{state.notice}</InfoNote>}{/* 비밀번호 재설정은 됐는데 자동 로그인만 실패했을 때 (자체 점검 #22) */}
        <p className={cx('text-body-s', styles.secondary)}>{copy.description}</p>
      </header>

      {/* 입력: 휴대폰 번호(앞자리 010 은 칸 밖에 고정, PhoneField) + 비밀번호. 비밀번호 칸 아래에 실패 문구가 뜬다 */}
      <div className={styles.form}>
        <PhoneField label={copy.phoneLabel} value={phone} onChange={(e) => setPhone(e.target.value)} />
        <TextField
          label={copy.passwordLabel}
          placeholder={copy.passwordPlaceholder}
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          enterKeyHint="go"
          onKeyDown={(e) => { if (e.key === 'Enter' && phone && password && !login.loading) login.run(); }} // 키보드의 이동/완료로도 로그인 (직접 사용 점검 U7)
          error={failed ? copy.failed : login.error ? login.error.message || copy.failed : undefined}
        />
      </div>

      {/* 글자 링크처럼 보이는 버튼 두 개: 비밀번호 재설정 화면, 회원가입 1단계(약관 동의) */}
      <p className={cx('text-body-s', styles.prompt)}>
        <button type="button" className={cx('text-title-small', styles.link)} onClick={() => navigate('/auth/reset-password')}>
          비밀번호를 잊으셨나요?
        </button>
      </p>

      <p className={cx('text-body-s', styles.prompt)}>
        {copy.signUpPrompt}
        <button type="button" className={cx('text-title-small', styles.link)} onClick={() => navigate('/auth/signup')}>
          {copy.signUpLink}
        </button>
      </p>
    </ScreenLayout>
  );
}
