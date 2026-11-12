// Manager/Auth/Mfa (코드에서 정함, rbac.md §15-20) — Figma 에 없다. 어드민이 매니저 앱에 비밀번호로 로그인하면 여기서 인증앱 6자리를 넣는다.
// 들어오는 길: Login(어드민 계정) 또는 RequireAuth(어드민인데 MFA 토큰이 아님). 성공 → 관리자 홈(/admin).
// 로그인 화면과 같은 골격(제목 + 설명 + 입력 + StickyCTA). 6자리 칸은 ui 의 OtpCodeField. 문구는 api/mock.js AUTH_COPY.mfa.
// 이 라우트는 공개 화면이다(routes.jsx publicRoutes) — 토큰 없이 오면 로그인으로, 이미 MFA 를 통과했으면 관리자 홈으로 보낸다.
import { useState, useSyncExternalStore } from 'react';
import { Navigate, useNavigate } from 'react-router';
import { ScreenLayout, OtpCodeField, Button, ScreenError, ScreenLoading, useAsync, useAction, cx } from '@ansimclean/ui';
import { getAuthCopy, verifyAdminMfa, isAdmin, hasAdminMfa, subscribeSession, logout } from '../../api/manager.js';
import styles from './auth.module.css';

const snapshot = () => `${isAdmin()}:${hasAdminMfa()}`;

/** 어드민 인증앱 6자리 화면 (/auth/mfa). 6자리를 다 넣어야 확인 버튼이 켜진다 */
export default function MfaVerify() {
  const navigate = useNavigate();
  useSyncExternalStore(subscribeSession, snapshot); // 세션이 지워지면(401) 다시 그려 로그인으로 보낸다
  const { data: copy, error, reload } = useAsync(() => getAuthCopy('mfa'), []);
  const [code, setCode] = useState('');
  const [failed, setFailed] = useState(false); // 서버가 6자리가 틀렸다고(OTP_INVALID) 답했을 때
  const verify = useAction(async () => {
    setFailed(false);
    const { ok } = await verifyAdminMfa(code);
    if (!ok) {
      setCode(''); // 인증앱 숫자는 30초마다 바뀐다 — 새 숫자를 처음부터 넣게
      return setFailed(true);
    }
    navigate('/admin', { replace: true });
  });
  const switchAccount = useAction(async () => {
    await logout();
    navigate('/auth/login', { replace: true });
  });

  if (!isAdmin()) return <Navigate to="/auth/login" replace />;
  if (hasAdminMfa()) return <Navigate to="/admin" replace />;
  if (error) return <ScreenError error={error} onRetry={reload} />;
  if (!copy) return <ScreenLoading />;

  const ready = code.length === 6;
  return (
    <ScreenLayout
      topPadding="lg"
      stickyCta={
        <Button chevron={false} loading={verify.loading} disabled={!ready} onClick={verify.run}>
          {copy.submit}
        </Button>
      }
    >
      <header className={styles.header}>
        <h1 className={cx('text-heading-l', styles.title)}>{copy.title}</h1>
        <p className={cx('text-body-s', styles.secondary)}>{copy.description}</p>
      </header>

      <OtpCodeField
        label={copy.codeLabel}
        value={code}
        onChange={(v) => {
          setCode(v);
          if (failed) setFailed(false);
        }}
        autoFocus
        error={failed ? copy.failed : verify.error ? verify.error.message || copy.failed : undefined}
      />

      <p className={cx('text-body-s', styles.prompt)}>
        <button type="button" className={cx('text-title-small', styles.link)} disabled={switchAccount.loading} onClick={switchAccount.run}>
          {copy.switchAccount}
        </button>
      </p>
    </ScreenLayout>
  );
}
