// Figma NoticeIcon/* 18개. NoticeChip 의 icon="pet" 처럼 이름으로 고른다.
import NoticeIconPet from './NoticeIconPet.jsx';
import NoticeIconBaby from './NoticeIconBaby.jsx';
import NoticeIconSmoke from './NoticeIconSmoke.jsx';
import NoticeIconLock from './NoticeIconLock.jsx';
import NoticeIconStairs from './NoticeIconStairs.jsx';
import NoticeIconElevator from './NoticeIconElevator.jsx';
import NoticeIconTools from './NoticeIconTools.jsx';
import NoticeIconWarning from './NoticeIconWarning.jsx';
import NoticeIconDots from './NoticeIconDots.jsx';
import NoticeIconBox from './NoticeIconBox.jsx';
import NoticeIconBuilding from './NoticeIconBuilding.jsx';
import NoticeIconParking from './NoticeIconParking.jsx';
import NoticeIconDroplet from './NoticeIconDroplet.jsx';
import NoticeIconClock from './NoticeIconClock.jsx';
import NoticeIconShield from './NoticeIconShield.jsx';
import NoticeIconDesk from './NoticeIconDesk.jsx';
import NoticeIconServer from './NoticeIconServer.jsx';
import NoticeIconAC from './NoticeIconAC.jsx';

// 이름 → 아이콘 컴포넌트 표. NoticeChip 이 NOTICE_ICONS[icon] 으로 찾는다.
// 이 이름은 서버가 주는 주의사항 칩(DB catalog_notice_chips 의 icon 칸)과 고객 앱 api/mock.js 의 칩 데이터에
// icon 값으로 들어 있다 (예: icon: 'pet'). 이름을 바꾸거나 지우면 둘 다 고쳐야 한다 (DB 는 새 마이그레이션으로).
// 표에 없는 이름이 오면 NoticeChip 이 렌더 중 오류를 던진다.
// 이름을 늘리거나 줄이면 NoticeChip.test.jsx 의 개수 검사도 고친다.
// 갤러리(dev/Gallery.jsx)는 이 표를 돌며 칩을 전부 그린다. packages/ui/src/index.js 가 이 표를 내보낸다.
export const NOTICE_ICONS = {
  pet: NoticeIconPet,
  baby: NoticeIconBaby,
  smoke: NoticeIconSmoke,
  lock: NoticeIconLock,
  stairs: NoticeIconStairs,
  elevator: NoticeIconElevator,
  tools: NoticeIconTools,
  warning: NoticeIconWarning,
  dots: NoticeIconDots,
  box: NoticeIconBox,
  building: NoticeIconBuilding,
  parking: NoticeIconParking,
  droplet: NoticeIconDroplet,
  clock: NoticeIconClock,
  shield: NoticeIconShield,
  desk: NoticeIconDesk,
  server: NoticeIconServer,
  ac: NoticeIconAC,
};
