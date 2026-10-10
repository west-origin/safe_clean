# 안심클린 앱 제작 계획 (2026-09-18 승인됨)

작성일: 2026-09-18
원본 스펙: `~/Desktop/figma-to-react.md`
Figma: `oByA7xoimyX0pPaS7aJpuo` (Page 1 = 고객용, `청소매니저 앱` = 매니저용)

> 승인된 결정: JavaScript(JSX) · CSS Modules · React 웹 + Capacitor(iOS/Android) · LiveKit(실시간 송출 + 서버 녹화). 단계마다 보고 후 다음 단계로 간다.
> 진행 상태는 `PROGRESS.md`에 항목 단위로 기록한다 (아직 없음 = 처음 시작).

---

## 0. 전제: 유지보수 가능한 언어 범위

유지보수 담당자가 다룰 수 있는 언어는 **HTML / CSS / JavaScript / React / React Native / WebRTC** 뿐이다.
따라서 아래 원칙으로 스택을 고른다.

| 원칙 | 결정 |
|---|---|
| 새 언어 없음 | TypeScript 안 씀. **JavaScript + JSX** (`.jsx`). 편집기 힌트는 JSDoc 주석으로 |
| 새 스타일 문법 없음 | Tailwind 안 씀. **CSS Modules + CSS 변수**만 사용 (순수 CSS) |
| 서버 | ~~Node.js~~ → **Java 21 + Spring Boot** + Supabase + LiveKit Cloud (2026-09-19 변경 — 백엔드 담당자가 Java·Spring Boot·Supabase 만 다룸. 프론트엔드 담당자는 `apps/*/src/api/*.js` 만 보면 된다). 사양은 `BACKEND_PLAN.md` |
| 도구 최소화 | Vite + React + react-router. 상태 관리 라이브러리 없이 Context/hook. 필요해지면 그때 추가 |
| 네이티브 코드 없음 | 앱 패키징은 **Capacitor** (웹 빌드를 그대로 iOS/Android 앱으로 감쌈). Swift/Kotlin 파일을 만지지 않음 |

### 왜 React Native가 아니라 React 웹 + Capacitor인가
- Figma 스펙 자체가 웹 전제다 (CSS 변수, `:active`, `:focus-visible`, `<video>`, `word-break: keep-all`). RN에는 이 개념이 없어 스펙을 통째로 번역해야 한다.
- WebRTC: 브라우저 API(`getUserMedia`, `RTCPeerConnection`)를 그대로 쓴다. RN은 `react-native-webrtc` + 네이티브 빌드 설정이 필요하다.
- 하나의 코드로 웹(PWA) + iOS + Android가 나온다.
- 단점: 백그라운드(화면 꺼짐) 상태에서 카메라 송출이 멈춘다 → 매니저 앱은 촬영 중 화면 유지(Keep Awake)로 대응. 단계 5에서 실기기 검증.

---

## 1. Figma 실제 조사 결과 (스펙과의 차이)

