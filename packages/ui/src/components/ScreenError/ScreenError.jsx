// 화면 전체가 오류일 때 (api 실패). ScreenLayout 틀은 유지해서 탭바·뒤로가기는 계속 쓸 수 있다.
// 데이터를 받는 화면이 useAsync 의 error 일 때 돌려준다 (쓰는 모양은 CODE_GUIDE.md 2-2).
// 문구를 고르는 일(네트워크 / 404 / 그 밖 — 서버 문구)은 안쪽의 ErrorState → describeError 가 한다.
import ScreenLayout from '../ScreenLayout/ScreenLayout.jsx';
import ErrorState from '../ErrorState/ErrorState.jsx';
import NavBar from '../NavBar/NavBar.jsx';

/**
 * @param {object} props
 * @param {{ code?: string, status?: number, message?: string }|null} [props.error] useAsync 의 error — 네트워크·404·서버 문구를 구분해 보여준다
 * @param {() => void} [props.onRetry] useAsync 의 reload
 * @param {() => void} [props.onBack] navBar 가 없을 때 뒤로가기만 있는 상단 바 — 오류 화면이 막다른 길이 되지 않게
 * @param {import('react').ReactNode} [props.navBar]
 * @param {import('react').ReactNode} [props.tabBar]
 * @param {string} [props.title]
 * @param {string} [props.description]
 * @param {string} [props.retryLabel]
 */
export default function ScreenError({ error, onRetry, onBack, navBar, tabBar, title, description, retryLabel }) {
  // 상단 바: navBar 를 주면 그대로, 없고 onBack 만 있으면 제목 없는 뒤로가기 바를 만든다, 둘 다 없으면 상단 바 없음.
  // 404 처럼 다시 시도해도 소용없는 오류는 ErrorState 가 onRetry 를 받아도 버튼을 숨긴다.
  return (
    <ScreenLayout navBar={navBar ?? (onBack ? <NavBar title="" onBack={onBack} /> : undefined)} tabBar={tabBar}>
      <ErrorState error={error} onRetry={onRetry} title={title} description={description} retryLabel={retryLabel} />
    </ScreenLayout>
  );
}
