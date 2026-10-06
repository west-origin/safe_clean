// 고객용 하단 탭 ↔ 라우트. Figma 프로토타입: TabBarItem/Home → Home/Realtime, Reservations → Reservations, Settings → Settings. Repair 는 연결 없음(Repair 화면)
// 하단 탭바가 있는 화면(예: Home·Reservations)이 useCustomerTabs() 결과를 <TabBar onSelect={…}> 에 넘긴다.
import { useNavigate } from 'react-router';

/** 탭 키(TabBar 의 active·onSelect 값) → 라우트 경로 */
export const TAB_PATHS = {
  home: '/home',
  repair: '/repair',
  reservations: '/reservations',
  settings: '/settings',
};

// 탭 키를 받아 그 탭 경로로 이동하는 함수를 돌려준다. useNavigate 가 훅이라 이것도 화면 컴포넌트 안에서 부른다
/** @returns {(key: keyof typeof TAB_PATHS) => void} */
export function useCustomerTabs() {
  const navigate = useNavigate();
  return (key) => navigate(TAB_PATHS[key]);
}
