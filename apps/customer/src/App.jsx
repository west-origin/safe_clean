import { useEffect, useState } from 'react';
import { RouterProvider } from 'react-router';
import { router } from './routes.jsx';
import { refreshMe } from './api/customer.js';
import { initializeAuth } from './auth/client.js';
import { ScreenLoading, Button } from '@ansimclean/ui';
import { initPush, pushTarget } from './push/pushClient.js';
export default function App() {
  const [ready, setReady] = useState(false);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    initializeAuth().then(() => refreshMe()).then(() => {
      if (active) setReady(true);
    }).catch(() => { if (active) setError(true); });
    initPush({ onOpen: data => router.navigate(pushTarget(data)) }).catch(() => {});
    return () => { active = false; };
  }, [attempt]);
  if (error) return <div><p role="alert">로그인 상태를 확인하지 못했어요. 네트워크와 앱 설정을 확인해주세요.</p><Button onClick={() => { setError(false); setAttempt(value => value + 1); }}>다시 시도</Button></div>;
  if (!ready) return <ScreenLoading />;
  return <RouterProvider router={router} />;
}
