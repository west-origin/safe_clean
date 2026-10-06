// 고객용 TabBar 탭 순서·라벨은 Figma 그대로. 수리 아이콘만 3D 래스터(WebP)다.
// TabBar.jsx 가 이 배열을 차례로 돌며 탭 버튼을 그린다.
// webp 를 import 하면 Vite 가 빌드 결과에서의 파일 주소(문자열)를 넘겨준다.
import CustomerTabHomeIcon from '../../icons/CustomerTabHomeIcon.jsx';
import CustomerTabReservationsIcon from '../../icons/CustomerTabReservationsIcon.jsx';
import CustomerTabSettingsIcon from '../../icons/CustomerTabSettingsIcon.jsx';
import repair1x from '../../assets/tab-repair@1x.webp';
import repair2x from '../../assets/tab-repair@2x.webp';
import repair3x from '../../assets/tab-repair@3x.webp';

// 탭 한 칸 = { key, label, Icon(SVG 컴포넌트) 또는 image({ src, srcSet }) }.
// key 는 TabBar 의 active 값·onSelect 인자이고, 고객 앱 useCustomerTabs.js 의 TAB_PATHS 키와 같다.
export const CUSTOMER_TABS = [
  { key: 'home', label: '홈', Icon: CustomerTabHomeIcon },
  { key: 'repair', label: '수리', image: { src: repair1x, srcSet: `${repair1x} 1x, ${repair2x} 2x, ${repair3x} 3x` } },
  { key: 'reservations', label: '내 예약', Icon: CustomerTabReservationsIcon },
  { key: 'settings', label: '설정', Icon: CustomerTabSettingsIcon },
];
