# PROGRESS

세션을 시작할 때 이 파일을 먼저 읽는다. 완료된 항목은 다시 만들지 않는다.
계획 전체는 `PLAN.md`.

## 단계 0 — 부트스트랩
- [x] git 초기화, .gitignore, README
- [x] npm workspaces (packages/ui, apps/customer, apps/manager)
- [x] Vite + React 앱 껍데기 2개 (JS/JSX, TypeScript 없음)
- [x] ESLint / Prettier / Vitest 설정 (ESLint 10, Vitest 4, jsdom, Testing Library)

## 단계 1 — 토큰 파이프라인
- [x] Figma 변수 32개 덤프 → design/figma-tokens.json (스크립트: design/figma/dump-tokens.figma.js)
- [x] scripts/build-tokens.js → packages/ui/src/tokens/tokens.css, tokens.js
- [x] scripts/check-tokens.js (하드코딩 hex/px 검사)
- [x] 토큰 생성물 테스트 (packages/ui/src/tokens/tokens.test.js, 5개 통과)

## 단계 2 — 타이포그래피
- [x] Noto Sans KR self-host (@fontsource/noto-sans-kr 400/500/700, unicode-range 한글 서브셋, font-display: swap) → packages/ui/src/fonts.css
- [x] 공통 스타일 진입점 packages/ui/src/styles.css (fonts → tokens → typography → base) + base.css(keep-all, 폰트 스택)
- [x] scripts/build-tokens.js 확장: 텍스트 스타일 23개 → tokens/typography.css (--font-size-*, .text-* 클래스) + tokens.js textStyles
- [x] 타이포 테스트 5개 (packages/ui/src/tokens/typography.test.js)
- [x] 개발용 견본 페이지 packages/ui/src/dev/TypographySpecimen.jsx (브라우저에서 폰트 로딩·행간·keep-all 확인 완료)

## 단계 3 — 프리미티브
### 3-a Button 계열 (완료)
- [x] tokens/extra.css — Figma 에 없는 값(--touch-target 44px, --screen-max-width 393px, 모션, z-index)
- [x] icons/ChevronRight, icons/Spinner — Figma 내보낸 path 그대로, currentColor
- [x] Button (primary Default/Pressed/Disabled/Loading, secondary·destructive Default/Pressed/Disabled) → packages/ui/src/components/Button/
- [x] Button 테스트 6개 (variant 렌더, chevron, disabled, loading, onClick, CSS 토큰·:active)
- [x] 갤러리 페이지 packages/ui/src/dev/Gallery.jsx + 두 앱 /dev/gallery 라우트 (react-router, DEV 전용)
- [x] 브라우저 393px 에서 Figma 와 대조: 높이 64/56, 테두리 1.5px, chevron 우측 22px, 색 전부 일치
### 3-b TextField (완료)
- [x] TextField (Default/Focused/Filled/Error/Disabled, Label·Placeholder TEXT → props) → packages/ui/src/components/TextField/
- [x] Error = 테두리 status/live + 헬퍼 텍스트(role=alert) + aria-invalid + aria-describedby
- [x] 테스트 6개 (라벨 연결, onChange, Error 접근성, Disabled, props 전달, CSS 토큰)
- [x] 갤러리에 5개 상태 + 제어/검증 예시 추가
- [x] 브라우저 393px 에서 Figma 와 대조: 345×50, 테두리 1/1.5px, 패딩 10/12, radius 10, 라벨 간격 5, 색 전부 일치
### 3-c-1 매니저 컴포넌트 + 공통 (완료)
- [x] 아이콘 19개 (Back, Check, ChevronRightSmall, Badge×7, StatusOk/Issue, Tab×5, InfoClock/Pin, Timer) → packages/ui/src/icons/ — Figma path 원본, currentColor
- [x] utils/cx.js (클래스명 합치기)
- [x] NavBar (Title TEXT, onBack) · StatusBadge (Status 7종) · ChecklistItem (Checked|Unchecked + Label) · CameraStatusItem (OK|Issue + Label/Value) · ListItem (Label)
- [x] ManagerTabBar (Active 5종 + Show Call Badge + Call Badge Count) · ScheduleCard · JobCard (CTA Label) · CallCard (Expiry/Accept Label/Reject Label)
- [x] Sheet (바텀시트 껍데기: dialog/aria-modal, ESC·배경·핸들로 닫기, 포커스 이동·복귀, 스크롤 잠금) · Skeleton
- [x] 테스트 17개 추가 (총 39개)
- [x] 갤러리에 11개 섹션 추가
- [x] 브라우저 393px 대조: NavBar 52, TabBar 83, ListItem 52, Checklist 44, Camera 46, ScheduleCard 107, JobCard 254, CallCard 315 — Figma 와 1px 이내
- [x] fix: Vite optimizeDeps.include (링크 패키지 때문에 React 가 두 벌 로드되던 문제)
### 3-c-2 고객용 + 라이브 컨트롤 (완료)
- [x] 아이콘 31개 추가 (NoticeIcon 18, InfoIcon Calendar/Person, 고객용 탭 3, 라이브 8) → icons/ (총 53개). icons/noticeIcons.js 에 이름→컴포넌트 맵
- [x] 3D 래스터 6종 WebP 1x/2x/3x → assets/ (수리 탭, 카테고리 4, 선물 일러스트). Figma PNG 원본에서 잘라 변환 (SVG 화 안 함)
- [x] StatusBar(높이만) · TabBar(Active 4종) · RadioOption(Selected|Default) · Chip · NoticeChip(Default|Selected + Icon 18종) · StepProgress(1~8) · ServiceCategoryList · GiftBanner
- [x] LiveCircleControl(Pause|Back15|Forward15) · LivePillControl(Capture|Exit) · LiveLabeledControl(Capture|Exit|Mute) — 오버레이형/하단바형 분리 유지
- [x] tokens/extra.css 에 "Figma 원본색(변수 없음)" 구역 추가 (--raw-*)
- [x] 테스트 13개 추가 (총 52개), 갤러리 10개 섹션 추가
- [x] 브라우저 393px 대조: TabBar 83, RadioOption 55, Chip 28, NoticeChip 44, StepProgress 106×8, 카테고리 100, GiftBanner 160, Pause 72 / Back15 56, Pill 38, Labeled 78/88 — Figma 와 1px 이내

