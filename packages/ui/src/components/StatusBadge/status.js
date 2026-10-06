// Figma Status variant → 라벨·아이콘. 순서·문구는 Figma 그대로.
// StatusBadge 가 status 값으로 이 표를 찾아 문구와 아이콘을 고른다. 색은 여기 없고 StatusBadge.module.css 에 있다.
// 새 상태를 추가하면 여기, CSS 의 같은 이름 클래스, StatusBadge.test.jsx 의 기대 표를 함께 고쳐야 한다.
import BadgeNewIcon from '../../icons/BadgeNewIcon.jsx';
import BadgeScheduledIcon from '../../icons/BadgeScheduledIcon.jsx';
import BadgeInProgressIcon from '../../icons/BadgeInProgressIcon.jsx';
import BadgeLiveIcon from '../../icons/BadgeLiveIcon.jsx';
import BadgePausedIcon from '../../icons/BadgePausedIcon.jsx';
import BadgeCompleteIcon from '../../icons/BadgeCompleteIcon.jsx';
import BadgeSettlementPendingIcon from '../../icons/BadgeSettlementPendingIcon.jsx';

export const STATUS = {
  new: { label: '신규', Icon: BadgeNewIcon },
  scheduled: { label: '방문 예정', Icon: BadgeScheduledIcon },
  inProgress: { label: '진행 중', Icon: BadgeInProgressIcon },
  live: { label: 'LIVE', Icon: BadgeLiveIcon },
  paused: { label: '일시정지', Icon: BadgePausedIcon },
  complete: { label: '완료', Icon: BadgeCompleteIcon },
  settlementPending: { label: '정산 예정', Icon: BadgeSettlementPendingIcon },
};
