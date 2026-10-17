// @ansimclean/ui 진입점. 화면은 여기서만 import 한다.
// 두 앱의 화면은 `import { Button, useAsync } from '@ansimclean/ui'` 처럼 이 파일을 통해 가져온다.
// 새 컴포넌트·훅을 만들면 여기에 export 한 줄을 더해야 화면에서 이 경로로 쓸 수 있다.
// 컴포넌트 갤러리(dev/)는 여기서 내보내지 않는다 — 두 앱의 routes.jsx 가 개발 모드에서만 '@ansimclean/ui/dev' 로 따로 불러온다.

// 토큰(JS 에서 쓰는 CSS 변수 이름·값)과 공용 훅·도우미 (utils/)
export { tokens, tokenValues, textStyles, cssVar } from './tokens/tokens.js';
export { cx } from './utils/cx.js';
export { useAsync } from './utils/useAsync.js';
export { useAction } from './utils/useAction.js';
export { useLatest } from './utils/useLatest.js';
export { useCountdown, mmss, secondsUntil } from './utils/useCountdown.js';
export { usePolling } from './utils/usePolling.js';
export { useElapsed, hms } from './utils/useElapsed.js';
export * from './icons/index.js'; // icons/index.js 가 내보내는 SVG 아이콘 컴포넌트 전부
export { ASSETS } from './assets/index.js'; // 래스터 이미지(WebP) 목록 — src·srcSet·크기

// 공통
export { default as Button } from './components/Button/index.js';
export { default as TextField } from './components/TextField/index.js';
export { default as Sheet } from './components/Sheet/index.js';
export { default as Skeleton } from './components/Skeleton/index.js';
export { default as Card } from './components/Card/index.js';
export { default as Dot } from './components/Dot/index.js';
export { default as EmptyState } from './components/EmptyState/index.js';
export { default as ErrorState } from './components/ErrorState/index.js';
export { default as ActionError } from './components/ActionError/index.js';
export { default as ScreenError } from './components/ScreenError/index.js';
export { default as ScreenLoading } from './components/ScreenLoading/index.js';
export { default as LoadingBar } from './components/LoadingBar/index.js';
export { default as ToastHost } from './components/Toast/index.js';
export { showToast, hideToast } from './utils/toast.js';
export { default as ScreenLayout } from './components/ScreenLayout/index.js';
export { default as ProgressBar } from './components/ProgressBar/index.js';
export { default as ChecklistProgressCard } from './components/ChecklistProgressCard/index.js';
export { default as ChecklistRequests } from './components/ChecklistRequests/index.js';
export { default as ImageButton } from './components/ImageButton/index.js';

// 매니저
export { default as NavBar } from './components/NavBar/index.js';
export { default as ManagerTabBar, MANAGER_TABS } from './components/ManagerTabBar/index.js';
export { default as StatusBadge, STATUS } from './components/StatusBadge/index.js';
export { default as ChecklistItem } from './components/ChecklistItem/index.js';
export { default as CameraStatusItem } from './components/CameraStatusItem/index.js';
export { default as ListItem } from './components/ListItem/index.js';
export { default as CallCard } from './components/CallCard/index.js';
export { default as JobCard } from './components/JobCard/index.js';
export { default as ScheduleCard } from './components/ScheduleCard/index.js';
export { default as SettlementCard } from './components/SettlementCard/index.js';
export { default as SummaryRow } from './components/SummaryRow/index.js';
export { default as SegmentedControl } from './components/SegmentedControl/index.js';