## 단계 4 — 화면
### M1 Manager/Home · Home/Empty · Call/List · Call/Empty (완료)
- [x] 프로토타입 연결 68개 추출 → apps/manager/src/routes.jsx 상단 표 + 라우트 16개 (미구현 화면은 Placeholder)
- [x] ui: ScreenLayout(StatusBar→NavBar→Content→하단 고정(StickyCTA+TabBar)) · Card(tone default/live/brand/subtle) · Dot · EmptyState · Button outline variant · EmptySmile/EmptyCall 아이콘 · CallCard onOpen
- [x] api: mock.js(Figma 값) + manager.js(getHome/getCalls) + README.md 스키마 표. `?scenario=empty` 로 빈 상태 확인
- [x] screens/home/Home.jsx (Empty 조건부) · screens/call/CallList.jsx (Empty 조건부) · useManagerTabs
- [x] 브라우저 393px 대조: 4화면 모두 Figma 스크린샷과 일치. Call/List 문서 높이 893 (Figma 894). 탭 이동·CTA·카드 탭 동작 확인
- [x] 테스트 4개 추가 (총 56)
### M2 Call/Detail · Call/RejectSheet · Job/Detail · Job/Preparing (완료)
- [x] ui: Card tone muted · padding xs/list · gap none/xs · divided / Button outlineGray / ChecklistItem readOnly / ScreenLayout NavBar 아래 8px · StickyCTA(1px 선, 14/24/28, 간격 10)
- [x] api: getCall · getRejectReasons · rejectCall · getJob · getEquipmentStatus + README 스키마
- [x] screens/call/CallDetail.jsx (RejectSheet 는 Sheet + RadioOption 6개, `?reject=1` 로 바로 열림) · screens/job/JobDetail.jsx · screens/job/JobPreparing.jsx
- [x] 브라우저 393px 대조: 4화면 Figma 와 일치 (Job/Detail 문서 958 vs Figma 968). 프로토타입 링크 6개 동작 확인 (수락/거절/거절하기/현장 도착/카메라 다시 연결/청소 시작/뒤로)
- [x] 테스트 1개 추가 (총 57)
### M3 Job/Preparing/Error · Job/InProgress · Job/PauseSheet · Job/Paused (완료)
- [x] ui: AlertIcon(28) · ProgressBar · Card tone danger/warning/warningMuted · padding note/alert · gap smd · ScreenLayout gap ml(22)/none
- [x] api: getEquipmentStatus({error}) · getActiveJob · setChecklistItem · getPauseReasons · pauseJob · getPausedJob · resumeJob + README
- [x] screens/job/JobPreparingError.jsx · JobInProgress.jsx (PauseSheet 포함, 체크리스트 토글 → 진행률 갱신) · JobPaused.jsx
- [x] 브라우저 393px 대조: 4화면 Figma 와 일치 (InProgress 문서 1343 vs Figma 1325). 체크 토글·일시정지 시트·다시 시작 동작 확인
- [x] 테스트 1개 추가 (총 58)
### M4 Job/CompleteConfirm · Job/Complete · Settlement · Settlement/Detail (완료)
- [x] ui: CompleteCheckIcon(56) · SummaryRow(라벨+값 행, tone/emphasis/size) · SettlementCard · ScreenLayout topPadding="lg"(32)
- [x] api: completeJob · getCompletedJob · getSettlement · getSettlementDetail + README
- [x] CompleteConfirm 은 JobInProgress 안의 Sheet (미완료 항목을 체크리스트 상태에서 "그룹 · 항목 / …" 로 계산) · screens/job/JobComplete.jsx · screens/settlement/Settlement.jsx · SettlementDetail.jsx
- [x] 브라우저 393px 대조: 4화면 Figma 와 일치. 링크 확인 (청소 완료 → 시트 → Complete → 정산 내역 보기 → Settlement → 카드 → Detail → 뒤로 → 탭 홈)
- [x] 테스트 2개 추가 (총 60)
### M5 Schedule · Schedule/Empty · Schedule/Availability · Schedule/AddUnavailable (완료)
- [x] ui: SegmentedControl(오늘/이번 주/달력) · EmptyCalendarIcon(+EmptyState icon="calendar") · SummaryRow labelTone/size sm · Card gap xxs · ScreenLayout gap sm(16)/xl(28) · Sheet gap lg(18)
- [x] api: getSchedule(빈 상태 지원) · getAvailability · getRepeatOptions · addUnavailable + README
- [x] screens/schedule/Schedule.jsx (Empty 조건부) · Availability.jsx (AddUnavailable 시트 포함, `?add=1`, 저장하면 목록에 추가)
- [x] 브라우저 393px 대조: 4화면 Figma 와 일치 (AddUnavailable 시트 높이 759 = Figma 759). 저장 → 목록 갱신 확인
- [x] 테스트 1개 추가 (총 61)
### M6 Profile · Profile/Equipment (완료) — 매니저 앱 22개 화면 전부 완료
- [x] ui: StatusBadge label 오버라이드 (Figma 인스턴스 오버라이드 "정상 연결")
- [x] api: getProfile · getEquipmentDevice + README
- [x] screens/profile/Profile.jsx (메뉴 10개, 연결 있는 2개만 이동) · Equipment.jsx
- [x] Placeholder 화면 제거 (모든 라우트가 실제 화면)
- [x] 브라우저 393px 대조: 2화면 Figma 와 일치 (Profile 문서 900 = Figma 900). 촬영 장비 → Equipment → 뒤로 → 근무 일정 관리 → Availability 확인
- [x] 테스트 1개 추가 (총 62)
### 고객용 C1 Onboarding/Slide1~3 · Auth/SignUp (완료)
- [x] Page 1 프로토타입 58개 추출 → apps/customer/src/routes.jsx 상단 표. 온보딩·인증·예약 플로우는 연결이 없어 순서를 코드에서 정함
- [x] 래스터 WebP: 온보딩 슬라이드 3장(1x/2x — 원본이 2x 크기), 소셜 버튼 3 + 전화 가입 버튼 (1x/2x/3x), 가입 일러스트 (1x/2x/3x) → packages/ui/src/assets/index.js 의 ASSETS
- [x] ui: ImageButton(글자가 이미지에 든 버튼, aria-label 필수) · ScreenLayout statusBar={false} · extra.css 원본 그라디언트·색 4개
- [x] apps/customer: api/customer.js(signUpWith) · routes.jsx(라우트 14개, 미구현은 Placeholder) · useCustomerTabs · screens/onboarding/Onboarding.jsx(스와이프 캐러셀) · screens/auth/SignUp.jsx
- [x] 브라우저 393px 대조: 슬라이드 3장·SignUp Figma 와 일치 (일러스트가 카카오 버튼 위에 겹치는 순서까지). 탭 이동 1→2→3→가입, 소셜/전화/로그인 링크 확인
- [x] 테스트 1개 추가 (총 63)
### 고객용 C2 Auth/TermsConsent · LoginPhone · ProfileInfo-Phone · ProfileInfo-Social (완료)
- [x] 4 프레임 구조·프로토타입 확인 (연결 0개 → 이동은 코드에서 정함). 래스터 WebP: 약관 안내 자물쇠 아이콘 50×48, 로그인 일러스트 262×263 (가입 일러스트와 같은 원본, 자르기만 다름) → ASSETS.termsNoteIcon · loginHero
- [x] ui: Checkbox(원형 25) · TermRow(체크박스 + 보기>) · StepDots(점 4 + 01/04) · PhoneField(010 앞자리 + 번호 + 가리기 토글 + 인증하기) · BackButton(떠 있는 뒤로가기) · NoteCard(아이콘 + 안내문) · TextField `success` · Card `padding="none"` · 아이콘 3개(BackArrowIcon, CheckMarkIcon, EyeIcon) · extra.css 원본색 4개 · 갤러리 6 섹션
- [x] apps/customer: api(getTerms, agreeTerms, loginWithPhone, checkPassword, requestPhoneVerification, saveProfileInfo) · screens/auth/{TermsConsent,LoginPhone,ProfileInfo}.jsx + auth.module.css(공통 레이아웃) · routes 3개 교체
- [x] 브라우저 393px 대조: 4화면 Figma 와 일치 (카드 219≈220, CTA 682=682, 뒤로 21/52=21/52, 필드 y 1px 이내). 전체 동의 → 5개 체크 → 다음 → /auth/profile-info?method=, 로그인 → /home, 비밀번호 8자 → 헬퍼 표시, 번호 입력 → 인증하기 brand 채움, 다음 → /auth/profile-address
- [x] 테스트 12개 추가 (총 75)
### 고객용 C3 Auth/ProfileAddress · ProfileHousing · SignUpComplete · Home/Standard-A (완료)
- [x] 4 프레임 구조·프로토타입 확인 (Home 프레임 탭 → Home/Realtime 1개, 나머지 0개). 래스터 WebP 9종: 거주형태 아이콘 4(스프라이트에서 잘라냄) · 안내 전구 · 반려동물 2 · 가입 완료 이미지 401×852(1x/2x) · 홈 히어로 옆 장식(hero-peek). login-hero → hero-room 으로 이름 변경(홈과 공유)
- [x] ui: SearchField(돋보기 입력) · ImageOption(3D 아이콘 선택 카드 sm/lg) · SlideCounter · CarouselDots · NoteCard tone=brand · Checkbox reverse/muted/labelStyle · TextField textStyle=labelM · SearchIcon · extra.css 원본색 2개 · 갤러리 4 섹션 + 3 행
- [x] apps/customer: api(saveProfileAddress, getHousingOptions, saveProfileHousing, completeSignUp, getHome) · screens/auth/{ProfileAddress,ProfileHousing,SignUpComplete}.jsx · screens/home/Home.jsx · routes 4개 교체
- [x] 브라우저 393px 대조: 4화면 Figma 와 일치 (Address 필드 y 1px, Housing 1275 화면 4px 이내, Home 제목 102=102 · 슬라이드 제목 307=307 · 점 394=394 · 탭바 769=769). 주소 → 거주형태 → 가입 완료(이미지 탭) → 홈, 홈 카테고리 → /booking/home/frequency
- [x] 테스트 6개 추가 (총 81)
### 고객용 C4 Home/Realtime · Repair · Reservations · Settings (완료)
- [x] 4 프레임 구조·프로토타입 확인 (Home/Realtime ⇄ Standard-A, Repair 카테고리 8개 → 예약 Frequency). 래스터 WebP 12종: 수리 아이콘 8 · 수리 히어로 · 실시간 히어로 + 옆 장식 · 수리 선물 일러스트
- [x] ui: UnderlineTabs · ReservationCard(+RESERVATION_STATUS) · Toggle · MenuRow(chevron/value/toggle) · MenuGroup · ProfileCard(Avatar 교체필요) · RepairCategoryList(+REPAIR_CATEGORIES) · GiftBanner variant=repair · ChevronRightTinyIcon · extra.css 그라디언트 2개 · 갤러리 5 섹션
- [x] apps/customer: api(getRepair, getReservations, changeReservation, cancelReservation, getSettings, updateNotificationSetting, logout) · Home.jsx 캐러셀 2장(탭·스와이프, 슬라이드별 배경·이미지 위치) · screens/repair/Repair.jsx · screens/reservations/Reservations.jsx · screens/settings/Settings.jsx · routes 3개 교체
- [x] 브라우저 393px 대조: 4화면 Figma 와 일치 (Home/Realtime 히어로 102·옆 장식 125, Repair 히어로 97·그리드 412·배너 598, Reservations 탭 207·카드 256/479, Settings 1233 화면 그룹 y 1px 이내). 히어로 탭 → 02/02 일반 청소 + 배경 전환, 탭바 이동, 토글, 로그아웃 → /signup, 수리 카테고리 → /booking/appliance/frequency
- [x] 테스트 8개 추가 (총 89)
### 고객용 C5 Booking/Frequency · DateTime · Step2 · AddressSearch · AddressConfirm (완료)
- [x] 4 카테고리 diff (use_figma 로 구조·문구·간격 서명 비교): DateTime·Step2·AddressSearch·AddressConfirm 은 4개 완전 동일. Frequency 만 ServiceLabel 문구(가정/원룸 청소·사무실 청소·가전청소·입주(이사) 청소)와 프로모션 배너(home 은 Banner/RecurringPromo 258, 나머지는 "PromoBanner (자리표시 · 이미지 교체 필요)" 217)가 다름 → `/booking/:category/...` 하나로
- [x] ui: StepNavBar(bar/sheet) · SelectField(calendar/chevron) · PromoBanner(recurring/placeholder) · Calendar · TimeSlotGrid · SearchField filled · 아이콘 6개(Calendar, MonthChevron, Verified, Benefit×3) · 주소 검색 결과 이미지(1x/2x) · extra.css 원본색 4개 · 갤러리 5 섹션
- [x] apps/customer: api(getBookingCategory, getFrequencyOptions, getTimeSlots, getAddressReuseOptions, searchAddress, saveBookingStep) · screens/booking/bookingDraft.js(sessionStorage 초안) · Booking{Frequency,DateTime,Step2,AddressSearch,AddressConfirm}.jsx · routes 5개 + C6 자리
- [x] 브라우저 393px 대조: 5화면 Figma 와 1px 이내 (Frequency 제목 119=119·배너 502/503·버튼 768=768, Step2 160/204/274/408/452/736 모두 일치, 시트 헤더 점 84=84). office 카테고리 라벨·자리표시 배너 확인. 흐름: 주기 → 날짜(16일)·시간(11:00) → 다음 → 기존 주소 아니오 → 주소 검색 입력 → 확인(검색어 미리 채움) → 상세 입력 → /options
- [x] 테스트 8개 추가 (총 97)
### 고객용 C6 Booking/Options · NotesRequests · Confirm · Complete (완료)
- [x] 4 카테고리 diff: Complete 는 완전 동일. Options 는 옵션 문구·가격·아이콘(교체필요 20개), NotesRequests 는 주의사항 칩 문구·아이콘(9×4), Confirm 은 서비스 종류 라벨·주기 부가 문구·칩 내용만 다름 → 전부 mock 데이터(카테고리 키)로 갈라짐
- [x] 래스터 WebP: 옵션 아이콘 20종(교체필요 임시 · Figma 스프라이트/개별 이미지에서), Confirm 행 아이콘 7 + 히어로, 요청사항 팁 전구, 예약 완료 이미지 504×896(1x/2x)
- [x] ui: OptionItem(무료/유료, Selected 는 코드에서) · TextArea · TipCard · SummaryItem(값/칩/요청사항 상자 + 수정하기) · BookingInfoCard · extra.css 원본 그림자·그라디언트 · 갤러리 4 섹션
- [x] apps/customer: api(getBookingOptions, getNoticeChips, getConfirmCopy, getSavedAddress, createBooking, getCompleteRows) · Booking{Options,NotesRequests,Confirm,Complete}.jsx · bookingDraft 함수형 update + formatDate/formatTimeKo · routes 4개 (예약 플로우 9화면 완성, Placeholder 는 /service/* 만 남음)
- [x] 브라우저 393px 대조: Options 제목 137=137·무료 3개 x 33/158/283 일치·유료 446=446·버튼 736=736, NotesRequests 카드 253/609, Confirm 헤더 131=131·카드 246/247·수정하기 275/276·완료 버튼 1004=1004(304 폭), Complete 카드 (28,488) 344×210 일치. 흐름: 무료 2개 선택 시 3번째 비활성 → 다음 → 칩 선택·요청사항 입력 → 다음 → 확인(주소·일정·주기·칩·요청사항 반영) → 완료 → 완료 화면(값 4개) → 탭 → /home, 초안 삭제 확인
- [x] 테스트 7개 추가 (총 104)
### 고객용 C7 Service/Start · Service/LivePortrait · Service/LiveLandscape (완료 — 단계 4 고객용 29화면 전부 완료)
- [x] 최신 프레임(2167:2238 · 2165:2222)만 변환. (구버전 · 사용 안 함) 2개는 손대지 않음. 프로토타입 연결 0개
- [x] 래스터 WebP: 매니저 아바타 48(원본 96px → 3x 확대), Service/Start 소개 이미지 403×852(교체필요, 1x/2x). VideoSurface 는 포스터 없이 <video playsInline muted autoPlay>
- [x] ui: LiveBadge · RealtimePill · ViewerChip · ManagerIdentity(light/onVideo) · VideoSurface(portrait/landscape 스크림, forwardRef) · CleaningStatusCard · ReassuranceBanner(Mascot 에셋 필요 → 자리표시) · LiveProgress · IconButton · 라이브 아이콘 6개 · extra.css 영상 위 원본색 14개 · 갤러리 4 섹션
- [x] apps/customer: live/useLiveSession.js(단계 5 자리 — status/videoRef/muted/toggleMute/capture/seek/back15/forward15/togglePause/leave) · api getLiveSession · screens/service/{ServiceStart,ServiceLivePortrait,ServiceLiveLandscape}.jsx · routes 3개 · Placeholder 삭제
- [x] 브라우저 대조: LivePortrait 393px 모든 요소 Figma 와 1px 이내(뒤로 16/58, 로고 167/60, 아바타 24/111, 영상 24/175 345×430, LIVE 36/187, 시청자 94, 시각 246/192, 상태 카드 36/537, 전체화면 335/503, 컨트롤 74/165/266 y 642, 배너 24/759). LiveLandscape 852×480 전부 일치(아바타 24/22, 알약 605·724, 원형 306/390/490, 트랙 24/406 804, 시간 430, 음소거 760, 전체화면 804). 세로 393px 에서는 90° 회전·축소. Start 버튼 39/714 314×75
- [x] 흐름: Start → 실시간 영상 보기 → LivePortrait → 전체 화면 → LiveLandscape → 전체 화면 닫기 → LivePortrait → 음소거(video.muted 토글) → 종료하기 → /reservations
- [x] 테스트 5개 추가 (총 109)

## 단계 5 — 라이브 (LiveKit) — 구조만 완료 (2026-09-19, 사용자 결정: 실제 연결은 뒤로 미룸)
- [x] 고객용 `apps/customer/src/live/{README.md, liveClient.js, useLiveSession.js}` — 앱에 하나뿐인 시청 클라이언트(상태 + join/leave/toggleMute/capture/seek/back15/forward15/togglePause) + 훅(useSyncExternalStore). 화면 사이를 오가도 음소거 등 상태 유지
- [x] 매니저용 `apps/manager/src/live/{README.md, liveClient.js, useLiveSession.js}` — 송출 클라이언트(start/pause/resume/stop) + 훅. Job/Preparing(청소 시작 → start) · InProgress(일시정지 → pause, 완료 → stop) · Paused(다시 시작 → resume) 연결
- [x] `server/` 뼈대 — 패키지 없이 Node 내장 http. `/health`, `/live/token`·`/live/recording/start|stop`·`/live/recording/:id` 는 501 + 안내. README 에 API 표, `.env.example`, `npm run dev:server`. 루트 workspaces 에 추가
- [x] 각 파일 상단에 단계 5 체크리스트(TODO(stage5)). 테스트 2개 추가 (총 111)
- [ ] (미착수) 실제 연결: livekit-client · livekit-server-sdk · LiveKit 클라우드/자체 서버 결정 · 녹화 저장소 · 인증

## 단계 5 — 매니저 휴대폰 송출 (2026-10-10, 사용자 결정 "바디캠이 아닌 스마트폰으로" · "카메라 마이크 권한 제거하지 마")
영상 출처를 바디캠(RTMP Ingress)에서 **매니저 휴대폰 후면 카메라 → LiveKit** 으로 바꿨다. `LIVE_SOURCE` 기본 `app`, 바디캠 코드는 `ingress` 로 남김(지우지 않음). 사양은 BACKEND_PLAN §8.0, 결정 §13-18 갱신·§13-14 다시 열림.
- [x] 검사 도구: Windows 에서 `check:tokens` 제외 경로(`\`)·`tokens.test.js` CRLF 실패 수정 (기존 실패, 이번 작업 전)
- [x] 문서: CLAUDE.md §8 단계 5·§11-12, BACKEND_PLAN §8.0·§13-14·§13-18, PLAN.md 패키지 표(keep-awake), api.md §5.4(start 응답 `publish`, 상태 응답 `source`, 웹훅 `track_*`)
- [x] 백엔드: `app.livekit.source` · `LiveKitGateway.publishToken`(카메라만 publish, 구독·데이터 불가, 6시간) · `LiveService.startFromPhone`(기기 행 '휴대폰 카메라' 를 만들고 Ingress 생략, identity `camera-<managerId>` — 일시정지 mute·재조정기·녹화는 바디캠과 같은 코드) · 웹훅 `track_published/unpublished` → 카메라 붙음·끊김 · 상태 응답 `source` · 재개 때 원격 unmute 가 거절돼도 트랙이 풀렸는지 확인. 테스트 `LiveAppSourceTest`(9)·`LiveControllerTest` 1건·`LiveAppSourceIT`(**IT 미실행 — 이 컴퓨터에 Docker 없음, 컴파일만**). 백엔드 523 통과
- [x] 매니저 앱: `live/sources/{cameraVideoSource,index}.js`(영상 입력 — LiveKit 을 모름) · `roomConnector.publishRoom`(publish·재연결·replaceTrack·unmute) · `liveClient` prepare/releaseCamera/송출 연결·재연결·카메라 복구·keep-awake · 훅 반환값(source·camera·cameraError·reconnecting) · Job/Preparing(카메라 실패 배너 자리에 미리보기) · Job/InProgress(재연결 문구). 테스트 `liveClient.phone.test.js`(6)·JobPreparing 2건
- [x] 고객 앱: `roomConnector`·`liveClient` 에 TrackUnsubscribed(멈춘 화면 떼기)·Reconnecting/Reconnected(새로고침 없이 이어짐)
- [x] Capacitor: 매니저 AndroidManifest `CAMERA`·`RECORD_AUDIO`·`MODIFY_AUDIO_SETTINGS`, Info.plist `NSCameraUsageDescription`·`NSMicrophoneUsageDescription`, `@capacitor-community/keep-awake` 8.0.1, `cap:sync`
- [ ] **실기기 확인 전** — LiveKit Cloud 키(`LIVEKIT_MODE=cloud`·URL·KEY·SECRET)와 웹훅 등록이 필요하다. 확인할 것: 매니저 미리보기 → 청소 시작 → 고객 시청(지연) · 일시정지 검은 화면/재개(원격 unmute 설정 여부) · 앱 백그라운드 다녀오기 · 완료 뒤 녹화본(single-speaker 레이아웃이 휴대폰 트랙을 고르는지)
- **판단(Figma 에 없음)**: ① 청소 준비 화면은 휴대폰 송출이면 머리(제목·배너) 자리에 미리보기(높이 320, VideoSurface 기본 430 은 장비 목록을 밀어냄) ② 카메라 실패 배너 본문은 Figma 의 바디캠 문구 대신 이유별 문구 `PHONE_CAMERA_COPY`(권한 거부·카메라 없음·사용 중·그 밖) ③ 진행 화면의 재연결 문구 `LIVE_STATE_COPY.reconnecting` ④ 카메라 행 값은 서버와 같은 '연결됨'·'연결 안 됨' ⑤ 기기 행 표시 이름 '휴대폰 카메라'(장비 화면) ⑥ iOS 권한 문구 — 카메라는 사용자 승인 문구, 마이크는 같은 형식으로 정함 ⑦ 화질 720p·24fps(데이터·발열) ⑧ 지금은 영상만(결정 §13-23) — 마이크 권한은 선언만
- **남은 위험**: 앱이 백그라운드로 가면 WebView 카메라가 멈춘다(돌아오면 다시 켜 트랙을 바꿔 끼움, 백그라운드 송출은 §13-14 결정 필요) · 장시간 송출 발열·배터리 · 모바일 데이터(720p ≈ 시간당 0.7~1.3GB) · 녹화 레이아웃 · 원격 unmute

## 수리 예약 플로우 (R1) — Figma `rGCjx3zWMBk18fXgNZ9UYX` (2026-09-21, 완료)
사용자 요청: 수리 탭이 청소 예약 플로우를 그대로 쓰던 것을 수리 전용 8화면으로. 커밋 `e94c4e0`(ui) `cb05124`(customer) `59c4781`(backend).
- [x] R1-a 프리미티브 11개 + `TimeSlotGrid layout=flat` + `StepNavBar total`: `StepQuestion` `OptionGroup` `SelectionSummary` `PhotoPicker` `InfoNote` `SelectedAddressCard` `ToggleChip` `WeekCalendar` `ConfirmSummaryRow` `SuccessHeader` `StackedInfoCard`. 원본색 5개 `tokens/extra.css`(`--raw-repair-option-bg` `--raw-repair-notice-bg` `--raw-success-green` `--raw-repair-card-shadow` `--raw-repair-complete-gradient`). 갤러리 `RepairGallery`.
- [x] R1-b 화면 8개 `apps/customer/src/screens/repair/booking/` — 가전수리 종류 선택(1:493) · 고장 증상 선택(1:515) · 증상 상세와 사진 첨부(1:455) · 서비스 주소 확인(1:424) · 방문 날짜와 시간 선택(1:356) · 현장 상황과 요청사항(1:326) · 예약 내용 최종 확인(1:273) · 예약 완료(1:236). 라우트 `/repair/booking/:category/{appliance,symptom,detail,address,datetime,notes,confirm,complete}`. 문구는 `api/mock.js` `REPAIR_BOOKING`. 393px 스크린샷 8장을 Figma 와 대조 — 일치.
- [x] R1-c 백엔드: `V012`(category `repair` '수리 서비스' 금액 0, 현장 상황 칩 4개, `bookings.repair_*`), `POST /customer/repair-photos`(Storage 비공개 버킷 `repair-photos`), `createBooking` 의 `repair{}`. 실제 dev Supabase 로 업로드 → 예약 → 매니저 콜 확인. 백엔드 265·앱 221 테스트.
- **판단한 항목**: ① 가전수리 외 7개 수리 종류(가구 조립·배관·누수 …)는 Figma 에 가전·증상 화면이 없어 증상 상세부터 시작(6단계). `TODO(figma)` ② 냉장고 외 가전의 증상 목록이 Figma 에 없어 냉장고 목록 중 공통 3개(소음·전원·기타)만 ③ 달력이 한 주 띠(1:370)라 화살표를 주 단위로(Figma 이름은 이전/다음 달) ④ 사진 삭제 UI 가 없어 첨부 사진을 탭하면 삭제 ⑤ 증상 라벨이 Figma 에서 폭 81 로 잘려 있어(디자인 결함) 폭을 채우게 ⑥ 현장 상황 요약 문구 "반려동물 있음"·"공동현관 호출 필요" 는 Figma 예시(주차 가능·엘리베이터 있음)에서 유추 ⑦ 주소 변경은 청소 예약의 주소 검색·확인 화면을 `?returnTo=` 로 빌려 씀 ⑧ 수리비 0원, 매니저 콜 '예상 정산 0원' — 정산 정책은 결정 필요 ⑨ 매니저 Figma 에 수리 항목이 없어 콜 상세·작업 상세 요청사항에 "가전/증상/상세/사진 N장" 줄로 표시 ⑩ 가전·증상은 키가 아니라 표시 문구로 저장(카탈로그가 앱 안에 있어서) ⑪ 사진 미리보기 서명 URL 24시간 — 만료되면 다시 첨부 ⑫ `duration_hours.repair`=2시간 ⑬ 완료 화면 "예약 확인" → 내 예약 탭.
- **결정 필요**: 수리 예약의 매니저(기사) 매칭·정산 규칙 — 지금은 청소와 같은 매칭(강남구 승인 매니저)에 금액 0 으로 나간다. 기사 풀을 따로 둘지, 출장비를 둘지.

## 외부 검토 3차 반영 — `88e8810` 기준 6건 (2026-09-21, 완료)
전부 코드로 확인·수정. `9c87a1d` 백엔드 · `52af20c` 앱. 백엔드 266·앱 226 테스트, lint·토큰·빌드 통과. **IT 미실행(Docker)**.
1. [높음] 재개/중단 확인 폴링(`getParticipant`)이 UPSTREAM 을 던지면 pending 표시를 남기지 못해 서버 paused ↔ 영상 송출이 어긋남 → 폴링 예외를 '미확인' 으로 (`LiveService.setMuted`).
2. [높음] bookingDraft 모듈이 로드되지 않은 실행에서 로그아웃해도 sessionStorage 예약 초안이 남음 → `session.clear()` 가 직접 지움.
3. [높음] refresh 응답이 로그아웃보다 늦게 오면 옛 토큰 부활 → 시작할 때의 refreshToken 과 다르면 버림 (두 앱 http.js).
4. [기능] 수리 사진 업로드가 FormData 를 JSON 으로 바꿔 `{}` 로 감 + 실패 문구 미표시 → http.js FormData 통과, RepairDetail 은 `useAction.error`. 실서버로 업로드→예약→기사 상세 확인.
5. [누락] 매니저 홈·고객 내 예약 자동 갱신 없음, 전경 푸시 무시 → 30초 폴링 + `onPushReceived` 이벤트로 즉시 갱신(홈·콜 목록·내 예약).
6. [공백] 기사가 사진을 못 보고 가전·증상이 키(`fridge`)로 감 → 앱이 표시 문구로 보내고, `JobResponse.photos`(서명 URL 1시간) + 매니저 작업 상세 썸네일(코드에서 정함). CSP img-src https:.
- 검토가 짚은 운영 공백(기사 매칭·정산 0원 / iOS 푸시 / 실제 결제)은 그대로 — 위 "수리 예약 플로우" 결정 필요 항목과 B7·결제 범위.

## 외부 검토 4차 반영 — `5995516` 기준 4건 (2026-09-21, 완료)
전부 실제 문제로 확인·수정. 백엔드 267·앱 229 테스트, lint·토큰·빌드 통과. **IT 미실행(Docker)**.
1. [높음] `PushService.send`(@Transactional) 가 던지면 아웃박스 배치가 rollback-only → sent/failed 기록 유실·반복 발송 → send 는 실패 수를 돌려주고 `PushHandler`(트랜잭션 밖)가 던진다.
2. [높음] A 계정 refresh 를 기다리다 B 로 로그인하면 늦은 A 401 이 B 세션을 지움 → 거부된 토큰이 현재 토큰일 때만 `session.clear()` (두 앱).
3. [중간] 매니저 프리뷰 첫 연결 실패가 조용히 남고 재시도 없음 → 폴링 때 재연결(10→60초 백오프), stop/reset 이면 중단.
4. [중간] 폴링 응답이 취소·수락 뒤 재조회를 덮음 → `useLatest`(ui) 로 옛 응답 폐기 — 내 예약·매니저 홈·콜 목록.

## 검수 3차 (적대적 자체 검수) — `8e73a5b` 기준 (2026-09-21)
외부 검토가 실제 결함을 찾아낸 5가지 유형(응답 순서·경합 / 프레임워크 경계 효과 / 모듈 로드 순서 / 조용한 실패 / 최근 쓴 코드)만 노려 영역별 발견자 → 영역당 검증자 1개로 돌리고, 검증자가 실제로 확인한 것만 고쳤다. 산출물은 scratchpad `audit3/`(세션이 바뀌면 사라짐).

### 배치 1 — 앱 4영역 (완료: `581e363` `8e8844d` `b60ebb2` `4f864cc` `4b46217`) — 앱 234·백엔드 268 테스트, lint·토큰·빌드 통과. **IT 미실행(Docker)**
- ui: `useLatest` 가 매 렌더 새 객체(useEffect 재구독) → useMemo. `useAsync.setData` 가 error 를 남김 → error: null. 테스트 추가.
- 고객·매니저 핵심: 로그아웃 중 refresh 가 세션 구독을 깨워 옛 사용자에게 푸시 토큰 재등록 → `deviceToken.suspend`. 늦은 `/auth/me` 가 새 계정 세션을 덮음 → 토큰·user id 대조. 가입 초안: 저장소 막힌 환경 메모리 사본. OTP 확인 중 고친 6자리 재확인 큐(refs 는 early return 위). multipart 90초·`timeoutMs` 옵션, 로그아웃 5초. `initPush` 실패 시 `started` 되돌림.
- 고객 라이브: `connectLive` 중복(폴링+join) 가드, 방 끊김 1초 뒤 refresh, 녹화본 URL 재발급 상한 3회(DECODE/SRC_NOT_SUPPORTED 는 즉시 오류), `startReplay` 가 세션·ended 를 먼저 두고 `getRecording` 실패를 드러냄, `join(replay:true)` 가 실시간 시청 중에도 녹화본으로 전환.
- 고객 화면: 견적 실패 재요청, 수리 사진 장별 반영 + 업로드 전 축소(`api/image.js`, 긴 변 1600 JPEG 0.8), 도로명 변경 시 상세주소 초기화, `bookingDraft` 저장 예외 삼킴, Settings 로그아웃 useAction + 오류 화면에도 로그아웃, 알림 토글 키별 순서 + role=alert.
- 매니저 화면: JobInProgress `videoBlocked` 시도마다 초기화, Schedule 월 실패 재시도, Availability 저장 뒤 재조회 useLatest, Notifications 토글 순서 + 실패 문구.
- 백엔드: 진행 중 작업의 라이브 `start` 재호출이 자정 이후 409 → 날짜 조건은 SCHEDULED 에만.
- 검증자가 low 로 낮춰 **안 고친 것**: pause 실패 뒤 3초간 '일시정지' 표시 흔들림(서버 진실과 일치, 재시도 통과).

### 배치 2 — 백엔드 4영역 (완료: `c5f27d7` `3f16697` `647e799` `a11295c`) — 백엔드 275 테스트, 앱 234, lint·토큰·빌드 통과. **IT 미실행(Docker)**. V013 은 다음 서버 기동 때 dev DB 에 적용된다
발견 22건 → 검증자가 실제로 확인 20건(2건 반박: Egress 실패 재녹화는 runbook 이 명시한 동작, 미방문 취소의 시리즈 지연은 정상 완료 경로와 같은 설계상 지연). 검증자는 spring-tx 7.0.9 를 javap 로 열어 package-private 메서드도 프록시된다는 전제를 확인했다.
- **푸시(높음)**: `OffersDispatched` 가 매니저를 순서대로 돌며 첫 실패에서 던져 뒤 후보는 그 회차에 못 받고, 앞 토큰이 영구 실패면 6회 내내 못 받았다 → 전원에게 보낸 뒤 던짐. `BookingCompleted` 도 고객·매니저 둘 다 보낸 뒤. FCM Android `tag`=collapseKey(재전송 트레이 교체), `SENDER_ID_MISMATCH` 토큰 삭제.
- **트랜잭션 프록시**: `LiveService.pause/resume(noRollbackFor)` 안에서 참여 트랜잭션 `requireManagerBooking` 이 404 를 던지면 rollback-only → `UnexpectedRollbackException`(500) → `noRollbackFor` 추가. 재조정기가 프록시를 거쳐 부르는 `bindIngress` 의 ApiException 이 배치 커밋을 깨 그 분의 나머지 세션이 건너뛰어짐 → `bindIngressQuietly` + `each()` 세션 단위 예외 격리.
- **라이브 재조정기**: mute 확인 실패(카메라는 muted) 뒤 작업 in_progress 면 트랙을 켜지 않았다 → unmute. `retryMute` 확인 실패가 pending 을 남기지 않아 다음 live/pause 가 '이미 paused' 로 통과(§8.2.1 위반) → pause/resume 과 같은 표시. live/pause ①→jobs/pause ② 사이에 재조정기가 unmute 하던 창 → `paused_at`(V013) 2분 유예. `live/resume` 은 작업 PAUSED 면 거부. `ManagerDevice @DynamicUpdate`.
- **예약**: 취소가 `findByIdAndCustomerId` 로 올린 엔티티를 FOR UPDATE 뒤에도 옛 상태로 써서 그 사이 수락된 회차를 매니저 없이 취소 → 프로젝션 `findSeriesRef`. 수리 예약 `frequency` 빈 값이 recurring 으로 채워져 0원 시리즈 → oneTime 강제. `getJob.requests` 의 "사진 N장" 을 실제 URL 수에 맞춤. 사진 업로드 시간당 60장 RateLimiter. `api.md` getJob `photos`·ext·429 보강.
- **기타**: `LiveKitCloudGateway` busy 문구 판정(죽은 코드) → 실패 뒤 `getIngress` 재판정. 가입 (d) passwordGrant 실패 → '가입은 완료됐어요. 로그인해서…'.
- **안 고친 것(검증자 low·설계상 동작)**: Egress 실패 뒤 재녹화 없음(runbook 대로 '업로드 실패' 안내), 미방문 취소가 시리즈를 바로 닫지 않음(term_end+1 에 SeriesDispatcher 가 닫음), INVALID_ARGUMENT 를 토큰 무효로 보는 것(본문 모양이 서버 고정이라 이론적), `manager_devices` 행 잠금(DynamicUpdate 로 lost update 만 막음).


## 개선 제안 반영 — 검증된 제안 (2026-09-22, 완료: `6fd72e0`~ 22 커밋)
"디자인·구성·흐름 개선 제안" 20건을 검토자 6명이 코드로 반박(타당 3·전제 틀림 6·더 작은 길 11)하고 영역별로 빠진 제안 69건 + 완결성 검토 12건을 찾았다. 사용자 승인("전부 추천대로")으로 아래를 반영. 앱 251·백엔드 284 테스트, lint·토큰·빌드 통과, dev DB 에서 V013~V018 적용·기동 확인. **IT 미실행(Docker)** — `.github/workflows/ci.yml` 이 원격 저장소가 생기면 돌린다.

### 흐름
- 확인 화면 "수정하기" → `useReturnTo`(?returnTo=) 로 한 단계만 고치고 복귀(청소·수리 8단계). 예약 없는 라이브 화면은 /reservations 로(mock 은 DEV 만). 카드·푸시 → 바로 /service/live. 인증번호 재전송 60초 카운트다운(`resendAfter`, `useCountdown`, `PhoneField.verifyDisabled`). RouteShell: 스크롤 맨 위·첫 h1 포커스·문서 제목·LoadingBar. 예약 카드 `changeable`(24시간 규칙 미리)·예정 탭 가까운 순·예약 변경 프리필+일반 NavBar. 라이브 컨트롤은 모드별(실시간엔 탐색 없음, 녹화본 세로에도 재생/멈춤·LivePlayIcon), 안심 배너는 실시간만, 일시정지 문구 즉시 갱신, 세로↔가로 유예 2초. 완료 화면 `managerAssigned`·시간 표기 통일. 매니저: 준비 화면 하나(카메라 issue → Error 프레임, '다시 연결' = 재조회), JobDetail 뒤로가기는 온 곳으로, Schedule 상태를 URL 에, 콜 거절은 목록 시트에서, 사유는 시트 안(ReasonList), 콜 카드 만료 카운트다운, 근무 불가 삭제·'매주 반복' 표시, 미승인 매니저 안내(홈 카드·콜 탭 '승인 대기 중'), 정산 '더 보기', JobComplete 업로드 중 재조회.
- 오류 표면: `ErrorState/ScreenError(error)` — NETWORK/404/서버 문구 구분, 404 는 재시도 없음, bare 호출 12곳에 onBack. `ActionError` 로 동작 오류 34곳 통일. 성공 토스트(`ToastHost`·`showToast`). OS 알림 권한 거부 안내(`getPushState`).
- 데이터 갱신: `usePolling`(간격·푸시·복귀, 숨김 중 정지, invalidate) — 홈·콜·내 예약. 라이브 폴링도 백그라운드에서 쉼. 푸시 토큰 등록 중복 제거.
### 서버
- 녹화본 상태 `live.recording`(none|pending|ready|failed) + `live_status_copy.ended_*`(V014) + `RecordingFailed` 푸시. 매칭 실패 `bookings.matching_failed_at`(V015) + `MatchingFailed` 푸시 + 카드 문구 + 운영자 SQL. 1:1 문의 답변: `GET /support/inquiries`·`POST /admin/inquiries/{id}/answer`·`InquiryAnswered` 푸시·'내 문의' 목록. `approved` (home/offers). `DELETE /availability/unavailable/{id}` + 작업 겹침 거절(`ManagerWorkLookup`). 정산 페이지네이션(before 커서, DB 집계). `SettlementDeletionGuard`. 원시값(`sinceLabel/streaming/online/start/end/startedAt/pausedSeconds`)으로 앱의 문구 되파싱 제거. 요청 ID(rid·X-Request-Id·500 requestId). Solapi 키 기동 검증. ECS JSON 로그. 거래성 푸시는 토글 무관. 마케팅 동의 초기값·매니저 마케팅 토글(V018). eta 문구(V016). 탈퇴 뒤 녹화본·수리 사진·Ingress 정리(`AccountMediaDeleted`), Ingress 삭제/키 회전(`rotate`). 수리 가전·증상 키 컬럼(V017). 녹화본 URL 6시간. `RATE_LIMITED` 코드. livekit_events 7일 정리. 작업 상세 '주거 정보' 행. `AdminAuth` → common/security.
### 앱 구성
- 세션은 네이티브 `@capacitor/preferences`(웹 localStorage), `session.hydrate()` 뒤 라우터. 버전 정보 = package.json(vite define). 수리 예약도 서버 카탈로그(time_slots·notice_chips). `INPUT_LIMITS`. 날짜 형식 함수 `dateKey.js` 하나로. 전역 포커스 링·sr-only·바깥 배경. 갤러리 '화면 상태' 섹션. `.github/workflows/ci.yml`.
### 안 한 것(검토자가 폐기·설계상 동작·사업 결정)
- 일시정지 단일 호출(사양 §8.2.1 개정·실기기 검증 뒤), 수신자 단위 아웃박스, 저장 주소 선택 목록(Figma 없음, Step2 가 이미 기본 주소), 라이브/녹화본 클라이언트 분리, 공용 패키지(§9.1 결정), 다크 모드, Android 전용 출시(사업 결정), 오프라인 배너(Figma·새 패키지), Egress 실패 재녹화(runbook 대로), IT 컨테이너 공유(IT 를 못 돌려 손대지 않음), ScreenLoading variant, 약관 개정 재동의(B8 이후), 수리 사진 고아 파일 정리, 라이브 문구 3곳 통합, 멱등 키 관리 통일, 매칭 후보 SQL 화(성능·상한 없음, 출시 규모엔 무관).
### 코드에서 정한 것(Figma 없음)
- `useReturnTo`, `LoadingBar`, `ToastHost`, `ActionError`, `LivePlayIcon`, `ReasonList`, `MyInquiries`, 예약 카드 '방문 24시간 전까지 변경·취소할 수 있어요', 승인 대기 카드·'승인 대기 중', 근무 불가 카드 '삭제'·'매주 반복', 세로 녹화본 컨트롤, 오류 문구(네트워크·찾을 수 없어요), 토스트 문구, OS 알림 권한 안내, 문의 답변 푸시·'내 문의', 매칭 실패 문구·푸시, 녹화 실패 문구·푸시, eta '이동 시간은 준비 중이에요'.

## 검수 4차 (코드 전체 직접 읽기) — 2026-09-22 (완료, `fb156e0`~ 10 커밋)
앱 13.4k·백엔드 13k 줄을 처음부터 끝까지 읽어 동작에 영향 있는 버그 1건 + 작은 결함 8건을 찾아 전부 고쳤다. 앱 254·백엔드 286 테스트, lint·토큰·빌드 통과, dev DB 로 기동·정산 커서 호출 확인. **IT 미실행(Docker)**.
- **[중] 푸시 재등록**: 로그아웃(서버 토큰 삭제) 뒤 같은 계정으로 로그인하면 `pushClient` 의 중복 방지 키가 남아 토큰을 다시 보내지 않았다 → 앱을 껐다 켜기 전까지 푸시 없음. 세션이 사라지면 키를 비운다(두 앱, 테스트로 재현·확인).
- `usePolling` 의 loadRef 가 useRef 가 아니라 옛 렌더의 load 로 폴링(호출처는 무해했음) → useRef + 효과에서 갱신.
- `StaleBookingCleaner` 미방문(not_started) 취소: 푸시 없음 + 시리즈를 안 닫아 회차가 다 사라진 정기청소가 term_end 까지 탈퇴를 막았다 → `BookingCancelled(not_started)`(고객·매니저 거래성 푸시, 문구 코드에서 정함) + `endSeriesIfNothingLeft`.
- 매니저 일정 달력 탭의 달 조회 로딩이 ScreenLayout 안에 ScreenLayout(main·100dvh) → Skeleton.
- 준비 화면 '카메라 다시 연결' 이 reload 로 화면 전체를 스켈레톤으로 → useAction + setData 제자리 재조회.
- 정산 목록 커서가 시각만이라 같은 시각의 정산이 빠짐 → `(createdAt, id)` 키셋, `nextBefore` 는 `<ISO>_<id>` 불투명 문자열(옛 ISO 도 받음).
- 고객 주소 삭제 때 모든 버튼이 스피너 → 누른 것만(`removingId`).
- 매니저 `liveClient.stop()` 이 startedAt 을 안 비워 다음 작업이 앞 작업 시각을 이어 받음(화면은 서버 값이라 안 보였음) → 같은 작업 재시도만 잇는다.
- `useCountdown`/`useElapsed` getSnapshot 이 Date.now() 로 매번 달라 React 'getSnapshot should be cached' 경고 가능 → 틱 사이 값 고정(구독자 수 세어 정리).
### 코드에서 정한 것
- 미방문 취소 푸시 문구: 고객 "매니저가 방문하지 않았어요 / 예약이 취소됐어요. 불편을 드려 죄송해요. 다른 일정으로 다시 예약해주세요", 매니저 "방문하지 않은 예약이 취소됐어요 / 방문일이 지나도록 청소를 시작하지 않아 예약이 취소됐어요. 문의가 있으면 고객센터로 알려주세요". 준비 화면 재조회 실패 문구 "장비 상태를 다시 받지 못했어요. 다시 시도해주세요."

## 보안 검토 — 2026-09-22 (완료: `1663085`~`4c76233`)
앱·서버·설정을 보안 관점으로 다시 읽었다. 당장 뚫리는 취약점은 없음(JWT 검증·DB 역할·소유권 404·웹훅 서명·RLS deny-all·CSP·비밀값 미커밋·의존성 0건). 강화한 것:
- 앱 세션 토큰을 `@capacitor/preferences`(평문) → `capacitor-secure-storage-plugin`(iOS Keychain / Android Keystore). 웹은 localStorage 그대로.
- `/admin/**` 는 `AdminTokenFilter` 가 컨트롤러 앞에서 X-Admin-Token 검사(컨트롤러의 require 는 이중 방어로 유지). AdminAuth 빈이 없으면 닫힘.
- `POST /auth/refresh` IP 당 60/h, `POST /auth/signup` IP 당 20/h.
- `.env.example`: 운영 ALLOWED_ORIGINS 에 `http://localhost` 를 넣지 않도록 주석.
### 결정이 필요한 항목(코드로 안 함)
- **Supabase anon(publishable) key 비활성화 또는 "Secure phone change" 켜기** — 앱은 anon key 를 쓰지 않지만, 키가 새면 로그인한 사용자의 JWT 로 Auth 의 `PUT /auth/v1/user` 를 불러 자기 번호를 남의 번호로 바꿔 그 번호의 가입을 막을 수 있다(phone 자동확인). 대시보드 작업(runbook 참고).
- 전화번호 존재 노출(`PHONE_TAKEN`): 앱 UX 를 위해 유지, IP 20/h 로 제한. 받아들일지 결정.
- 비밀번호 정책 8자 이상만 — Supabase password strength 설정으로 보완 가능.
- 메모리 RateLimiter 는 단일 인스턴스 전제(§13-11). 서버 2대 이상이면 Redis 등 공유 저장소 필요.
- Android 릴리스 서명·R8, OWASP dependency-check 는 B7.

## 사용성 개선 — 2026-09-22 (내가 두 앱을 직접 써 보고 찾은 25건, 결제 제외 전부 수정)
로컬 백엔드에 붙여 신규 가입 → 정기예약 → 취소 → 1회 예약 → 매니저 로그인 → 콜 수락 → 청소 준비까지 손으로 눌러 본 결과. 결제(PG)는 사용자 결정으로 **구조만**(§13-5) 유지.
- [x] 1 비밀번호 재설정 — 잊으면 계정이 영구히 죽었다(찾기 없음·재가입 불가·탈퇴도 비밀번호 필요)
- [x] 14 비밀번호 규칙을 입력 전부터 보여 준다 (TextField `hint`) — 4자를 넣어도 아무 표시 없이 '다음' 만 꺼져 있었다
- [x] 2 개발용 자리표시가 사용자 화면에 보이던 것 — 사무실·가전·입주 예약 2단계의 "프로모션 배너 영역 / Office 전용 이미지 필요" → 정기청소 혜택 알약 3개(`PromoBanner variant="benefits"`), 가정/원룸 배너의 빈 흰 사각형(일러스트 자리) 제거. 배너 이미지가 오면 되돌린다
- [x] **정기청소 = 매월 1회로 변경 (사용자 정정 2026-09-22, `V021`)** — 그 전에는 '한 달 동안 매주 4~5회'였다. 매월 같은 날짜·시각, 6개월 6회, 주말이면 다음 평일. 문구·견적·회차 생성·푸시·문서 전부 반영 (Figma 의 '매주 지정한 요일에 진행해요' 와 달라졌다 — 디자이너 확인 필요)
- [x] 5 정기청소가 무엇을 약속하는지 고르기 전에 안 보이던 것 → 주기 라디오에 설명("매주 지정한 요일에 진행해요")과 안내("한 달 동안 매주 … 4~5회, 회차마다 변경·취소 가능"). **주기 선택(격주·월1회)은 가격표 결정이 필요해 안 함 — 아래 '결정이 필요한 항목'**
- [x] 6 요금이 총액 한 줄뿐이라 옵션·할인이 안 보이던 것 → 기본 요금·선택 옵션·정기 할인("1회 청소보다 회당 20,000원 저렴해요") 내역 (`QuoteResponse.savingNote`, `perVisit.baseLabel/optionsLabel`)
- [x] 4 바디캠이 죽으면 '영업일 1일 안에 답변' 뿐이던 것 → 장비 실패 화면·고객센터 화면에 "고객센터 전화하기"(`app_settings.support_phone`, 비어 있으면 버튼 없음. 운영 번호는 runbook). **매니저가 스스로 '오늘 못 감' 처리하는 경로는 취소·보상 정책이 필요해 안 함 — 결정 항목**
- [x] 7 홈이 예약을 전혀 안 보여 주던 것 → 고객 홈에 '다가오는 방문' 카드(`GET /customer/home` 의 `upcoming`), 진행 중이면 '실시간 영상 보기'
- [x] 9 배정된 매니저가 이름뿐이던 것 → 내 예약 카드에 '매니저 소개'(경력·완료 청소·평점)
- [x] 8·21 매니저가 깜깜이로 수락하던 것 — 콜 카드에 `details`(예상 소요·주거 정보·청소 범위) 추가. 개발 상태 문구 "이동 시간은 준비 중이에요" 는 지우고(`V019`), 이동 시간은 거리 계산이 붙을 때까지 줄 자체를 그리지 않는다
- [x] 17 정산 탭 '업무별 정산' 아래가 그냥 비어 있던 것 → EmptyState
- [x] 18 매니저 MY 평점이 완료 0건인데 0.0 으로 보이던 것 → '-'
- [x] 19 일정 '이번 주' 탭이 실제로는 오늘+6일이라 다음 주가 섞이던 것 → '7일'
- [x] 10 "기존 주소를 사용할까요?" 에 그 주소가 안 보이던 것 → 기본 주소를 항상 한 줄로
- [x] 16 취소 시트에서 안내 문구가 나타나며 버튼이 밀려 잘못 눌리던 것 → 문구를 항상 같은 자리에, 버튼 라벨도 '이 회차만 취소하기' / '정기청소 전체 취소하기' 로
- [x] 11 주소 검색에 가짜 '검색 결과 예시' 그림이 뜨던 것 → 입력 안내와 "주소 검색이 연결되어 있지 않아요" 로 사실대로. 가입 주소 화면에도 직접 입력임을 한 줄. **주소 API(juso.go.kr) 연동은 키가 필요해 남음 (§13-4)**
- [x] 12 선물하기 CTA 가 아무 동작도 안 하던 것 → 선물 화면이 생길 때까지 버튼을 그리지 않는다(`GiftBanner` 는 `onCta` 가 있을 때만 버튼). 선물 기능 자체는 결제 연동 뒤 (남은 것)
- [x] 20 주말·당일이 왜 회색인지 안 알려주던 것 → 달력 아래 안내 문구(청소·수리 예약 둘 다)
- [x] 22 가입 때 받은 반려동물 여부를 예약에서 다시 묻던 것 → 주의사항 칩을 미리 켜 둔다(`GET /customer/profile/suggested-notices`, 끌 수 있다)
- [x] 23 카카오·구글·네이버 버튼이 말없이 전화번호 가입으로 가던 것 → "지금은 전화번호로만 가입할 수 있어요" 시트로 먼저 알리고 고르게
- [x] 24 본문 없는 HTTP 오류가 전부 "잠시 후 다시 시도해주세요" 이던 것 → 404·5xx·429·타임아웃을 구분해 말한다(두 앱)
- [x] 15 예약 완료 화면에 보이는 버튼이 없던 것(화면 전체가 탭 영역) → '내 예약 보기' + '홈으로'. 카드·버튼을 아래에서 띄워 짧은 화면에서도 안 잘린다
- [x] 13 휴대폰 번호 8자리가 잘려 보이던 것 (PhoneField: 앞자리 칸을 글자 크기로, 칸 간격 15→10, 입력칸 최소 130 + 좁으면 인증 버튼이 아랫줄로). 재전송 카운트다운은 70px 버튼에 맞게 시간만 표시

### 사용성 개선에서 코드로 하지 않은 것 (결정·외부 작업 필요)
- **결제(PG)**: 사용자 결정대로 구조만(§13-5). `payments`·`payment_methods` 테이블, `PaymentGateway` 인터페이스 + `NoopPaymentGateway`, 호출 지점 주석(`BookingService` 의 `// PAYMENT:`), `GET /customer/payment-methods` 는 `[]`. **붙일 때 새로 만들어야 하는 것**: 결제수단 등록 화면·빌링키 등록 API(`registerBillingKey` 호출 지점), 결제 시점 결정(예약 시 승인 vs 완료 후 청구), 취소·부분 환불 정책과 `refund` 연결, 영수증·결제 내역 화면, 정기청소 회차별 청구. 그래서 "나중에 붙이면 끝" 이 아니라 **인터페이스 구현체 1개 + 화면·정책 작업**이다.
- ~~정기청소 주기 선택~~ → **사용자 결정 2026-09-22: 정기청소는 매월 1회**. 첫 방문일부터 매월 같은 날짜에 1회씩 `series_term_months`회(기본 **6개월 6회**), 없는 날짜는 그 달 마지막 날, 주말이면 다음 평일. 회차 수는 설정 한 줄(`series_term_months`)로 바꾼다. 격주 등 다른 주기를 더하려면 주기별 가격이 필요하다(§13-3).
- ~~매니저가 스스로 '오늘 못 감' 처리~~ → **사용자 승인 2026-09-22: 점수제로 구현** (`backend/docs/manager-policy.md`, `V022`). 앱 › 업무 상세 › 방문 취소(사유 선택) → 예약은 `assigning` 으로 돌아가 다시 매칭 · 패널티 기록 · 고객 푸시. 4시간 이내는 고객센터 전화. MY › 패널티 내역 화면. 남은 것: 고객 사과 쿠폰(결제 연동 후), 증빙 면제용 운영자 화면(지금은 SQL·`waive()`).
- **주소 검색 API(juso.go.kr)**: 키가 필요(§13-4). 지금은 직접 입력이고 화면이 그렇게 말한다.
- **`app_settings.support_phone` 채우기**: 비어 있으면 전화 버튼이 안 보인다 (runbook).

## 판단이 필요한 항목
- ~~**탈퇴 5년 뒤 삭제 미구현**~~ → **결정(사용자 2026-09-27): 만든다.** 반영 — 아래 '자체 점검' 절 '탈퇴 + 5년 파기'
- ~~**자동 재매칭 스케줄러**~~ → **결정(사용자 2026-09-27): 만든다.** 반영 — 아래 '자체 점검' 절 '자동 재매칭'
- ~~**#16 후속 질문**~~ → **결정(사용자 2026-09-27): 시작 시각만 근무시간 안이면 콜을 보낸다**(매니저가 수락 여부를 정한다). 반영 — 시작 시각이 근무시간 `[시작, 종료]` 안(종료 시각인 마지막 슬롯 18:00 포함). 아래 '자체 점검' 절
- ~~**#31 후속 — 사용자 확인 필요**~~ → **결정(사용자 2026-09-27): 그대로.** 매니저 동의 V039 '보유 기간: 매니저 탈퇴 시까지 (1:1 문의 기록은 분쟁 처리를 위해 탈퇴 후 3년 보관)' 유지(원칙 + 예외)
- **자체 점검 2026-09-27 판단 필요 3건**: #16 늦은 시간대 매칭 → 결정: 슬롯을 막지 않는다 · #25 지각 고객 알림 → 결정: 푸시를 만든다 · #31 탈퇴 후 문의 보존 → 결정: 3년 (모두 사용자 2026-09-27) — 자세한 내용은 아래 '자체 점검' 절
- **Figma 와 다른 문구(코드에서 정함, 자체 점검)**: 매칭 실패 푸시 4가지·카드 2가지(V036 → V041·V044, 가능한 길만), FAQ 배정 시점(V035), 지각 안내 푸시(`PushCopy.MANAGER_LATE_*` — V037 은 보낸 시각 열만), 일정 변경 '다른 매니저에게 다시 배정돼요' 푸시, '콜 배정 정지 중이에요' 홈 카드, `PASSWORD_CHANGED` 안내, 진행 중 고객 탈퇴 차단 문구 — 디자이너 확인 필요
- **Figma 파일 오류 의심**: `Button/Secondary State=Disabled`(2171:2399), `Button/Destructive State=Disabled`(2171:2404) 의 배경 원본 fill 이 검정(#000000)이고 변수 바인딩은 각각 `color/surface/subtle`, `color/surface/default` 다. 스크린샷도 검정으로 렌더됨. 검정 비활성 버튼은 의도가 아니라고 판단해 **변수 바인딩을 따름**. 디자이너 확인 필요
- Button 너비: Figma 컴포넌트는 286/200 고정이지만 화면 인스턴스는 345(=393−24×2). 기본 `width: 100%` 로 두고 부모가 폭을 정한다
- Button Loading 라벨 "처리 중" 은 Figma 기본값. `loadingLabel` prop 으로 바꿀 수 있음
- Spinner 회전(1s linear infinite), 버튼 배경 전환(150ms), :focus-visible 아웃라인: Figma 에 없어 코드에서 정함
- hover: 만들지 않음 (모바일 우선)
- TextField 라벨↔입력창 간격 5px: Figma 원본값이지만 변수에 묶여 있지 않고 spacing 토큰(4/6)에도 없음 → 5px 그대로 씀. 토큰화 여부 디자이너 확인 필요
- TextField Focused: Figma 의 Focused 는 입력창 포커스이므로 `:focus-visible` 대신 `:focus-within` 으로 매핑 (텍스트 입력은 포커스가 항상 보여야 함)
- TextField Error 문구 기본값은 없음. 화면에서 `error="…"` 로 넘긴다 (Figma 예시 문구 "올바른 값을 입력해주세요")
- TextField 는 한 줄 input 만. textarea/멀티라인은 Figma 에 없어 만들지 않음
- CameraStatusItem Issue 아이콘: Figma 원본 stroke 가 토큰에 없는 색(#E0342C 계열). 옆 글자와 같은 `color/status/live` 로 통일. 디자이너 확인 필요
- ManagerTabBar: Figma 는 절대 위치 5칸(78.6px)이지만 균등 분할이므로 flex 로 옮김. 하단 18px 은 `max(18px, env(safe-area-inset-bottom))` 로 기기 safe-area 대응 (코드에서 정함)
- NavBar 뒤로가기: 아이콘 24px 을 44px 터치 영역으로 감싸고 음수 margin 으로 Figma 위치(좌 12px) 유지
- Sheet 스크림(뒤 배경): Figma 에 없음 → text/primary 40% (`color-mix`). 열림 애니메이션 150ms, reduced-motion 시 없음
- Sheet 하단 패딩 32 는 Figma 값이고 `env(safe-area-inset-bottom)` 을 더함
- Skeleton: Figma 에 없음 → surface/subtle 바탕 + surface/pressed 반짝임 1.4s
- ChecklistItem: Figma 프레임이 높이 44 고정(체크박스 24 + 패딩 12×2 = 48 보다 작음) → 높이 44 고정으로 옮김
- StatusBadge/TabBar 문구·순서는 Figma 그대로. 코드에서 바꾸지 말 것
- 고객용 TabBar 배경: Figma 는 흰색 10% 투명(의도 불명) → surface/default 로. 디자이너 확인 필요
- Chip 테두리 #c5c5c5, Live 오버레이 rgb(0 0 0/45%), Exit rgb(224 52 44/92%)·#e0342c, 컨트롤 원 #edf2fa: Figma 에 변수 없이 쓰인 원본색 → tokens/extra.css 의 `--raw-*` 구역. 디자이너가 변수로 승격하면 옮길 것
- Live/LabeledControl 라벨색 #17171f 는 text/primary(#16171e) 로 봄 (1 차이, 오타로 판단)
- Live/CircleControl 접근성 라벨(일시정지 / 15초 뒤로 / 15초 앞으로)은 Figma 에 없어 코드에서 정함. `ariaLabel` prop 으로 바꿀 수 있음
- RadioOption·NoticeChip 간격 5px 은 Figma 원본값(토큰 없음). NoticeChip 폭은 Figma 169 이지만 화면에서 그리드가 정하도록 100%
- GiftBanner 좌측 패딩 23 → 토큰 24 로 (1px). 헤드라인은 Figma 의 혼합 크기(20/18, 안심 Bold) 그대로
- Chip·NoticeChip 테두리는 Figma INSIDE stroke → `box-shadow: inset` 으로 그려 크기를 유지
- PillControl·GiftBanner CTA 는 시각 높이(38/27)가 44 보다 작아 `::before` 로 터치 영역만 44 확보
- StatusBar 는 높이(44 또는 safe-area)만 있는 빈 요소. 시계·배터리는 OS 가 그림
- Button `outline`: Figma 에 variant 는 없고 Button/Secondary 인스턴스 오버라이드(흰 배경 + 1.5px brand 테두리)로 Home 등에 쓰임 → variant 로 추가. Pressed 는 brand/subtle 로 정함
- Card(tone)·Dot·EmptyState: Figma 에 컴포넌트가 아니라 화면 프레임으로 그려진 반복 요소 → ui 로 공통화 (화면 CSS 에 색 금지 규칙)
- ScreenLayout 하단 영역(StickyCTA+TabBar)은 sticky 로 화면 아래 고정. Figma 프레임은 세로로 길게 펼쳐져 있지만 앱은 Content 만 스크롤
- Home/Empty 의 "근무 일정 관리" 버튼: Figma 프로토타입 연결 없음 → /schedule/availability 로 (문구로 판단)
- Home 의 진행 중 타이머 "01:23:42" 는 mock 고정값. 실제 경과 시간은 단계 5 라이브 세션에서
- 데이터 스키마(api/README.md)는 Figma 문구에서 뽑은 것. 서버 설계 시 확인 필요
- Job/Detail 의 "지도 보기"·"고객 연락": Figma 프로토타입 연결 없음 → onClick 없이 TODO(figma). 단계 5 이후 지도/전화 연동
- Button `outlineGray`: Job/Detail 의 Button/Secondary 인스턴스 오버라이드(흰 배경 + 1.5px border/default) → variant 추가. Pressed 는 surface/pressed
- RejectSheet: Figma 는 첫 사유가 Selected → 기본 선택. 거절하기 → rejectCall(id, reason) 후 /call
- ChecklistItem readOnly: Job/Detail 의 청소 범위는 표시용(체크 불가)으로 판단 → role=list/listitem
- StickyCTA 하단 28 은 Figma 값(홈 인디케이터 포함) → `max(28px, 14px + safe-area)`
- Preparing/Error 배너·아이콘: Figma 아이콘 stroke 는 토큰에 없는 #E0342C → 제목과 같은 status/live 로 통일 (CameraStatusItem 과 같은 결정)
- Preparing/Error 의 "청소 시작 (카메라 연결 후 가능)": Figma 는 Secondary 에 글자 text/secondary(#767676) 로 그려짐 → Button secondary disabled(text/disabled #a8a8a8) 로 표현. 1단계 차이, 디자이너 확인
- Preparing/Error 의 "고객센터 문의": 프로토타입 연결 없음 → TODO(figma)
- InProgress 의 "청소 완료" → Job/CompleteConfirm 은 바텀시트(393×318, DragHandle) → M4 에서 InProgress 안의 Sheet 로 구현 예정. 지금은 /job/complete Placeholder 로 이동
- InProgress Content 간격 22 는 Figma 원본값 (ScreenLayout gap="ml")
- 체크리스트 토글은 setChecklistItem(mock) 후 로컬 상태 갱신. 실서버 연결 시 함수 안만 교체
- CompleteConfirm 경고 카드: Figma 예시는 "2개 있습니다 / 주방 · 가스레인지 / 욕실 · 변기". 실제 체크리스트 상태에서 같은 형식으로 계산. 미완료 0개면 카드를 숨김 (Figma 에 variant 없음)
- SummaryRow: Figma 의 SummaryRow·StatRow·BreakdownRow·FinalRow 는 값 글꼴만 다른 같은 구조 → tone/emphasis/size prop 으로 통합
- Schedule 의 "이번 주"/"달력" 탭: Figma 에 화면이 없음 → 탭은 선택되지만 목록은 오늘 데이터 그대로. TODO(figma)
- Availability 의 "기본 근무시간 수정": 프로토타입 연결 없음 → TODO(figma)
- AddUnavailable 의 "하루 종일" 은 RadioOption 하나를 토글로 씀 → 선택 시 시작/종료 시간 필드를 disabled 로 (코드에서 정함). 날짜·시간은 TextField 문자열 입력(Figma 에 피커 없음)
- addUnavailable 저장 결과의 라벨: 사유가 있으면 사유, 없으면 하루 종일→"휴무" / 아니면 "개인 일정" (Figma 예시 문구 기준)
- Profile 메뉴 10개 중 프로토타입 연결이 있는 것은 근무 일정 관리·촬영 장비뿐 → 나머지 8개는 onClick 없음(TODO(figma)). Equipment 의 "카메라 테스트" 도 연결 없음
- (완료) 매니저 Placeholder 는 M6 에서 제거됨. 고객용은 C1 에서 다시 추가(apps/customer/src/screens/dev/Placeholder.jsx) → C7 끝에 제거
- **고객용 플로우 순서(프로토타입 없음 → 코드에서 정함)**: Onboarding 1→2→3 → SignUp → TermsConsent → ProfileInfo-Phone|Social → ProfileAddress → ProfileHousing → SignUpComplete → Home. SignUp 의 소셜/전화 → `/auth/terms?method=…`, 로그인하기 → `/auth/login`. 디자이너 확인 필요
- 온보딩 슬라이드: Figma 에 버튼·인디케이터 없음 → 가로 스와이프(scroll-snap) + 탭으로 다음, 마지막 탭 시 가입. 탭 이동은 즉시 스크롤(클릭 핸들러 안의 smooth 스크롤이 브라우저에 따라 취소됨). 문구가 이미지에 구워져 있어 다국어·접근성은 aria-label 로만 제공. 원본이 2x 크기라 3x 없음
- SignUp 의 소셜 버튼 4개는 Figma 가 글자까지 그린 flat image → ImageButton + aria-label. 실제 소셜 SDK 는 실서버 단계
- SignUp 배경 그라디언트·부제 회색(#868686)·링크 파랑(#0af)·온보딩 그라디언트: 토큰 없음 → extra.css --raw-*
- SignUp 의 OrDivider·HeroArt 는 Figma 에서 절대 위치 → 그대로 absolute
- 고객용 '/' 는 `Start.jsx` — 로그인 상태면 /home, 가입 중이면 남은 가입 화면, 그 외 온보딩(접속마다 무작위 1장, 사용자 결정 2026-09-21)
- Vite: 워크스페이스 링크 패키지(@ansimclean/ui) 의 react 의존을 뒤늦게 발견해 재최적화 → React 두 벌 로드("Invalid hook call"). 두 앱 vite.config.js 의 optimizeDeps.include 로 해결
- 갤러리 위치: CLAUDE.md 초안은 apps/*/src/dev/Gallery.jsx 였으나 두 앱이 같은 내용을 쓰므로 packages/ui/src/dev/Gallery.jsx 한 곳에 두고 두 앱이 /dev/gallery 로 마운트
- 모드 이름: Figma에 `Mode 1` 하나뿐 → 코드에서는 `data-theme="default"`로 매핑 (scripts/build-tokens.js 의 MODE_TO_THEME)
- 텍스트 스타일 line-height가 전부 AUTO → `--line-height-auto: 1.2` 로 고정 (scripts/build-tokens.js 의 LINE_HEIGHT_AUTO). 처음엔 1.45 였으나 3-c-1 에서 Figma 텍스트 박스 높이를 실측해 1.2 로 수정 (14→17, 16→19, 18→22, 20→24 = ×1.2, Noto Sans KR OTF 메트릭). 브라우저 normal(1.45) 을 쓰면 카드 높이가 Figma 보다 3~9% 커짐
- 폰트 fallback 스택(Apple SD Gothic Neo, Malgun Gothic, system-ui)은 Figma에 없어 코드에서 정함 (FONT_FALLBACK)
- 폰트 번들 크기: 유니코드 범위별 분할 파일이 woff2+woff 로 약 13MB 가 dist 에 포함됨. 웹에서는 필요한 조각만 내려받지만 Capacitor 앱 용량에는 전부 들어감 → 단계 6에서 woff 제거 또는 korean 단일 파일로 전환 검토
- (C2) 인증 화면 이동은 Figma 연결이 없어 코드에서 정함: TermsConsent 다음 → /auth/profile-info?method= (phone 이면 ProfileInfo-Phone 형, 그 외 소셜 형) → 다음 → /auth/profile-address. LoginPhone 로그인 성공 → /home. 뒤로가기(Button/Back)는 이전 화면. LoginPhone 프레임에는 Button/Back 이 없어 넣지 않음
- (C2) TermsConsent "다음" 은 (필수) 3개가 모두 체크돼야 활성 (Figma 는 Default 상태만). "보기>" 가 여는 약관 본문 화면이 Figma 에 없어 아직 아무 동작 없음 (TODO(figma))
- (C2) 다섯 번째 약관 문구 "(선택) 서비스 개선을 위한 \n          사용자 경험 수집 동의" 는 Figma 의 줄바꿈·들여쓰기 공백을 그대로 둠 (`white-space: pre-wrap`). 디자이너가 문구를 바꾸면 mock.js 만 고치면 됨
- (C2) StepDots 현재 점 색: Figma 는 프레임마다 원본색 #2486fd / #2485fb / 변수 brand/primary 가 섞여 있음 → brand/primary(#2284fc) 로 통일. StepCounter "01/" #000000, "04" #999999 와 안내문 #7f7f7f 는 변수 없음 → `--raw-step-current` `--raw-step-total` `--raw-note-text`
- (C2) LoginPhone 배경 그라디언트는 SignUp 과 정지점이 조금 다름(0/23/100% vs 4.6/25.3/75.2%) → `--raw-login-gradient` 별도. 디자이너가 통일하면 하나로
- (C2) PhoneField 앞자리 "010" 은 고정 텍스트 (Figma 에 선택 UI 없음). Icon/TogglePassword(눈 아이콘)는 번호 가리기 토글로 구현했고, Figma 는 placeholder 바로 뒤에 두었지만 코드는 입력창 오른쪽 끝에 둠 (input 은 폭을 채우므로). 용도 디자이너 확인 필요
- (C2) Button/Verify "인증하기": ProfileInfo-Phone 은 흰 배경+테두리, ProfileInfo-Social 은 brand 채움(원본색 #2384fc → brand/primary) → 번호를 입력하면 채움(`verifyActive`) 으로 매핑. 인증 코드 입력 화면은 Figma 에 없음
- (C2) 비밀번호 헬퍼 "사용가능한 비밀번호입니다!" (Caption/Medium, brand/primary) 는 Field/Text 컴포넌트에 없는 상태라 TextField `success` prop 으로 추가. 표시 규칙(8자 이상)은 코드에서 정함 → api/customer.js checkPassword
- (C2) ProfileInfo-Phone 과 -Social 은 비밀번호 필드 유무(+ 폼 간격 18/20)만 달라 한 화면(ProfileInfo.jsx)으로 만들고 ?method= 로 구분
- (C2) 인증 화면 하단 버튼(286×64, 하단 여백 106)은 Figma 대로 본문 흐름 안에 두었다(StickyCTA 아님). 내용이 화면보다 길면 함께 스크롤됨
- (C2) NoteCard·Checkbox·StepDots 점·Button/Verify 테두리는 Figma INSIDE stroke → `box-shadow: inset` 으로 크기 유지 (NoteCard 안쪽 폭 243 이 맞아야 안내문이 두 줄로 떨어짐)
- (C3) 이동은 코드에서 정함: ProfileAddress 다음 → /auth/profile-housing → 다음 → /auth/complete → 이미지("지금 시작하기") 탭 → /home. SignUpComplete 는 문구·버튼이 전부 이미지라 화면 전체가 ImageButton 하나
- (C3) 도로명주소는 우편번호 검색 화면이 인증 플로우 Figma 에 없어 일반 입력(SearchField). 예약 플로우의 AddressSearch(C5~)와 연결할지 디자이너 확인 필요
- (C3) ImageOption(거주형태·반려동물) Selected 모양은 Figma 에 없음 → brand/primary 1.5px 테두리 + brand/subtle 배경. "다음" 은 거주형태·평수(또는 기억이 안나요)·방·화장실·반려동물이 모두 있어야 활성
- (C3) ProfileHousing 4번째 진행 점이 Figma 에서 #a7c2db(연한 파랑)로 다른 화면(brand/primary)과 다름 → brand/primary 로 통일. "몇 평인가요?" 입력창은 Figma 높이 52·좌 패딩 11 이지만 TextField(50·10) 사용 → 아래 필드들이 2~4px 위로 올라옴. "기억이 안나요" 체크 시 평수 입력 비활성(코드에서 정함)
- (C3) Home 히어로 캐러셀은 슬라이드 데이터(api getHome)로 그린다. 지금은 Standard-A 1장이라 카운터 01/01 · 점 1개, C4 에서 Realtime 슬라이드를 앞에 추가하면 Figma 대로 02/02. 히어로 탭 = 다음 슬라이드(Figma 프레임 탭 → Home/Realtime). DecoBackdrop (flat image) 은 채우기가 없어 그리지 않음
- (C3) SlideCounter 의 20/14 Medium 텍스트는 Figma 텍스트 스타일이 없어 Body/L(20 Regular)·Body/S(14 Regular) 클래스 사용 (굵기 Medium → Regular). 검정/회색은 StepCounter 와 같은 --raw-step-* 재사용
- (C3) Home 배경 그라디언트(#c9ddc2→흰→#f5fbfd)와 "기억이 안나요" 회색 #c2c1c2 는 변수 없음 → `--raw-home-gradient` `--raw-checkbox-muted-label`
- (C3) GiftBanner "청소 선물하러 가기 >" 연결 화면 없음 → TODO(figma). 홈 카테고리 4종 → /booking/<home|move-in|appliance|office>/frequency (프로토타입 그대로)
- (C4) Home 캐러셀: Figma 두 프레임(Realtime ⇄ Standard-A)이 서로를 가리켜 탭·가로 스와이프(40px 이상)로 전환. 배경 그라디언트·히어로 이미지·옆 장식 위치가 슬라이드마다 달라 슬라이드 id 별 CSS 클래스로 둠. 처음 슬라이드는 Figma 카운터 01 인 실시간 청소
- (C4) Repair 카테고리 8개의 Figma 프로토타입이 청소 예약 Frequency 화면(1·5 Booking, 2·6 MoveIn, 3·7 Appliance, 4·8 Office)을 가리킴 → 그대로 따름. 수리 전용 예약 플로우는 Figma 에 없어 디자이너 확인 필요
- (C4) Repair 의 GiftBanner 인스턴스는 일러스트·문구가 오버라이드돼 있어 `variant="repair"` 추가. 헤드라인 16 Regular 는 텍스트 스타일이 없어 Label/M(16 Medium) 사용
- (C4) Reservations 탭 문구 "예정 2" 의 2 는 건수로 보고 데이터에서 계산. 완료·취소 탭의 목록·빈 상태, "예약 변경/취소" 연결 화면은 Figma 에 없음 → 비어 있음 + TODO(figma)
- (C4) Settings 메뉴 행(내 정보 수정 …)·프로필 카드의 연결 화면 없음 → TODO(figma). 로그아웃 → /signup. 알림 토글은 api updateNotificationSetting 으로 저장. Avatar 는 (교체필요: 프로필 사진) → 회색 원, `avatarSrc` 가 오면 사진
- (C4) Figma MenuRow/SegmentTabs 의 구분선은 아래쪽 1px stroke → `box-shadow: inset` 으로 그림 (마지막 행 없음)
- (C5) 예약 플로우 이동은 Figma 연결이 없어 코드에서 정함: Frequency(1) 다음·날짜 필드 탭 → DateTime(2) → Step2(3) → 기존 주소 "네" 면 Options(6)로 건너뛰고 "아니오" 면 AddressSearch(4) → AddressConfirm(5) → Options. 옵션 필드 탭 → Options
- (C5) Frequency 정기청소·Step2 "네" 는 Figma Selected 상태를 기본 선택으로 봄. DateTime 은 날짜·시간 둘 다 골라야 "다음" 활성. 토·일과 다른 달 날짜는 Figma 에서 회색(border/default)이라 선택 불가로 처리 — 디자이너 확인 필요
- (C5) 달력은 오늘의 달부터 시작(Figma 예시는 2025년 9월). 선택된 날짜·시간은 Frequency 의 날짜 필드에 "2026년 9월 16일 (수) 11:00" 형식으로 표시(Figma 에 채워진 예시 없음)
- (C5) AddressSearch 의 MapPreview (flat image) 는 주소 검색 결과 목록이 그려진 이미지 → 주소 API 연동 전까지 이미지 자리표시. Sheet 하단 272 는 키보드 자리라 52 로. "다음" 은 검색어가 있으면 활성, 검색어가 AddressConfirm 도로명주소에 미리 채워짐
- (C5) AddressSearch·AddressConfirm 의 Button/Primary 는 Figma 에서 시트 정렬(MIN) 때문에 좌측(x 24)에 있으나 다른 화면과 통일해 가운데 정렬 — 디자이너 확인 필요
- (C5) 진행 중 입력(bookingDraft)은 sessionStorage 에 카테고리별 보관. 서버 저장은 saveBookingStep 이 단계마다 호출(실서버 전에는 그대로 반환)
- (C5) 정기청소 배너 원본색(그라디언트 #e7f6f1→#dceff8, 칩 글자 #136751, 혜택 아이콘 #1f8a4c, 일러스트 자리 흰 55%) 은 변수 없음 → `--raw-promo-*`. 달력 선택 셀 radius 12 는 토큰(5/10/20/32)에 없어 원본값
- (C6) 이동은 코드에서 정함: Options(6) → NotesRequests(7) → Confirm(8) → 완료 → Complete → 화면 탭 → /home. Confirm 의 "수정하기" 는 해당 단계로(서비스 종류는 /home). Complete 에는 버튼·연결이 없어 화면 전체를 탭 영역으로
- (C6) OptionItem 선택 모양은 Figma 에 없음 → 아이콘 박스에 brand 1.5px 테두리 + brand/subtle 배경. "무료 옵션(택2)" 는 2개 고르면 나머지 비활성. 옵션 아이콘 20개는 `(교체필요)` 임시 에셋
- (C6) NotesRequests 의 Figma 선택 예시(3개 Selected)는 기본값으로 쓰지 않음(빈 선택으로 시작). OptionGrid (원본 이미지 · 참고용) 은 무시
- (C6) Confirm 의 칩 문구는 Figma 예시("침구류 교체", "냉장고 정리")와 옵션 화면 문구("침구교체", "냉장고 정리 및 청소")가 달라 옵션 화면 문구(줄바꿈은 공백)로 통일. 서비스 주기 부가 문구는 카테고리×주기 표(FREQUENCY_DESCRIPTIONS)로, Figma 에 없는 조합은 home 문구
- (C6) "네(기존 주소 사용)" 의 기존 주소는 Confirm 의 예시 값(부산광역시 중앙대로 2127-23 / 금정아파트 802동 203호)을 mock(SAVED_ADDRESS) 으로. 담당 클리너는 배정 전이라 Reservations 문구 "배정 후 알려드려요"
- (C6) 방문 일정 값 형식 "2026년 9월 16일" + "오전 11시" 는 Figma 예시("2026년 11월 1일 / 오전 9시")를 따름. Complete 카드는 "{예약일자}" 등 자리표시 대신 실제 값
- (C6) Confirm 의 완료 버튼은 Figma 대로 304 폭(다른 화면 286). BookingInfoCard 그림자 0 6 9 10% 와 Complete 그라디언트는 변수 없음 → `--raw-card-shadow` `--raw-complete-gradient`. ValueField radius 8 은 토큰에 없어 원본값
- (C6) 요청사항 TextArea 는 Figma InputArea(305×196) 그대로 고정 높이. SummaryItem 의 EditLink 는 Figma 절대 위치(우측, 위로 13) 유지
- (C7) 라이브 이동은 코드에서 정함: Service/Start "실시간 영상 보기" → /service/live, 전체 화면 ⇄ /service/live/landscape, 종료하기 → /reservations, 뒤로 = 이전 화면. **Service/Start 로 들어오는 화면이 Figma 에 없음** (예: 내 예약 카드에서 진입) — 디자이너 확인 필요
- (C7) LiveLandscape(852×480)는 393px 컨테이너 규칙의 예외: Figma 무대 852×480 을 화면에 맞춰 축소하고 세로 화면에서는 CSS 로 90° 회전. Capacitor 에서는 화면 방향 잠금으로 대체 가능
- (C7) VideoSurface 에 포스터 이미지를 넣지 않음(CLAUDE.md). Service/Start 의 "VideoPoster (placeholder · 코드에서 교체)" 는 실제로는 문구가 든 소개 일러스트라 이미지로 표시(교체필요). 라이브 화면의 영상은 단계 5 전까지 검정
- (C7) BrandLogo (로고 이미지 에셋 필요) → "안심클린" Title/Card 글자. Mascot (일러스트 에셋 필요) → #cce0fa 원 자리표시. 설정 버튼이 여는 화면 없음 → TODO(figma)
- (C7) 영상 위 흰색·반투명 검정·스크림·진행 막대 색은 토큰이 없어 `--raw-on-video*` `--raw-overlay-*` `--raw-scrim-*` `--raw-progress-track` 등 원본색. LIVE 배지·진행 막대 빨강은 `--raw-danger` 재사용, RealtimePill 초록은 정기청소 배너 아이콘과 같은 값
- (C7) useLiveSession 은 단계 5 자리표시: 세션 정보는 mock, status 는 'connecting' → 'idle'. 시청자 수·시작 시각·진행률(5:20/10:30)은 Figma 값. 음소거는 <video>.muted 만 토글
- (C7) 매니저 아바타 원본이 96px 이라 3x 는 확대본. 실제 프로필 사진은 서버에서

- **화면 폭 (2026-09-21, 사용자 결정 A)**: `--screen-max-width` 393 → 480px. 폰(360~430)은 가로를 꽉 채우고 태블릿·PC 에서만 가운데 정렬. 393 기준 `left: Npx` 였던 절대 위치 요소 9곳(홈·수리·로그인·가입 일러스트, 예약 완료 카드, 확인 히어로, 서비스 시작 CTA·포스터, 가입 완료 이미지)을 가운데(50%)·오른쪽 기준으로 바꿔 393 에서는 같은 자리, 넓어지면 비례해 따라간다. 360/393/430/480 스크린샷으로 확인. CLAUDE.md §2-9 갱신

### 전체 검수 1차에서 나온 결정 항목 (2026-09-20)
- **정기청소 회차 자동 매칭이 실제로 성립하지 않는 구조** — `SeriesDispatcher` 03:00 에 30분 TTL 오퍼가 나가고(§2.5·§6.2), 만료된 매니저는 재매칭에서 제외(§6.2-1)되므로 새벽에 아무도 수락하지 않으면 그 회차는 03:30 이후 후보가 없다. 코드는 사양대로. 선택지: ① 스케줄러 시각을 09:00 으로 ② 예약된 매칭(정기·재예약)에는 긴 TTL ③ 만료 매니저 재오퍼 허용. ~~**결정 필요**~~ → 2026-09-28 결정: ① `SeriesDispatcher` 를 09:00 으로, 밤(21시~08시)에 생긴 새 콜은 아침 08:05 에 (문제 목록 2번)
- ~~완료 화면 '총 소요' 는 벽시계(ended−started, §6.3 예시 "14:02 - 17:25, 3시간 23분"), 진행 화면 경과 시간은 중단 제외 — 두 계산이 다르다. 통일할지 결정~~ → 2026-09-28 결정: 총 소요는 벽시계로 둔다(같은 화면의 '청소 시간' 범위·Figma 예시와 맞다). 진행 중 타이머는 순작업 시간(중단 제외) 그대로 (문제 목록 13번)
- ~~작업 상세 '예상 소요' 는 `app_settings.duration_hours` 현재값(§6.3), 일정 카드·매칭은 예약 스냅샷 `duration_minutes` — 통일할지 결정~~ → 자체 점검 #26(`522ade1`)으로 예약 스냅샷 값으로 통일(해결, §6.3 사양도 2026-09-27 고침)
- 오퍼 수락 오류 코드는 사양 §6.2-3 순서 그대로(만료된 오퍼도 `OFFER_TAKEN`) — 앱은 `isCallClosed` 로 두 코드를 같은 시트로 처리하므로 영향 없음
- 속도 제한 값은 코드에서 정함: 인증번호 IP 당 20/시간·전체 500/시간, 로그인 IP 당 60/시간, 탈퇴 비밀번호 확인 5/시간(`RateLimiter`). 프록시 뒤 IP 는 `CLIENT_IP_HEADER`(Fly: `Fly-Client-IP`, 일반: `X-Forwarded-For` 마지막 값)로 켠 경우에만 헤더를 믿는다(`ClientIp`, 2차 검수). 비우면 remoteAddr
- 취소된 예약의 매니저 작업 상세는 Figma 에 배지가 없어 `INVALID_STATE "취소된 예약이에요"` → 앱 `ScreenError`. 디자인이 오면 화면으로
- 정기 회차 일정 변경 문구 "정기청소 기간(M월 D일까지) 안에서만 바꿀 수 있어요" 는 코드에서 정함(§6.4 에 기간 밖 변경 규정 없음)

## 에셋 TODO (Figma 에 원본 없음 — 단계 4 화면에서 플레이스홀더 + TODO 로 처리)
스펙은 `(교체필요)` 15개라 했으나 실제 파일에는 **21개** (옵션 아이콘 20 + 아바타 1).
- `(에셋 필요)` 3개: `BrandLogo (로고 이미지 에셋 필요)` 2167:2250 (Service/LivePortrait) · `Mascot (일러스트 에셋 필요)` 2167:2300 (Service/LivePortrait) · `Illustration (일러스트 에셋 필요 · 캘린더+청소도구)` 2169:2267 (Booking/Frequency)
- `(교체필요)` 옵션 아이콘 80×65~89, 카테고리별 5개:
  - Booking/Options: 설거지, 세탁, 침구교체, 반려동물 배변 청소, 냉장고 정리 및 청소
  - Office/Options: 탕비실 정리, 회의실 정돈, 분리배출, 카펫 스팀 청소, 유리창 외부 청소
  - Appliance/Options: 필터 세척, 외관 광택, 배수구 청소, 에어컨 분해 청소, 세탁조 살균
  - MoveIn/Options: 창틀 청소, 베란다, 붙박이장, 줄눈 시공, 새집증후군 케어
- `Avatar (교체필요: 프로필 사진)` 2048:1724 (Settings) 56×56
- `VideoPoster (placeholder · 코드에서 교체)` 92:768 (Service/Start) → 코드에서 실제 영상/썸네일로
- `OptionGrid (원본 이미지 · 참고용)` ×4 → 무시
- (C5) `(에셋 필요)` Illustration (2169:2267) — Booking/Frequency 정기청소 배너의 캘린더+청소도구 일러스트 132×132 → 흰 55% 자리표시 (PromoBanner)
- (C5) `(교체필요)` PromoBanner 자리표시 (2044:1665 등) — Office/Appliance/MoveIn Frequency 의 프로모션 배너 이미지 3종 → Figma 자리표시 문구 그대로 표시
- (C5) MapPreview (flat image) (92:398) — 주소 검색 결과 목록 이미지 → 주소 API 연동 시 실제 목록으로 교체
- (C6) `(교체필요)` 옵션 아이콘 20개 (Booking/Office/Appliance/MoveIn Options › Icon) — Figma 의 임시 3D 이미지를 그대로 WebP 로 넣음 → packages/ui/src/assets/option-*.webp, ASSETS.bookingOptionIcons
- (C7) `(에셋 필요)` BrandLogo (2167:2250) → 글자 자리표시 (ServiceLivePortrait) · Mascot (2167:2300) → 원 자리표시 (ReassuranceBanner) · `(교체필요)` VideoPoster (92:768) → 소개 이미지 그대로 (ASSETS.serviceStart)

## 단계 6 — 앱 패키징 (Capacitor) (완료 2026-09-19)
- [x] 폰트 번들 축소: `packages/ui/src/fonts.css` 를 @fontsource 기본 CSS(@import, 유니코드 120여 조각 × woff2+woff ≈ 13MB) 대신 korean+latin × 400/500/700 woff2 6개(≈ 1.7MB) 로. dist 18MB → 5.5MB(고객) / 5.1MB(매니저). iOS 12+/Android 5+ 모두 woff2 지원
- [x] `@capacitor/core` `@capacitor/ios` `@capacitor/android` + `@capacitor/cli`(dev) 를 두 앱에 추가 (PLAN.md 패키지 표). `apps/*/capacitor.config.json` (appId kr.ansimclean.customer / kr.ansimclean.manager, webDir dist, androidScheme https)
- [x] `npx cap add ios` · `android` → `apps/*/ios`, `apps/*/android` 생성 (Capacitor 8, iOS 는 SPM). 네이티브 코드는 손대지 않음. 권한 문구만: 매니저 iOS `NSCameraUsageDescription`·`NSMicrophoneUsageDescription`, Android `CAMERA`·`RECORD_AUDIO`·`MODIFY_AUDIO_SETTINGS`; 고객 iOS `NSPhotoLibraryAddUsageDescription`(화면 캡처 저장). 가로 모드는 두 플랫폼 기본값이 세로+가로 허용
- [x] 스크립트: 앱마다 `cap:sync` `cap:ios` `cap:android`, 루트 `npm run cap:sync`. check-tokens·eslint 는 `apps/*/ios|android` 를 건너뜀
- [x] 빌드 검증: iOS 시뮬레이터용 `xcodebuild` 두 앱 BUILD SUCCEEDED, Android `./gradlew assembleDebug` 두 앱 성공 (APK 8.7MB). iPhone 17 Pro 시뮬레이터에서 고객(온보딩)·매니저(홈) 실행 확인 — 폰트·safe-area(다이내믹 아일랜드 아래 시작, 홈 인디케이터 위 탭바) 정상
- [ ] (에셋) 앱 아이콘·스플래시: Figma 에 로고 에셋이 없어 Capacitor 기본값. `BrandLogo (에셋 필요)` 가 오면 `@capacitor/assets` 로 생성
- [ ] (단계 5 이후) 실기기에서 카메라·마이크 권한 팝업과 WebRTC 검증, 매니저 촬영 중 화면 꺼짐 방지(`@capacitor/keep-awake`)

## 점검 (전체 코드 리뷰) — 2026-09-19, 백엔드 시작 전
19개 항목을 찾아 순서대로 처리했다. 커밋 0c758a1 → 7be0a5f → b268eb4 → 1809dc7 → (이 커밋).
- [x] 1 서버가 잘못된 JSON 에 죽음 → 핸들러 try/catch, 400/413, 본문 64KB 제한 (`server/index.js`)
- [x] 2 `new Date('YYYY-MM-DD')` 시간대 하루 어긋남 → `fromDateKey`(로컬 파싱) (`packages/ui/src/components/Calendar/dateKey.js`)
- [x] 3 과거 날짜 예약 가능 → 달력 기본 규칙에 오늘 이전 비활성
- [x] 4·5·9·10 화면에 오류·로딩 처리 없음 → `useAsync` 훅 + `ScreenError`/`ScreenLoading` 을 30개 화면 전부에 적용, 라우터 `errorElement`·404(`RouteError`), 설정 토글 되돌리기, 예약 완료 화면은 `?id=` 로 재조회. DEV `?scenario=error` 로 확인
- [x] 6 비밀번호 검사 응답 순서 → 마지막 요청만 반영 (`ProfileInfo`)
- [x] 7 라이브 join 이 화면마다 재호출 → 같은 세션이면 건너뜀 (`liveClient.join`)
- [x] 8 CORS `*` → `.env` `ALLOWED_ORIGINS` 허용 목록 (비우면 개발용 전체 허용 + 경고)
- [x] 11 Android `allowBackup="false"` (두 앱)
- [x] 12 프로덕션 CSP 메타 (`scripts/vite-csp-plugin.js`, 빌드에만 삽입. frame-ancestors 는 서버 헤더로)
- [x] 13 고객용 로그인 가드 `RequireAuth` (홈·예약·라이브·설정). 로그인 상태는 실서버 전 sessionStorage 표시. 매니저 앱은 Figma 에 로그인 화면이 없어 가드 없음 → 아래 "매니저 로그인·회원가입" 에서 추가
- [x] 14 바텀시트 Tab 포커스 가두기
- [x] 15 진행 막대(←/→/Home/End)·밑줄 탭(←/→)·홈 캐러셀(←/→/Enter) 키보드
- [x] 16 화면 흐름 테스트 5개 + useAsync·ErrorState·서버 테스트 (총 128)
- [x] 18 `TODO(figma)` 버튼 전부 채움 — 아래 "Figma 에 없어 코드에서 정한 화면" 참고
- [x] 19 라우트 코드 분할 (`lazy: page(() => import(...))`, 화면당 청크)
- [ ] 17 뒤로가기 아이콘 3종 통일 — Figma 가 3가지로 그려서 그대로 둠. 디자이너가 하나로 정하면 합친다

### Figma 에 없어 코드에서 정한 화면·동작 (점검 4, 디자인이 오면 교체)
고객: 약관 본문 `/terms/:id` · 예약 변경(`/booking/:category/datetime?edit=`)·취소(확인 시트)·완료/취소 탭·빈 상태 · 진행 중 예약 카드의 "실시간 영상 보기"(→ `/service/start?booking=`) · 설정 아래 7개(내 정보 수정, 주소 관리, 결제 수단(안내만), FAQ, 1:1 문의, 공지사항 목록/본문) · 주소 검색 결과 목록(`AddressResultList`, Figma 이미지의 4건을 mock) · 라이브 설정 시트(소리)
매니저: 지도 보기(카카오맵 검색)·고객 연락(tel:, mock 번호) · 고객센터 문의 폼 `/profile/support` · 촬영 장비 카메라 테스트(후면 카메라 미리보기, `liveClient.testCamera`) · 일정 이번 주/달력 · 기본 근무시간 수정 시트 · 프로필 5개(매니저 정보·활동 지역·신원 인증·교육 이수·정산 계좌 읽기 전용, 알림 설정, 공지사항)
새 ui: `ErrorState` `ScreenError` `ScreenLoading` `AddressResultList` `FaqItem`, `ReservationCard` 상태 3종 추가(inProgress/done/cancelled), `useAsync`

## 매니저 로그인·회원가입 (코드에서 정함) — 2026-09-19 (완료)
Figma 매니저 페이지(22화면)에 로그인·가입 화면이 없어 사용자 요청으로 코드에서 정했다. 고객용 Auth/* 의 입력 구성과 프리미티브(PhoneField·TextField·StepDots·Checkbox·TermRow·NoteCard)를 매니저 앱 스타일(NavBar + StickyCTA, 일러스트 없음)로 조립했다. 디자인이 오면 화면 파일만 교체하면 된다.
- [x] `apps/manager/src/screens/auth/` — `Login`(휴대폰+비밀번호, 실패 문구, 회원가입 링크) · `SignUpTerms`(1/3 약관 5개: 고객용 3개 + 실시간 촬영·영상 녹화 동의(필수) + 마케팅(선택), 전체 동의, 보기>) · `TermDetail`(약관 본문) · `SignUpInfo`(2/3 이름·휴대폰 인증하기·비밀번호 8자·비밀번호 확인 불일치 오류) · `SignUpComplete`(3/3 완료 안내 → 시작하기)
- [x] 흐름: `/auth/login` ⇄ `/auth/signup` → `/auth/signup/info` → `/auth/signup/complete` → `/`. 로그인 성공은 RequireAuth 가 넘긴 원래 주소로 복귀
- [x] `screens/RequireAuth.jsx` — 22화면 + 프로필 아래 화면 전부 가드. 로그인 상태는 실서버 전 sessionStorage(`ansimclean.manager.session`)
- [x] Profile 메뉴 아래 "로그아웃" 텍스트 버튼(Figma 에 없음) → `/auth/login`
- [x] `api/manager.js` 함수 10개 + `mock.js` AUTH_COPY · MANAGER_TERMS · MANAGER_TERM_BODIES (api/README.md 표)
- [x] 테스트 3 파일 8개 (총 136): 로그인 활성 조건·성공 이동·from 복귀·회원가입 링크, 약관 필수 4개·전체 동의·보기, 정보 입력 검증 → 완료 → 로그인 상태, RequireAuth
- 판단(디자이너·기획 확인): ① 매니저 가입 즉시 로그인 상태가 되며, 신원 확인·교육은 프로필의 기존 항목에서 본다(승인 대기 화면 없음) ② 소셜 로그인 없음(고객용에만 Figma 가 있음) ③ 약관 문구·본문은 임시 ④ 비밀번호 규칙 8자 이상(고객용과 동일)

## 2차 점검 (외부 검토 반영) — 2026-09-19 (완료)
외부 검토가 지적한 사용자 흐름 단절 6건과 API 계약 불일치 3건을 코드로 확인하고 고쳤다. 테스트 150개.
- [x] 1 예약을 완료해도 내 예약에 없음 → `createBooking` 이 목록(메모리)에도 넣고, 시작 시 sessionStorage 의 예약을 목록에 합친다. 빈 값(일정·주소·무료 옵션 3개)은 API 에서도 거른다 (`customer.test.js`)
- [x] 2 시트 안 입력 중 포커스 이탈 → `Sheet` 가 `onClose` 를 ref 로 들고 효과는 `open` 에만 반응 (`Sheet.test.jsx`)
- [x] 3 수락한 작업을 다시 열 경로 없음 → 홈 "나머지 일정"·일정 화면의 `ScheduleCard` 탭 → `/job/:id` (`Schedule.test.jsx`)
- [x] 4 "아니오(새 주소)" 뒤 옵션 필드로 주소 입력 우회 → 주소가 없으면 어느 버튼이든 주소 검색으로; 확인 화면은 새 주소 선택 시 기존 주소를 쓰지 않고 "주소를 입력해주세요" + 완료 비활성 (`BookingStep2.test.jsx`, `BookingConfirm.test.jsx`)
- [x] 5 완료 후 뒤로가기로 빈 예약 생성 → 일정·주소 없으면 완료 비활성, 요청 실패 문구, 진행 중 더블 탭 차단(`useAction`), 성공 후 `replace` 이동
- [x] 6 수리 종류가 예약에 남지 않음 → Repair 에서 `repairType` 을 초안에 넣고 확인 화면 "수리 종류" 행 + 예약에 포함 (`Repair.test.jsx`). → 이후 수리 전용 흐름(`/repair/booking`)으로 대체, 청소 초안의 repairType 경로는 2026-09-28 문제 목록 38번에서 지웠다. **이동 대상 청소 카테고리는 Figma 임시 연결 그대로** — 수리 전용 플로우는 디자인 필요(판단 항목)
- [x] 계약 불일치: `LoginPhone` 요청 실패 시 로딩 해제 + 실패 문구, `JobPaused` 가 고정 `job-1` 대신 라이브 세션의 작업 id, `getJobContact(jobId)`
- [x] 촬영 중단 순서: `live.pause()`(영상 차단, Promise) → 성공 후에만 `pauseJob` → 화면 이동. 실패하면 시트에 "영상이 계속 전송되고 있으니 다시 시도" (`JobPaused.test.jsx`). 재개·완료·취소도 `useAction` 으로 실패 문구
- 새 ui: `useAction(fn)` — 버튼 동작의 진행 중·실패·더블 탭 방지. 규칙은 CLAUDE.md §10 에 추가
- 판단 항목: 수리 서비스는 청소 예약 플로우를 빌려 쓰고 있음(Figma 임시 연결). 수리 전용 예약 모델·화면은 디자인 확정 후 (BACKEND_PLAN §13)

## 백엔드 (BACKEND_PLAN.md) — 승인 2026-09-19, 다음 회차 B0
사양은 `BACKEND_PLAN.md` 하나다(§15 검토 이력 4차까지 반영). 회차마다 §14 형식으로 보고하고 멈춘다. 백엔드 코드는 다른 컴퓨터에서, 문서·앱 어댑터는 이 저장소에서.
- [x] B0 (이 컴퓨터 몫, 2026-09-19) — `CLAUDE.md` §1 서버 행을 Java/Spring Boot/Supabase/LiveKit 으로, §0 검증 명령에 `./mvnw verify`, **§11 백엔드 규칙 14개**(계획 §3 + 멱등·라이브 순서). `PLAN.md` §0 서버 행·단계 7 을 계획 링크로. `backend/.env.example`(변수 26개, 값 없음)·`backend/README.md`(백엔드 컴퓨터 체크리스트). 결정 §13: 1·2·3·5·18·21·22·23 완료, 19(바디캠 사양)·24(수리 모델) 대기, 나머지는 기본값
- [ ] B0 (백엔드 컴퓨터 몫) — `backend/README.md` 체크리스트: JDK 21 · Docker · Supabase `ansimclean-dev`(Phone/Email provider 끔, 가입 끔, 버킷 2개, S3 키) · **Supabase 휴대폰+비밀번호 로그인 curl 검증**(안 되면 계획 대안 B) · LiveKit(Ingress·Egress) · Solapi(발신번호 등록) · `OTP_SALT` · GitHub 비공개 저장소. 끝나면 결과를 이 줄에 적고 B1 로
- [x] B1 뼈대 (2026-09-19, 이 컴퓨터에서 작성 — Android Studio JDK 21 로 컴파일·단위·슬라이스 테스트까지 확인, **통합 테스트(`CatalogIT`)와 dev Supabase 마이그레이션은 Docker·자격증명이 없어 미실행 → 백엔드 컴퓨터에서 `./mvnw verify` 로 확인할 것**)
  - `backend/` Spring Boot **4.1.1**(start.spring.io 기본 GA. 계획의 4.0.x 보다 새 버전, 스타터 이름은 생성된 그대로) · Java 21 · Maven Wrapper. pom: livekit-server 0.16.0, archunit 1.5.0, failsafe(*IT), jacoco
  - `common/`: ErrorCode 26개·ApiException·ErrorResponse·GlobalExceptionHandler / SecurityConfig(경로별 권한, 401·403 JSON)·SupabaseJwtDecoderConfig(jwks|secret)·JwtToCurrentUserConverter·CurrentUserResolver(B1 은 프로필 없음) / KoreanFormat 22 메서드 / AppClock / CorsConfig / RequestLogFilter
  - Flyway `V001__schema.sql`(계획 §4 그대로, RLS 루프에서 flyway_schema_history 제외) + `V002~V005` seed — `scripts/backend-mock-export.mjs seed` 가 앱 mock.js 에서 생성(문구 글자 그대로). 테스트 기대값 `mock-shapes/*.json` 도 같은 스크립트
  - API: `GET /catalog/categories/{c}` `/options` `/notice-chips` `/confirm-copy` `/time-slots` `/reasons` `/address`, `GET /terms`, `GET /terms/{id}`, `/actuator/health`
  - 테스트 15 통과(KoreanFormatTest 6 · SecurityConfigTest 5 · ArchitectureTest 4) + `CatalogIT` 23 케이스(작성·컴파일만)
  - Node `server/` 삭제(workspaces·vitest·문서 정리, 프론트 테스트 147). docs: `backend/docs/{api,domain,runbook}.md` (계획 §5·§6·§12 를 옮김 — 이후엔 이 파일이 기준)
  - 판단: Jackson 3 기본값(ISO 날짜·null 포함·camelCase)이 계약과 같아 JacksonConfig 없음 / String 컬럼은 `columnDefinition="text"` 로 validate 통과시킴(백엔드 컴퓨터 첫 verify 에서 validate 불일치가 나오면 어노테이션만 고친다) / 체크리스트 템플릿은 home 만 seed / duration_hours 는 home 3(Figma) 외 임시
- [x] **B2 전 결정 (§13-25)**: 2026-09-19 dev 프로젝트에 직접 호출해 확인 — 휴대폰+비밀번호 로그인은 Phone provider 가 꺼져 있으면 `422 phone_provider_disabled` 로 거부된다(이메일은 정상). → **A 로 결정**(Phone provider 켬 + Twilio 더미 값 + phone confirmations 끔 + 가입 끔). 서버에서 재확인: phone grant `invalid_credentials`, `/signup`·`/otp` `signup_disabled`
- [x] B2-a 기반 (2026-09-19, 인증 방식 선택과 무관한 부분): `common/sms/{SmsSender, SmsProperties, LoggingSmsSender, SolapiSmsSender}` · `auth/{PhoneVerification, PhoneVerificationRepository, OtpService}`(6자리 난수·SHA-256+salt 저장·5분 만료·5회 오답 폐기·60초/시간당 5회 제한·15분 불투명 토큰·1회 소비) · `common/outbox/{OutboxEvent, OutboxService, OutboxDispatcher, OutboxHandler}`(업무와 같은 트랜잭션에 기록 → 5초마다 발송 → 2^n분 백오프 → 6회 실패 시 failed) · `common/web/{IdempotencyKey, IdempotencyKeyRepository, Idempotent}`(§6.9 알고리즘). 테스트 12개 추가(백엔드 27)
- [x] lint 수정: ESLint 가 `backend/target`(JaCoCo 가 만든 JS 리포트)을 검사해 145개 오류가 나고 있었다 → ignores 에 추가, `.mjs` 도 검사 대상에 포함. **B1 보고의 "lint 통과" 는 `tail -1` 로 출력을 잘라 실패를 놓친 것이었다**
- [x] B2-b 백엔드 인증 (2026-09-19): `auth/{AuthService, AuthController, RateLimiter, SupabaseUserLookup, AccountDeletionGuard}` · `common/supabase/SupabaseAuthClient` · `profile/{Profile, Address(+AddressRegion), CustomerHousing, NotificationSetting, ManagerProfile, ProfileService, CurrentUserCache, CustomerProfileController}` · `schedule/{ManagerHours, AvailabilityService}` · `support/TermAgreement`. 엔드포인트 11개(계획 §5.1 전부 + 가입 주소·주거). 가입 중 계정은 `PENDING_*` 권한. 테스트 50 통과 + `AuthFlowIT` 2 흐름(Docker 필요, 컴파일만)
- [x] B2-c 앱 연결 (2026-09-19): 두 앱에 `api/http.js`(Bearer·401 refresh·ApiError·Idempotency-Key)·`api/session.js`·`.env.example(VITE_API_URL)`. 인증 함수 전부 서버 호출로(README 표). 화면: `ProfileInfo`·`SignUpInfo` 인증번호 6자리 필드(재전송·이미 가입한 번호 → 로그인 안내), 로그인 후 `signupStep` 으로 재개, `RequireAuth` 가 가입 중 계정을 남은 화면으로, `App.jsx` 시작 시 `refreshMe`, SignUp 소셜 버튼 → 휴대폰 흐름(§13-1), **회원 탈퇴**(`DeleteAccountSheet` 새 ui, 고객 설정·매니저 프로필). 테스트: `src/test/fakeHttp.js` 로 http 대체, 156 통과
- [ ] B2 남은 것: **실제 서버로 앱 흐름 확인** — `backend/.env`(DB 비밀번호·service_role 키·`OTP_SALT`)가 있어야 서버를 띄울 수 있다. 그 뒤 브라우저에서 가입→로그인→탈퇴 1회, 실제 휴대폰 문자는 Solapi 설정 후 OTP(Spring)·가입·재개(signupStep)·로그인·탈퇴, 아웃박스, 멱등, http/session 어댑터, 인증번호 입력 화면
- [x] B3-a 고객 프로필·주소·설정 API (2026-09-19): `profile/CustomerProfileController` 에 `GET|PATCH /customer/profile`(이름·닉네임, 휴대폰은 §13-7) · `GET|POST /customer/addresses` · `GET /customer/addresses/default`(404 → 앱은 null) · `DELETE /customer/addresses/{id}`(기본 주소 삭제 시 가장 오래된 주소가 기본) · `PUT /customer/notifications/{key}`. 새 패키지 `customer/`(`GET /customer/home·repair·settings` — app_settings + ProfileService 읽기 조합), `payment/`(구조만: `PaymentGateway`·`NoopPaymentGateway`·`PaymentMethod`·`Payment`·`GET /customer/payment-methods` = `[]`). 판단: Settings 프로필 카드 `avatarSrc` 는 B6(Storage 서명 URL) 전까지 `""` / 탈퇴 차단 문구는 코드에서 정함(`BookingDeletionGuard`)
- [x] B3-b 예약·가격 (2026-09-19): `booking/{Booking(§6.1 전이 메서드 전부), BookingStatus(+Converter), BookingSeries, BookingStatusHistory, BookingService(견적·생성·조회·변경·취소), BookingSeriesService(§6.8 전체 취소·회차 매칭·종료·종료 안내), BookingDispatcher(인터페이스 — B4 OfferService 가 @Primary 구현, 지금은 NoopBookingDispatcher 로그), SeriesDispatcher(매일 03:00), StaleBookingCleaner(03:10), BookingDeletionGuard, BookingMapper, BookingController}` · `catalog/{CatalogPrice, PricingService}` + `CatalogService.requireOptions/requireNoticeKeys/isTimeSlot/cancelDeadlineHours` · `common/events/{BookingCancelled, BookingRescheduled, BookingUnassigned, SeriesEnding}` · `OutboxHandler.types()`(한 핸들러가 여러 타입) · `push/PushHandler`(B6 전에는 로그만 — failed 로 쌓이지 않게) · `V006__settings_b3.sql`(`payment_note`). 판단: 스케줄러는 `common/scheduling` 이 아니라 기능 패키지 안(ArchitectureTest 가 common→기능 의존을 금지) / 정기 회차의 일정 변경은 `requested` 면 상태·회차 그대로(매칭 전) / `wholeSeries` 취소는 요청한 회차가 기한 안이어도 시리즈는 취소하고 그 회차는 남긴다(앱 시트 문구) / 취소 이유 값 `customer`·`series_cancelled`·`unassigned`·`not_started`. 테스트 92 통과(+42: BookingTest 8 · QuoteTest 5 · PricingServiceTest 2 · BookingServiceTest 8 · BookingSeriesServiceTest 6 · BookingMapperTest 4 · BookingControllerTest 5 · CustomerProfileControllerTest 4) + `BookingFlowIT` 3 흐름·`IdempotencyIT` 3 (Docker 필요 — **IT 미실행**)
- [x] B3-c 앱 연결 (2026-09-19): `customer.js` 고객 데이터 함수 전부 서버 호출로(README 표. 로컬 유지: 주기·주소 재사용 선택지·완료 카드 라벨·주거 옵션·라이브(B5)·FAQ/공지/문의(B6)). 화면(코드에서 정함): `BookingConfirm` "요금" 행(서버 견적 — 1회 금액, 정기면 회차 날짜 칩·총액, 결제 안내; 견적 없으면 완료 불가; 처음 "완료" 때 `submitKey` 생성, 초안이 바뀌면 `useBookingDraft` 가 지움) · `BookingComplete` 5번째 행 "결제 금액"(ui `InfoWonIcon` 새로 그림 — Figma 에 없음) · `Reservations` 취소 시트에 정기청소 "이번 회차만 / 정기청소 전체" 라디오 + 24시간 안내 + 서버 거부 문구 · `BookingDateTime`(예약 변경)·`Addresses`·`EditProfile` 버튼을 `useAction` 으로. `fakeHttp.js` 에 카탈로그·고객 경로 추가(멱등 키 재요청·본문 불일치 422·정기 회차 흉내). **DEV `VITE_API_MOCK=1`**(`npm run dev:customer:mock`, launch `customer-mock` 5175): 서버 없이 가짜 서버로 화면 확인 — 이 컴퓨터엔 백엔드 자격증명이 없어 브라우저 확인은 이걸로 했다. 테스트 161 통과. 판단: 요금 행 아이콘은 옵션 아이콘 재사용(에셋 TODO) / 주소 검색은 서버가 빈 결과라 화면이 검색어를 주소로 씀(§13-4 전)
- [ ] B3 남은 것: **실제 서버로 확인** — `backend/.env` 뒤 고객 앱 예약 9화면 → 완료 → 내 예약 → 변경·취소, `BookingFlowIT`·`IdempotencyIT`(Docker)
- [x] B4-a 작업·정산 + 기반 (2026-09-19): `job/{JobChecklistItem, JobPause, JobService, JobController}`(start·pause·resume·complete·체크리스트·상세·연락처·진행 중·중단·완료 화면) · `settlement/{Settlement, SettlementService, SettlementController}` · `live/{LiveSession, ManagerDevice, LiveSessionService, DeviceController}`(B4 는 세션 upsert·리셋·장비 상태 읽기만) · `catalog/ChecklistTemplate` + `CatalogService.{isReason, optionLabels, noticeLabels, checklistTemplate, shortLabel}` · `common/events/{OffersDispatched, BookingAssigned, BookingCompleted}` · `OutboxHandler.types()` · `V007__b4_manager.sql`(`offers.preferred`, 운영 문구·`max_dispatch_rounds`·`category_short_labels`) · `GlobalExceptionHandler` 에 `HandlerMethodValidationException`(List<@Valid>) → 400. 판단: 스케줄러·장비 엔드포인트는 계획의 `common/scheduling`·`job` 이 아니라 `offer/`·`live/` 안(의존 방향) / `job → live` 는 읽기만(선행 조건 검사), 쓰기는 이벤트 / 촬영 기록 `none` → "기록 없음"(issue) / "기타" 수수료 0 은 "0원"
- [x] B4-b 근무시간·근무불가·프로필 (2026-09-19): `schedule/{ManagerUnavailable, AvailabilityService(availability·updateHours·addUnavailable·isAvailable·status), AvailabilityController}` · `profile/ManagerProfileController`(프로필·상세 5종·알림) + `ProfileService.{approvedManagers, lockManager, approveManager, incrementCompleted, managerProfile, managerProfileDetail, managerNotifications}`. 판단: 활동 시작 없음 → "승인 후 표시", 계좌 없음 → "미등록", 신원·보험 미완 → "미완료"/"미가입"
- [x] B4-c 콜 매칭·수락·만료 + 홈·일정 (2026-09-19): `offer/{Offer, OfferRepository(findForUpdate·id 프로젝션), OfferService(dispatch·dispatchPreferred·onCancelled·onRescheduled·accept·reject·expireDue·calls), OfferExpirer, OfferController}` · `managerhome/{ManagerHomeService, ManagerHomeController}`(홈·일정). `BookingService` 의 `BookingDispatcher` 는 `@Lazy`(OfferService 가 BookingService 를 쓰는 생성자 순환). 테스트 145 통과(+53: OfferTest 3 · OfferServiceTest 13 · JobServiceTest 11 · SettlementServiceTest 3 · ManagerHomeServiceTest 3 · AvailabilityServiceTest 5 · LiveSessionServiceTest 3 · KoreanFormatManagerTest 2 · 슬라이스 9) + `MatchingIT` 5 흐름 · `JobFlowIT` 1 흐름 · `OutboxIT` 1 (Docker 필요 — **IT 미실행**)
- [x] B4-d 앱 연결 (2026-09-19): `manager.js` 매니저 데이터 함수 전부 서버 호출로(README 표. 로컬 유지: 반복 옵션·인증 문구·공지/문의(B6)). 새 함수 `acceptCall(id)`·`startJob(id)`·`isCallClosed(error)`, 쓰기(수락·시작·완료) 멱등 키는 어댑터가 대상별로 만들어 성공까지 재사용(§9.4). §9.0 변환: 근무시간 행 `{day,hours,off}` ↔ `{weekday,start,end,off}`, 반복 문구 ↔ `none|weekly|monthly`, 날짜 `2026.09.18` → ISO, 연락처 404 → null, `note` null → 생략. 화면(코드에서 정함): `CallList`·`CallDetail` 수락 = `acceptCall` → `/job/{jobId}`, `OFFER_TAKEN|EXPIRED` → 시트 "이미 마감된 요청이에요"(`CALL_CLOSED_COPY`) → 확인 시 목록 새로고침 · 거절 `useAction` · `JobPreparing` "청소 시작" = `startJob` → `live.start` 순서, 서버 거부 문구 표시 · `Availability` 저장 2곳 `useAction`(실패 시 시트 유지) · `JobPreparingError` 는 서버의 실제 장비 상태(`error` 인자 삭제). `SummaryRow` tone `issue`(ui, 촬영 기록 실패). `fakeHttp.js` 매니저 경로 전부, **DEV `VITE_API_MOCK=1`**(`npm run dev:manager:mock`, launch `manager-mock` 5176). 테스트 170 통과(+9: CallList 3 · JobPreparing 2 · manager.test 4). 브라우저(가짜 서버): 콜 목록 → 수락 → 업무 상세 → 청소 준비 → 청소 시작 → 진행 → 완료 → 정산 확인. **고객 앱 예약 → 매니저 앱 콜 도착은 실서버가 있어야 확인 가능(backend/.env 없음)**
- [ ] B4 남은 것: **실제 서버로 확인** — 고객 예약 → 매니저 콜 → 수락 → 시작 → 완료 → 고객 완료 탭, `MatchingIT`·`JobFlowIT`·`OutboxIT`(Docker)
- [x] B5-a 백엔드 라이브 (2026-09-19): `live/{LiveKitGateway(+Cloud·Logging 구현), LiveService(start·status·pause·resume·stop·viewerToken·recording·customerLive), LiveWebhookService(6종·중복 방어), LiveReconciler(1분), RecordingRetention(04:00), DeviceService + AdminDeviceController(POST /admin/devices, X-Admin-Token), LiveController, LiveWebhookController, LivekitEvent}` · `common/supabase/SupabaseStorageClient`(서명 URL·삭제) · `common/events/{CameraOnline, CameraOffline, RecordingReady}` · `booking/RecordingLookup`(완료 카드 `recordingReady`, live 가 구현) · `V008__b5_live.sql`(`unmute_pending`·`camera_wait_notified_at`, 라이브 문구) · `AppProperties.{livekit, storage, admin}`, `.env.example` 에 `LIVEKIT_MODE`·`ADMIN_TOKEN`. 판단: LiveKit 제어 호출은 동기(확인 필요) + 재조정기, 아웃박스는 푸시만 / `logging` 모드가 기본이라 키 없이도 서버가 뜬다 / 매니저 소개 문구 "경력 N년 · 완료 청소 M건" / 5분 대기 알림 1회 표시 열 추가. 테스트 174 통과(+29: LiveServiceTest 12 · LiveWebhookServiceTest 6 · LiveReconcilerTest 6 · SupabaseStorageClientTest 2 · LiveControllerTest 3) + `LiveSessionIT` 2 흐름(Docker — **IT 미실행**). **LiveKit Cloud 키·Storage S3 키 없음 → 실제 영상 미확인**
- [x] B5-b 앱 라이브 (2026-09-19): `livekit-client` 두 앱(`live/roomConnector.js` 에서만 import, 시청 전용, wss 가 아니면 연결 안 함). 고객 `liveClient.js`: 세션 → 시청 토큰 → 방 → `<video>`, 30초 폴링, 녹화본(`?replay=1`·끝난 예약) 재생·탐색·캡처(PNG 다운로드; 사진첩은 B7). 매니저 `liveClient.js`: `start/pause/resume/stop` = 서버 세션, 3초 폴링, `cameraBusy` 면 start 재호출, 프리뷰. `customer.js` `getLiveSession(id)`·`getLiveViewerToken`·`getRecording`, `manager.js` 라이브 5함수. 화면(코드에서 정함): `ServiceLivePortrait` 소리 설정 시트 삭제·음소거/설정 버튼 비활성(§13-23)·LIVE 배지는 live 일 때만 · `ServiceLiveLandscape` 탐색·재생은 녹화본에서만 · `ReservationCard`/`Reservations` 완료 탭 "녹화 영상 보기"(`recordingReady`) · `JobInProgress` 상단 카드에 라이브 상태 문구(`LIVE_STATE_COPY`)·배지·바디캠 프리뷰 · `Equipment` 카메라 테스트 → 바디캠 설정 정보(RTMP 주소·마스킹 키·마지막 연결, `EQUIPMENT_SETUP_COPY`). ui `LiveLabeledControl`·`IconButton` 에 `disabled`. CSP `media-src https:`(녹화본). 매니저 앱 카메라·마이크 권한 제거(Info.plist·AndroidManifest). 판단: Job/Preparing 은 프리뷰를 미리 열지 않는다(장비 4행이 바디캠 송출 여부를 이미 보여줌) / `live.start` 실패는 status=error 로 남기고 진행 화면으로 간다(작업은 이미 시작) / `?booking` 없이 열린 라이브 화면은 mock + 대기. 테스트 176 통과(+6: 고객 liveClient 4 · 매니저 liveClient 3 · Reservations 1, 기존 갱신). **실제 영상 미확인**(LiveKit·Storage 키 없음, 바디캠 미확정) — `logging` 모드/가짜 서버로 흐름만
- [ ] B5 남은 것: LiveKit Cloud 키 + Storage S3 키 + 바디캠(또는 ffmpeg)으로 §8.3 검증 5항목, `LiveSessionIT`(Docker), 캡처 사진첩 저장(B7 Capacitor), `npm run cap:sync` 뒤 네이티브 빌드
- [x] **전체 검수 1차 — 백엔드 5개 영역 (2026-09-20)**: 검수 에이전트 5개(인증·예약·콜/작업·프로필·라이브)가 59건 보고 → 영역당 검증자 1개가 반박 검증 → 실제 60건(추가 발견 포함)·기각 6건. 확인된 항목은 아래 커밋 8개로 수정, 백엔드 테스트 200 통과(+26)·앱 176 통과·lint·토큰·빌드 통과. **IT 는 여전히 미실행(Docker)** — 특히 스케줄러 트랜잭션 결함이 IT 미실행 때문에 초록으로 지나갔었다
  - `cae6ce9` 스케줄러 4개(StaleBookingCleaner·OutboxDispatcher·LiveReconciler·RecordingRetention)가 같은 클래스의 `@Transactional` 메서드를 직접 불러 **트랜잭션 없이** 돌았다 → 취소·sent·세션 상태·recordingDeleted 가 저장되지 않음(아웃박스는 같은 이벤트를 5초마다 재발송). `TransactionTemplate` 으로 감쌈 + 단위 테스트 3개 신설
  - `d15294f` 재개 확인 실패(unmute_pending) 뒤 다시 일시정지하면 '영상이 나가는데 paused' → `LiveSession.mayBeStreaming()`, live/pause 가 실제로 mute 확인, jobs/pause 선행 조건도 같은 기준
  - `7baa894` 탈퇴 매니저가 approved 후보·우선 오퍼 대상으로 남음(`ManagerProfile.withdraw`) · 취소된 예약의 작업 상세가 '예정·현장 도착' 으로 보임(배지에 취소 없음 → `INVALID_STATE "취소된 예약이에요"`) · start/pause/resume/complete 를 `FOR UPDATE`
  - `56bee48` 인증: jwks 디코더가 RS256 만 받아 **Supabase 기본 ES256 토큰이 전부 401** · OTP 오답 횟수 롤백 · 성공한 코드 재검증 · 문자 발송을 트랜잭션 밖으로(번호별 advisory lock) · 가입 토큰을 profiles 와 같은 트랜잭션에서 소비(재시도 멱등) · 탈퇴 잔재 Supabase 사용자를 옛 id 로 재사용하던 문제(잔재 삭제 후 새 id, 삭제는 `AccountDeleted` 아웃박스 재시도) · IP·전체·탈퇴 속도 제한 · Supabase 429/422 매핑 · `AuthCleaner`(04:10, §2.5) · `OTP_SALT` 비면 기동 거부
  - `f7a8169` 405/415/DB 제약 위반 → `METHOD_NOT_ALLOWED`/`UNSUPPORTED_MEDIA_TYPE`/`CONFLICT`(api.md) · 검증 문구는 한국어만 · INTERNAL/UPSTREAM 서버 로그 · **프로필 없음·탈퇴 토큰은 §7.2 대로 401**(앱은 401 에서만 로그아웃) · 관리자 API 토큰 검사가 본문 검증보다 먼저 · logging 대역 기동 경고
  - `3757284` 라이브: 카메라 재접속은 mute 확인 뒤에만 paused · 트랙 없는 ingress_started 가 live 를 만들어 jobs/pause 409 루프 · 재조정기가 mute_pending+이미 muted 를 해소·시청자 수 보정·끝난 세션의 남은 녹화 마무리 · 진행 중 작업의 live/stop 거절(**매니저 앱은 jobs/complete → live/stop 순서로 변경**, stop 실패는 재조정기) · 고객 화면 state 는 청소 시작 전 waiting · egress 이벤트 순서·Storage 연결 실패·LiveKit 오류 본문 · 탈퇴 매니저의 녹화 화면 404 · 승인 취소 매니저의 수락 차단
  - `1e83ae2` 예약·프로필: **'서울/서울시/경기/강원도' 주소가 sido=null 이 돼 후보 0명**(정식 이름 정규화, 매니저 활동 지역도 같은 기준) · 고객 취소·변경 `FOR UPDATE` · 정기 회차 변경(기간 밖 거부, 매칭 시점 안이면 즉시 매칭, 같은 일정이면 무변경, 이력 기록) · 유료 옵션 key 중복 합산 · 회차 전부 취소한 시리즈 닫기 · 시스템 취소의 pending 오퍼 정리 · 지난 회차 매칭 방지 · 설정 키가 응답에 나가던 INTERNAL 문구 · 가입 주소 공백 · CurrentUser 캐시 커밋 뒤 evict
  - **검수가 실제 문제로 봤지만 아직 안 고친 것(low)**: `RequestLogFilter` 가 보안 체인 뒤라 401·403 은 요청 로그에 안 남음 / 첫 주소 추가·기본 주소 삭제의 동시 요청(`addresses_one_default` 위반 → 이제 409 CONFLICT) / LiveKit 호출·mute 확인 폴링(최대 3초)이 DB 트랜잭션 안(설계상 동기 확인이 필요, 커넥션 점유는 감수) / support·profile·customer·payment 서비스 단위 테스트 층 부족(§11-10)
  - **검수가 기각(사양대로)했지만 결정이 필요한 것** — 아래 "판단이 필요한 항목" 에 추가
  - 미검수: 앱(고객·매니저 화면·api·live·ui 컴포넌트·빌드 설정)·백엔드 테스트 공백·횡단(계약 정합·동시성·비밀값·상태 기계) 14개 영역 — 세션 한도로 검수 에이전트가 실행되지 못했다. 다음 배치에서 3~4개씩
- [x] **외부 검토 반영 (2026-09-20, `ce3a000` 기준 8건 → 전부 실제로 확인·수정)**: `6f604b5` 백엔드 — stop 때 LiveKit 녹화 정지·방 삭제가 실패해도 세션이 ended 로 남아 다시 정지할 경로가 없던 것(재조정기가 ended+recording 이면 정지 재전송) · 정기 전체 취소·회차 매칭이 잠금 없이 읽어 수락/취소와 경합하면 옛 상태로 덮어쓰던 것(id 조회 → 회차마다 FOR UPDATE → 잠금 뒤 상태 재확인). `a576ec8` 앱 — 매니저 진행/일시정지 화면 재진입 시 라이브 세션이 비어 `pause(null)` 로 가던 것(서버 작업 id 로 재부착) · refresh 가 네트워크·5xx 로 실패하면 강제 로그아웃(401 만 세션 삭제) · 고객 라이브: 업로드 중 폴링 중단·녹화본 전환 시 실시간 트랙 잔존·화면 이탈 시 연결 잔존·leave 뒤 늦은 응답 재연결 · 가입 약관 초안이 sessionStorage 라 새 탭/재실행에서 완료가 빈 배열로 거부되던 것(localStorage + 없으면 약관 화면으로 돌려보냄). 백엔드 202·앱 185 테스트. **IT 미실행(Docker)**. 판단: 고객 라이브 화면은 사라지면 0.5초 뒤 나간다(세로↔가로 전환은 그 사이 다시 join) / 완료 화면 실패 문구·`?next=complete` 되돌아오기는 코드에서 정함 / 1:1 문의 미저장·푸시 미발송은 B6 범위 그대로
- [x] **전체 검수 2차 — 앱·횡단·테스트 14개 영역 (2026-09-20~21, 배치 A~D)**: 영역 3~4개씩 검수 → 영역당 검증자 1개 → 확인된 것만 수정. A 인증·세션·라이브 클라이언트·API 계약·상태 기계(`f2eae07` `1f7ff38` `08b88b7`) · B 고객·매니저 화면 4묶음 + 외부 검토 2차 10건(`bc9c1db` `18b49f5`) · C UI 프리미티브·빌드 설정·DB/설정(`36aed93` `927bb5b` `26867db`) · D 비밀값·동시성·테스트 공백·최근 수정분 회귀(`6b8894a` `66203e7` `b6a7eab` `9c12ecc`). 큰 것: **`RestClient.Builder` 빈 누락으로 서버 기동 불가**(restclient 스타터) · 매니저 라이브 시작 실패 무재시도 · pause/resume 502 가 pending 표시까지 롤백 · mute 요청이 진행 중 작업에도 적용 · **mute 미확인이 paused+mute_pending 이 돼 jobs/pause 가 통과(영상이 나가는 채 '일시정지')** · live_sessions 잠금 없음(마지막 커밋이 앞 갱신을 덮음, egress 이중 시작) → pause/resume/stop·웹훅·재조정기·jobs/pause 선행 조건이 잠그고 읽고, 재조정기는 세션마다 별도 트랜잭션 + SKIP LOCKED · 진행 중 작업의 muted 세션 unmute(계획 §9.2) · 웹훅 없이 재접속한 새 트랙 sid · 예약 잠금 순서 통일(시리즈 → 예약 id 순: 회차 취소·전체 취소·만료 배치·회차 매칭) · 탈퇴 가드+익명화 한 트랜잭션(프로필 잠금) · OTP 오답 카운트 advisory lock · 바디캠 등록 매니저 advisory lock · 아웃박스 커밋 즉시 발송(`OutboxQueued` → `TaskScheduler`) + 배치 하나만 · '광주시' 는 경기도 광주(다음 토큰 판별) · 옵션 key 중복이 한도 검사·저장에 남던 것 · created 세션이 `recordingPending` 영구 true · 가입 미완료 매니저 탈퇴 404 · 프록시 IP 헤더 무조건 신뢰 · 앱: 매니저 가입 초안이 로그인/로그아웃/탈퇴 뒤 남음, 약관 id 가 바뀌면 약관↔완료 무한 왕복. **테스트**: 죽은 IT 3개 수정(`live/stop` 은 complete 뒤·홈 API 200·테스트마다 `truncate profiles cascade`·`deleteRoom` atLeastOnce·5분 푸시는 시각 이동), `@EnableScheduling` 을 `SchedulingConfig(!test)` 로(IT 중 스케줄러 경합 제거), `application-test.yml` 이 `livekit.mode=logging` 고정, 새 단위 테스트 11개 파일(JWT 디코더 실검증·AdminAuth·ClientIp·BookingDeletionGuard·이벤트↔핸들러 짝·DeviceService·OfferService.call·refresh/logout·removeAddress/updateNotification·홈 DONE 제외·재조정기 3건), JaCoCo 최소 0.60→0.70(실측 서비스 73%). 백엔드 **234** 테스트·앱 **195** 테스트·lint·토큰·빌드 통과. **IT 미실행(Docker) — 고친 IT 6개(AuthFlowIT·LiveSessionIT·MatchingIT·JobFlowIT 등)는 백엔드 컴퓨터에서 `./mvnw verify` 로 반드시 확인**
  - **low 항목 후속 (2026-09-21, 커밋 아래)**: 500/405/415·DB 제약 위반 응답 모양 슬라이스(`GlobalExceptionHandlerTest`, 스택트레이스·SQL 미노출 확인) · 웹훅 서명 검증·이벤트 변환 테스트(`LiveKitCloudGatewayTest`, 실제 `WebhookReceiver` + 서명된 본문) · ArchUnit 3규칙 추가(역방향 의존·컨트롤러의 DB 접근·다른 기능 Service 직접 사용 — 현재 위반 0) · `IdempotencyIT` 사용자 분리·매니저 수락 키 재사용·키 길이(128) · 슬라이스 4묶음(`/customer/home·repair·settings`, `/customer/payment-methods`, `/manager/device`, `/terms`) · 커버리지 게이트에 상태 전이 엔티티 8개 포함 · 401·403 요청 로그(`SecurityConfig` 가 같은 모양으로) · 멱등 키 128자 제한. **여전히 남은 것**: LiveKit 호출·mute 확인 폴링이 DB 트랜잭션 안(설계상 동기 확인 필요; 세션별 트랜잭션이라 영향은 한 세션) / 재조정기의 '진행 중 ∧ muted → unmute' 는 `live/pause` 뒤 `jobs/pause` 전의 아주 짧은 창에서 unmute 할 수 있다(그러면 `jobs/pause` 가 409 로 막고 매니저가 다시 누른다 — 안전한 방향)
  - **결정 항목(2차)**: 아웃박스 즉시 발송은 test 프로필(스케줄러 없음)에서는 꺼진다 — IT 는 `dispatch()` 를 직접 부른다 / `CLIENT_IP_HEADER` 는 배포 시 프록시에 맞춰 설정(Fly 면 `Fly-Client-IP`), 비우면 IP 한도가 remoteAddr 기준 / '광주시 …' 로 시작하는 주소는 다음 토큰이 '…구' 면 광주광역시, 읍·면·동이면 경기도 광주시, 둘 다 아니면 시/도 없음(후보 0명 로그) / 재조정기가 지금 다른 요청이 쥔 세션은 건너뛰고 다음 분에 본다(SKIP LOCKED)
- [x] B6 지원·푸시 (2026-09-21): `fc592af` 백엔드 — `support/{Notice, Faq, Inquiry, SupportService, SupportController}`(FAQ·공지 seed 읽기, 공지는 대상+all 최신순·다른 대상 404, 문의 저장 + `Idempotency-Key`) · `push/{PushToken, PushService, PushSender(Fcm·Logging), PushHandler, PushCopy, PushController}`(PUT/DELETE `/push/token`, 이벤트 10종 → 받는 사람·문구·알림 설정 키, 무효 토큰 삭제, 일시 실패는 아웃박스 재시도, 탈퇴 시 토큰 삭제) · `firebase-admin` 9.7.0 · `app.push.mode`(PUSH_MODE, 기본값 없음). `d1060ee` 앱 — 지원 함수 실서버 연결(문의는 내용마다 멱등 키), `push/pushClient.js`(@capacitor/push-notifications: 권한 → 기기 토큰 → 로그인돼 있으면 등록, 로그인 시 재등록, 알림 탭 → `pushTarget`), `logout` 은 `DELETE /push/token` 먼저. 백엔드 259·앱 203 테스트, lint·토큰·빌드 통과. **IT 미실행(Docker)** — `SupportIT` 포함. **실기기 푸시 4종 수신(완료 기준)은 아래 항목이 갖춰져야 확인 가능**
  - **실기기(에뮬레이터) 푸시 확인 완료 (2026-09-21)**: Firebase 프로젝트 `ansimclean-de807` + 서비스 계정(`backend/.env`, `PUSH_MODE=fcm`) + `google-services.json` 2개(git 제외). 서버를 이 컴퓨터에서 Supabase 프로젝트(`ckvanmhgepdlxvfhdxes`, 도쿄)에 붙여 기동 → Flyway 11개 적용 → cloudflared 임시 HTTPS 터널 → Android 에뮬레이터(google_apis, `reltest`) 매니저 앱 로그인 → `PUT /push/token` → 고객 예약(강남구) → **"새 콜이 도착했어요" 배너 수신 → 탭 → 콜 목록 이동**. 앱이 화면에 떠 있으면 Android 는 배너 없이 `pushNotificationReceived` 로만 준다(정상). 이 과정에서 고친 실제 결함: ① `V011` 이 Flyway 이력표에 `ALTER TABLE` 을 걸어 자기 연결을 기다리다 2분 타임아웃 → REVOKE 만(어느 DB 에도 적용된 적 없어 체크섬 충돌 없음 — §11-4 예외로 기록) ② firebase-admin 기본 전송의 gzip 이중 해제로 오류 코드가 null(무효 토큰 6회 재시도) → JDK 전송 ③ `FcmPushSender` 생성자 둘에 `@Autowired` 없어 fcm 모드 기동 실패 ④ `application-local.yml` 이 push 를 logging 으로 고정해 `.env` 무시. 계정: 매니저 010-9999-0001 / 고객 010-9999-0002 (dev DB — 비밀번호는 저장소에 적지 않는다, 재감사 L-02)
  - **남은 것(사용자·인프라)**: ~~Firebase 프로젝트~~(완료) + 서비스 계정 JSON → `FIREBASE_SERVICE_ACCOUNT_JSON`(base64) · `PUSH_MODE=fcm` / Android: `android/app/google-services.json` + `com.google.gms:google-services` gradle 플러그인(네이티브 빌드 파일 — CLAUDE.md §8 단계 6 규칙상 승인 후 수정) / iOS: 플러그인이 주는 토큰은 **APNs 토큰**이라 FCM 으로 못 보낸다 — Firebase iOS SDK(AppDelegate 수정, `GoogleService-Info.plist`) 또는 서버 APNs 직접 발송 중 결정 필요(결정 항목). 그때까지 iOS 토큰은 FCM 이 INVALID_ARGUMENT 로 거부해 서버가 지운다(무해). Xcode Push Notifications capability(`aps-environment`) 도 필요
  - **판단**: 푸시 문구는 Figma 에 없어 `push/PushCopy` 에서 정함(계획 §6·§8 표현 그대로 — "매니저가 배정됐어요", "청소가 시작됐어요 · 실시간 영상 보기", "바디캠 연결이 끊겼어요", "정기청소가 곧 끝나요. 다음 달도 예약해 보세요" + 나머지 6종) / 카메라 재접속(`CameraOnline.first=false`)은 알리지 않는다 / 매니저 새 콜은 설정 `push`+`call`, 정산은 `push`+`settlement`, 나머지는 `push` 만 / 콜 푸시는 오퍼 상세가 아니라 콜 목록으로 이동(만료·수락 여부는 목록이 최신) / 문의 답변 화면은 Figma 에 없어 `inquiries.answer` 는 운영자 SQL / 같은 토큰을 다른 사용자가 등록하면 앞 사람 것을 지운다(기기 공유) / 로그아웃 때 토큰 삭제가 실패해도 로그아웃은 진행
- [ ] B7 배포
- [ ] B8 점검


## 운영 준비·보안 감사 반영 (2026-09-24)

외부 감사 보고서 `audit/PRODUCTION_READINESS_SECURITY_AUDIT.md`(기준 `488c9eb`, 결제 제외)를 항목별로 코드와 대조해 판단하고 반영한다. IT 는 Docker 가 없어 **미실행**(추가한 IT 는 컴파일만).

- [x] H-01 가입·비밀번호 재설정 인증 토큰을 외부(Supabase) 변경 **전에** 조건부 UPDATE 로 소비 (`consumeIfUnused`, 영향 행 1일 때만). 재설정 화면은 `OTP_EXPIRED` 면 인증부터 다시. IT `OtpConsumptionIT` 추가(미실행)
- [x] M-01 네이티브 세션을 Keychain/Keystore 에만 저장(localStorage 평문 사본 제거), 쓰기 직렬화, 삭제 실패 뒤 재실행 시 옛 세션을 거부(pending 표식). 실기기 확인은 남음
- [x] M-07 콜 배정 정지 중인 매니저는 정지 전에 받은 오퍼도 수락할 수 없다 (`OfferService.doAccept` 가 잠근 매니저로 재검사)
- [x] M-05 쓰기 남용 제한: 예약 생성 고객당 시간당 10건, 문의 사용자당 시간당 10건, 인증번호 검증 IP당 시간당 60회(값은 코드에서 정함). `RateLimiter` 를 `common.web` 으로 옮기고 키 수 상한(10만, 넘으면 창 밖 키 정리 → 그래도 넘으면 새 키 거부). 여러 대로 늘리기 전에는 공유 저장소/edge 제한 필요
- [x] M-06 예약 생성·주소·주거·이름·알림 쓰기가 프로필 행을 공유 잠금(`holdLive`, FOR SHARE)으로 잡아 탈퇴(FOR UPDATE)와 직렬화. 탈퇴가 먼저면 401. 실제 DB 경합 IT 는 미작성·미실행
- [x] M-02 수리 사진은 서명 바이트로 형식을 판정(JPG·PNG·WEBP·HEIC)하고 JPG·PNG·WEBP 는 헤더의 가로×세로로 1억 화소 상한(`ImageBytes`). 저장 형식·확장자도 내용 기준. 서버는 디코딩하지 않는다. 문구 "사진 파일을 읽을 수 없어요…"·"사진 해상도가 너무 커요…"(코드에서 정함)
- [x] M-10 `ProductionGuard`(local·test 외 프로필): 로그 대역(SMS·푸시·LiveKit)·비 https URL·빈 키·개발 origin 이면 기동 실패. 운영 주입값 자체는 배포 때 확인
- [x] M-03 로그아웃·비밀번호 재설정으로 끊은 세션 id 를 `revoked_sessions`(V023, 90일)에 적어 인증 필터·`/auth/refresh` 가 거부 — upstream 폐기가 실패해도 access(만료 전)·refresh 토큰 모두 401. 재설정은 `auth.sessions` 를 읽어 새 세션 외 전부 끊는다. dev 실측: 로그아웃 뒤 같은 토큰 `/auth/me` 401·refresh 401, 재설정 뒤 옛 두 기기 401·새 세션 200(Supabase 가 비밀번호 변경 때 세션 행을 지워서, 변경 **전에** 목록을 읽도록 고쳤다). `postgres` 계정 `auth.sessions` select 권한 있음. **남은 설정**: Supabase 세션 비활성 만료 ≤ 90일
- [x] M-04 수리 사진 업로드 원장 `repair_photo_uploads`(V024): 올릴 때 적고 예약에 붙으면 `attached_at`. 24시간 동안 안 붙은 사진은 `RepairPhotoCleaner`(매일 03:40)가 삭제. 탈퇴는 원장 + Storage 고객 폴더 목록(`SupabaseStorageClient.list`) 전부 삭제. dev 실측: 실제 PNG 업로드 → 원장 행 생성, `image/jpeg` 로 보낸 HTML → 400, Storage 목록 API 응답 형식 확인
- [x] H-02 일시정지 중 바디캠 재접속 차단: 카메라가 붙어 있지 않은 동안 LiveKit Ingress 를 `enabled=false` 로 잠가(V025 `manager_devices.ingress_enabled`) 새 연결 자체를 거부시킨다 — 일시정지 때 카메라가 없으면 즉시, 송출 중이면 끊기는 순간(`ingress_ended` 웹훅·재조정기 1분). 재개·작업 종료·다음 작업 시작·재조정기(진행 중인데 잠김)에서 연다. 재접속이 잠금보다 먼저면 기존 사후 mute 가 막고, mute 를 확인하기 전에는 녹화를 새로 시작하지 않는다. SDK 0.16 에 enabled 인자가 없어 Twirp `UpdateIngress` 를 protobuf 로 직접 부른다.
  - **남은 위험(실기기 검증 필요)**: ① 카메라가 끊긴 뒤 잠금 API 가 닿기 전(웹훅 지연 수백 ms~수 초)에 바디캠이 다시 붙으면 사후 mute 까지 영상이 나갈 수 있다 ② LiveKit Cloud 가 `enabled=false` 인 Ingress 의 RTMP 재연결을 실제로 거부하는지 격리 방에서 확인(공식 proto 주석: "new connection attempts will be rejected"). 배포 전 격리 방 시험: 일시정지 → 바디캠 전원 끔 → 켬 → 재연결 거부·녹화에 프레임 없음 확인
- [x] M-08 아웃박스: id 만 짧게 고른 뒤 **이벤트마다 별도 트랜잭션**(SKIP LOCKED 로 잠금 → 핸들러 → sent). 실패하면 그 트랜잭션은 되돌리고 실패 기록만 새 트랜잭션에 — 한 건 실패가 앞서 보낸 건의 sent 를 지워 중복 발송하던 문제도 없앴다. 라이브 pause/resume 의 확인 폴링(최대 6×0.5초)은 웹훅·재조정기와 줄 세우려는 의도적 잠금이라 그대로 둔다
- [x] M-09 (일부) 홈 '다가오는 방문'은 열린 예약만 조회(전체 이력 → OPEN 상태), 매니저 소개는 `findAllById` 한 번. **보류**: 예약·문의·패널티 목록 페이지네이션(API 계약·앱 화면을 같이 바꿔야 함 — 지금 규모에선 체감 없음), 매칭 후보의 매니저별 근무시간·휴무 조회를 SQL 한 번으로(매니저 수가 늘면). 판단 필요 항목으로 남김
- [x] L-01 CI 의 checkout·setup-node·setup-java 를 커밋 SHA 로 고정(v4.4.0·v4.4.0·v4.9.1, `git ls-remote` 로 확인) + `permissions: contents: read`
- 검증(2026-09-24): 앱 130 files / 273 tests, 백엔드 62 suites / 332 tests + JaCoCo 게이트(단위·슬라이스·ArchUnit, **IT 미실행**), lint·토큰·두 앱 빌드 통과. dev DB 에 V023~V025 적용, `ddl-auto=validate` 통과. `npm audit --omit=dev` 0건(개발 도구 `@capacitor/cli → xcode → uuid` moderate 3건은 앱에 들어가지 않아 그대로). Maven CVE 대조는 미실행
- 감사 판단: H-02 는 타당하나 "배포 차단" 은 과함 — 노출 대상은 그 집 고객과 우리 Storage 이고, 이번 잠금으로 재접속 경로는 대부분 막혔다. 다만 격리 방 실기기 시험 전까지는 완료로 보지 않는다

## 전체 재점검 (2026-09-24, 사용자 요청 "처음부터 끝까지")

백엔드 14.6k줄·앱 9.4k줄·UI 를 같은 체크리스트(정확성·권한·동시성·데이터 수명·계약·실패 처리)로 전부 읽었다. 결과 High 1 · Medium 5 · Low 6 · 판단 2.

- [x] O1 [H] 매니저 방문 취소 뒤 재매칭이 사실상 불가능했다 — 취소한 매니저의 오퍼가 `accepted` 로 남아 다른 매니저 수락이 `offers_one_accepted` 에 막히고(dev DB 에서 실제 확인), 같은 회차라 처음 콜을 받았던 매니저 전원이 후보에서 빠졌다. `BookingDispatcher.onReleased`: accepted → superseded, 회차 +1, 취소한 매니저만 제외. `MatchingIT` 에 "취소 → 다른 매니저 수락 200" 추가(미실행)
- [x] J1 [M] 라이브를 시작한 매니저가 방문을 취소하면 바디캠 Ingress 가 고객 방에 묶인 채 남았다(예약 managerId 가 비어 재조정기도 못 찾음) — 옛 매니저가 켜면 남의 집 방으로 송출. `CameraReleased` 아웃박스 → 그 방에 묶여 있을 때만 대기 방으로. 웹훅도 담당이 아닌 매니저의 바디캠은 붙이지 않고 대기 방으로
- [x] P1 [M] 패널티 정지가 기록마다 '현재 총점' 으로 다시 걸려, 5점을 넘은 뒤엔 0점(무료 취소)·0.5점 기록만 생겨도 오늘부터 7일 정지가 다시 늘었다 → 이번 기록으로 문턱을 **넘을 때만**
- [x] P2 [M] 회복 행(-1)이 12개월 남아, 상쇄한 벌점이 먼저 만료되면 다음 벌점을 깎았다 → 회복 행 만료 = 남은 양수 벌점 중 가장 늦은 만료
- [x] P3 [L] 증빙 면제로 점수가 정지 기준 아래가 되면 걸려 있던 정지도 푼다(정책 문서 두 곳에 한 줄 추가)
- [x] A1 [M] 비밀번호 재설정: 옛 세션 끊기를 새 세션 발급 **뒤**에 해서, 발급이 실패하면 옛 기기 토큰이 만료까지 통과했다 → 비밀번호 변경 직후 끊는다
- [x] A2 [L] 역할이 다른 로그인·탈퇴 비밀번호 확인이 쓰지 않을 Supabase 세션을 남겼다 → 바로 logout
- [x] A3 [L] `OtpService.peek` 주석을 현재 순서(외부 변경 전 소비)로
- [x] B1 [M] 예약 날짜 상한이 서버·앱 모두 없었다(10년 뒤 예약도 생성·즉시 오퍼) → `app_settings.booking_horizon_days`(V026, **60일 — 코드에서 정함, 판단 항목**). 예약 생성·변경이 넘으면 `SLOT_UNAVAILABLE`, `/catalog/time-slots` 가 `bookableDays` 를 주고 두 달력(`notBookableWithin`)이 그 뒤 날짜를 막는다
- [x] S1 [M] 근무 불가·근무시간 변경의 '맡은 작업과 겹침' 검사가 28일 앞까지만 → 366일
- [x] X1 [M] `mock-shapes/confirm-copy-*.json` 이 V021(정기청소 매월 1회) 뒤 갱신되지 않아 CI 의 `CatalogIT` 가 실패했을 것 → 재생성 + CI 에 "shape 가 최신인가" 검사 추가
- [x] B2 [L] 일정 변경으로 매칭 창 안에 들어온 정기 회차는 '그 시리즈를 취소한 매니저 제외' 규칙을 건너뛰었다 → `dispatchPreferred` 한 곳에서 검사(스케줄러·일정 변경 공통)
- [x] P4 [L] 저장 주소 수 상한 없음 → 20개(코드에서 정함). 예약 흐름의 '새 주소 저장' 은 가득 차면 조용히 건너뛴다
- [ ] B3 [L] 정기청소 상세의 회차 목록에 취소 회차가 섞인다 — **수정 불필요로 판단**: 그 응답은 예약 직후 완료 화면에서만 쓰여(취소 회차가 생길 수 없다) 사용자에게 드러나지 않는다
- 판단 필요: **D1** 고객 개인정보 동의 문안의 수집 항목에 수리 사진·청소 영상(집 안 녹화, 90일 보관)이 없다 — 법무 검토 문안이라 고치지 않았다. **D2** 예약 가능 기간 60일(바꾸려면 `app_settings.booking_horizon_days`)
- 검증(재점검): 앱 130 files / 274 tests, 백엔드 63 suites / 345 tests + JaCoCo(IT 미실행), lint·토큰·빌드·shape 최신 통과. dev DB V026 적용. **실서버 E2E**: 매니저 2명(테스트 매니저 010-9999-0003 추가·SQL 승인) — 고객 예약 → 둘 다 콜 → 매니저1 수락 → 매니저1 취소 → 매니저2 가 2회차 콜 수락 200(오퍼: 1회차 cancelled·superseded, 2회차 accepted, 아웃박스 7건 sent). 60일 넘는 날짜 예약 → `SLOT_UNAVAILABLE`

## 직접 사용 점검 (2026-09-24, 사용자 요청 "직접 모든 기능을 사용해보면서")

로컬 서버(dev DB)에 두 앱을 붙여 새 고객(010-9999-0801)으로 가입 → 청소·수리 예약 → 매니저(김미영)가 콜 수락 → 준비·시작·일시정지·재개·완료 → 고객 라이브·내 예약·설정, 매니저 일정·정산·MY 까지 375px 폭으로 눌러 봤다. 바디캠은 운영자 API 로 테스트 기기를 등록하고 `stream_status` 를 SQL 로 online 으로, 수리 예약 날짜를 오늘로 옮겨 진행(LiveKit 은 local logging 모드).

- [x] U1 [H] 가입·주소 관리·예약 주소 안내의 예시 "강남대로 396" 처럼 시·도 없이 쓰면 매칭 지역(sido)이 null → 후보 0명 → 예약이 매니저 누구에게도 가지 않고 고객 화면엔 '매니저를 찾고 있어요' 만 남았다(dev 실측: 예약 2548a11c, 로그 "지역=null null"). 서버가 가입 주소·주소 추가·예약 생성에서 시·도를 못 읽으면 `VALIDATION` "주소를 시·도부터 입력해주세요. 예: 서울 강남구 강남대로 396"(`AddressRegion.requireMatchable`, 예약은 한도 검사 전). 안내 문구도 시·도부터(코드에서 정함), 주소 추가 시트에도 같은 안내
- [x] U14 [M] 고객 평가 화면이 없어 `manager_profiles.rating` 은 0 에서 바뀌지 않는데, '완료 1건 이상' 이면 평점을 보여 줘서 첫 작업을 마친 매니저가 고객 예약 카드·매니저 MY 에 "평점 0.0" 으로 보였다(dev 실측) → 평점이 실제로 매겨졌을 때(>0)만. **판단 필요**: 고객 평가(별점) 기능을 둘지
- [x] U5 [M] 방문 시간 버튼이 104px 고정 줄바꿈이라 375px 폰(아이폰 미니·SE)에서 한 줄 2칸만 들어가 오른쪽 1/3 이 비었다(청소·수리 예약 둘 다) → 3칸 격자(393 에서는 Figma 그대로 104px, 375 에서 98px)
- [x] U9·U10·U11 [M] 매니저 업무 상세: 날짜 없이 "14:00 시작" 만 있어 내일 작업이 오늘처럼 읽혔고, 서버는 당일에만 시작을 받는데 전날에도 '현장 도착' 이 보여 준비 화면까지 갔다가 막혔다. 옵션이 없는 예약(수리)은 '청소 범위' 제목만 남았다 → `JobResponse.today` + 오늘이 아니면 "9월 25일 (금) 14:00 시작", '현장 도착' 자리에 "현장 도착은 방문 당일에 할 수 있어요."(코드에서 정함), 방문 취소는 그대로. 범위가 비면 섹션을 그리지 않는다
- [x] U16 [M] 고객 FAQ 가 규칙과 달랐다 — 변경·취소 "방문 전날까지"(실제는 24시간 전), 결제 "등록된 결제 수단으로"(등록 기능 없음, 결제 수단 화면과 모순) → V027 로 답변 교체(코드에서 정함), 앱 mock 도 같게
- [x] U13 [M] 수리 작업에 가정 청소 체크리스트(거실 바닥 청소·변기 …)가 나왔다(템플릿이 없으면 home 을 대신 쓴다) → V028 수리 체크리스트 6항목: 진단(고객과 증상 확인·원인 진단) / 수리(부품·비용 안내 후 고객 동의·수리 진행) / 마무리(작동 확인·주변 정리) — **코드에서 정함, 판단 항목**. 사무실·입주·가전 청소는 여전히 가정 청소 목록(기존 판단 항목)
- [x] U15 [M] 예약 2단계 "기존 주소를 사용할까요?" 는 기본 주소를 쓰는데, 주소 관리에서 어느 것이 기본인지 보이지 않고 바꿀 길도 없어 가입 주소를 고치려면 지우는 수밖에 없었다 → 목록에 `isDefault` + "예약 기본 주소" 표시, 다른 주소에 '기본으로' 버튼(`PUT /customer/addresses/{id}/default`, 문구 코드에서 정함). 단위·슬라이스 + `BookingFlowIT` 한 단계(미실행)
- [x] U2 [M] 내일 오전 방문은 예약하는 순간 이미 24시간 기한 안이라 바꾸거나 취소할 수 없는데, 예약 전에 알 길이 없었고 잠긴 카드에는 "방문 24시간 전까지 변경·취소할 수 있어요" 만 있어 아직 되는 것처럼 읽혔다(dev 실측: 9/24 13시에 9/25 10시 예약 → 버튼 없음). → 날짜·시간 화면(청소·수리)에서 기한 안의 시간을 고르면 "방문까지 24시간이 남지 않아, 예약한 뒤에는 바꾸거나 취소할 수 없어요."(`/catalog/time-slots` 에 `changeDeadlineHours`, `isWithinHours`), 잠긴 카드는 "방문 24시간 전이 지나 앱에서는 바꾸거나 취소할 수 없어요. 고객센터(번호)로 연락해주세요."(번호가 없으면 1:1 문의 — 만들어만 두고 안 쓰던 `getSupportPhone` 을 연결). 문구 코드에서 정함. **판단 필요**: 매니저가 아직 배정되지 않은 예약은 기한과 상관없이 취소를 허용할지(§6.1 "하나의 규칙" 결정을 바꾸는 일)
- [x] U3 [L] 휴대폰 칸(앞자리 010 은 칸 밖)에 "010-9999-0801" 을 통째로 넣으면 "010 | 010-9999-0801" 로 보였다 → PhoneField 가 숫자만, 9자리 이상이 010 으로 시작하면 010 을 떼고 8자리까지(서버 toE164 와 같은 규칙). 가입·로그인·비밀번호 재설정 두 앱 공통
- [x] U7 [L] 로그인 화면에서 비밀번호를 치고 키보드의 이동/완료(Enter)를 눌러도 아무 일이 없었다(폼이 없다) → 두 앱 로그인의 비밀번호 칸에서 Enter = 로그인하기(`enterKeyHint="go"`)
- [ ] U4 [L] 주기 화면에서 1회를 골라도 정기청소 안내(🔁)가 남는다 — **수정 불필요로 판단**: 두 선택지를 비교하는 설명이고, 지우면 고를 때마다 아래 내용이 위아래로 흔들린다
- 판단 필요(코드로 안 함): **E1** 수리 예약의 정산이 "0원"(콜·업무 상세·완료·정산 화면) — 기존 결정 필요 항목(수리 매칭·정산 규칙) 그대로. **E2** ~~공휴일·휴무일 예약 막기~~ → **결정(사용자 2026-09-24): 막지 않는다.** 주말만 막는 지금 규칙 그대로. 이 결정과 어긋나던 V004 예시 고객 공지 "추석 연휴(9월 24일~27일)에는 방문 청소가 진행되지 않아요" 는 V029 로 지웠다(사용자 승인, 앱 mock 도). 매니저 공지 "추석 연휴 정산 일정 안내" 는 방문을 막는 내용이 아니라 둔다. **E3** 수리 화면의 예시·팁이 냉장고 기준(증상 상세 placeholder "냉장실 온도가…", 현장 팁 "냉장고 주변의 물건을…")이라 에어컨 등을 골라도 그대로 — Figma 문구. **E4** ~~일시정지 화면 "촬영이 중단된 동안에는 정산이 집계되지 않습니다" 와 달리 정산은 예약 금액 고정~~ → **결정(사용자 2026-09-26): 문구를 고친다.** V033 "촬영이 중단된 동안에는 고객 화면과 녹화 영상에 아무것도 나오지 않아요. 정산 금액은 그대로예요."(재감사 2차 #7, `2123d51`, 앱 mock 도 같게). Figma 문구와 다르다 — 디자이너에게 알릴 것. **E5** 고객 평가(별점)·매니저 미배정 예약의 기한 무관 취소(U14·U2 참고)
- 검증(직접 사용 점검): 앱 131 files / 282 tests, 백엔드 63 suites / 352 tests + JaCoCo(**IT 미실행** — `BookingFlowIT` 에 기본 주소 단계 추가, 컴파일만), 토큰 생성물·lint·토큰·두 앱 빌드·shape 최신 통과. dev DB V027·V028 적용. 실서버 재확인: 시·도 없는 주소 → 주소 추가·예약 모두 400 문구, 새 수리 예약 배정 → 수리 체크리스트 6항목 복사, 내일 작업 상세 "9월 28일 (월) 15:00 시작"·'현장 도착' 대신 안내, 기본 주소 바꾸기, 날짜 화면 기한 경고, 잠긴 카드 문구, MY 평점 "-", "010-9999-0003" 입력 → 99990003·Enter 로그인. 테스트 예약은 취소, 테스트 바디캠(김미영, `TEST-0001`)은 offline 으로 남김

## 전체 점검 (2026-09-24, 직접 사용 점검 뒤 사용자 요청)

직접 사용 점검에서 바꾼 코드(`ff9c46b..`) 전체를 같은 체크리스트로 다시 읽고, 같은 종류의 결함이 다른 곳에 없는지 두 앱 전 화면을 360px 로 돌려(가로 넘침·줄바꿈 빈자리 검사), 실제 DB 의 화면 문구를 규칙과 대조하고, 빠졌던 흐름(예약 변경·취소·정기청소·로그아웃·비밀번호 재설정)을 실서버로 돌렸다.

- [x] G1 [M] 홈 '다가오는 방문' 카드가 기한이 지난 방문에도 "변경·취소는 '내 예약' 에서 할 수 있어요" 라고 했다(U2 와 같은 모순의 남은 자리, dev 실측) → `changeable=false` 면 내 예약 카드와 같은 안내(고객센터 번호/1:1 문의). `Home.test.jsx` 신설
- [x] G2 [M] 정기청소를 예약하면 내 예약에 '가정/원룸 청소' 카드가 6장 더 생기는데, 어느 것이 정기청소이고 몇 번째 방문인지 알 수 없었다(1회 예약과 구분 불가, dev 실측) → 서버가 회차 카드 제목에 "· 정기 2/6회"(방문일 순, 취소 회차 포함)를 붙인다. 홈 카드도 같다
- [x] G3 [M] 정기청소 2~6회차 중 60일 뒤 방문은 '예약 변경' 버튼이 있지만 달력 날짜가 전부 막혀 있고 서버도 "오늘부터 60일 안의 날짜만" 으로 거부했다(dev 실측, 지난 재점검 B1 이 만든 결함) → 일정 변경의 예약 가능 기간은 오늘과 지금 방문일 중 늦은 날부터 센다(서버·달력 같게, 넘으면 "지금 방문일(12월 8일)부터 60일 안의 날짜로만 바꿀 수 있어요"). 시리즈 기간(term_end) 제한은 그대로
- [x] G5 [L] 예약 취소 시트가 "예약을 취소할까요?" 뿐이라, 비슷한 카드가 여러 장(정기 회차)일 때 어느 방문을 취소하는지 시트에서 알 수 없었다 → 시트 맨 위에 "제목 · 일정"
- [x] G4 [L] 예약 일정을 바꾸면 취소 때와 달리 아무 확인 없이 목록으로만 돌아갔다 → "예약 일정을 바꿨어요" 토스트(코드에서 정함)
- [x] G6 [L] 예약 확인 화면에서 고르지 않은 선택 옵션·주의사항은 제목만, 요청사항은 빈 회색 상자만 남아 빠뜨린 건지 알 수 없었다 → "선택한 옵션이 없어요"·"해당하는 주의사항이 없어요"·"남긴 요청사항이 없어요"(코드에서 정함)
- 확인만 한 것(문제 없음): 두 앱 전 화면 360px 가로 넘침·줄바꿈 빈자리 0건(검사기는 옛 시간 격자 모양을 잡는 것까지 확인), 실서버 정기청소 예약 → 회차 변경(11/5→11/6) → 회차 취소 → 전체 취소, 로그아웃 → 비밀번호 재설정(010 붙인 번호 입력) → 새 비밀번호로 로그인·옛 비밀번호 거부. 이번 변경 전체 diff 재검토
- 남긴 것: 서로 다른 두 기기에서 동시에 다른 주소를 '기본으로' 누르면 부분 유니크 인덱스에 걸려 한쪽이 500 — 한 기기에서는 버튼이 막혀 생기지 않아 두었다. 정기청소 예약 생성이 dev(원격 도쿄 DB)에서 5초 — 운영 DB 가 서버 가까이 있으면 줄어든다(배포 뒤 재측정). 라이브 화면 "거실 청소 중"·"청소하고 있어요" 가 수리에도 나온다(Figma 문구, E6)
- 검증(전체 점검): 앱 132 files / 285 tests, 백엔드 63 suites / 354 tests + JaCoCo(**IT 미실행**), 토큰 생성물·lint·토큰·두 앱 빌드·shape 최신 통과. dev DB V029 적용. 테스트 예약은 모두 취소, 체험고객(010-9999-0801) 비밀번호는 [평문 제거, 기존 값 폐기 필요]

## 홈 캐러셀 스와이프 (2026-09-24, 사용자 승인 "추천대로")

- [x] 홈 히어로를 **손가락을 따라 움직이다 놓으면 가까운 장에 붙는** 가로 트랙으로(CSS scroll-snap — 따라오기·관성·세로 스크롤과의 구분을 브라우저가 맡는다, 새 패키지 없음). 예전에는 손을 뗄 때 40px 이상이면 순간 교체 + 배경이 번쩍 바뀌었고, 세로로 스크롤하다 옆으로 40px 틀어져도 넘어갔다. 근거: Figma HeroArtPeek(옆 장이 흐릿하게 삐져나온 그림) = 옆에 붙어 있다는 뜻
  - 배경 그라디언트는 두 겹(`.screen::before/::after`)으로 깔고 위 겹 투명도만 300ms 크로스페이드. 점(●○)은 트랙 밖 제자리, 반 넘게 밀면 바뀐다. 탭·→/←·Enter 는 같은 트랙을 부드럽게 스크롤(마지막 장에서 탭하면 처음으로). 자동 넘김 없음. 동작 줄이기 설정이면 크로스페이드·부드러운 스크롤 없이 바로
  - **코드에서 정함(판단 항목)**: `--duration-slow: 300ms`, `--ease-standard: cubic-bezier(0.2, 0, 0, 1)`(tokens/extra.css 모션 구역), 한 장씩 멈춤(`scroll-snap-stop: always`). Figma 프로토타입 전환 설정은 Figma 연결 인증이 풀려 확인하지 못했다 — 디자이너 값이 있으면 그걸로 바꾼다
  - 확인: 375·360px 에서 쉬는 화면이 예전과 같다(히어로 높이 366, Figma 367), 트랙을 30% 밀면 제자리·70% 면 2장째, 배경 0→1 전환 0.3s, 에뮬레이션 없이 부드러운 스크롤 약 0.5초. 미리보기의 모바일 에뮬레이션에서는 부드러운 스크롤이 멈칫거렸는데 에뮬레이션을 끄면 정상이라 도구 쪽 현상으로 봤다 — **실기기(iOS·Android) 손가락 확인은 아직**


## 운영 준비·보안 재점검 (2026-09-24, 사용자 요청 "한번 더 점검")

기준 `9b131e4` 이후 작업 트리. 현재 판정/상세 증거는 `audit/PRODUCTION_READINESS_SECURITY_AUDIT.md`, 최초 보고서는 `audit/PRODUCTION_READINESS_SECURITY_AUDIT_INITIAL.md`에 분리했다. 결제 미구현은 평가에서 제외. 배포·실서비스 호출·데이터 삭제·커밋/푸시는 하지 않았다.

- [x] M-11 신규: A 계정의 늦은 401·성공 응답·409 재시도를 B 계정에 적용하던 경합. 두 앱 공통 HTTP에 로그인 세대 검사, refresh 전용 renew 및 세대별 single-flight 적용. 로그아웃 어댑터도 push 삭제/로그아웃 응답 뒤 새 계정의 서버/로컬 세션을 지우지 않게 같은 경계 검사. 공격 시나리오 수정 전 6+4개 실패, 수정 후 통과. 정상 병렬 refresh 2개도 추가.
- [x] M-01 재개방: 네이티브 평문→secure 이전 쓰기가 로그아웃 삭제를 추월해 토큰이 복원됨. 이전도 같은 쓰기 큐로. 두 앱 재현 2개 수정 전 실패/후 통과.
- [x] M-02 재개방: PNG unsigned 치수 곱의 long overflow로 픽셀 상한 우회. 나눗셈 비교로 수정, 재현 1개 전 실패/후 통과. 실제 decoder/HEIC 전체 구조 검증과는 구분.
- [x] H-02 추가 완화: 재조정기의 공통 녹화 함수에서도 mute/unmute 미확인 시 새 녹화 금지. PAUSED 예약/세션 또는 영상 중단 요청 중 새 고객 시청 token 금지. 재현 2개 전 실패/후 통과, API 문서 갱신. **기존 시청자/기존 Egress의 빠른 재접속 보호까지 해결한 것은 아니므로 H-02 잔존**.
- [x] 문서에 적힌 개발 테스트 계정 비밀번호 1개 제거. 과거 커밋에 남은 값은 별도 폐기 필요. 값은 감사 보고서/검사 결과에 복사하지 않음.
- [ ] 사진 정리 후보 조회와 예약 첨부의 경합, 탈퇴 정리 뒤 늦은 업로드 완료로 파일 잔존(M-04). 상태 전이/IT 필요.
- [ ] 이력 pagination/매칭 후보별 조회(M-09), 주소 개수/기본 주소 동시 쓰기(L-03). 기본 주소 제약 위반은 현재 handler에서 409이며 기존 기록의 500 주장은 재현하지 않음.

### 재감사 검토·반영 (2026-09-24, 사용자 요청 "타당한 건 반영")

재감사가 작업 트리에 남긴 수정을 하나씩 읽고 검증해 커밋했고, 남은 항목 중 코드로 닫을 수 있는 것을 더 고쳤다.
- [x] 재감사 수정 검토·커밋: M-02 overflow(`e0ec03b`) · H-02 일부 — mute 미확인 중 새 녹화 금지, 일시정지 중 새 시청 토큰 거부(`70842ae`, 앱은 `LIVE_NOT_AVAILABLE` 을 상태 표시 + 다음 폴링 재시도로 이미 처리) · M-11·M-01 세션 세대·쓰기 큐(`b896be0`, `session.set` 호출 3곳이 모두 새 로그인 경계임을 확인)
- [x] H-02 추가: 일시정지는 mute **전에** 이미 방에 있는 고객의 `canSubscribe` 를 끄고 확인된 재개 뒤 연다(`live_sessions.viewers_blocked`, V030). 옛 토큰으로 다시 들어오면 `participant_joined` 에서 막고 재조정기가 1분마다 맞춘다 — 재접속한 바디캠의 mute 전 트랙이 기존 시청자에게 가지 않는다. **남은 틈**: 그 몇 초가 실행 중인 녹화(room composite)에 들어갈 수 있다 → 실기기 격리 시험 필요(H-02 는 계속 미완)
- [x] 오디오(재감사 지적 확인): 명세(domain.md "기기가 음성을 끄지 못하면 서버가 오디오 트랙을 늘 mute")에만 있고 **구현이 없었다** — 서버는 영상 트랙만 mute 해서, 소리를 보내는 바디캠이면 시청 토큰으로 집 안 소리를 들을 수 있었다(일시정지 중 포함). 카메라가 붙을 때·재조정기 매 주기 오디오 트랙 mute
- [x] M-04: 정리는 원장을 '아직 안 붙었을 때만' 먼저 지우고 Storage 를 지운다(실패하면 원장을 되살림). 예약 첨부는 붙은 수를 확인해 모자라면 "첨부한 사진이 만료됐어요. 사진을 다시 올려주세요"(코드에서 정함). 탈퇴한 계정은 올리지 못하고, 올리는 사이 탈퇴 정리가 끝났으면 늦게 올라간 파일을 지운다
- [x] L-02: 저장소 본문의 개발 계정 비밀번호 제거(PROGRESS·갤러리 견본 값). **과거 커밋에 남은 값은 dev 계정 비밀번호를 바꿔야 무효가 된다 — 사용자가 할 일**(앱의 비밀번호 재설정으로. 바꾸면 그 계정의 기존 로그인은 모두 끊긴다)
- [ ] L-03 **수용**: 주소 20개 상한의 동시 추가는 21개까지, 기본 주소 동시 변경은 부분 유니크 인덱스가 막고 409(앞서 적은 "500" 은 틀렸다 — GlobalExceptionHandler 가 409 로 바꾼다). 주소 쓰기를 배타 잠금으로 묶으면 예약 생성의 공유 잠금과 잠금 승격·교착이 생길 수 있어 두었다
- [ ] M-09 페이지네이션은 계속 보류(API·화면을 같이 바꿔야 함)
- 검증: **133 files / 301 frontend tests**, **63 suites / 357 backend tests**, Maven offline verify(package·JaCoCo), lint·455 files token 검사·두 앱 build·mock shapes 최신 확인 통과. 백엔드 최초 시도는 로컬 mock HTTP 소켓 권한으로 1개 실패, 해당 로컬 소켓 권한 확대 후 전체 재실행 통과. .env import는 테스트용 없는 경로로 덮음. **Postgres IT 미실행(Docker 없음)**. 현재 추적 파일 1,515개 + dist 580개에 로컬 환경 secret 3개 일치 0. Git 전체 이력/CVE/실기기/운영 환경은 이번에 검증하지 않음.
- 판정: **NOT READY**. 재접속이 Ingress 잠금보다 먼저 오면 기존 viewer/녹화까지 막는 보장이 없어 라이브를 포함한 프로덕션은 승인할 수 없다.

### 재감사 2차 (2026-09-26, 사용자 요청 "타당한 건 반영")

지적 8건을 코드로 하나씩 확인했다(보고서의 `api/media/` 경로는 실제로 `api/booking/`).
- [x] #1 [높음] 맞음 — 막을지를 세션 상태로만 봐서, 일시정지 중 바디캠이 끊겨 `waiting_camera` 가 되면 다음 동기화가 구독을 다시 열었다(바로 뒤 재접속이 막아야 할 상황). 작업(예약) 일시정지도 보고, 재조정기가 참가자마다 실제 `canSubscribe` 를 비교해 한 명 막기 실패도 다시 고친다(`e1e6d7e`)
- [x] #2 [중간] 맞음 — 고객 라이브: 붙는 중에 다른 예약으로 옮기면 옛 방의 늦은 트랙·음소거가 새 화면을 바꿨고, 새 연결이 옛 시도를 기다렸다. 콜백마다 join 순번 확인·붙는 중 표시를 join 마다(`7c3a7e7`). 매니저 프리뷰는 이미 순번을 본다
- [x] #3 [중간] 맞음, **내 실수** — 앞 커밋 `1bd2aa1` 의 "붙은 수 확인" 이 사진 서비스에만 있고 예약 생성은 저장소를 직접 불러 돌지 않았다. 예약 생성이 사진 서비스를 거치게(`23c5324`)
- [x] #4 [중간] 맞음 — 정리는 행에 `deleting_at`(V031)을 적어 잡고 → Storage → 행 순서(멈춰도 다음 정리가 다시). 업로드가 끝난 뒤 계정이 살아 있는지 다시 보고 아니면 파일·행 삭제(`cd13932`)
- [x] #5 [중간] 맞음(범위는 좁다 — 같은 화면의 재시도는 `resumeJob` 이 멱등이라 됐다). ①만 되고 나갔다 오면 일시정지 조회 404 를 반복했다 → 서버의 지금 작업을 보고 영상 재개를 다시 부른 뒤 진행 화면으로(`8ea14e3`)
- [x] #6 [중간] 맞음 — 사무실·가전·입주 청소 체크리스트(V032, **항목은 코드에서 정함·판단 항목**). 무료·유료 옵션과 겹치는 일(탕비실·베란다·붙박이장)은 빼고 늘 하는 일만
- [x] #7 → **사용자 결정(2026-09-26): 문구를 고친다.** V033: "촬영이 중단된 동안에는 고객 화면과 녹화 영상에 아무것도 나오지 않아요. 정산 금액은 그대로예요."(Figma 문구와 다름 — 디자이너에게 알릴 것). 앞 판단 기록: 사실이지만 결정 필요(E4) — "촬영이 중단된 동안에는 정산이 집계되지 않습니다" 는 Figma 문구, 정산은 예약 때 고정(§11-7 결정). 문구를 바꾸려면 디자이너, 계산을 바꾸려면 정산 정책 결정이 필요하다
- [ ] #8 [중간] 사실 — M-09 와 같다. 계속 보류(이력 페이지네이션은 API·화면을 같이 바꿔야 하고, 후보 일괄 조회는 매니저 수가 늘 때)

## 자체 점검 (2026-09-26, 사용자 요청 "자체적으로 다시 문제점 검토")

읽기 전용 검토 워크플로(찾는 에이전트 11 + 영역 밖을 찾는 비평가, 발견마다 반박하려는 검증자 2명·의견이 갈리면 3번째)로 65건 → 중복 정리 55건 → 검증 통과 48건 + 후속 탐색 10건 = **58건(중간 18·낮음 40, 높음 0)**. 반박된 것 7건. 번호는 검토 결과 순서.
- [x] #1·#51·#52·#2 라이브 바디캠: 끝난 작업의 정리(재조정기 15분 재확인·늦은 stopSession)가 같은 매니저의 다음 작업이 쓰는 바디캠의 일시정지 잠금을 풀거나 대기 방으로 빼던 것 → 다른 진행 중 작업이 없을 때만·이 방에 묶여 있을 때만. 잠긴 채 남은 바디캠은 기기 단위로 매분 푼다. 카메라 대기 중(트랙이 늦게 올라온 재접속) 일시정지는 LiveKit 의 실제 트랙을 확인해 mute(`8ab4942`)
- [x] #15·#42 일시정지 절반 실패: 영상만 막고 작업 일시정지가 실패하면 재조정기가 2분 뒤 영상을 다시 켜, '영상은 중단됐다' 안내를 믿고 자리를 비운 매니저의 영상이 나갔다 → 작업이 영상 일시정지 뒤 실제로 일시정지됐을 때만 다시 켠다(`live_sessions.job_paused_at`, V034). 그 상태로 시트를 닫으면 앱이 영상을 다시 켜고 문구도 그렇게 말한다(코드에서 정함). 재개 ②만 실패하면 일시정지 화면에 남기지 않고 진행 화면으로
- [x] #3 청소 완료 직후(녹화 업로드 중) 탈퇴하면 탈퇴 정리가 준비된 녹화만 지워 그 집 영상이 90일 남았다 → 녹화가 끝나 도착할 때 고객이 탈퇴했으면 받는 즉시 지운다(알림 없음)
- [x] #4 일시정지 중 바디캠을 끄면 고객은 '카메라 연결을 기다리고 있어요', 매니저는 '바디캠이 끊겼어요'·5분 알림 → 작업이 일시정지면 고객에게 일시정지 문구, 장애 푸시는 작업 진행 중에만, 5분은 재개한 때부터
- [x] #43 진행 화면 경과 시간이 라이브 상태(영상 막힘)로 멈춰, 작업 일시정지가 실패한 채 계속 청소하면 시계가 거꾸로 멈춰 있었다 → 작업 조회에 `status`(inProgress|paused)를 더하고 시계는 그걸로 멈춘다. 일시정지 응답만 잃고 시트를 닫으면 상태를 다시 받아 일시정지 화면으로
- [x] #41 작업 재개 뒤 영상 재개 전에 앱을 껐다 열면, 일시정지 화면의 복구가 빈 라이브 세션에 resume 을 보내 실패했다 → 세션부터 다시 붙이고(start) 재개
- [x] #34·#38 고객 시청: 음소거 이벤트 직후의 즉시 조회가 서버 커밋보다 먼저 와 일시정지·재개 상태를 거꾸로 덮어 최대 30초 틀렸고, 카메라 대기 중 바디캠이 붙어도 30초 폴링까지 '카메라 연결을 기다리고 있어요' 였다 → 트랙 이벤트로 정한 상태를 기대값으로 두고 서버가 따라올 때까지 1초 간격 최대 4번 확인(더 새 이벤트가 오면 옛 확인은 멈춤). 대기 중 연결 실패는 폴링이 다시 붙인다
- [x] #6·#7 콜 제외: 방문을 취소한 매니저가 다음 매니저도 취소하거나 일정이 바뀌어 회차가 새로 시작되면 같은 예약의 콜을 다시 받고 수락할 수 있었다 → 이 예약의 취소 기록이 있는 매니저는 어느 회차든 빼고, 옛 오퍼 수락도 막는다. 지각 한 번으로도 정기청소 우선 제안에서 빠지고 일부 경로에선 일반 콜에서도 빠지던 것 → 취소·미방문만 세고, 우선 제안에서 빠져도 일반 콜은 받는다(정책 §4)
- [x] #8 정기청소 한 회차를 미리 취소하고 나머지를 다 마치면 시리즈가 term_end 까지 active 로 남아 탈퇴가 막히고 종료 안내 푸시가 나갔다 → 매니저 완료 때도 남은 회차를 보고 닫는다(마친 회차가 있으면 '끝남'). 완료는 시리즈 → 예약 순서로 잠근다(고객 취소와 같은 순서)
- [x] #9 패널티 회복(-1)이 남은 점수(예: 0.5)보다 커도 1점 전부 기록돼 남는 몫이 다음 벌점을 깎아 정지가 걸리지 않았다(면제 뒤 남은 회복도 같다) → 회복은 남은 만큼만, 누적 점수는 생긴 순서대로 더하며 매번 0 아래로 내려가지 않게. 정책 문서에 한 줄 추가(코드에서 정함)
- [x] #10 '매월' 근무 불가의 작업 겹침 검사가 31일 → 2월 28일 → 3월 28일 … 로 밀려, 실제로 막는 날(31일)의 작업은 못 보고 막지 않는 날의 작업으로 거절했다 → 원래 날짜에서 N개월 뒤를 구하고 그 날짜가 없는 달은 건너뛴다
- [x] #11 콜 배정이 정지된 매니저의 홈에 '승인 대기 중이에요 · 신원 확인과 기본 교육…' 카드가 떴다 → 홈 `manager.approved` 는 승인만, 새 `manager.suspension` 이 있으면 '콜 배정 정지 중이에요'(코드에서 정함) + 서버 문구 카드
- [x] #12 속도 제한 키가 상한(10만)에 차면 새 키를 전부 막아, IP 를 바꿔 가며 키를 채운 한 사람이 모든 사용자의 로그인·인증번호·토큰 갱신을 한 시간 동안 막을 수 있었다 → 가장 오래 쓰이지 않은 키를 버리고(LRU) 새 키를 받는다. IPv6 는 /64 로 묶어 주소마다 새 키를 만들지 못하게(코드에서 정함)
- [x] #14 주의사항 화면에서 가입 정보로 미리 켜 둔 '반려동물이 있어요' 칩을 건드리지 않고 다음을 누르면 초안에 저장되지 않아 확인 화면·예약에서 빠졌다 → 다음을 누를 때 보이는 선택을 초안에 남긴다
- [x] #17 시작한 뒤 '청소 완료' 를 누르지 않은 작업(in_progress·paused)을 닫을 길이 없어, 고객 탈퇴가 영영 막히고 녹화·재조정이 끝없이 이어졌다 → 운영자 완료 API `POST /admin/jobs/{id}/complete`(매니저 완료와 같은 처리) + 매일 03:10 방문일+1일 지난 작업을 ERROR 로그로 알림(자동으로 닫지 않는다 — 매니저 확인 없이 정산이 생기지 않게, 코드에서 정함) + runbook 조회·절차. 이런 예약이 있는 고객의 탈퇴 문구는 '청소가 진행 중인 예약이 있어 탈퇴할 수 없어요. 청소가 끝난 뒤 다시 시도해주세요'(코드에서 정함). IT 추가(미실행)
- [x] #18·#21·#56 우선 오퍼: 정기청소 다른 회차를 취소한 매니저가 일반 콜로 맡은 회차를 고객이 옮기면 그 매니저는 빠지는데도 '새 콜을 확인해주세요' 푸시를 받았고(정지·탈퇴도 같다), 우선 오퍼는 근무 불가·겹치는 작업을 보지 않아 쉬는 날 배정되거나 10분을 헛되이 기다렸다 → 우선 오퍼도 일반 후보 조건을 통과할 때만, 일정 변경은 자기 회차라 취소 이력을 보지 않고, 못 보냈으면 '고객이 일정을 바꿔 이 예약은 다른 매니저에게 다시 배정돼요'(코드에서 정함, 일정 탭으로)
- [x] #19 매칭 실패 뒤 고객이 일정을 바꿔도 실패 표시가 남아, 새 일정 매칭이 다시 실패하면 알림이 안 나가고 운영자 조회에 매칭 중인 예약이 섞였다 → 일정 변경으로 매칭을 다시 시작할 때 표시를 지운다
- [x] #20 일정 변경에 속도 제한이 없어 한 고객이 10시↔11시를 번갈아 바꿔 지역 매니저 전원에게 '새 콜' 푸시를 수십 번 보낼 수 있었다 → 같은 고객 시간당 10회(코드에서 정함, 같은 일정 재전송은 세지 않음)
- [x] #26 업무 상세의 '예상 소요' 가 현재 설정값이라 설정이 바뀌면 콜 카드·일정 시간대와 달랐다 → 예약 때 고정한 소요 시간으로
- [x] #27 콜 카드 위치(주소 앞 3어절)가 세종시 주소에서 건물번호까지 담아 수락 전 후보 전원에게 정확한 주소가 보였다 → 건물번호 앞에서 멈춘다
- [x] #28 패널티 푸시(type=penalty)를 매니저 앱이 몰라 누르면 홈으로 갔다 → 패널티 내역으로
- [x] #22·#29 비밀번호 재설정: 비밀번호는 바뀌었는데 새 세션 발급이 실패하면 '잠시 후 다시 시도' 가 나오고, 다시 누르면 '휴대폰 인증을 다시 해주세요' 였다 → 새 코드 `PASSWORD_CHANGED`('비밀번호가 변경됐어요. 새 비밀번호로 로그인해주세요', 코드에서 정함)로 두 앱 모두 로그인 화면에 이 안내를 띄운다. 다른 기기 세션은 끊으면서 푸시 토큰은 남겨 끊긴 기기가 알림을 계속 받던 것 → 재설정 때 푸시 토큰도 지운다(이 기기는 새 세션 뒤 다시 등록)
- [x] #24 같은 매니저의 패널티 기록을 줄 세우지 않아, 동시에 들어온 두 취소가 무료 한도를 넘기거나 둘이 함께 넘은 정지 문턱을 놓쳤다 → 매니저별 advisory lock 뒤에 횟수·점수를 읽는다(매니저 행 잠금은 콜 수락과 순서가 엇갈려 쓰지 않음). IT 미실행(Postgres 함수)
- [x] #39·#47 비밀번호 재설정으로 로그인하면 가입 초안을 지우지 않아, 공용 기기에서 앞사람이 남긴 약관 동의로 가입 완료가 기록될 수 있었다 → 로그인과 같게 초안을 지운다(두 앱)
- [x] #48 `useAsync.reload` 는 받아 둔 데이터를 버리고 Promise 도 돌려주지 않아, 토글 실패 뒤 재조회가 설정 화면 전체를 로딩·오류로 덮고(실패 문구는 안 보임) `await reload()` 는 기다리지 않았다 → 조용한 재조회 `refresh()`(데이터를 둔 채 바꾸고, 실패하면 그대로 두고 reject) 추가. 설정·알림 토글 실패는 먼저 되돌린 뒤 refresh, 주소 삭제·예약 취소·콜 거절 뒤 목록도 refresh. `reload` 는 '다시 시도' 에만
- [x] #49 `useAction.reset()` 을 진행 중에 부르면 버튼은 풀려 보이는데 눌러도 반응이 없고, 끝난 옛 실행의 실패 문구가 새로 연 시트에 떴다 → 진행 중이면 스피너를 두고 문구만 지우며, reset 뒤 끝난 실행의 실패는 보이지 않게
- [x] #35 주기 화면에서 확인 문구 조회만 실패하면 '다시 시도' 가 카테고리만 다시 불러 오류 화면이 사라지지 않았다 → 실패한 쪽을 모두 다시
- [x] #36 예약 변경 화면이 저장하지 않고 나간 지난 선택을 '현재 일정' 처럼 다시 채웠다 → 들어올 때마다 실제 현재 일정으로
- [x] #37 비밀번호 재설정에서 확인 중에 고친 인증번호가 버려져, 맞는 번호가 보이는데 '맞지 않아요' 가 남았다 → 가입 화면처럼 앞 확인이 끝난 뒤 한 번 더 확인(두 앱)
- [x] #32·#45 업무 상세: '바디캠 연결이 끊겼어요' 푸시가 여는 진행 중 작업의 상세에 아무 버튼이 없어 막다른 곳이었다 → 홈 카드와 같은 '업무 화면으로 돌아가기'(일시정지면 진행 화면이 일시정지 화면으로 보낸다). 방문 취소 사유를 못 받으면 시트가 비고 버튼이 영영 비활성이었다 → 오류와 '다시 시도'
- [x] #46 콜 상세의 만료 시간이 서버 문자열 그대로 멈춰 5분을 읽어도 '4:32 후 만료' 였다 → 목록처럼 1초마다 줄고, 지나면 '이미 마감된 요청이에요' 안내
- [x] #50 휴대폰 칸에 '+82 10-1234-5678' 을 붙여 넣으면 앞 8자리(82101234)만 남아 모르는 사람에게 인증 문자가 갔다 → +82 는 국내 형식으로 바꾸고, 그래도 8자리를 넘으면 자르지 않아 화면에 그대로 보이고 서버가 거절
- [x] #44 매니저 방문 취소가 멱등 키 없이 나가, 응답이 늦어 다시 누르면(이미 취소·패널티 완료) '요청한 정보를 찾을 수 없어요' 가 떴다 → `Idempotency-Key` 로 같은 응답
- [x] #57·#58 고객 취소: 요청 사유에 'not_started' 를 넣으면 매니저·고객에게 미방문 푸시가 갔다 → 시스템 사유 값은 'customer' 로 바꿔 기록. 응답이 늦어 다시 누르면 이미 취소됐는데 '지금은 할 수 없는 동작이에요' 였다 → 이미 고객이 취소한 예약이면 그 카드를 그대로(시스템 취소는 그대로 막음)
- [x] #33 판단 항목 E4(일시정지 정산 문구)가 V033 이후에도 '미결정·Figma 문구' 로 남아 있었다 → 사용자 결정(2026-09-26)으로 닫고, Figma 와 다르다는 표시
- [x] #23 패널티 유효 기간·회복 주기를 '개월×30일' 로 계산해 12개월이 360일이었다(벌점이 닷새쯤 먼저 사라지고 회복도 빨랐다) → 서울 달력 월로. 이미 저장된 행의 만료일은 그대로(dev 에만 있다)
- [x] #30 고객 FAQ '예약 후 보통 24시간 안에 배정' 이 정기청소(이후 방문은 7일 전 매칭)와 달랐다 → V035 로 답변 교체(코드에서 정함), 앱 mock 도 같게
- [x] #5·#13 매칭 실패: 푸시·카드가 '계속 찾고 있어요 · 조금 더 걸릴 수 있어요' 였지만 실패 뒤에는 아무도 다시 찾지 않고 방문일 다음 날 취소됐고, runbook 의 'SQL 로 표시를 지우면 다음 재매칭 스케줄러가 집어 간다' 도 없는 스케줄러였다 → 문구를 사실대로(푸시 '매니저를 찾지 못했어요 · 다른 날짜·시간으로 바꾸면 다시 찾아요. 1:1 문의로 알려주셔도 돼요', 카드 '매니저를 찾지 못했어요 · 일정을 바꾸면 다시 찾아요' V036, 코드에서 정함) + 운영자 재매칭 API `POST /admin/bookings/{id}/redispatch`(새 회차로 후보 전원, 표시를 지워 또 실패하면 또 알림) + runbook 절차 교체. 자동 재매칭은 사용자 결정(2026-09-27)으로 추가 — 아래. IT 추가(미실행)
- [x] #55 runbook 의 승인 대기 조회·승인 SQL 이 없는 컬럼(m.id·m.created_at)을 쓰고 탈퇴를 보지 않아, 목록을 본 뒤 탈퇴한 매니저를 승인하면 콜이 유령 계정으로 갔다 → SQL 을 고치고(탈퇴했으면 0행), 매칭 후보 조회도 탈퇴한 프로필을 뺀다
- [x] #53 푸시 토큰 등록이 탈퇴와 줄을 서지 않아, 탈퇴와 겹친 등록이 탈퇴 뒤에 커밋되면 탈퇴자의 토큰 행이 영영 남았다 → 등록 전에 프로필을 공유 잠금으로 확인(탈퇴가 먼저면 401)
- [x] #54 사용자 캐시가 탈퇴 커밋과 겹쳐 읽은 '살아 있음' 을 커밋 뒤에 1분 동안 캐시해 탈퇴한 계정의 토큰이 계속 통과했다 → 사용자별 비운 횟수로, 읽는 사이 비워졌으면 넣지 않는다
- [x] #40 날짜 도우미가 기기 시간대로 '오늘'·방문 시각을 계산해 해외 시간대 기기에서 서버(Asia/Seoul) 규칙과 어긋났다(24시간 경고 누락·하루 어긋남) → 오늘은 서울 날짜, 방문 시각은 +09:00 으로 읽는다. 테스트도 LA 시간대에서 통과 확인
- [x] #16 **결정(사용자 2026-09-27): 늦은 시간대 슬롯을 막지 않는다** — '고객이 예약해도 매니저가 콜을 받아야 성립하니까'. 코드 변경 없음(§13-6 (b) 그대로). 결과: 매칭은 매니저 근무 가능 시간 안에 **끝나는** 작업만 보내므로, 끝나는 시각이 18시를 넘는 예약(가정 16~18시 등)은 근무 종료를 늦춘 매니저에게만 가고, 없으면 매칭 실패 안내(V036). → **같은 날 후속 결정으로 매칭 기준을 바꿨다**(시작 시각만 근무시간 안, 아래 '#16 후속')
- [x] #25 **결정(사용자 2026-09-27): 고객 푸시를 만든다.** 정책 문서는 30분 초과 지각이면 '고객에게 자동으로 알림이 갑니다' 였지만 구현은 매니저 0.5점뿐이었다 → 약속 시간 + 30분(tardyMinutes)이 지나도 시작하지 않으면 1분마다 도는 `LateStartNotifier` 가 고객에게 한 번 '매니저가 늦어지고 있어요 · 약속 시간이 30분 넘게 지났어요. 도착이 더 늦어지면 고객센터로 알려주세요'(코드에서 정함, 설정 토글과 무관). 기다리는 동안 알려야 쓸모가 있어 시작 때가 아니라 기준 시각에 보낸다. V037 `bookings.late_notified_at`. IT 추가(미실행)
- [x] #31 **결정(사용자 2026-09-27): 3년.** 탈퇴해도 1:1 문의 본문(주소·번호 등 자유 입력)이 기간 없이 남았다 → 소비자 불만·분쟁처리 기록으로 탈퇴 + 3년 보관 뒤 삭제(V038 `inquiries.purge_after`, 탈퇴 트랜잭션에서 적고 매일 04:30 `InquiryRetention` 이 지운다, 이미 탈퇴한 사용자도 채움). 문의 등록은 탈퇴와 줄을 선다. 고객 개인정보 동의 문구 '보유 기간: 회원 탈퇴 시 또는 관계 법령이 정한 기간' 과는 맞는다(최종 문안은 법무 검토). 매니저 동의 문구('매니저 탈퇴 시까지')는 어긋났다 → 아래 '#31 후속'(V039, 사용자 결정: 그대로). IT 추가(미실행)
- 정리: 58건 모두 처리 — 55건은 `49842c9`…`18f3df5`, 판단 3건은 사용자 결정(2026-09-27)대로 #25 `6ad731d`·#31 `e175b93`·#16 처음엔 코드 변경 없음 → 후속 결정으로 `f45d13b`·`4faae52`(시작 시각이 근무시간 [시작, 종료] 안). 새 마이그레이션 V035(FAQ)·V036(매칭 실패 문구)·V037(지각 안내 시각)·V038(문의 보관 기한), 후속 V039(매니저 동의 문구). 새 관리 API 2개(`POST /admin/jobs/{id}/complete`, `POST /admin/bookings/{id}/redispatch`). 새 오류 코드 `PASSWORD_CHANGED`. IT 추가·미실행(Docker 없음): JobFlowIT 2(운영자 완료·지각 안내), MatchingIT 2(운영자 재매칭·늦게 끝나는 예약), SupportIT 2(문의 보관·답변 경합), CatalogIT 1행(매니저 동의 문구)
- 후속 검토(2026-09-27, 판단 3건 반영 뒤 적대적 검토 14 에이전트 — 확인 3·반박 7):
  - [x] 앱을 켜 둔 고객에게는 푸시가 OS 배너로 뜨지 않아(presentationOptions 없음), 카드에 흔적이 없는 지각 안내가 통째로 사라졌다 → 고객 앱 `capacitor.config.json` 에 `PushNotifications.presentationOptions`(sound·banner·list·alert), `cap:sync` 로 두 플랫폼에 복사. **실기기·에뮬레이터 확인 전**. 매니저 앱도 같은 설정(재검토: 일정·업무 상세는 새로 읽지 않아 '예약이 취소됐어요' 가 흔적 없이 사라졌다 — '폴링으로 보여 준다' 는 근거가 틀렸다)
  - [x] 서버가 멈췄다 따라잡아 보내면 '약속 시간이 30분 지났어요' 가 사실과 달랐다 → '30분 넘게 지났어요'
  - [x] 운영자 답변 저장이 모든 열을 다시 써, 읽은 사이 탈퇴가 적은 문의 보관 기한(purge_after)을 NULL 로 되돌릴 수 있었다 → `Inquiry` 에 `@DynamicUpdate`(바뀐 열만). IT 추가(미실행)
  - [x] 간헐 실패 2건: ① `Sheet` 가 최신 onClose 를 수동 효과로 들고 있어, 문구가 뜬 직후 ESC 면 한 렌더 전 함수가 불렸다(일시정지 실패 뒤 닫을 때 '영상 다시 켜기' 누락 — 실제 앱에서도 가능) → 레이아웃 효과로. 전체 테스트 21회 연속 통과(전에는 약 5회에 1번 실패) ② 1:1 문의 테스트가 입력칸(제어 textarea 는 값이 글자 내용에도 들어간다)을 목록으로 착각 → 목록 영역 안에서 찾게
  - [x] #16 후속 **결정(사용자 2026-09-27): 시작 시각만 근무시간 안이면 콜을 보낸다.** 매칭(`AvailabilityService.isAvailable`)이 작업 전체가 근무시간 안이어야 해서 기본값(09~18시) 매니저에게는 18시를 넘겨 끝나는 예약(가정 16·17시 등)의 콜이 아예 가지 않았다 → 시작 시각이 [근무 시작, 근무 종료] 안이면 콜(끝나는 시각은 넘어도 됨). 근무 불가·겹치는 작업은 작업 전체로 본다. 근무시간 변경 검사(`requireNoWorkOutside`)도 같은 기준. 재검토에서 처음엔 [시작, 종료) 로 해 마지막 슬롯 18:00 이 여전히 아무에게도 가지 않던 것(사용자에게 물은 예 '16·17·18시' 중 18시)을 종료 시각 포함으로 고쳤다. BACKEND_PLAN §6.2 본문도 같은 규칙으로(§13-6 행만 고쳐 같은 문서 안에서 규칙이 반대였다). IT 추가(미실행)
  - [x] #31 후속 사용자 답 '동의서 문구에서 빼줘' → 매니저 개인정보 동의의 보유 기간 '매니저 탈퇴 시까지' 가 탈퇴 뒤 문의 3년 보관과 어긋났다 → V039 '보유 기간: 매니저 탈퇴 시까지 (1:1 문의 기록은 분쟁 처리를 위해 탈퇴 후 3년 보관)'(version 1→2, 코드에서 정함). **'탈퇴 시까지' 는 빼지 않고 예외를 덧붙였다** — 보유 기간 항목은 개인정보 보호법 §15② 상 알려야 하고 문의 말고는 탈퇴 때 지우므로 원칙 + 예외가 사실에 맞다(처음 기록에 '뺐다' 고 잘못 적었다, 재검토). **사용자 결정(2026-09-27): 그대로.** 앱 mock 도 같게, mock-shapes `term-manager-privacy` 로 서버 문구와 비교(CatalogIT 미실행 — 로컬 서버 응답이 mock 과 같음을 확인)
  - [x] 간헐 실패 ③ '청소 완료 뒤 끝난 세션에 start 를 다시 보내지 않는다' — 완료 → 영상 정지 → 시트 닫기 → 이동 흐름이 CPU 를 나눌 때 기본 대기 1초를 넘겨 '완료 화면' 을 못 찾았다(실패 순간 화면은 '촬영 종료', 로직은 정상) → `vitest.setup.js` 에서 Testing Library `asyncUtilTimeout` 3초
  - 반박된 것: 카메라 문제로 시작이 막혀도 지각 안내가 나감(문구가 '늦어지고 있어요'라 사실과 어긋나지 않음), '다음 날 미방문 정리' 표현(→ 문서만 '방문일 이틀 뒤 03:10' 으로 고침), #16 전제가 코드와 다름·매니저 문의 3년(→ 다시 물어 #16 후속·#31 후속으로 반영)
- [x] **탈퇴 + 5년 파기(사용자 결정 2026-09-27: 만든다)** — BACKEND_PLAN §13-22 결정(5년)의 삭제 작업이 없어 탈퇴한 사람의 예약 주소·요청 등이 기한 없이 남았다 → `auth/DeletedAccountPurger` 매일 04:20. **지울 범위(코드에서 정함)**: 행을 지우지 않고 개인정보 칸만 비운다 — 예약·정산·결제 행은 상대방 이력과 법정 기록이 가리킨다. 고객: 예약·정기청소 주소(시도·시군구만)·상세 주소·요청·주의사항·수리 상세·사진 경로·자유 입력 취소 사유(→ customer)·상태 이력의 고객 사유, 결제 수단. 매니저: 근무시간·근무 불가·패널티·신원/교육/보험 기록. 공통: 약관 동의, 남은 문의. V040 `profiles.purged_at`. 기능별로 `auth.AccountPurge`(예약·결제) 또는 서비스 직접 호출(프로필·일정·지원) — ArchUnit 순환 없음. IT `DeletedAccountPurgeIT` 추가(미실행)
- [x] **자동 재매칭(사용자 결정 2026-09-27: 만든다)** — 매칭 실패 뒤에는 고객 일정 변경·운영자 API 말고 다시 찾는 길이 없었다 → `offer/MatchingRetry` 매시 05분(08:05~20:05, 밤에는 매니저에게 '새 콜' 이 가지 않게). **규칙(코드에서 정함)**: 약속 3시간 전까지인 매칭 실패 예약에, 이 예약의 콜을 한 번도 받지 않은 새 후보(새로 승인·근무시간·지역 변경, 방문 취소한 매니저 제외)가 생겼을 때만 새 회차로 그들에게 보낸다 — 이미 받았던 매니저에게 되풀이하지 않고, 새 후보가 없으면 아무것도 하지 않는다(고객 실패 알림 반복 방지). 문구도 맞춤: 푸시 '매니저를 찾지 못했어요 · 가능한 매니저가 생기면 다시 찾아볼게요. 일정을 바꾸면 바로 다시 찾아요', 카드 '매니저를 찾지 못했어요 · 가능한 매니저가 생기면 다시 찾아요'(V041, 코드에서 정함). IT 추가(미실행)
- 검토(2026-09-27, 5년 파기·자동 재매칭 뒤 적대적 검토 15 에이전트 — 확인 8(중복 정리 5)·반박 3):
  - [x] 고객 취소 이벤트(BookingCancelled)에 자유 입력 사유가 원문으로 실려, 지우지 않는 아웃박스 원장에 남아 5년 파기를 비켜 갔다 → 사유 코드만 싣고(푸시는 not_started 인지만 본다) 쌓인 행은 V042 로 바꾼다
  - [x] 매니저 동의 '보유 기간: 매니저 탈퇴 시까지'(V039, 사용자 결정 '그대로')의 근거로 '문의 말고는 탈퇴 때 지운다' 고 했지만, 매니저의 근무시간·근무 불가 사유·패널티·신원/교육/보험 기록은 5년 파기까지 남았다(내 잘못된 설명) → 결정이 사실이 되도록 **탈퇴 때 지운다**(`AuthService.deleteAccount`·`ManagerProfile.withdraw`). 파기는 그 전에 탈퇴한 계정 몫
  - [x] 자동 재매칭이 이 예약의 모든 오퍼를 '받은 적' 으로 세어, 옛 일정의 콜만 받은 매니저나 다른 매니저 수락으로 콜이 닫힌 매니저까지 영영 뺐다 → 지금 매칭 구간(일정 변경·매니저 방문 취소 때 새로 시작, V043 `matching_from_round`)의 오퍼만 센다
  - [x] 매칭 실패 푸시·카드가 자동 재매칭이 볼 수 없는 예약에도 '가능한 매니저가 생기면 다시 찾아요' 라고 약속했다(예: 21시에 실패한 다음 날 10시 예약 — 다음 실행 08:05 + 3시간이 약속 뒤, 일정 변경도 24시간 안이라 불가) → `booking/MatchingRetryWindow` 로 가능한 길을 계산해 문구 4가지 중 고른다(코드에서 정함), 카드는 못 보면 V044 '매니저를 찾지 못했어요 · 1:1 문의로 알려주세요'. cron 과 계산이 어긋나지 않게 테스트로 묶었다. runbook 자동 재매칭 조건(약속 3시간 전·밤 제외)도 정확히
  - [x] 마지막 확인(6 에이전트, 확인 3): ① 탈퇴와 동시에 들어온 근무 불가 추가·근무시간 변경이 탈퇴 뒤 커밋되면 사유가 남았다 → 두 쓰기도 탈퇴와 줄을 선다(`holdLive`, 문의·푸시 등록과 같다) ② 거절·만료 뒤 일반 재매칭(`redispatch`)은 여전히 모든 구간의 오퍼를 셌다 → 자동 재매칭과 같게 지금 구간만 ③ 예약 상세·생성 응답의 매니저 칸은 매칭 실패여도 '배정 후 알려드려요' 였다 → 목록과 같은 안내
  - 반박된 것(메모로 남김): 하루 100건 파기 상한(설계대로, 대상이 쌓이면 다음 날 이어서), 매니저 파기 단위 테스트가 약함(→ IT 에서 남은 기록을 실제로 비우는지 보게 보강), **결제 수단(빌링키)은 지금 탈퇴 때가 아니라 5년 파기 때 지워진다** — PG 연동 전이라 행이 생기지 않아 지금은 문제 없음. **PG 를 붙일 때 탈퇴 트랜잭션에서 빌링키를 바로 지울 것**(PG 쪽 빌링키 해지는 외부 효과라 아웃박스로)
- 재검토(2026-09-27, #16·#31 후속 반영 뒤 사용자 요청 '수정 후 다시 검토' — 적대적 검토 10 에이전트, 발견 6·확인 3): ① #31 후속 기록이 '뺐다' 였는데 실제 문구는 예외를 덧붙인 것 → 기록을 고치고 사용자 확인 항목으로 ② BACKEND_PLAN §6.2 본문이 옛 규칙('작업 구간이 [start,end] 안') → 새 규칙으로 ③ 매니저 앱도 앞에 떠 있을 때 푸시가 안 보였다 → 같은 presentationOptions. 반박됐지만 고친 것: 마지막 슬롯 18:00 이 기본 근무시간 매니저에게 여전히 안 가던 것 → 종료 시각 포함. 반박: PROGRESS 옛 서술(→ 이번에 정리)

## 코드 주석 (2026-09-27~28, 사용자 요청 "나중에 내가 보고 이해하기 쉽게 모든 코드에 주석 — 코드는 절대 수정하지 마")
- [x] 손으로 쓴 코드 1,102개 파일(앱·공용 UI·스크립트·설정·서버·서버 테스트)에 주석을 달았다. 57개 묶음마다 작성 에이전트 → 검토 에이전트(코드와 대조) → 2차 검토(외부 평가 반영) → 주제별 파일 간 일관성 점검 10개.
  - 손대지 않은 것: Flyway 마이그레이션(`db/migration/*.sql` — 한 글자만 바뀌어도 체크섬 검사로 서버가 멈춘다), 생성물(`tokens/tokens.css`·`typography.css`·`tokens.js`), Capacitor 네이티브(`apps/*/ios`·`android`), Maven 래퍼.
  - 코드가 바뀌지 않았다는 확인: JS/JSX 는 구문 트리(JSX 텍스트는 React 공백 규칙으로 정리), CSS 는 postcss 구조, Java 는 주석을 뺀 토큰, XML·HTML 은 주석을 뺀 토큰, YAML 은 주석 줄만 허용 + 모든 파일에서 기존 줄의 코드 부분이 글자 그대로 남았는지. 1,102개 모두 통과.
  - 공통 개념(Spring 계층·트랜잭션과 그 예외·잠금·아웃박스·멱등 키·CSS Modules·useAsync 등)은 새 문서 `CODE_GUIDE.md` 에 한 번만 적고, 파일에는 그 파일만의 역할·이유·조건·실패 시 동작을 적었다 (외부 평가 반영: 반복 설명 609곳 정리, 금방 낡는 개수·사용처 목록 390곳 정리).
  - 기존 주석 중 코드와 확실히 다른 것 207곳을 고쳤다 (예: 스케줄러 '7개' → 지금은 13개라 개수 대신 BACKEND_PLAN §2.5 로, 고객 앱 푸시 구독 사용처, 폭 393 고정 시절의 '345px'). 확실하지 않아 그대로 둔 후보가 104곳 있다.
- 주석을 달다가 드러난 **코드끼리 정책이 다른 곳**은 코드를 고치지 않고 두 곳 주석에 `주의:` 로 적었다. 보고 81건을 합쳐 43건, 건마다 코드로 검증 → 실제 41건. 그중 라이브 3건은 아래 '두 앱 사이 동작 수정' 1번에서 고쳤고, **남은 38건(중간 4·낮음 34, 결정 필요 9, 통합 테스트 불일치 6)은 `audit/CODE_ISSUES_2026-09-28.md`** — 사용자 결정 대기

## 두 앱 사이 동작 수정 (2026-09-28, 외부 평가 → 사용자 결정 "주석 뒤 3건 모두 수정", 1번은 "매니저가 재개할 때까지 차단 유지")
- [x] **1. 영상 일시정지 뒤 재접속하면 영상이 다시 나감(높음)** — 영상 차단 ①(live/pause)은 됐는데 작업 일시정지 ②(jobs/pause)가 실패한 채 바디캠이 끊겼다 다시 붙으면, 재접속 처리(cameraAttached)가 작업 상태(진행 중)만 보고 새 트랙을 막지 않고 고객 구독·새 시청 토큰까지 열었다(재조정기는 같은 상태를 '막아 둔다' 로 다뤄 두 곳이 반대). 바디캠 없이 ① 을 누른 뒤 처음 붙는 경우도 같았다.
  - `live_sessions.video_held`(V045): ① 이 매니저에게 200('멈췄다')으로 답할 때 켜고, 502('계속 전송') 로 답하거나 매니저가 재개(live/resume·jobs/resume)하면 끈다. 모든 판단이 `LiveService.holdsVideo`(작업 일시정지 ∨ video_held) 하나를 쓴다 — 재접속·재조정기(mute·unmute·Ingress 잠금·기기 잠금 정리)·웹훅(끊김 잠금)·고객 상태 문구('paused')·구독 차단·시청 토큰·라이브 재시작의 잠금 해제.
  - 재조정기의 재개 판단에서 시각 비교 짐작(jobPausedSinceVideoPause)을 뺐다 — 일시정지 중 재접속으로 다시 mute 되면 재개한 뒤에도 영영 막혔다. 매니저가 멈춘 채면 바디캠 끊김·5분 대기 장애 푸시를 보내지 않는다. live/resume 은 예약 → 세션 순서로 잠그고 읽는다(동시 jobs/pause 뒤 옛 상태로 영상을 켜던 틈).
  - 매니저 앱: GET /live/sessions/{id} 가 `held` 를 준다. 작업이 진행 중인데 held 면 진행 화면이 기존 '영상은 중단됐지만 작업 상태를 바꾸지 못했어요' 시트를 다시 띄운다(다시 시도 또는 닫기 = 재개) — 닫을 때의 재개가 실패했거나 앱이 꺼졌다 다시 열려도 빠져나갈 길이 있게. ① 을 한 번이라도 보낸 시트를 닫으면(응답을 잃은 경우 포함) 재개를 보낸다.
  - 검증: 새 테스트(서버 16·앱 2)가 고치기 전 코드에서 실패하는 것을 확인했다. 적대적 검토 1회 뒤 찾은 것(시청 토큰, 502 뒤 무기한 차단, 재개 수단 없음, resume 잠금, 장애 푸시)을 모두 반영했다.
  - V045 백필: dev DB 에 해당 행이 0개였고 운영 DB 는 새로 만든다. 백필 조건은 '지금 paused 인 세션' 만 보므로(카메라가 끊겨 waiting_camera 가 된 세션은 구분할 수 없다) 배포 당시 진행 중이던 건은 옛 동작을 따른다 — 파일은 dev 에 이미 적용돼 고치지 않았다.
- [x] **2. 상대 앱의 변경이 열린 화면에 안 보임(중간)** — 매니저 일정·업무 상세, 고객 홈이 화면을 열 때 한 번만 받았다(고객이 취소·일정 변경해도 옛 카드, 매니저가 배정돼도 '배정 중'). 셋을 `usePolling`(30초 + 푸시 + 앱 복귀)으로 바꿨다.
  - 적대적 검토 반영: `usePolling` 이 대상(deps)이 바뀔 때 옛 대상으로 나가 있던 폴링 응답을 버린다(/job/A → /job/B 뒤 A 가 B 화면을 덮던 틈) · 실패를 화면이 받을 수 있게 `onError` — 업무 상세는 고객 취소(409)·일정 변경(404)이면 서버 문구의 오류 화면으로 바꾼다(조용한 갱신이 오류를 버려 옛 버튼이 남았다) · 업무 상세 사진은 폴링마다 새 서명 URL 로 다시 내려받지 않게 한 시간 동안 처음 URL 을 쓴다 · 일정의 달력 조회는 달력 탭일 때만 다시 받는다.
- [x] **3. 매니저가 멈췄는데 고객 화면이 다시 '실시간'(중간, 표시만)** — 일시정지 전에 나간 30초 폴링 응답이 늦게 오면 더 새 '일시정지' 를 덮었다. 요청 뒤에 트랙 이벤트가 왔으면 그 응답을 버리고, 이벤트의 확인(서버가 따라올 때까지 1초 간격)이 끝나기 전에는 이벤트 뒤에 나간 보통 폴링도 상태를 덮지 않는다(적대적 검토 반영). 테스트용 가짜 서버에 '한 응답만 늦게 도착' (holdOnce)을 더했다.

## 코드 문제 38건 수정 (2026-09-28, 사용자 결정 "추천대로 전부 고쳐줘" — 목록 `audit/CODE_ISSUES_2026-09-28.md`)
- 결정이 필요했던 9건은 이렇게 했다: 2번 밤(서울 21시~08시)에 생긴 새 콜은 아침 08:05 에 보내고 정기 회차 매칭은 09:00 으로(급한 예약·운영자 재매칭은 바로) · 7번 수락 때 근무 일정을 다시 검사해 막음 · 10번 닫힌 콜 거절은 닫힌 이유대로 오류 · 13번 완료 화면 '총 소요' 는 벽시계로 둠 · 14번 경고(3점)·정지(5·8점) 전용 알림(푸시 설정과 무관) · 15번 일정 변경의 우선 콜은 콜 푸시 없이 일정 변경 푸시만 · 16번 5분 대기 푸시 문구를 시점과 무관하게 · 18번 세로 녹화본 컨트롤은 공용 컴포넌트의 밝은 배경 tone · 27번 면제된 취소도 다음 회차 우선 제안에서 뺌(정책 문서대로).
- 7개 묶음을 각자 격리 작업 폴더에서 고치고(항목마다 고치기 전 실패하는 테스트 확인) main 에 합쳤다. 항목별 기록은 아래.
- [x] 13번: 완료 화면 '총 소요' 는 벽시계로 둔다 — 코드 변경 없이 결정과 주석·domain.md 를 맞췄다.
- [x] 15번: 1회 예약의 일정을 바꾸면 원래 매니저에게 '단골 고객의 정기청소 콜' 푸시가 '예약 일정이 변경됐어요' 와 함께 갔다. 일정 변경의 우선 콜은 콜 푸시(OffersDispatched)를 적지 않게 해 일정 변경 푸시 하나가 콜 목록으로 안내한다. 정기 다음 회차의 우선 콜 푸시는 그대로다 (문제 목록 15번, 결정 A).
- [x] 10번: 닫힌 콜을 거절해도 서버가 200 을 돌려줘 앱의 마감 안내가 나오지 않았다. 이미 거절한 콜만 200(저장된 사유), 내가 수락한 콜은 INVALID_STATE, 다른 이유로 닫힌 콜은 OFFER_TAKEN, 만료는 OFFER_EXPIRED 로 답하고, 앱 거절 시트는 서버 문구를 보이게 했다(가짜 서버·api.md 같이, 마감 분기는 테스트로 확인) (문제 목록 10번, 결정 B). 코드에서 정한 문구: "이미 수락한 콜이에요. 일정에서 확인해주세요"
- [x] 7번: 콜을 받은 뒤 그 시간에 휴무·근무 불가를 넣어도 수락이 그대로 됐다. 수락 때 매칭 후보 판정과 같은 기준(AvailabilityService.isAvailable)으로 다시 보고 걸리면 INVALID_STATE 로 거절하며, 앱 콜 목록·상세는 이 문구를 보이고 카드를 남긴다(테스트로 확인) (문제 목록 7번, 결정 ①). 코드에서 정한 문구: "근무 일정과 맞지 않는 시간이라 수락할 수 없어요. 근무 일정 관리에서 확인해주세요"
- [x] 2번: 카드가 '근무 시간 외' 인 밤에도 콜·푸시가 가고 밤 콜이 만료돼 매칭 실패로 끝나기 쉬웠다. 밤(서울 08시 전·21시부터, MatchingRetryWindow)에는 새 콜을 보내는 자동 경로가 예약에 보류 표시(V046)만 남기고 아침 첫 MatchingRetry(08:05)가 우선 콜부터 보내며(일정 변경 안내도 그때), 미루면 약속까지 3시간이 안 남는 예약과 운영자 재매칭은 바로 보내고, SeriesDispatcher 는 09:00 으로 옮겼다 (문제 목록 2번, 결정 ③).
- [x] 6번: 정지 전에 받은 콜이 남아 수락할 수 없는 카드가 보이고 그 콜 때문에 재매칭이 늦어졌다. OfferExpirer 에 정지·미승인·탈퇴 매니저의 대기 콜을 닫고 재매칭하는 단계(밤이면 아침으로)를 더하고, 콜 탭·홈은 그런 매니저에게 콜 카드를 싣지 않아 앱이 정지·승인 대기 안내 카드를 그린다 (문제 목록 6번).
- [x] 5번: 매니저 방문 취소 뒤 후보가 없으면 '못 찾았어요' 와 '다른 매니저를 찾고 있어요' 가 함께 갔다. 재매칭이 곧바로 매칭 실패로 표시됐으면 ManagerReleased 를 적지 않고, 밤이라 아침으로 미룬 경우는 그대로 '찾고 있어요' 를 보낸다 (문제 목록 5번).
- [x] 6번: 6번 수정 뒤 CallsResponse·HomeResponse 주석이 예전 설명이라, 정지·미승인 매니저에게는 콜 탭 calls 가 비고 홈 '새로운 청소 요청' 이 null 이라고 고쳤다 (문제 목록 6번 후속).
- [x] 12번: 반복 없음인데 지난 날짜로 근무 불가를 넣으면 겹침 검사가 한 주 뒤(오늘 이후 첫 같은 요일)를 봐서, 막지도 않을 날의 작업 때문에 409 가 났다. 이제 지난 날짜의 1회성은 겹침 검사를 건너뛰고 그대로 저장한다(AvailabilityService.requireNoWorkIn, 단위 테스트 추가) (문제 목록 12번).
- [x] 26번: 지각 패널티가 늦은 시간을 분 단위로 버려 31분부터 기록돼, 30분 0초~59초에 시작하면 고객은 지각 안내를 받았는데 0.5점이 남지 않았다. 이제 시작 시각을 약속 + tardyMinutes 와 초 단위로 비교해 고객 안내(LateStartNotifier)와 같은 기준으로 맞췄다(30분 0초·30분 30초 경계 테스트, domain.md·주석 수정) (문제 목록 26번).
- [x] 3번: 면제 API 가 없어 운영자가 SQL 로 status 만 'waived' 로 바꾸면 점수는 빠져도 콜 배정 정지가 남았다. X-Admin-Token 관리 API POST /admin/penalties/{id}/waive({ note })를 만들어 점수와 정지 해제를 한 트랜잭션에서 바꾸고 { penaltyId, managerId, points, suspendedUntil } 를 돌려준다(이미 면제면 그대로, 회복·만료 행은 409, 기록과 같은 매니저 잠금). api.md·runbook(SQL 대신 관리 API)·BACKEND_PLAN 수정, 서비스 단위·@WebMvcTest·PenaltyWaiveIT(IT 미실행, 컴파일만) (문제 목록 3번). 코드에서 정한 문구: "무사고 회복 기록은 면제할 수 없어요" / "기간이 끝난 기록은 이미 점수에 들어가지 않아요"
- [x] 27번: 다음 회차 우선 배정 검사(droppedSeries)가 적용 중인 취소 기록만 봐, 운영자가 면제하면 한 번 취소한 매니저에게 다음 회차 우선 콜이 다시 갔다. 사용자 결정 A 대로 기록 상태(면제·만료)와 관계없이 세어 정책 '한 회차라도 취소하면 우선 제안에서 빠진다' 와 같은 예약 후보 제외 규칙에 맞췄다(면제된 취소 단위 테스트 + PenaltyWaiveIT 사례, IT 미실행) (문제 목록 27번).
- [x] 14번: 패널티 내역 화면·정책 문서가 '3점 = 경고 알림' 을 약속했지만 서버는 일반 '패널티가 기록됐어요' 만 보냈고 정지 전용 알림도 없었다. 사용자 결정 A 대로 ManagerPenalized 에 이번에 넘은 단계(정지 마지막 날, 경고면 다음 정지 단계 점수·일수)를 싣고, 경고·정지 전용 푸시를 기록 한 건에 하나만 매니저 알림 설정과 무관하게 보낸다(예전 이벤트는 일반 알림) (문제 목록 14번). 코드에서 정한 문구: "패널티 경고" / "누적 {N}점이에요. {다음 정지 점수}점이 되면 {일수}일 동안 콜 배정이 정지돼요" / "누적 {N}점이에요. MY › 패널티 내역에서 확인해주세요" / "콜 배정이 정지됐어요" / "누적 {N}점으로 {M월 D일}까지 새 콜을 받을 수 없어요. 이미 수락한 방문은 그대로 진행해주세요"
- [x] 4번: 미첨부 수리 사진 정리(매일 03:40)는 한 장의 삭제가 실패하면 그 자리에서 멈췄습니다. 그래서 매일 실패하는 한 장 뒤에 있는 모든 고객 사진이 24시간이 지나도 남을 수 있었습니다. 이제 녹화본 정리처럼 한 장씩 예외를 잡아 경고 로그만 남기고 넘어갑니다. 후보는 아직 잡지 않은 행을 오래된 순으로 먼저 읽고, '삭제 중' 행은 뒤로 보냅니다(RepairPhotoUploadRepository.findCleanupCandidates). 첫 실패에서 멈추던 동작을 굳혀 둔 테스트는 새 동작에 맞게 바꿨습니다 (문제 목록 4번).
- [x] 21번: Storage 서명 주소 함수는 404와 빈 응답에 녹화 영상용 문구를 던졌고, 이 문구가 수리 사진 업로드 응답과 매니저 작업 상세 로그에도 나왔습니다. 이제 이 함수는 NOT_FOUND·UPSTREAM 기본 문구만 던집니다. 녹화본(LiveService.recording)에서는 404를 RECORDING_NOT_READY로 바꾸고, 수리 사진 업로드에서는 기존 문구 '사진을 올리지 못했어요. 잠시 후 다시 시도해주세요'로 바꿔 던집니다 (문제 목록 21번).
- [x] 30번: 설정 누락처럼 서비스가 직접 던진 ApiException(INTERNAL) 응답에는 문의 번호와 requestId가 없었습니다. 이제 예상하지 못한 예외와 같은 본문('잠시 후 다시 시도해주세요 (문의 번호 xxxxxxxx)' + requestId)으로 답하고, 서비스가 적은 문구는 로그에만 남깁니다. api.md §5.0의 서로 다른 두 설명과 BACKEND_PLAN §5.0을 하나로 맞췄습니다 (문제 목록 30번).
- [x] 16번: 바디캠 5분 대기 푸시 본문 '작업을 시작한 지 5분이 지났어요'는 작업 중 끊긴 뒤나 재개한 뒤에 보낼 때 사실과 달랐습니다. 결정한 안 ①대로 본문을 기준 시점과 관계없는 문구로 바꾸고 제목은 그대로 뒀습니다. domain.md·BACKEND_PLAN §8.1의 '운영자 로그 WARN 만'은 실제 동작(대기 구간마다 매니저 푸시 1회 + WARN)에 맞게 고쳤습니다 (문제 목록 16번). 코드에서 정한 문구: "바디캠 5분 대기 푸시(PushCopy.CAMERA_WAITING) 본문: "5분 넘게 연결되지 않았어요. 바디캠 전원과 네트워크를 확인해주세요" (제목 "바디캠이 아직 연결되지 않았어요"는 그대로)"
- [x] 28번: 가입 완료는 프로필을 잠그지 않고 읽어 저장했습니다. 그래서 겹친 탈퇴가 익명화를 커밋한 뒤 이름·번호·탈퇴 표시가 옛 값으로 되돌아갈 수 있었습니다. 이제 첫 조회를 탈퇴와 같은 행 잠금(lockLive, FOR UPDATE)으로 해서 둘이 줄을 서고, 탈퇴가 먼저면 아무것도 쓰지 않고 NOT_FOUND로 끝납니다. 두 번째 방어로 Profile에 @DynamicUpdate를 붙였습니다 (문제 목록 28번).
- [x] 29번: 운영자의 바디캠 등록은 매니저를 잠그지 않고 확인했습니다. 그래서 겹친 탈퇴의 미디어 정리가 끝난 뒤에 탈퇴자의 Ingress와 스트림 키가 든 기기 행이 남을 수 있었습니다. 이제 탈퇴와 부딪치는 공유 잠금 조회(ProfileService.holdLiveIfPresent, FOR SHARE)로 확인합니다. 결과를 Optional로 받기 때문에 운영자 API에서 401이 아니라 기존 오류 '매니저를 찾을 수 없어요'로 끝납니다 (문제 목록 29번).
- [x] 9번: 예약 상세(GET /customer/bookings/{id})가 매니저 칸을 목록 카드와 따로 계산해, 매니저 없이 취소된 예약을 다시 열면(예약 완료 화면) '배정 전 취소' 대신 배정 전·매칭 실패 문구가 나왔다. 매니저 칸 규칙을 BookingMapper.managerLabel 한 곳으로 모아 목록과 상세가 같이 쓰게 하고 api.md 에 적었다 (문제 목록 9번).
- [x] 8번: 후보가 없어 예약 직후 매칭에 실패해도 수리 완료 화면은 Figma '배정 중' 과 '기사 배정이 완료되면 알려드릴게요' 를 그렸다. 예약 생성·상세 응답에 matchingFailed(bookings.matching_failed_at 여부)를 더하고(api.md → 서버 → 앱), 완료 화면은 배정됐거나 실패면 서버 cleaner 문구를 보이고 실패면 🔔 배정 완료 안내를 숨긴다 — 매칭이 멈춘 뒤라 사실과 다르고 다음 할 일은 실패 안내가 말한다 (문제 목록 8번, 코드에서 정함).
- [x] 25번: 잠긴 카드·정기 회차 취소 시트·주기 화면·거절 문구·FAQ 가 '24시간'·'6개월 6회' 를 고정 글자로 말해 운영자가 cancel_deadline_hours·series_term_months 를 바꾸면 안내가 틀렸다. 홈·내 예약 응답에 changeDeadlineHours, confirm-copy 응답에 seriesTermMonths 를 더해 앱이 그 값을 쓰고(없을 때만 24·6), 거절 문구는 설정 시간을 넣어 던지며 ErrorCode 기본 문구와 FAQ(V049)는 숫자 없는 문구로 바꿨다 (문제 목록 25번 안 ①). 코드에서 정한 문구: "취소할 수 있는 기한이 지났어요" / "변경할 수 있는 기한이 지났어요" / "방문 N시간 전까지만 취소할 수 있어요 (N = cancel_deadline_hours, 기존 문구에 설정 값을 넣음)" / "방문 N시간 전까지만 변경할 수 있어요 (N = cancel_deadline_hours, 기존 문구에 설정 값을 넣음)" / "내 예약 탭에서 예약 카드의 예약 변경 · 예약 취소 버튼으로 할 수 있어요. 방문이 가까워 기한이 지나면 버튼 대신 기한 안내가 보이고, 그 뒤에는 고객센터로 문의해주세요." / "N시간 안에 시작하는 방문은 취소할 수 없어요. (CANCEL_COPY.seriesNote, 기존 문구에 서버 값을 넣음)" / "정기청소는 매월 같은 날짜·같은 시간에 한 번씩, N개월 동안 N회 방문해요. 방문일이 주말이면 다음 평일로 옮겨요. 회차마다 따로 변경·취소할 수 있어요. (RECURRING_TERM_NOTE, 기존 문구에 서버 값을 넣음)"
- [x] 1번: 방문 취소 시트의 상세 입력란이 고객센터 문의 상한(2000자)을 같이 써서 501자 이상이면 서버(ManagerCancelRequest @Size(max = 500))가 400 으로 거절해 시트가 닫히지 않았다. INPUT_LIMITS 에 jobCancelDetail(500)을 따로 두고 입력란 maxLength 를 그 값으로 바꿨다(서버 DTO 주석·api.md 도 정리) (문제 목록 1번).
- [x] 11번: 서버는 일시정지를 'paused' 로 따로 주는데 업무 상세는 inProgress 일 때만 하단 버튼을 그려, 일정 탭에서 일시정지된 작업을 열면 재개 화면으로 갈 버튼이 없었다. paused 에도 같은 '업무 화면으로 돌아가기' 를 보이고 홈 카드처럼 /job/paused 로 보낸다 (문제 목록 11번).
- [x] 37번: DEV 가짜 서버 모드(VITE_API_MOCK=1)에서 가짜 서버가 따로 정의한 ApiError 를 던져 두 앱 manager.js·customer.js 의 instanceof ApiError 검사가 늘 거짓이었다(틀린 비밀번호가 로그인 실패 안내 대신 일반 오류 등). 두 앱 http.js 의 가짜 분기에서 그 오류를 http.js 의 ApiError 로 감싸 던지게 했다. 순환 import 는 두지 않았다 (문제 목록 37번).
- [x] 17번: 토큰과 refresh 가 모두 401 이면 앱은 세션만 지우고, 로그인이 필요한 DELETE /push/token 은 보낼 수 없어서 로그아웃된 폰에 앞 사용자의 알림이 계속 왔다(두 앱). 두 앱 pushClient 가 로그인에서 로그아웃으로 바뀌는 순간을 알아보고, 의도한 로그아웃과 같은 길(forgetDevice)로 기기 등록을 풀고(unregister) 토큰을 버린다. 다음 로그인 때는 register() 로 새 토큰을 받아 등록한다 (문제 목록 17번).
- [x] 18번: 세로 녹화본 화면에서 진행 막대의 흰 손잡이·시간 글자와 원 버튼의 흰 포커스 테두리가 흰 배경에 묻히던 문제를, LiveProgress·LiveCircleControl 에 tone prop(onVideo 기본 | onLight)을 더해 밝은 배경에서는 토큰 색(트랙 border/default, 손잡이 text/primary, 시간 text/secondary, 포커스 brand/primary)을 쓰게 하고 세로 화면이 onLight 를 넘기도록 고쳤다. 갤러리에 새 tone 줄 추가. 판단 항목: onLight 색은 Figma 에 없어 코드에서 정함 (문제 목록 18번, 결정 A)
- [x] 19번: 주간 달력(WeekCalendar)이 7칸·주 이동을 24시간 밀리초 덧셈으로 계산해 서머타임이 끝나는 날을 지나면 '월' 칸에 일요일이 들어가던 문제를, new Date(연, 월, 일 + n) 의 addDays 로 바꿔 고치고 미국 동부 시간대로 11/1 전환일을 지나는 테스트를 더했다 (문제 목록 19번)
- [x] 20번: 수리 사진 미리보기 주소·서버 보관(24시간)이 끝나도 앱이 몰라 썸네일만 깨지고 '예약하기' 에서야 막히던 문제를, 사진에 올린 시각(uploadedAt)을 저장해 증상 상세가 24시간 지난 사진을 빼고 서버와 같은 만료 문구를 보이며, 확인 화면은 지난 사진이 있거나 서버가 만료 오류(VALIDATION+만료 문구)를 주면 사진을 빼고 증상 상세로 보내도록 고쳤다. 판단 항목 ⑪ '만료되면 다시 첨부' 를 화면이 실제로 한다 (문제 목록 20번) 코드에서 정한 문구: "첨부한 사진이 만료됐어요. 사진을 다시 올려주세요 (서버 RepairPhotoService.PHOTO_EXPIRED 와 같은 문구를 앱 mock.js REPAIR_PHOTO_EXPIRED 로 두고 증상 상세 사진 칸 아래에도 보인다)"
- [x] 22번: 로그인 화면 방 일러스트가 전화번호 칸 이름표 위에 그려지던 문제를, 제목·입력칸 묶음(.stack)에 position relative + z-index 를 줘 가입 화면과 같은 방식으로 고쳤다. 겹친 그림 위 글의 층은 extra.css 새 토큰 --z-raised(1)로 정하고 가입 화면·재설정 링크도 이 토큰을 쓴다 (판단 항목: extra.css 에 --z-raised 추가) (문제 목록 22번)
- [x] 23번: 가입 완료 화면의 실패 문구가 852px 이미지 밑에 붙어 짧은 폰에서 스크롤해야 보이던 문제를, ActionError 에 className={styles.error} 를 넘기고 규칙을 position: fixed(보이는 화면 아래 24, 탭 통과)로 바꿔 고쳤다 — 틀 기준 absolute 로는 800px 폰에서 여전히 화면 밖이었다. 색 줄은 지워 공용 빨강을 따른다 (문제 목록 23번)
- [x] 24번: 예약 확인·날짜 화면의 실패 문구가 공용 ActionError 로 바뀔 때 className 이 빠져 위 여백·가운데 정렬을 잃었던 문제를, className={styles.error} 를 넘기고(위 여백은 CSS 순서와 무관하게 padding-top) 색 줄은 지워 고쳤다. 쓰이지 않던 auth·ProfileInfo .notice 와 repair·Reservations·settingsSub .error 규칙은 지웠다 (문제 목록 24번)
- [x] 38번: 수리 전용 예약 흐름(/repair/booking)으로 대체된 뒤 죽은 코드로 남은 청소 예약의 repairType 경로(확인 화면 '수리 종류' 행·계산, createBooking 의 repairType, 홈의 지우기 호출, 테스트 주입)를 지우고, api.md·BACKEND_PLAN 청소 예약 본문에서 repairType? 을 빼 '청소 예약은 보내지 않음' 으로 적었다(서버 DTO 필드는 수리 예약용으로 그대로). 옛 설명(검수 1차 6번)에 '수리 전용 흐름으로 대체' 를 덧붙였다 (문제 목록 38번)
- [x] 31번: BookingFlowIT 정기청소 테스트가 옛 '한 달 동안 매주'(회차 5개) 기준이라 첫 검사에서 실패했다. 매월 6회(9/18·10/19·11/18·12/18·1/18·2/18)와 방문 7일 전 매칭(10/11 은 0, 10/12 는 1), 기한 안 회차만 전체 취소(3건), 취소된 시리즈에 남은 requested 회차를 매칭하지 않는지를 매월 기준으로 다시 썼다. 운영 코드는 그대로, IT 미실행(문제 목록 31번)
- [x] 32번: 고객이 이미 취소한 1회 예약을 다시 취소하면 서비스·api.md 는 같은 카드(200)를 주는데 IT 만 409 를 기대했다. 200·취소 탭·'배정 전 취소' 를 확인하게 고치고, StaleBookingCleaner 가 먼저 취소한 예약은 409 INVALID_STATE 인 경우를 실제 DB 로 보는 IT 를 더했다. IT 미실행(문제 목록 32번)
- [x] 33번: 녹화본 서명 URL 이 6시간(21600초)으로 바뀌었는데 LiveSessionIT 스텁은 3600 이라 목이 null 을 돌려줬다. 스텁을 LiveService.RECORDING_URL_TTL_SECONDS 로 바꾸고 domain.md·BACKEND_PLAN.md·api.md 의 3600·'서명 URL 1시간' 도 6시간으로 고쳤다. IT 미실행(문제 목록 33번)
- [x] 34번: 라이브 정지는 바디캠이 이 방에 묶여 있을 때만 대기 방으로 옮기는데 IT 가 livekit.getIngress 를 정하지 않아 이동 검사가 실패했다. 정지 직전에 '이 방에 묶여 있음' 스텁을 넣고 domain.md·BACKEND_PLAN.md 의 완료(stop) 설명에 그 조건(다른 작업이 쓰거나 다른 방에 묶였으면 건드리지 않음)을 적었다. IT 미실행(문제 목록 34번)
- [x] 35번: 같은 매니저의 겹치는 두 콜 동시 수락에서 진 쪽 수락은 통째로 되돌려져 진 예약의 콜 2건이 대기로 남는데 테스트는 1건을 기대했다. 기대값을 2 로 고치고 이긴 예약(대기 0·scheduled)과 진 예약(a·b 대기·assigning)을 따로 본 뒤 b 가 진 예약을 수락하는 것까지 확인한다. IT 미실행(문제 목록 35번)
- [x] 36번: SupportIT 가 V029 로 지운 '추석 연휴 서비스 운영 안내' 를 첫 공지로 기대해 실패했고, 그 뒤 문의 멱등·푸시 토큰 검사는 실행되지 않았다. 기대값을 '실시간 청소 영상 기능이 추가되었어요'(2026.09.01)로 바꾸고 지운 공지가 목록에 없는지도 본다. IT 미실행(문제 목록 36번)
- 합친 38건을 다시 적대적으로 검토해 나온 18건(중복 합쳐 14건)도 고쳤다. 아래 R 번호는 그 검토 결과다 (IT 는 여전히 미실행 — 새 IT 는 컴파일만).
- [x] R1·R14: 밤에 일정을 바꿔 원래 매니저 안내를 아침으로 미룬 뒤 아침 전에 취소하면, 이제 그 매니저에게 '예약이 취소됐어요'(BookingCancelled)가 밤에도 바로 간다. 예전에는 아무 알림도 없었다. BookingService.cancelOne 이 보류를 지우기 전에 원래 매니저를 읽어 두고, 정기청소 우선 매니저 보류에는 보내지 않는다(합친 38건 검토 R1·R14).
- [x] R2·R17: 운영자 재매칭(redispatchByAdmin)이 밤에 미룬 일정 변경 예약을 다시 보낼 때, 원래 매니저도 일반 콜은 받지만 '새 콜' 푸시(OffersDispatched) 받는 사람에서는 빠진다. 대신 BookingRescheduled 하나로 안내해 15번 결정 A 와 같아졌다(합친 38건 검토 R2·R17).
- [x] R3: closeBlockedOffers 가 콜을 닫기 직전 하는 재검사가 이제 1차 캐시가 아니라 DB 의 지금 값을 본다(ProfileService.isApprovedManagerNow, 숫자 조회 countCallableNow). 그래서 잠그는 사이 면제로 정지가 풀린 매니저의 콜은 닫지 않는다. 캐시 순서를 흉내 낸 IT 는 실행하지 못했다(합친 38건 검토 R3).
- [x] R4: 패널티 기록 한 건이 여러 단계를 함께 넘으면(예: 7→10, 7.5→10.5점) 넘은 정지 단계 중 가장 긴 정지(30일)를 걸고 해지 심사 로그도 남긴다. 푸시 단계는 정지 > 경고 순으로 고른다. 예전에는 review 하나만 골라 정지와 정지 푸시가 모두 빠졌다(합친 38건 검토 R4).
- [x] R5: 운영자 면제(waive)는 이제 잠그기 전에 매니저 id 만 읽는다. 그 뒤 패널티 잠금 → 매니저 행 → 원장 행(FOR UPDATE) 순서로 잠그고 다시 읽은 행으로 판단한다. 그래서 같은 요청을 두 번 보내도 메모·시각을 덮어쓰지 않고, 그사이 탈퇴로 지워진 행은 404 가 된다(합친 38건 검토 R5).
- [x] R6: ManagerProfile 에 @DynamicUpdate 를 붙였다. 이제 청소 완료(incrementCompleted)와 운영자 면제(liftSuspension)가 겹쳐도, 전체 칸 UPDATE 가 풀린 정지나 완료 수를 옛 값으로 되돌리지 않는다. 겹침을 만든 IT 는 실행하지 못했다(합친 38건 검토 R6).
- [x] R7: 일반 패널티 푸시도 plainPoints 로 누적 점수를 적어, 이제 '누적 2.0점' 이 아니라 '누적 2점' 으로 보인다. 경고·정지 푸시, 패널티 내역 화면과 같은 글자다(합친 38건 검토 R7).
- [x] R9·R15: V050 으로 FAQ '매니저는 언제 배정되나요?' 답에 밤 문장을 끼웠고 mock.js f3 도 같은 글자로 맞췄다. 매칭 실패 푸시 본문에서는 '바로' 를 뺐다. api.md 의 matchingFailed 에는 '밤에 아침으로 미룬 예약은 아침 매칭 전까지 false' 를 더했다. 모두 코드에서 정한 문구다(합친 38건 검토 R9·R15). 코드에서 정한 문구: "FAQ '매니저는 언제 배정되나요?' 답 (V050·mock.js f3): 1회 청소는 예약하면 바로 매니저를 찾기 시작해요. 밤 9시~아침 8시에 한 예약은 아침 8시부터 찾아요. 정기청소는 첫 방문은 바로, 이후 방문은 각 방문 7일 전부터 찾아요. 배정되면 알림으로 알려드려요." / "매칭 실패 푸시 MATCHING_FAILED 본문 (PushCopy): 가능한 매니저가 생기면 다시 찾아볼게요. 일정을 바꾸면 다시 찾아요"
- [x] R11: BACKEND_PLAN §5 의 confirm-copy 행에 seriesTermMonths 를, createBooking 행에 frequency·managerAssigned·matchingFailed 를 더하고 'api.md 가 기준' 이라고 적었다. 계획 §6.8 과 domain.md 의 정기 취소 시트 문구는 'N시간(= cancel_deadline_hours)' 으로 고쳤다. PROGRESS.md 470·771행은 남아 있다(합친 38건 검토 R11).
- [x] R18: BookingFlowIT 주석의 SeriesDispatcher 시각을 03:00 에서 09:00 으로 고쳤다. domain.md §6.8 의 시각은 이미 09:00 이었고, 같은 절의 취소 시트 고정 문구는 R11 커밋에서 고쳤다. PROGRESS.md 395행은 남아 있다(합친 38건 검토 R18).
- [x] R8: 매칭에 실패한 수리 예약의 완료 화면은 🔔 안내를 숨겨도 머리글 설명이 '기사 배정 완료 시 알림으로 바로 알려드릴게요' 를 계속 약속했다. 이제 실패한 예약이면 새 글자 없이 Figma 설명의 첫 문장('방문 일정과 주소를 확인해 주세요.', mock.js REPAIR_COMPLETE_CHECK)만 보이고, 테스트가 실패·배정 전 두 경우의 머리글을 본다 (합친 38건 검토 R8). 코드에서 정한 문구: "매칭 실패 시 Figma 설명의 첫 문장만 — 수리 완료 화면 머리글 '방문 일정과 주소를 확인해 주세요.' (mock.js REPAIR_COMPLETE_CHECK / copy.complete.descriptionNoAssignment, 새 글자 아님)"
- [x] R10: 수리 확인 화면 요약이 만료된 사진까지 'N장 첨부' 로 세고, 증상 상세는 연 채 24시간을 넘기면 사진을 안내 없이 감추며 초안에 남겼다. 새 훅 useFreshPhotos 가 만료 순간(타이머)과 앱 복귀(visibilitychange)에 다시 재서, 증상 상세는 초안을 정리하고 만료 안내를 켜고 확인 화면은 누르기 전이라도 증상 상세로 보내며 요약은 남은 사진만 센다(effect 에서만 상태 변경, 테스트 4건) (합친 38건 검토 R10).
- [x] R12: 콜드 스타트에 FCM 토큰('registration')보다 401 강제 로그아웃이 먼저 끝나면 풀 토큰이 없어 등록 해제를 건너뛰었고, 늦게 온 토큰이 저장된 채 남아 앞 사용자의 알림이 계속 왔다. 두 앱 pushClient 가 forgetOnRegistration 표시를 남겨 로그아웃된 채 온 토큰은 저장하지 않고 unregister 하며, 다음 로그인 때 새 토큰을 받는다(두 앱 테스트) (합친 38건 검토 R12).
- [x] R13: Android 플러그인의 unregister 가 FCM 토큰 삭제를 기다리지 않아, 로그아웃 직후 다시 로그인하면 곧 무효가 될 옛 토큰이 등록돼 새 사용자가 앱을 다시 켤 때까지 푸시를 못 받았다. 두 앱 pushClient 가 마지막으로 푼 토큰을 기억해 같은 값이 오면 등록하지 않고 5초 뒤 register() 를 한 번 더 부른다(두 번째 답은 받아들임, iOS 는 그대로 — 한계는 주석, 네이티브 수정 없음) (합친 38건 검토 R13).
- [x] R16: 매칭 실패 표시 없이 취소된 수리 예약의 완료 화면을 다시 열면 '배정 중' 과 배정 안내가 보였다. 예약 응답(BookingResponse)에 cancelled 를 더하고(api.md → 서버 → 앱·가짜 서버, 마이그레이션 없음), 완료 화면은 매칭 실패·취소면 서버 매니저 칸('배정 전 취소')을 보이고 🔔 안내와 머리글 뒷문장을 같은 조건으로 숨긴다(서비스·컨트롤러 테스트, BookingFlowIT 는 IT 미실행) (합친 38건 검토 R16). 코드에서 정한 문구: "취소된 예약도 매칭 실패와 같이 수리 완료 화면 머리글은 Figma 설명의 첫 문장만('방문 일정과 주소를 확인해 주세요.'), 기사 배정 행은 서버 cleaner('배정 전 취소' — 기존 BookingMapper 문구), 🔔 안내 숨김"
- 남은 한계(코드로 막지 못한 것): ① 앱을 켜자마자 저장된 세션이 401 로 끊기고 그 처리가 푸시 모듈의 세션 구독보다 먼저 끝나면 기기 등록 해제를 놓칠 수 있다(세션 모듈 구조 변경이 필요해 보류) ② Android 푸시 플러그인은 토큰 삭제 완료를 기다리지 않아, 재로그인 때 5초 뒤 한 번 더 받는 우회만 했다(네이티브 수정 금지) ③ 새로 쓰거나 고친 통합 테스트(MatchingIT·PenaltyWaiveIT·SupportIT·BookingFlowIT·LiveSessionIT·IdempotencyIT)는 Docker 가 없어 **실행하지 못했다** — CI 나 Docker 가 있는 컴퓨터에서 `./mvnw verify` 로 확인해야 한다.
