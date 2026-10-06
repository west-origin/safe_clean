// 모든 화면을 감싸는 껍데기 (코드에서 정함 — 검증 검토): 화면이 바뀌면 ① 맨 위로 스크롤 ② 첫 제목으로 포커스·문서 제목 ③ 청크·데이터 로딩 중엔 상단 진행 막대.
// 데이터 라우터는 화면 코드를 받을 때까지 이전 화면에 머무르므로, 느린 망에서 탭을 눌러도 아무 변화가 없던 것을 LoadingBar 가 메운다.
// routes.jsx 의 최상위 라우트 element 다 (모든 라우트가 그 children). 토스트 표시(ToastHost)도 여기에 한 번만 둔다.
import { useEffect } from 'react';
import { Outlet, useLocation, useNavigation } from 'react-router';
import { LoadingBar, ToastHost } from '@ansimclean/ui';

const APP_NAME = '안심클린';

/** 자식 화면(<Outlet />) 위에 진행 막대, 아래에 토스트를 두고, 주소(pathname)가 바뀔 때마다 스크롤·제목·포커스를 맞춘다 */
export default function RouteShell() {
  const { pathname } = useLocation();
  const navigation = useNavigation(); // state 가 'loading' 이면 다음 화면 코드·데이터를 받는 중
  // 쿼리(?…)만 바뀔 때는 돌지 않는다 — 의존성이 pathname 뿐이다
  useEffect(() => {
    window.scrollTo(0, 0);
    // 제목은 데이터가 온 뒤 그려질 수 있어 잠깐 뒤에 한 번 더 본다
    const apply = () => {
      const heading = document.querySelector('main h1');
      document.title = heading?.textContent?.trim()
        ? `${heading.textContent.trim()} · ${APP_NAME}`
        : APP_NAME;
      if (heading && !heading.hasAttribute('tabindex')) heading.setAttribute('tabindex', '-1'); // tabindex=-1: 탭 순서에는 안 들어가지만 코드로 focus() 할 수 있게
      // 다른 요소에 이미 포커스가 있으면(화면이 직접 옮겼으면) 빼앗지 않는다
      if (heading && document.activeElement === document.body)
        heading.focus({ preventScroll: true }); // 스크린리더가 새 화면의 제목부터 읽게
    };
    apply();
    const timer = setTimeout(apply, 400);
    return () => clearTimeout(timer); // 400ms 안에 다시 이동하면 옛 화면용 두 번째 확인은 취소한다
  }, [pathname]);
  return (
    <>
      <LoadingBar active={navigation.state === 'loading'} />
      <Outlet />
      <ToastHost />
    </>
  );
}
