// 화면 전체가 로딩 중일 때. 제목 한 줄 + 카드 3개 모양의 스켈레톤. 스크린리더에는 "불러오는 중" 만 알린다.
// 데이터를 받는 화면이 아직 data 가 없을 때 돌려준다 (CODE_GUIDE.md 2-2). 두 앱의 App.jsx 도
// 저장된 로그인 세션을 되살리는(session.hydrate) 동안 이것을 보여 준다.
import ScreenLayout from '../ScreenLayout/ScreenLayout.jsx';
import Skeleton from '../Skeleton/Skeleton.jsx';
import styles from './ScreenLoading.module.css';

/**
 * @param {object} props
 * @param {import('react').ReactNode} [props.navBar]
 * @param {import('react').ReactNode} [props.tabBar]
 */
export default function ScreenLoading({ navBar, tabBar }) {
  // 부르는 화면이 불러온 뒤와 같은 navBar·tabBar 를 넘기면 로딩 중에도 뒤로가기·탭 이동을 할 수 있다.
  // role="status" + aria-live="polite": 화면 읽기 도구가 하던 말을 끊지 않고 안의 글을 읽어 준다.
  // .sr-only(base.css)는 눈에는 안 보이고 화면 읽기 도구만 읽는 글자다. Skeleton 조각은 aria-hidden 이라 읽히지 않는다.
  return (
    <ScreenLayout navBar={navBar} tabBar={tabBar}>
      <div className={styles.stack} role="status" aria-live="polite" aria-busy="true">
        <span className="sr-only">불러오는 중</span>
        <Skeleton width="55%" height={28} />
        <Skeleton height={96} radius="md" />
        <Skeleton height={96} radius="md" />
        <Skeleton height={96} radius="md" />
      </div>
    </ScreenLayout>
  );
}
