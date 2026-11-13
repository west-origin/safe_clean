import { useEffect, useState } from 'react';
import { RouterProvider } from 'react-router';
import { router } from './routes.jsx';
import { refreshMe } from './api/customer.js';
import { initializeAuth } from './auth/client.js';
import { ScreenLoading, Button } from '@ansimclean/ui';
import { initPush, pushTarget } from './push/pushClient.js';
import { Capacitor } from '@capacitor/core';
import { App as CapacitorApp } from '@capacitor/app';
import { openInviteUrl } from './invite/inviteLink.js';

/**
 * 친구 초대 링크(ansimclean://friend-invite/{code})로 앱이 열리면 초대 화면으로 (코드에서 정함, 2026-11-13).
 * 앱이 꺼져 있다 열린 경우는 getLaunchUrl, 떠 있을 때는 appUrlOpen. 로그인 전이면 코드를 기억해 두고 RequireAuth 가 가입으로 보낸다 — 가입 뒤 홈이 이어서 연다
 */
function listenInviteLinks() {
  if (!Capacitor.isNativePlatform()) return;
  const go = (url) => {
    const path = openInviteUrl(url);
    if (path) router.navigate(path);
  };
  CapacitorApp.getLaunchUrl().then((r) => r?.url && go(r.url)).catch(() => {});
  CapacitorApp.addListener('appUrlOpen', ({ url }) => go(url)).catch(() => {});
}
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
    listenInviteLinks();
    return () => { active = false; };
  }, [attempt]);
  if (error) return <div><p role="alert">로그인 상태를 확인하지 못했어요. 네트워크와 앱 설정을 확인해주세요.</p><Button onClick={() => { setError(false); setAttempt(value => value + 1); }}>다시 시도</Button></div>;
  if (!ready) return <ScreenLoading />;
  return <RouterProvider router={router} />;
}
