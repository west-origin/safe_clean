// 벡터 아이콘 컴포넌트를 한곳에서 내보내는 목록. packages/ui/src/index.js 가 `export *` 로 다시 내보내므로
// 앱에서는 import { AlertIcon } from '@ansimclean/ui' 처럼 쓴다. 갤러리(dev/Gallery.jsx)는 이 파일을
// import * as Icons 로 통째로 불러 Icons 섹션에 전부 나열한다. 새 아이콘 파일을 만들면 여기에 한 줄 추가한다.
//
// 아이콘 파일 공통 규칙 (파일마다 반복하지 않는다):
// - 인라인 <svg> 하나를 그리는 함수 컴포넌트. props 는 size(px)·className 이다
//   (예외: MonthChevronIcon 은 size 대신 direction, 매니저 탭 아이콘 Tab*Icon 은 strokeWidth 도 받는다).
// - 선·면 색은 currentColor = 부모 요소의 CSS color 를 따른다. 색은 쓰는 쪽 CSS Module 에서 color 로 정한다.
//   (예외: CompleteCheckIcon·VerifiedIcon 은 원만 currentColor 이고, 안의 체크 선은 CSS 변수로 고정이다.)
// - aria-hidden="true" 라 화면 읽기 도구가 건너뛴다. 뜻은 옆 글자나 버튼의 aria-label 이 전한다.
// - focusable="false" 는 svg 가 키보드 포커스를 받지 않게 한다.
// - Spinner 만 예외로 aria-hidden 대신 role="img" + aria-label="로딩 중" 이다 (Spinner.jsx 참고).
// - icons/ 폴더는 npm run check:tokens(하드코딩 검사) 대상에서 빠진다 (scripts/check-tokens.js 의 SKIP_PATH).
// NoticeIcon 이름 표(NOTICE_ICONS)는 여기 없고 noticeIcons.js 에 있다 (packages/ui/src/index.js 가 따로 내보낸다).
export { default as ChevronRight } from './ChevronRight.jsx';
export { default as Spinner } from './Spinner.jsx';
export { default as BackIcon } from './BackIcon.jsx';
export { default as CheckIcon } from './CheckIcon.jsx';
export { default as ChevronRightSmallIcon } from './ChevronRightSmallIcon.jsx';
export { default as BadgeNewIcon } from './BadgeNewIcon.jsx';
export { default as BadgeScheduledIcon } from './BadgeScheduledIcon.jsx';
export { default as BadgeInProgressIcon } from './BadgeInProgressIcon.jsx';
export { default as BadgeLiveIcon } from './BadgeLiveIcon.jsx';
export { default as BadgePausedIcon } from './BadgePausedIcon.jsx';
export { default as BadgeCompleteIcon } from './BadgeCompleteIcon.jsx';
export { default as BadgeSettlementPendingIcon } from './BadgeSettlementPendingIcon.jsx';
export { default as StatusOkIcon } from './StatusOkIcon.jsx';
export { default as StatusIssueIcon } from './StatusIssueIcon.jsx';
export { default as TabHomeIcon } from './TabHomeIcon.jsx';
export { default as TabCallIcon } from './TabCallIcon.jsx';
export { default as TabScheduleIcon } from './TabScheduleIcon.jsx';
export { default as TabSettlementIcon } from './TabSettlementIcon.jsx';
export { default as TabMyIcon } from './TabMyIcon.jsx';
export { default as InfoClockIcon } from './InfoClockIcon.jsx';
export { default as InfoPinIcon } from './InfoPinIcon.jsx';
export { default as InfoWonIcon } from './InfoWonIcon.jsx';
export { default as TimerIcon } from './TimerIcon.jsx';
export { default as NoticeIconPet } from './NoticeIconPet.jsx';
export { default as NoticeIconBaby } from './NoticeIconBaby.jsx';
export { default as NoticeIconSmoke } from './NoticeIconSmoke.jsx';
export { default as NoticeIconLock } from './NoticeIconLock.jsx';
export { default as NoticeIconStairs } from './NoticeIconStairs.jsx';
export { default as NoticeIconElevator } from './NoticeIconElevator.jsx';
export { default as NoticeIconTools } from './NoticeIconTools.jsx';
export { default as NoticeIconWarning } from './NoticeIconWarning.jsx';
export { default as NoticeIconDots } from './NoticeIconDots.jsx';
export { default as NoticeIconBox } from './NoticeIconBox.jsx';
export { default as NoticeIconBuilding } from './NoticeIconBuilding.jsx';
export { default as NoticeIconParking } from './NoticeIconParking.jsx';
export { default as NoticeIconDroplet } from './NoticeIconDroplet.jsx';
export { default as NoticeIconClock } from './NoticeIconClock.jsx';
export { default as NoticeIconShield } from './NoticeIconShield.jsx';
export { default as NoticeIconDesk } from './NoticeIconDesk.jsx';
export { default as NoticeIconServer } from './NoticeIconServer.jsx';
export { default as NoticeIconAC } from './NoticeIconAC.jsx';
export { default as InfoCalendarIcon } from './InfoCalendarIcon.jsx';
export { default as InfoPersonIcon } from './InfoPersonIcon.jsx';
export { default as CustomerTabHomeIcon } from './CustomerTabHomeIcon.jsx';
export { default as CustomerTabReservationsIcon } from './CustomerTabReservationsIcon.jsx';
export { default as CustomerTabSettingsIcon } from './CustomerTabSettingsIcon.jsx';
export { default as LivePauseIcon } from './LivePauseIcon.jsx';
export { default as LivePlayIcon } from './LivePlayIcon.jsx';
export { default as LiveBack15Icon } from './LiveBack15Icon.jsx';
export { default as LiveForward15Icon } from './LiveForward15Icon.jsx';
export { default as LiveCaptureIcon } from './LiveCaptureIcon.jsx';
export { default as LiveExitIcon } from './LiveExitIcon.jsx';
export { default as LiveCaptureLargeIcon } from './LiveCaptureLargeIcon.jsx';
export { default as LiveHangUpIcon } from './LiveHangUpIcon.jsx';
export { default as LiveMuteIcon } from './LiveMuteIcon.jsx';
export { default as EmptySmileIcon } from './EmptySmileIcon.jsx';
export { default as EmptyCallIcon } from './EmptyCallIcon.jsx';
export { default as AlertIcon } from './AlertIcon.jsx';
export { default as CompleteCheckIcon } from './CompleteCheckIcon.jsx';
export { default as EmptyCalendarIcon } from './EmptyCalendarIcon.jsx';
export { default as BackArrowIcon } from './BackArrowIcon.jsx';
export { default as CheckMarkIcon } from './CheckMarkIcon.jsx';
export { default as EyeIcon } from './EyeIcon.jsx';
export { default as SearchIcon } from './SearchIcon.jsx';
export { default as ChevronRightTinyIcon } from './ChevronRightTinyIcon.jsx';
export { default as CalendarIcon } from './CalendarIcon.jsx';
export { default as MonthChevronIcon } from './MonthChevronIcon.jsx';
export { default as VerifiedIcon } from './VerifiedIcon.jsx';
export { BenefitCalendarIcon, BenefitTagIcon, BenefitHomeIcon } from './benefitIcons.jsx';
export { LiveBackIcon, SettingsIcon, ViewerIcon, HomeOutlineIcon, FullscreenIcon, MuteSpeakerIcon } from './liveIcons.jsx';
