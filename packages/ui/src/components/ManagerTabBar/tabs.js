// 탭 순서·라벨은 Figma Manager/TabBar 그대로.
// ManagerTabBar 가 이 배열 순서대로 버튼을 그리고, index.js 가 MANAGER_TABS 로 함께 내보낸다.
// key 는 매니저 앱 useManagerTabs.js 의 TAB_PATHS 키(home·call·schedule·settlement·my)와 같아야 이동이 맞는다.
import TabHomeIcon from '../../icons/TabHomeIcon.jsx';
import TabCallIcon from '../../icons/TabCallIcon.jsx';
import TabScheduleIcon from '../../icons/TabScheduleIcon.jsx';
import TabSettlementIcon from '../../icons/TabSettlementIcon.jsx';
import TabMyIcon from '../../icons/TabMyIcon.jsx';

// key: 탭 식별자(onSelect 로 넘어간다), label: 보이는 문구, Icon: 아이콘 컴포넌트
export const MANAGER_TABS = [
  { key: 'home', label: '홈', Icon: TabHomeIcon },
  { key: 'call', label: '콜', Icon: TabCallIcon },
  { key: 'schedule', label: '일정', Icon: TabScheduleIcon },
  { key: 'settlement', label: '정산', Icon: TabSettlementIcon },
  { key: 'my', label: 'MY', Icon: TabMyIcon },
];
