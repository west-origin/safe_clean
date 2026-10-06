// 매니저 하단 탭 ↔ 라우트. Figma 프로토타입: TabBarItem/Home → Manager/Home, Call → Call/List, Schedule → Schedule, Settlement → Settlement, MY → Profile
// 탭바가 있는 화면(예: 홈·콜 목록)이 ManagerTabBar 의 onSelect 에 useManagerTabs() 결과를 넘긴다.
import { useNavigate } from 'react-router';

/** 탭 key → 경로. key 는 ManagerTabBar 가 onSelect(key) 로 넘기는 값('home'|'call'|'schedule'|'settlement'|'my')과 같다 */
export const TAB_PATHS = {
  home: '/',
  call: '/call',
  schedule: '/schedule',
  settlement: '/settlement',
  my: '/profile',
};

// 탭 key 를 받아 그 경로로 이동하는 함수를 돌려주는 훅. 화면 컴포넌트 안에서 불러야 한다(useNavigate 를 쓰므로)
/** @returns {(key: keyof typeof TAB_PATHS) => void} */
export function useManagerTabs() {
  const navigate = useNavigate();
  return (key) => navigate(TAB_PATHS[key]);
}
