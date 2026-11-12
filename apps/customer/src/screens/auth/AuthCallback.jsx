import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { Button, ScreenLoading } from '@ansimclean/ui';
import { exchangeCallback, loginError } from '../../auth/socialLogin.js';
import { refreshMe } from '../../api/customer.js';
let callbackFlight;
export default function AuthCallback() {
  const navigate = useNavigate();
  const [error, setError] = useState('');
  useEffect(() => {
    const url = window.location.href;
    window.history.replaceState(null, '', '/auth/callback');
    callbackFlight ??= exchangeCallback(url).then(() => refreshMe()).finally(() => { callbackFlight = null; });
    let active = true;
    callbackFlight.then(user => {
      if (active) navigate(user?.profileComplete ? '/home' : '/auth/terms?next=complete', { replace: true });
    }, error => { if (active) setError(loginError(error)); });
    return () => { active = false; };
  }, [navigate]);
  if (!error) return <ScreenLoading />;
  return <div><p role="alert">{error}</p><Button onClick={() => navigate('/auth/login', { replace: true })}>로그인으로 돌아가기</Button></div>;
}
