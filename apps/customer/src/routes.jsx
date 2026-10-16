// 고객용 앱 라우팅. 경로 = Figma 프레임 이름의 kebab-case.
// Figma 프로토타입 연결(58개)에서 뽑은 화면 이동 표 — 온보딩·인증·예약 플로우에는 연결이 없어 화면 이름·내용 순서로 정했다 (PROGRESS.md 판단 항목):
//   Home/Realtime ⇄ Home/Standard-A (화면 탭으로 토글)
//   Home/* · ServiceCategoryList ─ Home → Booking/Frequency · Move → MoveIn/Frequency · Appliance → Appliance/Frequency · Office → Office/Frequency
//   Repair ─ RepairCategoryItem 8개 → (Figma 는 예약 Frequency 화면으로 연결)
//   모든 탭 화면 ─ TabBarItem/Home → Home/Realtime · Reservations → Reservations · Settings → Settings (Repair 탭은 연결 없음 → /repair)
//   AddressSearch/AddressConfirm ─ Keyboard (OS 네이티브, 구현 안 함)
//   [코드에서 정한 순서] Onboarding 1→2→3 → Auth/SignUp → Auth/TermsConsent → Auth/ProfileInfo-Phone|Social → Auth/ProfileAddress → Auth/ProfileHousing → Auth/SignUpComplete → Home
//   [코드에서 정한 순서] Booking/Frequency → DateTime → Step2 → AddressSearch → AddressConfirm → Options → NotesRequests → Confirm → Complete (4 카테고리 공통)
//   [코드에서 정한 순서] Service/Start → Service/LivePortrait ⇄ Service/LiveLandscape(전체 화면). 진입 화면은 Figma 에 없음 (PROGRESS.md)
// 여기서 만든 router 는 App.jsx 가 RouterProvider 로 그리고, 푸시 알림을 탭하면 App.jsx 가 router.navigate 로 이동시킨다.
import { createBrowserRouter } from 'react-router';
import RouteError from './screens/RouteError.jsx';
import RouteShell from './screens/RouteShell.jsx';
import RequireAuth from './screens/RequireAuth.jsx';

// 화면을 라우트별로 나눠 싣는다 (첫 화면 번들이 작아진다). 데이터 라우터는 화면 코드를 받을 때까지 이전 화면에 머무르므로 라우트마다 로딩 화면을 두지 않는다.
// 그동안에는 RouteShell 의 상단 진행 막대(LoadingBar)가 보인다.
// page(load) = react-router 의 lazy 함수 모양. 그 주소로 처음 갈 때 load() 로 화면 파일을 받아 default export 를 Component 로 넘긴다.
const page = (load) => async () => ({ Component: (await load()).default });

