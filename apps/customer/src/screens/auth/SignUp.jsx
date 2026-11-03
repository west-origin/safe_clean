// Figma: Auth/SignUp (69:1089) — 2026-11-03 사용자 첨부 로그인 시안(파란 배경 · 로고 · 입력칸 2개 · 흰 로그인 버튼 · SNS 원 버튼)으로 바꿨다 (Figma 에 없음).
// 고객 앱 첫 화면 (/signup). 예: 온보딩, 로그인 전의 RequireAuth 가 여기로 보낸다 (전부 찾으려면 '/signup' 을 검색한다).
// 시안의 'kiwiplay' 로고 → '안심클린'(BrandWordmark), '이메일 아이디 입력'(편지봉투) → '전화번호를 입력해주세요'(휴대폰 아이콘),
// SNS 원 버튼은 카카오·구글·네이버. 문구는 mock.js ENTRY_COPY.
// 흐름: 번호 + 비밀번호 → loginWithPhone(POST /auth/login) → 가입이 덜 끝난 계정이면 남은 가입 화면, 아니면 원래 가려던 곳(없으면 /home).
//   '비밀번호 찾기' → /auth/reset-password, '회원가입' → 약관 동의(/auth/terms?method=phone).
//   소셜 로그인은 아직 없어서(결정 §13-1) SNS 버튼은 예전처럼 '전화번호로 계속' 안내 시트를 연다.
// 예전 로그인 화면(/auth/login, LoginPhone)은 비밀번호 재설정 등에서 그대로 쓴다.
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ScreenLayout, BrandWordmark, AuthPillField, SocialCircleButton, Sheet, Button, cx, useAction, ActionError } from '@ansimclean/ui';
import { loginWithPhone, signUpWith, SIGNUP_STEP_PATHS } from '../../api/customer.js';
import { ENTRY_COPY as COPY, SOCIAL_SIGNUP_COPY } from '../../api/mock.js';
import styles from './SignUp.module.css';

/** RequireAuth 가 넘긴 목적지는 앱 안 경로('/…')만 믿는다 — '//host' 같은 값은 버린다 (LoginPhone 과 같다) */
const safeReturnTo = (to) => (typeof to === 'string' && /^\/(?!\/)/.test(to) ? to : null);

/** 첫 화면: 전화번호 로그인 + 회원가입·비밀번호 찾기 링크 + SNS 버튼 */
export default function SignUp() {
  const navigate = useNavigate();
  const { state } = useLocation(); // RequireAuth 가 넘긴 원래 목적지 — 로그인까지 이어 준다
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [failed, setFailed] = useState(''); // 번호·비밀번호 불일치 문구
  const [socialOpen, setSocialOpen] = useState(false); // 소셜 안내 시트

  const login = useAction(async () => {
    setFailed('');
    // 숫자만 보낸다 — 서버는 010 으로 시작하는 11자리와 앞자리를 뗀 번호를 모두 받는다 (PhoneField 참고)
    const { ok, user } = await loginWithPhone({ phone: phone.replace(/\D/g, ''), password });
    if (!ok) return setFailed(COPY.failed);
    if (!user.profileComplete) return navigate(SIGNUP_STEP_PATHS[user.signupStep] ?? '/auth/profile-address', { replace: true });
    navigate(safeReturnTo(state?.from) ?? '/home', { replace: true });
  });
  // 가입 시작: 약관 동의 화면으로 (SIGN_UP_METHODS 에 있는 method 면 모두 전화번호 가입)
  const start = useAction(async () => {
    const res = await signUpWith('phone');
    navigate(`/auth/terms?method=${res.method}`);
  });

  const canLogin = phone.trim() && password && !login.loading;
  const message = failed || (login.error ? login.error.message || COPY.error : '');

  return (
    <ScreenLayout noPadding gap="none" className={styles.screen}>
      <div className={styles.body}>
        <h1 className={styles.logo}>
          <BrandWordmark />
        </h1>

        <div className={styles.form}>
          <AuthPillField icon="phone" label={COPY.phone} type="tel" inputMode="tel" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />
          <AuthPillField
            icon="lock"
            label={COPY.password}
            type="password"
            autoComplete="current-password"
            enterKeyHint="go"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && canLogin) login.run(); }}
          />
          {message && <p role="alert" className={cx('text-body-s', styles.error)}>{message}</p>}
          <Button variant="inverse" chevron={false} loading={login.loading} disabled={!phone.trim() || !password} onClick={login.run}>
            {COPY.login}
          </Button>
          <div className={styles.links}>
            <button type="button" className={cx('text-body-m', styles.link)} onClick={() => navigate('/auth/reset-password')}>{COPY.findPassword}</button>
            <span className={styles.divider} aria-hidden="true" />
            <button type="button" className={cx('text-body-m', styles.link)} onClick={start.run}>{COPY.signUp}</button>
          </div>
          <ActionError error={start.error} />
        </div>

        <div className={styles.sns}>
          <p className={cx('text-body-default', styles.snsText)}>{COPY.sns}</p>
          <div className={styles.snsRow}>
            <SocialCircleButton provider="kakao" label="카카오톡으로 로그인" onClick={() => setSocialOpen(true)} />
            <SocialCircleButton provider="google" label="구글로 로그인" onClick={() => setSocialOpen(true)} />
            <SocialCircleButton provider="naver" label="네이버로 로그인" onClick={() => setSocialOpen(true)} />
          </div>
        </div>
      </div>

      {/* 소셜 안내 시트 (코드에서 정함): 첫 버튼은 회원가입과 같고, 두 번째 버튼은 시트만 닫는다 */}
      <Sheet open={socialOpen} onClose={() => setSocialOpen(false)} title={SOCIAL_SIGNUP_COPY.title}>
        <p className={cx('text-body-default', styles.sheetText)}>{SOCIAL_SIGNUP_COPY.description}</p>
        <ActionError error={start.error} />
        <Button variant="primary" chevron={false} loading={start.loading} onClick={start.run}>
          {SOCIAL_SIGNUP_COPY.confirm}
        </Button>
        <Button variant="secondary" chevron={false} onClick={() => setSocialOpen(false)}>
          {SOCIAL_SIGNUP_COPY.back}
        </Button>
      </Sheet>
    </ScreenLayout>
  );
}
