import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, BrandWordmark, Button, Sheet } from '@ansimclean/ui';
import { refreshMe, isSignedIn } from '../../api/customer.js';
import { socialLogin, loginError } from '../../auth/socialLogin.js';
import { ACCOUNT_RECOVERY_URLS } from '../../auth/config.js';
import SocialProviderIcon from '../../auth/SocialProviderIcon.jsx';
import styles from './SignUp.module.css';

export default function SignUp() {
  const navigate = useNavigate();
  useEffect(() => { if (isSignedIn()) navigate('/home', { replace: true }); }, [navigate]);
  const lock = useRef(false);
  const [provider, setProvider] = useState(null);
  const [error, setError] = useState('');
  const [help, setHelp] = useState(false);
  const login = async (selected) => {
    if (lock.current) return;
    lock.current = true;
    setProvider(selected);
    setError('');
    try {
      const result = await socialLogin(selected);
      if (result.redirecting || result.pending) return;
      const user = await refreshMe();
      if (!user || user.role !== 'customer') throw new Error('고객 계정 필요');
      navigate(user.profileComplete ? '/home' : '/auth/terms?next=complete', { replace: true });
    } catch (error) { setError(loginError(error)); }
    finally { lock.current = false; setProvider(null); }
  };
  return (
    <ScreenLayout noPadding gap="none" className={styles.screen}>
      <div className={styles.body}>
        <div className={styles.logo}><BrandWordmark withMark width={140} /></div>
        <h1 className="text-heading-l">로그인</h1>
        <div className={styles.buttons} aria-busy={Boolean(provider)}>
          <span className={`${styles.badge} text-body-s`}>간편 로그인</span>
          <Button className={styles.socialButton} variant="primary" chevron={false} disabled={Boolean(provider)} loading={provider === 'kakao'} onClick={() => login('kakao')}>
            <SocialProviderIcon provider="kakao" /> 카카오 계정으로 로그인
          </Button>
          <Button className={`${styles.socialButton} ${styles.google}`} variant="secondary" chevron={false} disabled={Boolean(provider)} loading={provider === 'google'} onClick={() => login('google')}>
            <SocialProviderIcon provider="google" /> 구글 계정으로 로그인
          </Button>
          {provider && <p role="status" className="text-body-s">로그인 중이에요…</p>}
          {error && <p role="alert" className="text-body-s">{error}</p>}
        </div>
        <p className="text-body-s">카카오·구글 계정으로 로그인 또는 회원가입할 수 있어요.</p>
        <button type="button" className={`${styles.link} text-body-m`} onClick={() => setHelp(true)}>로그인에 문제가 있나요?</button>
      </div>
      <Sheet open={help} onClose={() => setHelp(false)} title="SNS 계정 복구 안내">
        <p className="text-body-default">로그인에 사용한 SNS에서 계정을 복구해주세요.</p>
        <p className="text-body-s">이메일을 제공하지 않거나 확인되지 않으면 계정이 자동으로 연결되지 않을 수 있어요. 기존 이용 내역이 보이지 않으면 고객센터에 문의해주세요.</p>
        <a className={styles.recovery} href={ACCOUNT_RECOVERY_URLS.kakao} target="_blank" rel="noopener noreferrer">카카오 계정 복구</a>
        <a className={styles.recovery} href={ACCOUNT_RECOVERY_URLS.google} target="_blank" rel="noopener noreferrer">구글 계정 복구</a>
      </Sheet>
    </ScreenLayout>
  );
}
