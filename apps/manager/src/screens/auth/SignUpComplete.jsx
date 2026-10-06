// Manager/Auth/SignUp/Complete (코드에서 정함) — 회원가입 3/3 완료 안내. "시작하기" → 로그인 상태로 만들고 홈(/)
// 약관 동의 기록이 없으면(다른 기기에서 이어가기 등) /auth/signup?next=complete 로 보내 동의 뒤 여기로 돌아온다
// 이 라우트는 공개 화면이다(routes.jsx publicRoutes). 토큰은 있고 가입이 덜 끝난 상태로 보호 화면에 가면
// RequireAuth 가 이리로 보낸다. 로그인 화면도 가입 미완료 계정이면 이리로 보낸다.
import { useNavigate } from 'react-router';
import { ScreenLayout, StepDots, Card, Button, ScreenError, ScreenLoading, useAsync, useAction, cx, ActionError } from '@ansimclean/ui';
import { getAuthCopy, completeManagerSignUp } from '../../api/manager.js';
import styles from './auth.module.css';

/**
 * 회원가입 3단계 완료 안내 (/auth/signup/complete). "시작하기" 가 completeManagerSignUp 을 불러
 * 초안의 약관 동의를 서버(POST /auth/signup/complete)에 보내고, 성공하면 가입이 끝난 로그인 상태로 홈에 간다.
 */
export default function SignUpComplete() {
  const navigate = useNavigate();
  const { data: copy, error, reload } = useAsync(() => getAuthCopy('complete'), []);
  // 실패 처리: 약관 동의가 없거나 모자람 → 약관 화면으로 (TERMS_REQUIRED 는 앱이 보내기 전에 막은 것, VALIDATION 은 서버가 거부한 것).
  // 401(로그인이 풀림) → 로그인 화면. 그 밖의 오류는 다시 던져 start.error 로 버튼 위에 문구를 보인다
  const start = useAction(async () => {
    try {
      await completeManagerSignUp();
    } catch (e) {
      if (e?.code === 'TERMS_REQUIRED' || e?.code === 'VALIDATION') {
        navigate('/auth/signup?next=complete', { replace: true });
        return;
      }
      if (e?.status === 401) {
        navigate('/auth/login', { replace: true }); // 토큰이 사라졌다 — 이 화면은 RequireAuth 밖
        return;
      }
      throw e;
    }
    navigate('/', { replace: true });
  });
  if (error) return <ScreenError error={error} onRetry={reload} onBack={() => navigate(-1)} />;
  if (!copy) return <ScreenLoading />;

  return (
    <ScreenLayout
      topPadding="lg"
      stickyCta={
        <>
          {/* message 를 넘겼으므로 실패하면 서버 문구 대신 항상 copy.error 를 보인다 */}
          <ActionError error={start.error} message={copy.error} />
          <Button loading={start.loading} onClick={start.run}>
            {copy.cta}
          </Button>
        </>
      }
    >
      <StepDots step={3} total={3} className={styles.steps} />
      <Card tone="brand" gap="sm" className={styles.complete}>
        <h1 className={cx('text-heading-l', styles.title)}>{copy.title}</h1>
        <p className={cx('text-body-s', styles.secondary)}>{copy.description}</p>
      </Card>
    </ScreenLayout>
  );
}
