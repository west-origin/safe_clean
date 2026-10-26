// 매니저 앱 라우팅. 경로 = Figma 프레임 이름의 kebab-case.
// Figma 프로토타입 연결(68개)에서 뽑은 화면 이동 표:
//   Home ─ Button/Primary → Job/InProgress · Button/Secondary(콜 확인하기) → Call/List
//   Call/List ─ CallCard → Call/Detail · 수락 → Job/Detail · 거절 → Call/RejectSheet
//   Call/Detail ─ Back → Call/List · 거절 → Call/RejectSheet · 수락 → Job/Detail
//   Call/RejectSheet ─ 거절하기 → Call/List
//   Job/Detail ─ Back → Home · Primary → Job/Preparing
//   Job/Preparing ─ Back → Job/Detail · Secondary → Job/Preparing/Error · Primary → Job/InProgress
//   Job/Preparing/Error ─ Back → Job/Detail · Primary → Job/Preparing
//   Job/InProgress ─ Secondary → Job/PauseSheet · Primary → Job/CompleteConfirm
//   Job/PauseSheet ─ Primary → Job/Paused · Job/Paused ─ Primary → Job/InProgress
//   Job/CompleteConfirm ─ Secondary → Job/InProgress · Primary → Job/Complete
//   [코드에서 정함] Job/InProgress ─ 청소 전 사진 찍기 → /job/photo · 청소 완료(청소 후 사진 빠짐) → /job/photos/after ─ 위치 → /job/photo?id= · 청소 완료 → CompleteConfirm
//   Job/Complete ─ Secondary → Settlement · Primary → Home
//   Schedule ─ Secondary → Schedule/Availability · Schedule/Availability ─ Back → Schedule · Secondary → Schedule/AddUnavailable
//   Schedule/AddUnavailable ─ Primary → Schedule/Availability
//   Settlement ─ SettlementCard → Settlement/Detail · Settlement/Detail ─ Back → Settlement
//   Profile ─ ListItem → Schedule/Availability · ListItem → Profile/Equipment · Profile/Equipment ─ Back → Profile
//   모든 탭 화면 ─ TabBarItem → Home / Call/List / Schedule / Settlement / Profile
//   빈 상태(Home/Empty, Call/Empty, Schedule/Empty)는 라우트가 아니라 데이터가 비었을 때의 조건부 렌더링
//   로그인·회원가입은 Figma 에 없어 코드에서 정함: /auth/login → (회원가입) /auth/signup → /auth/signup/info → /auth/signup/complete → /
//   위 22개 화면은 전부 RequireAuth 안 (로그인 전이면 /auth/login 으로, 로그인 후 원래 주소로 복귀)
import { createBrowserRouter } from 'react-router';
import RouteError from './screens/RouteError.jsx';
import RouteShell from './screens/RouteShell.jsx';
import RequireAuth from './screens/RequireAuth.jsx';

// 화면을 라우트별로 나눠 싣는다 (첫 화면 번들이 작아진다). 데이터 라우터는 화면 코드를 받은 뒤 이동하므로 화면마다 대체 화면을 둘 필요가 없다 (받는 동안은 RouteShell 의 LoadingBar 가 보인다).
// page(() => import('./x.jsx')) 는 라우트의 lazy 함수를 만든다: 그 주소로 처음 갈 때 파일을 받아 default export 를 화면으로 쓴다
const page = (load) => async () => ({ Component: (await load()).default });

// 로그인 없이 볼 수 있는 화면 (코드에서 정함)
const publicRoutes = [
  { path: '/auth/login', lazy: page(() => import('./screens/auth/Login.jsx')) },
  { path: '/auth/reset-password', lazy: page(() => import('./screens/auth/ResetPassword.jsx')) }, // 코드에서 정함 (사용성 검토 2026-09-22)
  { path: '/auth/signup', lazy: page(() => import('./screens/auth/SignUpTerms.jsx')) },
  { path: '/auth/signup/info', lazy: page(() => import('./screens/auth/SignUpInfo.jsx')) },
  { path: '/auth/signup/complete', lazy: page(() => import('./screens/auth/SignUpComplete.jsx')) },
  { path: '/auth/terms/:id', lazy: page(() => import('./screens/auth/TermDetail.jsx')) },
];