### 변수 컬렉션 `Design Tokens` — 32개 ✅
- 색상 **19개**: 스펙 목록 18개 + **`color/surface/field` (#f2f2f2)** 가 추가로 존재
- radius 4개 (xs 5 / sm 10 / md 20 / pill 32) ✅
- spacing 9개: `2xs 4 / xs 6 / sm 8 / md 10 / lg 12 / xl 16 / 2xl 18 / 3xl 20 / 4xl 24` (스펙엔 값만 있었고 실제 이름은 이것)
- 모드 이름은 `Default`가 아니라 **`Mode 1`** 하나
- 각 변수에 scope가 지정돼 있음 (예: `color/text/*` → TEXT_FILL). 생성물 주석에 반영
- Effect style / Paint style: 없음

### 텍스트 스타일 — 23개 ✅
- 전부 Noto Sans KR, 굵기 Regular/Medium/Bold만 사용 ✅
- **모든 스타일의 line-height가 `AUTO`** → 코드에서 숫자로 정해야 함 (판단 항목, §5)

### 컴포넌트 (고객용 Page 1, 36개)
스펙에 있던 것: `StatusBar`, `TabBar`(Active=**Home|Repair|Reservations|Settings**), `RadioOption`(Selected|Default), `Chip`, `NoticeChip`(State=Default|Selected + **Icon INSTANCE_SWAP**), `StepProgress`(Step=**1~8**), `Button/Primary`(Default|Pressed|Disabled|Loading), `Field/Text`(5 state + **Label/Placeholder TEXT prop**), `Live/CircleControl`, `Live/PillControl`, `Live/LabeledControl`
스펙에 없던 것: `ServiceCategoryList`, `GiftBanner`, `NoticeIcon/*` **18개**, `InfoIcon/*` 4개, `_Keyboard/iOS`(구현 안 함)

### 컴포넌트 (매니저 페이지, 11개)
`Manager/TabBar`(Active 5종 + Show Call Badge BOOLEAN + Call Badge Count TEXT), `Manager/StatusBadge`(7종), `Manager/JobCard`(CTA Label), `Manager/CallCard`(Expiry/Accept Label/Reject Label), `Manager/ScheduleCard`, `Manager/ChecklistItem`(Checked|Unchecked + Label), `Manager/ListItem`(Label), `Manager/NavBar`(Title), `Manager/CameraStatusItem`(OK|Issue + Label/Value), **`Button/Secondary`, `Button/Destructive`** (Label + 3 state — 매니저 페이지에 있음)

### 화면
- 매니저 22개 ✅ (플로우 19 + 빈 상태 3)
- 고객용 프레임 55개. 그러나 **예약 플로우 9화면이 4개 카테고리(Home/Office/Appliance/MoveIn)에 복제**돼 있음 → 카테고리를 파라미터로 받는 **1개 플로우로 구현** (36 → 9). 실제로 동일한지는 단계 4에서 메타데이터 diff로 확인
- 고유 화면 수: 예약 9 + 온보딩/인증 11 + 메인탭 5 + 라이브 3 = **28개**
- 구버전 2개(`(구버전 · 사용 안 함)`)는 LiveService 섹션 안에, 최신 `Service/LiveLandscape`(852×480)·`Service/LivePortrait`는 페이지 최상위에 있음 ✅

### 프로토타입 연결
매니저 68개 + 고객용 58개 = **126개** (스펙의 68은 매니저 페이지 수치)

---

## 2. 프로젝트 구조

```
청소앱 프론트엔드/
├─ package.json              npm workspaces (아래 3개 패키지를 한 번에 설치/실행)
├─ PROGRESS.md               진행 상태 (항목 단위 갱신)
├─ PLAN.md                   이 문서
├─ design/
│   ├─ figma-tokens.json     Figma 변수 32개 + 텍스트 스타일 23개 원본 덤프 (손으로 안 고침)
│   └─ figma/dump-tokens.figma.js  Figma에서 위 JSON을 읽어오는 스크립트
├─ scripts/
│   ├─ build-tokens.js       JSON → CSS/JS 생성기 (node scripts/build-tokens.js)
│   └─ check-tokens.js       하드코딩 hex/px 검사 (npm run check:tokens)
├─ packages/ui/              디자인 시스템 (두 앱 공용, 순수 React+CSS)
│   └─ src/
│       ├─ tokens/           tokens.css · typography.css · tokens.js  ← 전부 생성물
│       ├─ fonts.css         Noto Sans KR 400/500/700 (self-host, 한글 서브셋)
│       ├─ components/       Button/ Field/ Chip/ … (각각 .jsx + .module.css)
│       ├─ icons/            SVG 아이콘 (currentColor)
│       └─ assets/           래스터 3D 아이콘 (webp @1x/2x/3x)
├─ apps/customer/            고객용 앱 (Vite + React)
│   └─ src/
│       ├─ routes.jsx        라우팅 (프로토타입 연결 기준)
│       ├─ screens/          auth/ onboarding/ tabs/ booking/ live/
│       ├─ api/              데이터 어댑터 (처음엔 mock, 나중에 실서버로 교체)
│       ├─ live/             useLiveSession() — WebRTC 훅 (단계 5)
│       └─ dev/Gallery.jsx   컴포넌트 갤러리 페이지 (/dev/gallery)
├─ apps/manager/             매니저 앱 (동일 구조)
└─ backend/                  (단계 7) Java 21 + Spring Boot — 유일한 API 서버 (BACKEND_PLAN.md)
```

- `npm install` 한 번, `npm run dev:customer` / `npm run dev:manager`로 실행.
- Storybook 대신 **앱 안의 갤러리 페이지**(`/dev/gallery`)를 쓴다. 도구 하나를 덜 배워도 되고, 실제 앱과 같은 환경에서 보인다.

### 추가한 패키지와 이유
| 패키지 | 이유 | 추가 시점 |
|---|---|---|
| `@fontsource/noto-sans-kr` | Noto Sans KR self-host (한글 서브셋, swap 포함) | 단계 2 |
| `react-router` | 화면 라우팅 + 갤러리 라우트. 프로토타입 연결 126개를 라우트로 옮기는 데 필요 | 단계 3-a |
| `@capacitor/core` `@capacitor/ios` `@capacitor/android` (+ dev `@capacitor/cli`) | 웹 앱을 iOS/Android 앱으로 감싼다. 두 앱(customer, manager) 각각 | 단계 6 |
| `livekit-client` | 고객 시청·매니저 바디캠 프리뷰: LiveKit 방 연결 + 트랙을 `<video>` 에 붙임. 두 앱 각각, `live/roomConnector.js` 에서만 import (발행 없음 — 영상은 바디캠 → Ingress) | 단계 B5 |
| `@capacitor/push-notifications` | 푸시 권한·기기 토큰(APNs/FCM)·알림 탭 이벤트. 두 앱 각각, `push/pushClient.js` 에서만 import. 웹에서는 동작하지 않는다 | 단계 B6 |
| `@capacitor-community/keep-awake` | 매니저 앱이 휴대폰 카메라로 송출하는 동안 화면이 꺼지지 않게(꺼지면 WebView 카메라가 멈춰 송출이 끊긴다). 매니저 앱만, `live/liveClient.js` 에서만 import (2026-10-10, BACKEND_PLAN §8.0) | 단계 5 |
| `capacitor-secure-storage-plugin` | 네이티브 앱의 로그인 세션 저장(iOS Keychain / Android Keystore 암호화). WKWebView 의 localStorage 는 저장 공간 압박 때 지워질 수 있어 사용자가 이유 없이 로그아웃됐다(BACKEND_PLAN 결정 10: B7 전 이전). 처음엔 `@capacitor/preferences`(평문 UserDefaults/SharedPreferences)였으나 보안 검토(2026-09-22)에서 탈옥·루팅 기기·iOS 백업에서 토큰이 읽히는 문제로 교체. 두 앱 각각 `api/session.js` 에서만 import, 웹은 localStorage 그대로 | 2026-09-22 |

---

## 3. 단계별 계획

### 단계 0 — 부트스트랩 (단계 1과 같이 진행)
- `git init`, `.gitignore`, `PROGRESS.md` 생성
- npm workspaces + Vite React 템플릿 2개 + `packages/ui`
- ESLint(JS), Prettier, Vitest 설정
- 커밋: `chore: 프로젝트 부트스트랩 (단계 0)`

### 단계 1 — 토큰 파이프라인
1. Figma MCP로 변수 32개를 읽어 `design/figma-tokens.json`에 저장 (덤프 스크립트 포함)
2. `scripts/build-tokens.js` (Node) 가 JSON을 읽어 생성:
   - `tokens.css`: `:root[data-theme="default"] { --color-brand-primary: #2284fc; … }`
     → 나중에 다크 모드가 생기면 `[data-theme="dark"]` 블록만 추가하면 됨
   - `tokens.js`: JS에서 토큰 이름을 참조할 때용 (`tokens.color.brand.primary` → `var(--color-brand-primary)`)
   - 이름 규칙: Figma `color/brand/primary` → CSS `--color-brand-primary` (기계 변환, 예외 없음)
3. `scripts/check-tokens.js`: `packages/*/src`, `apps/*/src`에서 `#hex`, `font-size: NNpx` 직접 사용을 찾으면 실패 (tokens 폴더 제외). `npm run check:tokens`
4. 재실행: `npm run tokens` (JSON → CSS 재생성). Figma가 바뀌면 덤프 단계만 다시 돌리면 됨
5. 보고 후 정지

### 단계 2 — 타이포그래피
- `@fontsource/noto-sans-kr` (400/500/700) self-host — 유니코드 범위별 한글 서브셋 + `font-display: swap` 이 이미 포함. 앱으로 감쌌을 때 외부 요청 없이 동작
- fallback: `"Noto Sans KR", "Apple SD Gothic Neo", "Malgun Gothic", system-ui, sans-serif`
- `typography.css`: 텍스트 스타일 23개 → `.text-heading-xl` 같은 클래스 + `--font-size-*` 변수 (역시 JSON → 생성)
- 본문 계열: `word-break: keep-all; overflow-wrap: break-word`
- line-height AUTO → `--line-height-auto: 1.45` 한 곳에서 관리 (판단 항목)
- 보고 후 정지

### 단계 3 — 프리미티브 (3회 나눠 보고)
| 회차 | 컴포넌트 |
|---|---|
| 3-a | `Button` variant=primary/secondary/destructive, `disabled`, `loading` → 보고 |
| 3-b | `TextField` (Default/Focused/Filled/Error/Disabled, Error = 빨간 테두리 + 헬퍼 텍스트 + `aria-invalid`) → 보고 |
| 3-c | 고객용: `StatusBar` `TabBar` `RadioOption` `Chip` `NoticeChip` `StepProgress` `ServiceCategoryList` `GiftBanner` `NoticeIcon`(18) `InfoIcon`(4) / 라이브: `CircleControl` `PillControl` `LabeledControl` (오버레이형·하단바형 분리 유지) / 매니저: `ManagerTabBar` `StatusBadge` `CameraStatusItem` `ChecklistItem` `CallCard` `JobCard` `ScheduleCard` `ListItem` `NavBar` / 공통: `Sheet`(바텀시트), `Skeleton` → 보고 |

- Figma variant → props 1:1. Pressed → `:active`, Focused → `:focus-visible`
- 모든 탭 가능 요소 `min-height: 44px` (`--touch-target` 토큰)
- 각 컴포넌트마다 갤러리 항목 + Vitest 테스트 1개 이상(상태 렌더링, Error 텍스트 존재)

### 단계 4 — 화면 (3~4개씩 13회 보고)
| 회차 | 화면 |
|---|---|
| M1 | Manager/Home, Home/Empty, Call/List, Call/Empty |
| M2 | Call/Detail, Call/RejectSheet, Job/Detail, Job/Preparing |
| M3 | Job/Preparing/Error, Job/InProgress, Job/PauseSheet, Job/Paused |
| M4 | Job/CompleteConfirm, Job/Complete, Settlement, Settlement/Detail |
| M5 | Schedule, Schedule/Empty, Schedule/Availability, Schedule/AddUnavailable |
| M6 | Profile, Profile/Equipment |
| C1 | Onboarding/Slide1~3, Auth/SignUp |
| C2 | Auth/TermsConsent, LoginPhone, ProfileInfo-Phone, ProfileInfo-Social |
| C3 | Auth/ProfileAddress, ProfileHousing, SignUpComplete, Home/Standard-A |
| C4 | Home/Realtime, Repair, Reservations, Settings |
| C5 | Booking/Frequency, DateTime, Step2, AddressSearch (카테고리 4종 파라미터) |
| C6 | Booking/AddressConfirm, Options, NotesRequests, Confirm |
| C7 | Booking/Complete, Service/Start, Service/LivePortrait, Service/LiveLandscape (영상은 `<video>` 자리만) |

- 라우팅은 프로토타입 연결 126개에서 추출
- 빈 상태 3개는 조건부 렌더링
- 화면은 `api/` mock 어댑터에서 데이터를 받음 → 실서버 연결 시 어댑터만 교체

### 단계 5 — 라이브 (WebRTC) — Figma 스펙 밖, 별도 승인
역할: **매니저 = 송출**(후면 카메라+마이크) / **고객 = 시청**(음소거·캡처·나가기)

| 선택지 | 내용 | 유지보수 부담 |
|---|---|---|
| A. LiveKit (권장) | JS SDK `livekit-client`. Node 토큰 서버 약 50줄. TURN·녹화·다중 시청 내장. 오픈소스라 나중에 자체 서버로 옮길 수 있음 | 낮음 |
| B. 직접 P2P | `RTCPeerConnection` + Node(Socket.IO) 시그널링 + TURN 서버(coturn) 직접 운영 | 높음. 녹화·되감기 불가 |

- 두 방식 모두 `useLiveSession()` 훅 뒤에 숨긴다. 화면 코드는 방식이 바뀌어도 그대로
- 고객 `Capture` = `<video>` → canvas → 이미지 저장. `Mute` = 오디오 트랙 토글. 매니저 `Pause` = 트랙 일시정지 + 고객 화면에 Paused 상태 표시
- **확인 필요**: `Back15`/`Forward15`(15초 되감기/빨리감기)는 순수 라이브에서는 불가능. 서버 녹화(A안 Egress)가 전제. 의도 확인 요망
- 매니저 촬영 중 화면 꺼짐 방지: Capacitor Keep Awake
- **진행 상황(2026-09-19)**: 사용자 결정으로 실제 연결은 미루고 **구조만** 잡음 — `apps/*/src/live/{liveClient.js,useLiveSession.js,README.md}`, 서버 쪽은 `backend/` 의 `live` 패키지(단계 B5). 나머지는 각 파일의 `TODO(stage5)` 체크리스트. 결정(2026-09-19): LiveKit Cloud, 녹화는 Egress → Supabase Storage, 바디캠 Ingress

### 단계 6 — 앱 패키징 (완료 2026-09-19)
- Capacitor로 `apps/customer`, `apps/manager` 각각 iOS/Android 프로젝트 생성 (설정 파일만, 네이티브 코드 수정 없음) ✅
- 카메라/마이크 권한 문구 ✅ · 가로 모드(기본 허용) ✅ · 앱 아이콘/스플래시(로고 에셋 오면) · 폰트 번들 13MB → 1.7MB ✅
- 실기기에서 WebRTC 검증 → 단계 5 실제 연결 후

### 점검 (2026-09-19)
- 백엔드 전 전체 코드 리뷰 19개 항목 처리 완료 (PROGRESS.md "점검"). 화면은 전부 `useAsync` 로 오류·로딩을 처리하므로 실서버는 `api/*.js` 함수 안만 바꾸면 된다.

### 단계 7 — 백엔드 (승인 2026-09-19) → `BACKEND_PLAN.md`
- Spring Boot 4.0.x / Java 21 이 유일한 API(화면 하나 = 엔드포인트 하나, 응답 = `mock.js` 모양). Supabase = Postgres(Flyway)·Auth(비밀번호·JWT)·Storage(녹화본). 인증번호는 Spring 이 만들고 국내 문자 API(Solapi)로 보낸다. 영상은 별도 바디캠 → LiveKit Ingress, 녹화는 Egress → Storage.
- 회차 B0~B8 은 `PROGRESS.md` "백엔드" 절. 기존 Node `server/` 는 B1 에서 삭제.
- 결정 완료: 소셜 로그인 없음(휴대폰만) · 정기청소 한 달 단위 · 결제는 구조만(`PaymentGateway` + Noop) · 탈퇴 이력 5년 · 바디캠 영상만. 대기: 바디캠 사양(공장 답변), 수리 전용 주문 모델.

---

## 4. 품질 게이트 (매 단계 보고 시 확인)

| 검사 | 방법 |
|---|---|
| 하드코딩 hex/px 폰트 0건 | `npm run check:tokens` (CI 실패 조건) |
| 화면이 프리미티브만 사용 | 화면 폴더에서 `.module.css`에 색·폰트 선언 금지 검사 |
| variant ↔ props 누락 | 갤러리 페이지에 Figma variant 전부 나열 + 보고서 누락 목록 |
| 한글 줄바꿈 | 본문 클래스에 `keep-all` (typography.css 생성 시 강제) |
| 터치 타겟 44px | Vitest에서 인터랙티브 컴포넌트 `min-height` 검사 |
| Error = 색 + 텍스트 | TextField 테스트 |
| 진행 기록 | `PROGRESS.md` 항목 단위 갱신 + 항목마다 커밋 |

---

## 5. Figma에 없어서 내가 판단할 항목 (보고 대상)

| 항목 | 판단 |
|---|---|
| hover | 모바일 우선이라 미정의. hover 스타일 없음, `:active`만 |
| 터치 타겟 | `--touch-target: 44px` 토큰, 모든 버튼/탭/라디오에 적용 |
| 로딩 스켈레톤 | `Skeleton` 컴포넌트 (`color/surface/subtle` 바탕 + 은은한 shimmer) |
| line-height | 전부 AUTO → 1.45 고정 (Noto Sans KR 기본 메트릭에 근사) |
| 데이터 스키마 | 화면에 보이는 필드만으로 mock 스키마 정의, `api/README.md`에 문서화 |
| 예약 플로우 4종 | 구조 동일 가정 → 파라미터화. 단계 4에서 diff로 확인, 다르면 분기 |
| 반응형 | 393px 기준 고정, 중단점 없음. 큰 화면에서는 중앙 정렬 393px 컨테이너 |
| 스펙과 다른 결정 | TypeScript → JavaScript, Tailwind → CSS Modules (유지보수 언어 제약 때문) |

## 6. 에셋 TODO (Figma에 원본 없음)
- `BrandLogo (로고 이미지 에셋 필요)`, `Mascot (일러스트 에셋 필요)`, `Illustration (캘린더+청소도구)` → 플레이스홀더 컴포넌트
- `(교체필요: …)` 아이콘 15개 → 임시 에셋 사용 + TODO 주석 (단계 3에서 실제 목록 추출)

---

## 7. 승인 전 결정 사항

1. 언어: **JavaScript(JSX)** 로 진행 (TypeScript 제외) — 동의?
2. 스타일: **CSS Modules + CSS 변수**, Tailwind 제외 — 동의?
3. 플랫폼: **React 웹 + Capacitor** (React Native 아님) — 동의?
4. 라이브: **LiveKit** 방향으로 단계 5 설계 — 동의? (단계 1~4엔 영향 없음, 나중에 바꿔도 됨)
5. `Back15 / Forward15` 의 의도 (녹화 영상 되감기인지, 다른 의미인지)

승인되면 **단계 0 + 단계 1**만 실행하고 보고한다.
