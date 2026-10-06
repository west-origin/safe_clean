// 모든 화면을 감싸는 껍데기 (코드에서 정함 — 검증 검토): 화면이 바뀌면 ① 맨 위로 스크롤 ② 첫 제목으로 포커스·문서 제목 ③ 다음 화면 코드(청크)를 받는 동안 상단 진행 막대.
// 데이터 라우터는 화면 코드를 받을 때까지 이전 화면에 머무르므로, 느린 망에서 탭을 눌러도 아무 변화가 없던 것을 LoadingBar 가 메운다.
import { useEffect } from 'react';
import { Outlet, useLocation, useNavigation } from 'react-router';
import { LoadingBar, ToastHost } from '@ansimclean/ui';

const APP_NAME = '안심클린 매니저'; // 문서 제목 뒤에 붙는 앱 이름 (제목이 없으면 이것만)

/** routes.jsx 의 맨 바깥 라우트 element. 자식 화면을 Outlet 자리에 그리고, 위에 로딩 막대·아래에 토스트 자리를 둔다 */
export default function RouteShell() {
  const { pathname } = useLocation();
  const navigation = useNavigation(); // navigation.state: 'idle' | 'loading' | 'submitting' — 다음 화면을 준비하는 동안 'loading'
  // 경로(pathname)가 바뀔 때만 다시 돈다 — 쿼리(?…)만 바뀌면 스크롤·포커스를 건드리지 않는다
  useEffect(() => {
    window.scrollTo(0, 0);
    // 제목은 데이터가 온 뒤 그려질 수 있어 잠깐 뒤에 한 번 더 본다
    const apply = () => {
      const heading = document.querySelector('main h1');
      document.title = heading?.textContent?.trim()
        ? `${heading.textContent.trim()} · ${APP_NAME}`
        : APP_NAME;
      // h1 은 원래 포커스를 받을 수 없어 tabindex=-1 을 붙인다 (코드로만 포커스, 탭 키 순서에는 들어가지 않는다)
      if (heading && !heading.hasAttribute('tabindex')) heading.setAttribute('tabindex', '-1');
      // 포커스가 이미 다른 요소에 있으면 빼앗지 않는다 — 아무 데도 없을 때(body)만 제목으로 옮긴다
      if (heading && document.activeElement === document.body)
        heading.focus({ preventScroll: true }); // 스크린리더가 새 화면의 제목부터 읽게
    };
    apply();
    const timer = setTimeout(apply, 400);
    return () => clearTimeout(timer); // 400ms 안에 화면이 또 바뀌면 앞 화면용으로 걸어 둔 두 번째 apply 를 취소한다
  }, [pathname]);
  // LoadingBar: 다음 화면의 코드(lazy 청크)를 받는 동안 보인다. routes.jsx 의 라우트에 loader 가 없어서
  // 지금은 데이터 로딩 때 켜지지 않는다(화면 안의 ScreenLoading 이 맡는다). ToastHost: showToast 로 넣은 짧은 알림 문구 자리
  return (
    <>
      <LoadingBar active={navigation.state === 'loading'} />
      <Outlet />
      <ToastHost />
    </>
  );
}