// 고객용
export { default as StatusBar } from './components/StatusBar/index.js';
export { default as TabBar, CUSTOMER_TABS } from './components/TabBar/index.js';
export { default as RadioOption } from './components/RadioOption/index.js';
export { default as Chip } from './components/Chip/index.js';
export { default as NoticeChip } from './components/NoticeChip/index.js';
export { NOTICE_ICONS } from './icons/noticeIcons.js';
export { default as StepProgress } from './components/StepProgress/index.js';
export { default as ServiceCategoryList, SERVICE_CATEGORIES } from './components/ServiceCategoryList/index.js';
export { default as GiftBanner } from './components/GiftBanner/index.js';
export { default as Checkbox } from './components/Checkbox/index.js';
export { default as TermRow } from './components/TermRow/index.js';
export { default as StepDots } from './components/StepDots/index.js';
export { default as PhoneField } from './components/PhoneField/index.js';
export { default as BackButton } from './components/BackButton/index.js';
export { default as NoteCard } from './components/NoteCard/index.js';
export { default as SearchField } from './components/SearchField/index.js';
export { default as ImageOption } from './components/ImageOption/index.js';
export { default as SlideCounter } from './components/SlideCounter/index.js';
export { default as CarouselDots } from './components/CarouselDots/index.js';
export { default as UnderlineTabs } from './components/UnderlineTabs/index.js';
export { default as ReservationCard, RESERVATION_STATUS } from './components/ReservationCard/index.js';
export { default as Toggle } from './components/Toggle/index.js';
export { default as MenuRow } from './components/MenuRow/index.js';
export { default as MenuGroup } from './components/MenuGroup/index.js';
export { default as ProfileCard } from './components/ProfileCard/index.js';
export { default as RepairCategoryList, REPAIR_CATEGORIES } from './components/RepairCategoryList/index.js';
export { default as AddressResultList } from './components/AddressResultList/index.js';
export { default as FaqItem } from './components/FaqItem/index.js';
export { default as DeleteAccountSheet } from './components/DeleteAccountSheet/index.js';

// 예약 플로우
export { default as StepNavBar } from './components/StepNavBar/index.js';
export { default as SelectField } from './components/SelectField/index.js';
export { default as PromoBanner } from './components/PromoBanner/index.js';
export { default as Calendar, toDateKey, fromDateKey, startOfToday, formatDate, formatDateTime, formatDateShortWeekday, formatDateLongWeekday, formatTimeKo, notBookable, notBookableWithin, isWithinHours } from './components/Calendar/index.js';
export { default as TimeSlotGrid } from './components/TimeSlotGrid/index.js';
export { default as OptionItem } from './components/OptionItem/index.js';
export { default as TextArea } from './components/TextArea/index.js';
export { default as TipCard } from './components/TipCard/index.js';
export { default as SummaryItem } from './components/SummaryItem/index.js';
export { default as BookingInfoCard } from './components/BookingInfoCard/index.js';

// 라이브 화면
export { default as LiveBadge } from './components/LiveBadge/index.js';
export { default as RealtimePill } from './components/RealtimePill/index.js';
export { default as ViewerChip } from './components/ViewerChip/index.js';
export { default as ManagerIdentity } from './components/ManagerIdentity/index.js';
export { default as VideoSurface } from './components/VideoSurface/index.js';
export { default as CleaningStatusCard } from './components/CleaningStatusCard/index.js';
export { default as ReassuranceBanner } from './components/ReassuranceBanner/index.js';
export { default as LiveProgress } from './components/LiveProgress/index.js';
export { default as IconButton } from './components/IconButton/index.js';

// 라이브 컨트롤 (오버레이형 2 + 하단바형 1 — 배경이 달라 분리)
export { default as LiveCircleControl } from './components/LiveCircleControl/index.js';
export { default as LivePillControl } from './components/LivePillControl/index.js';
export { default as LiveLabeledControl } from './components/LiveLabeledControl/index.js';
// 수리 예약 플로우 (Figma rGCjx3zWMBk18fXgNZ9UYX)
export { default as StepQuestion } from './components/StepQuestion/index.js';
export { default as OptionGroup } from './components/OptionGroup/index.js';
export { default as SelectionSummary } from './components/SelectionSummary/index.js';
export { default as PhotoPicker } from './components/PhotoPicker/index.js';
export { default as InfoNote } from './components/InfoNote/index.js';
export { default as SelectedAddressCard } from './components/SelectedAddressCard/index.js';
export { default as ToggleChip } from './components/ToggleChip/index.js';
export { default as WeekCalendar } from './components/WeekCalendar/index.js';
export { default as ConfirmSummaryRow } from './components/ConfirmSummaryRow/index.js';
export { default as SuccessHeader } from './components/SuccessHeader/index.js';
export { default as StackedInfoCard } from './components/StackedInfoCard/index.js';
