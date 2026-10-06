// Figma: Repair › RepairCategoryItem 8개 — 순서·문구 그대로. 아이콘은 3D 래스터(ASSETS)
import { ASSETS } from '../../assets/index.js';

// 수리 카테고리 목록. RepairCategoryList(수리 탭 격자)가 이 순서로 그리고, 고객 앱은 key 로 라벨을 찾는다
// (예: 수리 예약 흐름의 repairFlow.js).
// key 는 수리 예약 주소(/repair/booking/:key/...)에도 들어가므로 바꾸면 그 화면들도 같이 봐야 한다.
// image 는 ASSETS 의 { src, srcSet, width, height } (3D 래스터 1x/2x/3x).
export const REPAIR_CATEGORIES = [
  { key: 'appliance', label: '가전수리', image: ASSETS.repairAppliance },
  { key: 'furniture', label: '가구 조립', image: ASSETS.repairFurniture },
  { key: 'plumbing', label: '배관·누수', image: ASSETS.repairPlumbing },
  { key: 'electric', label: '전기·조명', image: ASSETS.repairElectric },
  { key: 'toiletClog', label: '화장실 막힘', image: ASSETS.repairToiletClog },
  { key: 'toiletRepair', label: '화장실 수리', image: ASSETS.repairToiletRepair },
  { key: 'door', label: '도어·잠금', image: ASSETS.repairDoor },
  { key: 'other', label: '기타', image: ASSETS.repairOther },
];