// 로그인이 필요한 화면 (Figma 매니저 22화면 + 코드에서 정한 프로필 아래 화면)
// /job/in-progress·/job/paused·/job/complete 는 /job/:id 와 모양이 겹치지만, react-router 는 목록 순서가 아니라
// 경로 점수로 고르므로 고정 글자 경로가 :id 보다 먼저 잡힌다. /profile/notices 등과 /profile/:key 도 같다.
const protectedRoutes = [
  { path: '/', lazy: page(() => import('./screens/home/Home.jsx')) }, // M1 Manager/Home (+Empty)
  { path: '/call', lazy: page(() => import('./screens/call/CallList.jsx')) }, // M1 Manager/Call/List (+Empty)
  { path: '/call/:id', lazy: page(() => import('./screens/call/CallDetail.jsx')) }, // M2 Manager/Call/Detail (+RejectSheet, ?reject=1 로 바로 열기)
  { path: '/job/:id', lazy: page(() => import('./screens/job/JobDetail.jsx')) }, // M2 Manager/Job/Detail
  { path: '/job/:id/preparing', lazy: page(() => import('./screens/job/JobPreparing.jsx')) }, // M2 Manager/Job/Preparing
  // 아래 /preparing/error 는 지금 옛 주소 호환용이다: JobPreparingError 가 /job/:id/preparing 으로 바로 보내고, 장비 오류 모습은 JobPreparing 이 그린다
  { path: '/job/:id/preparing/error', lazy: page(() => import('./screens/job/JobPreparingError.jsx')) }, // M3 Manager/Job/Preparing/Error
  { path: '/job/in-progress', lazy: page(() => import('./screens/job/JobInProgress.jsx')) }, // M3 Manager/Job/InProgress (+PauseSheet)
  { path: '/job/paused', lazy: page(() => import('./screens/job/JobPaused.jsx')) }, // M3 Manager/Job/Paused
  { path: '/job/complete', lazy: page(() => import('./screens/job/JobComplete.jsx')) }, // M4 Manager/Job/Complete (CompleteConfirm 은 InProgress 안의 Sheet)
  // 청소 전·후 사진 (Figma 에 없음 — 코드에서 정함, 2026-10-23): 진행 화면 '청소 전 사진 찍기' → /job/photo,
  // 진행 화면 '청소 완료'(청소 후 사진이 빠졌을 때) → /job/photos/after → 위치 줄 → /job/photo?id=… → 다 찍으면 '청소 완료' → 진행 화면의 완료 확인 시트
  { path: '/job/photo', lazy: page(() => import('./screens/job/JobPhotoCapture.jsx')) },
  { path: '/job/photos/before', lazy: page(() => import('./screens/job/JobBeforePhotos.jsx')) },
  { path: '/job/photos/after', lazy: page(() => import('./screens/job/JobAfterPhotos.jsx')) },
  { path: '/settlement', lazy: page(() => import('./screens/settlement/Settlement.jsx')) }, // M4 Manager/Settlement
  { path: '/settlement/:id', lazy: page(() => import('./screens/settlement/SettlementDetail.jsx')) }, // M4 Manager/Settlement/Detail
  { path: '/schedule', lazy: page(() => import('./screens/schedule/Schedule.jsx')) }, // M5 Manager/Schedule (+Empty)
  { path: '/schedule/availability', lazy: page(() => import('./screens/schedule/Availability.jsx')) }, // M5 Manager/Schedule/Availability (+AddUnavailable 시트, ?add=1)
  { path: '/profile', lazy: page(() => import('./screens/profile/Profile.jsx')) }, // M6 Manager/Profile
  { path: '/profile/equipment', lazy: page(() => import('./screens/profile/Equipment.jsx')) }, // M6 Manager/Profile/Equipment
  { path: '/profile/penalties', lazy: page(() => import('./screens/profile/Penalties.jsx')) }, // 매니저 정책 (docs/manager-policy.md, 사용자 승인 2026-09-22)
  // Profile 메뉴 중 Figma 에 화면이 없던 것 (코드에서 정함)
  { path: '/profile/notifications', lazy: page(() => import('./screens/profile/Notifications.jsx')) },
  { path: '/profile/notices', lazy: page(() => import('./screens/profile/Notices.jsx')) },
  { path: '/profile/notices/:id', lazy: page(() => import('./screens/profile/NoticeDetail.jsx')) },
  { path: '/profile/support', lazy: page(() => import('./screens/profile/Support.jsx')) },
  { path: '/profile/:key', lazy: page(() => import('./screens/profile/ProfileDetail.jsx')) }, // info · area · identity · education · account
];

// 경로 없는 RequireAuth 라우트로 감싼다: 로그인돼 있으면 자식 화면을(Outlet), 아니면 로그인·가입 화면으로 보낸다
const routes = [...publicRoutes, { element: <RequireAuth />, children: protectedRoutes }];

// 개발 전용: 컴포넌트 갤러리. 프로덕션 빌드에는 포함되지 않는다.
if (import.meta.env.DEV) {
  const { Gallery } = await import('@ansimclean/ui/dev');
  routes.push({ path: '/dev/gallery', element: <Gallery /> });
}

// 화면 렌더 중 에러는 RouteError 가 받고, 없는 주소는 404 화면
routes.push({ path: '*', element: <RouteError notFound /> });
// 모든 화면을 RouteShell(스크롤 맨 위·제목 포커스·로딩 막대·토스트)로 감싼다. App.jsx 가 그리고, 푸시 탭 이동에도 쓴다
export const router = createBrowserRouter([{ element: <RouteShell />, errorElement: <RouteError />, children: routes }]);