// 여기부터 공개 화면 (로그인 없이 열린다): 온보딩·가입·로그인·비밀번호 재설정·약관 본문
const routes = [
  { path: '/', lazy: page(() => import('./screens/onboarding/Start.jsx')) }, // C1 Onboarding/Slide1~3 (로그인 상태면 /home, 가입 중이면 남은 화면 — Start.jsx)
  { path: '/signup', lazy: page(() => import('./screens/auth/SignUp.jsx')) }, // C1 Auth/SignUp
  { path: '/auth/terms', lazy: page(() => import('./screens/auth/TermsConsent.jsx')) }, // C2 Auth/TermsConsent
  { path: '/auth/login', lazy: page(() => import('./screens/auth/LoginPhone.jsx')) }, // C2 Auth/LoginPhone
  { path: '/auth/reset-password', lazy: page(() => import('./screens/auth/ResetPassword.jsx')) }, // 코드에서 정함 (사용성 검토 2026-09-22)
  { path: '/auth/profile-info', lazy: page(() => import('./screens/auth/ProfileInfo.jsx')) }, // C2 Auth/ProfileInfo-Phone (?method=phone) · -Social (그 외)
  { path: '/auth/profile-address', lazy: page(() => import('./screens/auth/ProfileAddress.jsx')) }, // C3 Auth/ProfileAddress
  { path: '/auth/profile-housing', lazy: page(() => import('./screens/auth/ProfileHousing.jsx')) }, // C3 Auth/ProfileHousing
  { path: '/auth/complete', lazy: page(() => import('./screens/auth/SignUpComplete.jsx')) }, // C3 Auth/SignUpComplete
  { path: '/terms/:id', lazy: page(() => import('./screens/terms/TermsDetail.jsx')) }, // 약관 본문 (Figma 없음 — 코드에서 정함). 로그인 전에도 본다
  // 로그인 후에만 (RequireAuth)
  {
    element: <RequireAuth />, // path 가 없는 부모 라우트: 아래 children 은 모두 RequireAuth 를 거친다 (로그인 전이면 /signup, 가입 중이면 남은 가입 화면으로)
    children: [
      { path: '/home', lazy: page(() => import('./screens/home/Home.jsx')) }, // C3/C4 Home/Realtime · Home/Standard-A (캐러셀)
      { path: '/repair', lazy: page(() => import('./screens/repair/Repair.jsx')) }, // C4 Repair
      { path: '/reservations', lazy: page(() => import('./screens/reservations/Reservations.jsx')) }, // C4 Reservations
      { path: '/payments/checkout', lazy: page(() => import('./screens/payment/Checkout.jsx')) }, // 코드에서 정함: 회차별 PG 결제
      { path: '/settings', lazy: page(() => import('./screens/settings/Settings.jsx')) }, // C4 Settings
      // Settings 아래 화면 (Figma 없음 — 코드에서 정함)
      { path: '/settings/profile', lazy: page(() => import('./screens/settings/EditProfile.jsx')) },
      { path: '/settings/addresses', lazy: page(() => import('./screens/settings/Addresses.jsx')) },
      { path: '/settings/payment', lazy: page(() => import('./screens/settings/PaymentMethods.jsx')) },
      { path: '/settings/faq', lazy: page(() => import('./screens/settings/Faq.jsx')) },
      { path: '/settings/inquiry', lazy: page(() => import('./screens/settings/Inquiry.jsx')) },
      { path: '/settings/notices', lazy: page(() => import('./screens/settings/Notices.jsx')) },
      { path: '/settings/notices/:id', lazy: page(() => import('./screens/settings/NoticeDetail.jsx')) },
      { path: '/booking/:category/frequency', lazy: page(() => import('./screens/booking/BookingFrequency.jsx')) }, // C5 */Frequency (step 1)
      { path: '/booking/:category/datetime', lazy: page(() => import('./screens/booking/BookingDateTime.jsx')) }, // C5 */DateTime (2)
      { path: '/booking/:category/step2', lazy: page(() => import('./screens/booking/BookingStep2.jsx')) }, // C5 */Step2 (3)
      { path: '/booking/:category/address-search', lazy: page(() => import('./screens/booking/BookingAddressSearch.jsx')) }, // C5 */AddressSearch (4)
      { path: '/booking/:category/address-confirm', lazy: page(() => import('./screens/booking/BookingAddressConfirm.jsx')) }, // C5 */AddressConfirm (5)
      { path: '/booking/:category/options', lazy: page(() => import('./screens/booking/BookingOptions.jsx')) }, // C6 */Options (6)
      { path: '/booking/:category/notes', lazy: page(() => import('./screens/booking/BookingNotesRequests.jsx')) }, // C6 */NotesRequests (7)
      { path: '/booking/:category/confirm', lazy: page(() => import('./screens/booking/BookingConfirm.jsx')) }, // C6 */Confirm (8)
      { path: '/booking/:category/complete', lazy: page(() => import('./screens/booking/BookingComplete.jsx')) }, // C6 */Complete
      // 수리 예약 (Figma rGCjx3zWMBk18fXgNZ9UYX, 2026-09-21). 가전수리(appliance)만 appliance·symptom 단계, 나머지 카테고리는 detail 부터
      { path: '/repair/booking/:category/appliance', lazy: page(() => import('./screens/repair/booking/RepairAppliance.jsx')) }, // 가전수리 종류 선택 (1:493)
      { path: '/repair/booking/:category/symptom', lazy: page(() => import('./screens/repair/booking/RepairSymptom.jsx')) }, // 고장 증상 선택 (1:515)
      { path: '/repair/booking/:category/detail', lazy: page(() => import('./screens/repair/booking/RepairDetail.jsx')) }, // 증상 상세와 사진 첨부 (1:455)
      { path: '/repair/booking/:category/address', lazy: page(() => import('./screens/repair/booking/RepairAddress.jsx')) }, // 서비스 주소 확인 (1:424)
      { path: '/repair/booking/:category/datetime', lazy: page(() => import('./screens/repair/booking/RepairDateTime.jsx')) }, // 방문 날짜와 시간 선택 (1:356)
      { path: '/repair/booking/:category/notes', lazy: page(() => import('./screens/repair/booking/RepairNotes.jsx')) }, // 현장 상황과 요청사항 (1:326)
      { path: '/repair/booking/:category/confirm', lazy: page(() => import('./screens/repair/booking/RepairConfirm.jsx')) }, // 예약 내용 최종 확인 (1:273)
      { path: '/repair/booking/:category/complete', lazy: page(() => import('./screens/repair/booking/RepairComplete.jsx')) }, // 예약 완료 (1:236)
      { path: '/service/start', lazy: page(() => import('./screens/service/ServiceStart.jsx')) }, // C7 Service/Start
      { path: '/service/live', lazy: page(() => import('./screens/service/ServiceLivePortrait.jsx')) }, // C7 Service/LivePortrait (최신)
      { path: '/service/live/landscape', lazy: page(() => import('./screens/service/ServiceLiveLandscape.jsx')) }, // C7 Service/LiveLandscape (최신)
    ],
  },
];

// 개발 전용: 컴포넌트 갤러리. 프로덕션 빌드에는 포함되지 않는다.
if (import.meta.env.DEV) {
  const { Gallery } = await import('@ansimclean/ui/dev'); // 모듈 최상위 await — 갤러리 코드를 받은 뒤에 아래 router 를 만든다
  routes.push({ path: '/dev/gallery', element: <Gallery /> });
}

// 화면 렌더 중 에러는 RouteError 가 받고, 없는 주소는 404 화면
routes.push({ path: '*', element: <RouteError notFound /> });
// 맨 바깥 RouteShell(화면 전환 때 스크롤·제목·포커스, 로딩 막대, 토스트)이 모든 화면을 감싼다
export const router = createBrowserRouter([{ element: <RouteShell />, errorElement: <RouteError />, children: routes }]);
