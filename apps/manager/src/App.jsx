import { useEffect, useState } from 'react';
import { RouterProvider } from 'react-router';
import { router } from './routes.jsx';
import { refreshMe } from './api/manager.js';
import { session } from './api/session.js';
import { ScreenLoading } from '@ansimclean/ui';
import { initPush, pushTarget } from './push/pushClient.js';

// 매니저 앱의 가장 바깥 컴포넌트 (main.jsx 가 그린다).
// 순서: 저장된 로그인 세션을 읽는다(session.hydrate) → 다 읽으면 라우터를 그린다 → 서버에서 내 정보를 다시 받는다(refreshMe).
// 같은 시점에 푸시 알림도 준비한다(initPush). 알림을 탭하면 pushTarget 이 고른 화면으로 이동한다.

/** 세션을 다 읽기 전에는 로딩 화면, 읽은 뒤에는 라우터(routes.jsx)를 그린다 */
export default function App() {
  const [ready, setReady] = useState(false); // 네이티브: Keychain/Keystore 에서 세션을 읽은 뒤에 라우터를 그린다 (RequireAuth 가 동기 isSignedIn 을 보므로)
  // 앱을 열 때 한 번, 서버가 보는 사용자 상태로 세션을 맞춘다 (토큰이 없으면 아무것도 안 한다. 만료된 토큰은 http 가 refresh 로 갱신하고, refresh 도 401 이면 세션을 지운다)
  useEffect(() => {
    session
      .hydrate()
      .catch(() => {}) // 읽기에 실패해도 멈추지 않고 로그인 전 상태로 화면을 띄운다
      .finally(() => {
        setReady(true);
        // 기다리지 않는다 — 화면은 먼저 뜨고, 응답이 오면 세션의 사용자 정보만 바뀐다. 실패해도 앱은 계속 쓴다
        refreshMe().catch(() => {});
      });
    initPush({ onOpen: (data) => router.navigate(pushTarget(data)) }).catch(() => {}); // 네이티브에서만 동작. 알림을 탭하면 해당 화면으로
  }, []);
  if (!ready) return <ScreenLoading />;
  return <RouterProvider router={router} />;
}
